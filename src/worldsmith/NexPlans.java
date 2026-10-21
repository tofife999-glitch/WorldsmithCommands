package worldsmith;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.campaign.SectorManager;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.campaign.econ.ResourcePoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-save changes to how Nexerelin runs a faction: how often it sends colony expeditions, and a war focus that
 * spends its invasion points on one enemy. Everything is plain data in the faction's memory; Nexerelin's settings
 * are global, so they are applied again (or put back) whenever a save loads.
 */
public final class NexPlans {
    /** Same key as factionpersonality's expansionist/isolationist: the faction's colony expedition weight. */
    public static final String MEM_EXPANSION = FactionCharacter.MEM_EXPANSION;
    public static final String MEM_FOCUS = "$worldsmith_warfocus";
    public static final String MEM_FOCUS_START = "$worldsmith_warfocus_start";
    public static final String MEM_FOCUS_DAYS = "$worldsmith_warfocus_days";
    public static final String MEM_FOCUS_LAUNCHED = "$worldsmith_warfocus_launched";
    public static final String MEM_FOCUS_STUCK = "$worldsmith_warfocus_stuck";
    /** A focused faction launches at this share of the points Nexerelin waits for, so it acts before Nexerelin picks. */
    public static final float FOCUS_THRESHOLD = 0.8f;

    /** Nexerelin's own expedition weights of factions changed this session (not of spawnfaction slots). */
    private static final Map<String, Float> ORIGINAL_EXPANSION = new HashMap<>();

    private NexPlans() {
    }

    // ------------------------------------------------------------------ colony expeditions

    /** The weight the faction has without Worldsmith changes. */
    public static float normalExpansion(FactionAPI f) {
        if (FactionSlots.isSlot(f)) return 1f;
        Float orig = ORIGINAL_EXPANSION.get(f.getId());
        return orig != null ? orig : expansion(f);
    }

    /** The weight Nexerelin uses (factions without settings of their own share Nexerelin's default ones). */
    public static float expansion(FactionAPI f) {
        return NexConfig.getFactionConfig(f.getId()).colonyExpeditionChance;
    }

    /**
     * False for factions without Nexerelin settings of their own: they share Nexerelin's default settings, which
     * must not be changed for one of them.
     */
    public static boolean hasOwnConfig(FactionAPI f) {
        return NexConfig.getFactionConfig(f.getId(), false) != null;
    }

    /** Sets the faction's colony expedition weight; null puts back its normal one. False if it can't be changed. */
    public static boolean setExpansion(FactionAPI f, Float chance) {
        NexFactionConfig conf = NexConfig.getFactionConfig(f.getId(), false);
        if (conf == null) return false;
        if (!FactionSlots.isSlot(f) && !ORIGINAL_EXPANSION.containsKey(f.getId()))
            ORIGINAL_EXPANSION.put(f.getId(), conf.colonyExpeditionChance);
        float normal = normalExpansion(f);
        if (chance == null) f.getMemoryWithoutUpdate().unset(MEM_EXPANSION);
        else f.getMemoryWithoutUpdate().set(MEM_EXPANSION, chance);
        conf.colonyExpeditionChance = chance == null ? normal : chance;
        return true;
    }

    /** Factions Nexerelin can pick for an expedition (it picks by weight among the ones with colonies). */
    public static List<FactionAPI> liveFactions() {
        List<FactionAPI> out = new ArrayList<>();
        for (String id : SectorManager.getLiveFactionIdsCopy()) {
            FactionAPI f = Global.getSector().getFaction(id);
            if (f != null && !f.isPlayerFaction()) out.add(f);
        }
        return out;
    }

    /** The faction's share of all colony expeditions, 0-1 (before Nexerelin's bonus for survey data). */
    public static float expansionShare(FactionAPI f) {
        float total = 0f;
        for (String id : SectorManager.getLiveFactionIdsCopy()) { // as Nexerelin picks, your faction included
            total += Math.max(0f, NexConfig.getFactionConfig(id).colonyExpeditionChance);
        }
        return total <= 0f ? 0f : Math.max(0f, expansion(f)) / total;
    }

    // ------------------------------------------------------------------ war focus

    public static FactionAPI focus(FactionAPI f) {
        String id = f.getMemoryWithoutUpdate().getString(MEM_FOCUS);
        return id == null ? null : Global.getSector().getFaction(id);
    }

    public static float focusDaysLeft(FactionAPI f) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        if (!mem.contains(MEM_FOCUS_START)) return 0f;
        long start = (Long) mem.get(MEM_FOCUS_START);
        return mem.getFloat(MEM_FOCUS_DAYS) - Global.getSector().getClock().getElapsedDaysSince(start);
    }

    public static void setFocus(FactionAPI f, FactionAPI enemy, float days) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        mem.set(MEM_FOCUS, enemy.getId());
        mem.set(MEM_FOCUS_START, Global.getSector().getClock().getTimestamp());
        mem.set(MEM_FOCUS_DAYS, days);
        mem.set(MEM_FOCUS_LAUNCHED, 0);
        mem.unset(MEM_FOCUS_STUCK);
        ensureScript();
    }

    public static void clearFocus(FactionAPI f) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        for (String k : new String[]{MEM_FOCUS, MEM_FOCUS_START, MEM_FOCUS_DAYS, MEM_FOCUS_LAUNCHED, MEM_FOCUS_STUCK})
            mem.unset(k);
    }

    public static List<FactionAPI> focusedFactions() {
        List<FactionAPI> out = new ArrayList<>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f.getMemoryWithoutUpdate().contains(MEM_FOCUS)) out.add(f);
        }
        return out;
    }

    /** Why a focus has ended, or null if it still runs. */
    public static String focusEnd(FactionAPI f, FactionAPI enemy) {
        if (enemy == null) return "its target no longer exists";
        if (focusDaysLeft(f) <= 0f) return "its time ran out";
        if (!SectorManager.isFactionAlive(f.getId())) return f.getDisplayName() + " has no colonies left";
        if (!SectorManager.isFactionAlive(enemy.getId())) return enemy.getDisplayName() + " has no colonies left";
        if (!f.isHostileTo(enemy)) return "they are no longer at war";
        return null;
    }

    /** Why the focused faction can't launch right now (other than not having enough points yet), or null. */
    public static String focusBlocked(FactionAPI f) {
        if (!NexConfig.enableHostileFleetEvents) return "Nexerelin's invasion and raid fleets are switched off in its settings";
        InvasionFleetManager m = InvasionFleetManager.getManager();
        if (m == null) return "Nexerelin's invasion manager isn't running";
        if (OffensiveFleetIntel.isOffensiveCooldown()) return "Nexerelin's pause between invasions is still running";
        if (m.getActiveIntelCopy().size() >= InvasionFleetManager.MAX_ONGOING_INTEL)
            return "the sector already has " + InvasionFleetManager.MAX_ONGOING_INTEL + " invasions and raids under way";
        // Nexerelin itself waits for this too, so invasions don't set out with almost no ships.
        if (FleetPoolManager.USE_POOL && FleetPoolManager.getManager() != null
                && FleetPoolManager.getManager().getCurrentPool(f.getId()) <= InvasionFleetManager.BASE_INVASION_SIZE)
            return f.getDisplayName() + " has too few ships to spare (its fleet pool refills over time)";
        return null;
    }

    /** True for 5 days after the focused faction found no target it could reach. */
    public static boolean recentlyStuck(FactionAPI f) {
        Object t = f.getMemoryWithoutUpdate().get(MEM_FOCUS_STUCK);
        return t instanceof Long && Global.getSector().getClock().getElapsedDaysSince((Long) t) < 5f;
    }

    private static void ensureScript() {
        if (!Global.getSector().hasTransientScript(WarFocusScript.class))
            Global.getSector().addTransientScript(new WarFocusScript());
    }

    /** Spends a focused faction's invasion points on its target before Nexerelin picks a random enemy. */
    public static class WarFocusScript implements EveryFrameScript {
        private final IntervalUtil interval = new IntervalUtil(0.25f, 0.25f);

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public void advance(float amount) {
            interval.advance(Global.getSector().getClock().convertToDays(amount));
            if (!interval.intervalElapsed()) return;
            try {
                tick();
            } catch (Exception ex) {
                Global.getLogger(NexPlans.class).error("Worldsmith: war focus failed", ex);
            }
        }

        private void tick() {
            for (FactionAPI f : focusedFactions()) {
                FactionAPI enemy = focus(f);
                String end = focusEnd(f, enemy);
                if (end != null) {
                    WsUtil.report(f.getDisplayName() + " stopped focusing its invasions on "
                            + (enemy == null ? "its target" : enemy.getDisplayNameWithArticle()) + ": " + end + ".");
                    clearFocus(f);
                    continue;
                }
                if (focusBlocked(f) != null) continue;
                InvasionFleetManager m = InvasionFleetManager.getManager();
                if (m.getSpawnCounter(f.getId()) < NexConfig.pointsRequiredForInvasionFleet * FOCUS_THRESHOLD) continue;
                MemoryAPI mem = f.getMemoryWithoutUpdate();
                if (recentlyStuck(f)) continue;
                InvasionFleetManager.EventType type = NexConfig.enableInvasions
                        ? InvasionFleetManager.EventType.INVASION : InvasionFleetManager.EventType.RAID;
                OffensiveFleetIntel intel = null;
                try {
                    intel = m.generateInvasionOrRaidFleet(f, enemy, type, new ResourcePoolManager.RequisitionParams());
                    if (intel == null && type == InvasionFleetManager.EventType.INVASION)
                        intel = m.generateInvasionOrRaidFleet(f, enemy, InvasionFleetManager.EventType.RAID,
                                new ResourcePoolManager.RequisitionParams());
                } catch (Exception ex) {
                    // Waits like a failed try instead of failing again a few hours later.
                    Global.getLogger(NexPlans.class).error("Worldsmith: Nexerelin could not launch " + f.getId()
                            + "'s focused invasion", ex);
                }
                if (intel == null) {
                    mem.set(MEM_FOCUS_STUCK, Global.getSector().getClock().getTimestamp());
                    continue;
                }
                m.modifySpawnCounterV2(f.getId(), -InvasionFleetManager.getInvasionPointCost(intel));
                mem.set(MEM_FOCUS_LAUNCHED, mem.getInt(MEM_FOCUS_LAUNCHED) + 1);
            }
        }
    }

    // ------------------------------------------------------------------ save loading

    public static void onGameLoad() {
        // Nexerelin keeps its settings between saves: first undo what another save changed.
        for (Map.Entry<String, Float> e : ORIGINAL_EXPANSION.entrySet()) {
            NexFactionConfig conf = NexConfig.getFactionConfig(e.getKey(), false);
            if (conf != null) conf.colonyExpeditionChance = e.getValue();
        }
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (FactionSlots.isSlot(f)) continue; // FactionCharacter applies the slots' settings
            MemoryAPI mem = f.getMemoryWithoutUpdate();
            if (mem.contains(MEM_EXPANSION)) setExpansion(f, mem.getFloat(MEM_EXPANSION));
        }
        if (!focusedFactions().isEmpty()) ensureScript();
    }
}
