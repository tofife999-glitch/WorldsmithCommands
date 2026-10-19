package worldsmith;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.ImmigrationPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.econ.GroundPoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.groundbattle.GBUtils;
import exerelin.utilities.NexConfig;
import worldsmith.commands.PopCap;

import java.util.ArrayList;
import java.util.List;

/**
 * Gradual, self-expiring faction effects for strengthenfaction / weakenfaction / factionpower.
 * Everything here uses timers the game or Nexerelin already run (timed stability modifiers, expiring memory
 * keys, industry disruption, unrest decay, Nex's own point/pool refills). Nothing is permanent.
 */
public final class FactionEffects {
    public static final float MONTH = 30f;
    /** Marker for a Worldsmith growth freeze (expires together with the freeze). */
    public static final String MEM_FREEZE = "$worldsmith_growthFreeze";
    public static final String MEM_GARRISON = "$worldsmith_garrisonDamaged";
    public static final String MEM_DISRUPT = "$worldsmith_disrupted_";
    public static final String MEM_WEAKENED = "$worldsmith_weakened";
    public static final String MEM_STRENGTHENED = "$worldsmith_strengthened";
    private static final String[] WEAKEN_SOURCES = {
            "worldsmith_weaken_y1", "worldsmith_weaken_y2", "worldsmith_weaken_y3"};
    private static final String[] STRENGTHEN_SOURCES = {
            "worldsmith_strengthen_m6", "worldsmith_strengthen_y1", "worldsmith_strengthen_y2", "worldsmith_strengthen_y3"};
    private static final float GARRISON_DAMAGE = 0.8f;

    private FactionEffects() {
    }

    public static List<MarketAPI> colonies(FactionAPI f) {
        List<MarketAPI> out = new ArrayList<>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.getFaction() == f && !m.isPlanetConditionMarketOnly()) out.add(m);
        }
        return out;
    }

    public static String months(float days) {
        int mo = Math.max(1, Math.round(days / MONTH));
        return mo + (mo == 1 ? " month" : " months");
    }

    private static boolean hasTag(Industry ind, String tag) {
        return ind.getSpec() != null && ind.getSpec().hasTag(tag);
    }

    private static boolean isDefense(Industry ind) {
        return hasTag(ind, Industries.TAG_STATION) || hasTag(ind, Industries.TAG_GROUNDDEFENSES);
    }

    private static boolean isMilitary(Industry ind) {
        return hasTag(ind, Industries.TAG_MILITARY) || hasTag(ind, Industries.TAG_PATROL) || hasTag(ind, Industries.TAG_COMMAND);
    }

    private static boolean isShipIndustry(Industry ind) {
        return hasTag(ind, Industries.TAG_HEAVYINDUSTRY);
    }

    /** Sets a boolean marker that expires after the given days (keeping a longer existing one). */
    private static void mark(MemoryAPI mem, String key, float days) {
        float d = days;
        if (mem.contains(key)) d = Math.max(d, mem.getExpire(key));
        mem.set(key, true, d);
    }

    /** Resets progress towards the next size, the same way Nexerelin does for delayed growth. */
    private static void resetGrowthProgress(MarketAPI m) {
        ImmigrationPlugin plugin = Misc.getImmigrationPlugin(m);
        if (plugin == null) return;
        m.getPopulation().setWeight(plugin.getWeightForMarketSize(m.getSize()));
        m.getPopulation().normalize();
    }

    private static String join(List<String> parts) {
        return parts.isEmpty() ? "no change" : String.join(", ", parts);
    }

    // ------------------------------------------------------------------ weaken

    /** Weakens one colony; with apply=false it only describes what would happen. */
    public static String weakenColony(MarketAPI m, int level, boolean apply) {
        List<String> out = new ArrayList<>();
        MemoryAPI mem = m.getMemoryWithoutUpdate();

        if (apply) RecentUnrest.get(m, true).add(2, "Civil unrest");
        out.add("unrest +2 (fades over 6 months)");

        if (level >= 2) {
            float freeze = level >= 3 ? 36 * MONTH : 6 * MONTH;
            if (m.isPlayerOwned()) {
                out.add("no growth freeze (player colony)");
            } else if (mem.contains(PopCap.MEM_GROWTH_LIMIT) && !mem.contains(MEM_FREEZE)) {
                out.add("no growth freeze (it has a popcap)");
            } else {
                if (apply) {
                    float d = Math.max(freeze, mem.contains(MEM_FREEZE) ? mem.getExpire(MEM_FREEZE) : 0f);
                    mem.set(PopCap.MEM_GROWTH_LIMIT, Long.valueOf(m.getSize()), d);
                    mem.set(MEM_FREEZE, true, d);
                    resetGrowthProgress(m);
                }
                out.add("growth frozen " + months(freeze));
            }

            if (apply) {
                if (GBUtils.getGarrisonDamageMemory(m) < GARRISON_DAMAGE) GBUtils.setGarrisonDamageMemory(m, GARRISON_DAMAGE);
                mark(mem, MEM_GARRISON, level >= 3 ? 12 * MONTH : 4 * MONTH);
            }
            out.add("garrison -80%");

            List<String> disrupted = new ArrayList<>();
            for (Industry ind : m.getIndustries()) {
                float days;
                if (level >= 3 && isMilitary(ind)) days = 24 * MONTH;
                else if (level >= 3 && isShipIndustry(ind)) days = 12 * MONTH;
                else if (isDefense(ind)) days = 4 * MONTH;
                else continue;
                if (!ind.canBeDisrupted()) continue;
                if (apply) {
                    ind.setDisrupted(days, true);
                    mark(mem, MEM_DISRUPT + ind.getId(), days);
                }
                disrupted.add(ind.getCurrentName() + " " + months(days));
            }
            if (!disrupted.isEmpty()) out.add("disrupted: " + String.join(", ", disrupted));
        }

        if (level >= 3) {
            if (apply) {
                MutableStatWithTempMods stab = m.getStability();
                stab.addTemporaryModFlat(12 * MONTH, WEAKEN_SOURCES[0], "Lingering turmoil", -1f);
                stab.addTemporaryModFlat(24 * MONTH, WEAKEN_SOURCES[1], "Lingering turmoil", -1f);
                stab.addTemporaryModFlat(36 * MONTH, WEAKEN_SOURCES[2], "Lingering turmoil", -1f);
            }
            out.add("stability -3, fading by one point per year");
        }
        if (apply) mark(mem, MEM_WEAKENED, level >= 3 ? 36 * MONTH : 6 * MONTH);
        return m.getName() + ": " + join(out);
    }

    /** Faction-wide part of weaken (Nexerelin invasion points and troop reserves). */
    public static String weakenFaction(FactionAPI f, int level, boolean apply) {
        List<String> out = new ArrayList<>();
        if (level < 2) return null;
        if (f.isPlayerFaction()) return "Faction-wide: nothing (player faction)";
        InvasionFleetManager ifm = InvasionFleetManager.getManager();
        if (ifm != null) {
            float max = InvasionFleetManager.getMaxInvasionPoints(f);
            float cur = ifm.getSpawnCounter(f.getId());
            float delta = level >= 3 ? -(cur + max) : -0.25f * max;
            if (apply) ifm.modifySpawnCounterV2(f.getId(), delta);
            out.add(level >= 3 ? "invasion points drained far below zero (attacks pause for a year or more)"
                    : "invasion points -25% (their next attack comes later)");
        }
        if (level >= 3) {
            GroundPoolManager gpm = GroundPoolManager.getManager();
            if (gpm != null) {
                float cur = gpm.getCurrentPool(f.getId());
                if (cur >= 1000000f) {
                    out.add("troop reserves are disabled in Nexerelin's settings");
                } else {
                    if (apply) gpm.modifyPool(f.getId(), -(cur + 0.5f * gpm.getMaxPool(f.getId())));
                    out.add("troop reserves drained (garrison repairs stall for months)");
                }
            }
        }
        return "Faction-wide: " + join(out);
    }

    // ------------------------------------------------------------------ strengthen

    /** Strengthens one colony; with apply=false it only describes what would happen. */
    public static String strengthenColony(MarketAPI m, int level, boolean apply) {
        List<String> out = new ArrayList<>();
        MemoryAPI mem = m.getMemoryWithoutUpdate();

        RecentUnrest unrest = RecentUnrest.get(m, false); // get(m) would add the condition if it's missing
        if (unrest != null && unrest.getPenalty() > 0) {
            if (apply) unrest.setPenalty(0);
            out.add("unrest cleared");
        }
        int repaired = 0;
        for (Industry ind : m.getIndustries()) {
            if (!ind.isDisrupted()) continue;
            if (apply) {
                ind.setDisrupted(0f);
                mem.unset(MEM_DISRUPT + ind.getId());
            }
            repaired++;
        }
        if (repaired > 0) out.add(repaired + (repaired == 1 ? " disrupted industry repaired" : " disrupted industries repaired"));
        if (mem.contains(MEM_FREEZE)) {
            if (apply) {
                mem.unset(MEM_FREEZE);
                mem.unset(PopCap.MEM_GROWTH_LIMIT);
            }
            out.add("Worldsmith growth freeze lifted");
        }

        if (level >= 2) {
            if (GBUtils.getGarrisonDamageMemory(m) > 0f) {
                if (apply) {
                    GBUtils.setGarrisonDamageMemory(m, 0f);
                    mem.unset(MEM_GARRISON);
                }
                out.add("garrison repaired");
            }
            if (apply) m.getStability().addTemporaryModFlat(6 * MONTH, STRENGTHEN_SOURCES[0], "Renewed confidence", 2f);
            out.add("stability +2 for 6 months");
        }
        if (level >= 3) {
            if (apply) {
                MutableStatWithTempMods stab = m.getStability();
                stab.addTemporaryModFlat(12 * MONTH, STRENGTHEN_SOURCES[1], "Prosperous outlook", 1f);
                stab.addTemporaryModFlat(24 * MONTH, STRENGTHEN_SOURCES[2], "Prosperous outlook", 1f);
                stab.addTemporaryModFlat(36 * MONTH, STRENGTHEN_SOURCES[3], "Prosperous outlook", 1f);
            }
            out.add("stability +3, fading by one point per year");
        }
        if (apply && level >= 2) mark(mem, MEM_STRENGTHENED, level >= 3 ? 36 * MONTH : 6 * MONTH);
        return m.getName() + ": " + join(out);
    }

    /** Faction-wide part of strengthen (Nexerelin invasion points and troop reserves). */
    public static String strengthenFaction(FactionAPI f, int level, boolean apply) {
        List<String> out = new ArrayList<>();
        if (level < 2) return null;
        if (f.isPlayerFaction()) return "Faction-wide: nothing (player faction)";
        InvasionFleetManager ifm = InvasionFleetManager.getManager();
        if (ifm != null) {
            float max = InvasionFleetManager.getMaxInvasionPoints(f);
            float add = (level >= 3 ? 0.75f : 0.25f) * max;
            if (apply) ifm.modifySpawnCounterV2(f.getId(), add);
            out.add(level >= 3 ? "invasion points +75% (their next attack comes much sooner)"
                    : "invasion points +25% (their next attack comes sooner)");
        }
        GroundPoolManager gpm = GroundPoolManager.getManager();
        if (gpm != null && gpm.getCurrentPool(f.getId()) < 1000000f) {
            if (apply) gpm.modifyPool(f.getId(), (level >= 3 ? 0.5f : 0.25f) * gpm.getMaxPool(f.getId()));
            out.add("troop reserves topped up");
        }
        return "Faction-wide: " + join(out);
    }

    // ------------------------------------------------------------------ report and clear

    public static String report(FactionAPI f) {
        List<MarketAPI> cols = colonies(f);
        StringBuilder sb = new StringBuilder();
        sb.append(WsUtil.PREFIX).append(WsUtil.factionLabel(f)).append(": ");
        if (cols.isEmpty()) return sb.append("no colonies.").toString();
        int totalSize = 0;
        float stability = 0f;
        int unrest = 0;
        int disrupted = 0;
        float garrison = 0f;
        int weakened = 0;
        int strengthened = 0;
        int frozen = 0;
        float longestWeak = 0f;
        float longestStrong = 0f;
        float longestFreeze = 0f;
        for (MarketAPI m : cols) {
            MemoryAPI mem = m.getMemoryWithoutUpdate();
            totalSize += m.getSize();
            stability += m.getStabilityValue();
            if (RecentUnrest.getPenalty(m) > 0) unrest++;
            for (Industry ind : m.getIndustries()) if (ind.isDisrupted()) disrupted++;
            garrison += GBUtils.getGarrisonDamageMemory(m);
            if (mem.contains(MEM_WEAKENED)) {
                weakened++;
                longestWeak = Math.max(longestWeak, mem.getExpire(MEM_WEAKENED));
            }
            if (mem.contains(MEM_STRENGTHENED)) {
                strengthened++;
                longestStrong = Math.max(longestStrong, mem.getExpire(MEM_STRENGTHENED));
            }
            if (mem.contains(MEM_FREEZE)) {
                frozen++;
                longestFreeze = Math.max(longestFreeze, mem.getExpire(MEM_FREEZE));
            }
        }
        sb.append(cols.size()).append(" colonies, total size ").append(totalSize)
                .append(", average stability ").append(String.format(java.util.Locale.ROOT, "%.1f", stability / cols.size())).append("\n");
        sb.append("  Unrest in ").append(unrest).append(" colonies | disrupted industries: ").append(disrupted)
                .append(" | average garrison damage ").append(Math.round(100f * garrison / cols.size())).append("%\n");
        if (!f.isPlayerFaction()) {
            InvasionFleetManager ifm = InvasionFleetManager.getManager();
            if (ifm != null) {
                sb.append("  Nex invasion points: ").append(Math.round(ifm.getSpawnCounter(f.getId())))
                        .append(" (max ").append(Math.round(InvasionFleetManager.getMaxInvasionPoints(f)))
                        .append(", an attack needs ").append(Math.round(NexConfig.pointsRequiredForInvasionFleet)).append(")");
            }
            GroundPoolManager gpm = GroundPoolManager.getManager();
            if (gpm != null && gpm.getCurrentPool(f.getId()) < 1000000f) {
                sb.append(" | troop reserves: ").append(Math.round(gpm.getCurrentPool(f.getId())))
                        .append(" of ").append(Math.round(gpm.getMaxPool(f.getId())));
            }
            sb.append("\n");
        }
        sb.append("  Worldsmith effects: ");
        List<String> ws = new ArrayList<>();
        if (weakened > 0) ws.add("weakened in " + weakened + " colonies (ends in " + months(longestWeak) + ")");
        if (strengthened > 0) ws.add("strengthened in " + strengthened + " colonies (ends in " + months(longestStrong) + ")");
        if (frozen > 0) ws.add("growth frozen in " + frozen + " colonies (up to " + months(longestFreeze) + " left)");
        sb.append(ws.isEmpty() ? "none" : String.join("; ", ws));
        return sb.toString();
    }

    /** Ends every Worldsmith effect on the faction's colonies early. */
    public static String clear(FactionAPI f) {
        int colonies = 0;
        for (MarketAPI m : colonies(f)) {
            MemoryAPI mem = m.getMemoryWithoutUpdate();
            boolean any = false;
            for (String s : WEAKEN_SOURCES) {
                if (m.getStability().hasMod(s)) {
                    m.getStability().removeTemporaryMod(s);
                    any = true;
                }
            }
            for (String s : STRENGTHEN_SOURCES) {
                if (m.getStability().hasMod(s)) {
                    m.getStability().removeTemporaryMod(s);
                    any = true;
                }
            }
            if (mem.contains(MEM_FREEZE)) {
                mem.unset(MEM_FREEZE);
                mem.unset(PopCap.MEM_GROWTH_LIMIT);
                any = true;
            }
            for (Industry ind : m.getIndustries()) {
                if (mem.contains(MEM_DISRUPT + ind.getId())) {
                    ind.setDisrupted(0f);
                    mem.unset(MEM_DISRUPT + ind.getId());
                    any = true;
                }
            }
            if (mem.contains(MEM_GARRISON)) {
                GBUtils.setGarrisonDamageMemory(m, 0f);
                mem.unset(MEM_GARRISON);
                any = true;
            }
            if (mem.contains(MEM_WEAKENED) || mem.contains(MEM_STRENGTHENED)) any = true;
            mem.unset(MEM_WEAKENED);
            mem.unset(MEM_STRENGTHENED);
            if (any) colonies++;
        }
        return "Ended Worldsmith effects on " + colonies + " colonies of " + WsUtil.factionLabel(f)
                + ". Unrest, invasion points and troop reserves recover on their own.";
    }
}
