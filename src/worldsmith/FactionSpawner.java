package worldsmith;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsReputation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Plans where a new faction settles and founds it: name, flag, ships, relations, colonies, Nexerelin. */
public final class FactionSpawner {
    /** Extra colonies are looked for in systems at most this far from the home system. */
    private static final float NEIGHBOR_LY = 6f;

    public enum Size {
        SMALL("small", 5),
        MEDIUM("medium", 6, 4, 4),
        LARGE("large", 6, 5, 5, 4, 4);

        public final String key;
        public final int[] sizes;

        Size(String key, int... sizes) {
            this.key = key;
            this.sizes = sizes;
        }

        public static Size parse(String word) {
            for (Size s : values()) {
                if (s.key.equalsIgnoreCase(word)) return s;
            }
            return null;
        }

        public String describe() {
            if (sizes.length == 1) return "1 colony (size " + sizes[0] + ")";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sizes.length; i++) sb.append(i == 0 ? "" : ", ").append(sizes[i]);
            return sizes.length + " colonies (sizes " + sb + ")";
        }
    }

    /** Which planets the faction gets, capital first. */
    public static final class Plan {
        public final List<PlanetAPI> planets = new ArrayList<>();
        public final List<Integer> sizes = new ArrayList<>();
        public StarSystemAPI home;
        /** Why the faction can't be placed; null if it can. */
        public String problem;

        public String describe() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < planets.size(); i++) {
                PlanetAPI p = planets.get(i);
                if (i > 0) sb.append(", ");
                sb.append(p.getName()).append(" (").append(WsUtil.systemName(p)).append(", size ").append(sizes.get(i))
                        .append(i == 0 ? ", capital" : "").append(")");
            }
            return sb.toString();
        }
    }

    private FactionSpawner() {
    }

    // ------------------------------------------------------------------ planning

    /** An uncolonized planet or moon in a star system that a colony can be founded on. */
    public static boolean candidate(PlanetAPI p) {
        if (p == null || p.isStar() || p.getStarSystem() == null) return false;
        MarketAPI m = p.getMarket();
        if (m == null || !m.isPlanetConditionMarketOnly()) return false;
        return !Misc.isStoryCritical(m) && !p.hasTag(Tags.NOT_RANDOM_MISSION_TARGET);
    }

    /** Systems a random new faction shouldn't settle in: hidden, abyssal, cut off, or full of hostile machines. */
    private static boolean excluded(StarSystemAPI s) {
        return s.hasTag(Tags.THEME_HIDDEN) || s.hasTag(Tags.SYSTEM_ABYSSAL) || s.hasTag(Tags.THEME_REMNANT)
                || s.hasTag(Tags.THEME_REMNANT_MAIN) || s.hasTag(Tags.THEME_UNSAFE) || s.hasTag(Tags.THEME_SPECIAL)
                || s.hasTag(Tags.PK_SYSTEM) || s.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER);
    }

    private static boolean hasColonies(StarSystemAPI s) {
        for (MarketAPI m : Misc.getMarketsInLocation(s)) {
            if (!m.isPlanetConditionMarketOnly() && !m.isHidden()) return true;
        }
        return false;
    }

    /** Lower is better: low hazard, habitable, not a gas giant. */
    private static float score(PlanetAPI p) {
        MarketAPI m = p.getMarket();
        float s = m.getHazardValue();
        if (m.hasCondition(Conditions.HABITABLE)) s -= 0.5f;
        if (p.isGasGiant()) s += 0.75f;
        return s;
    }

    private static List<PlanetAPI> candidates(StarSystemAPI s) {
        List<PlanetAPI> out = new ArrayList<>();
        for (PlanetAPI p : s.getPlanets()) {
            if (candidate(p)) out.add(p);
        }
        final Map<PlanetAPI, Float> sc = new HashMap<>();
        for (PlanetAPI p : out) sc.put(p, score(p));
        out.sort(Comparator.comparing(sc::get));
        return out;
    }

    /** Settles in (or around) the given system; {@code capital} is used first if it is a valid planet. */
    public static Plan planHere(StarSystemAPI home, PlanetAPI capital, Size size) {
        Plan plan = new Plan();
        plan.home = home;
        List<PlanetAPI> picks = new ArrayList<>();
        if (capital != null && candidate(capital) && capital.getStarSystem() == home) picks.add(capital);
        for (PlanetAPI p : candidates(home)) {
            if (picks.size() >= size.sizes.length) break;
            if (!picks.contains(p)) picks.add(p);
        }
        if (picks.isEmpty()) {
            plan.problem = home.getName() + " has no uncolonized planet a colony could be founded on.";
            return plan;
        }
        addNeighbors(home, picks, size.sizes.length);
        finish(plan, picks, size);
        return plan;
    }

    /** A random home system with no colonies yet, with room for as many colonies as the size needs nearby. */
    public static Plan planRandom(Size size, Random random) {
        Plan plan = new Plan();
        int needed = size.sizes.length;
        List<StarSystemAPI> open = new ArrayList<>();
        Map<StarSystemAPI, List<PlanetAPI>> cands = new HashMap<>();
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (excluded(s)) continue;
            List<PlanetAPI> c = candidates(s);
            if (c.isEmpty()) continue;
            cands.put(s, c);
            if (!hasColonies(s)) open.add(s);
        }
        WeightedRandomPicker<StarSystemAPI> roomy = new WeightedRandomPicker<>(random);
        WeightedRandomPicker<StarSystemAPI> any = new WeightedRandomPicker<>(random);
        for (StarSystemAPI s : open) {
            int room = cands.get(s).size();
            int good = 0;
            for (PlanetAPI p : cands.get(s)) {
                if (score(p) < 1.25f) good++;
            }
            if (room < needed) {
                for (StarSystemAPI o : cands.keySet()) {
                    if (o != s && Misc.getDistanceLY(o.getLocation(), s.getLocation()) <= NEIGHBOR_LY) room += cands.get(o).size();
                    if (room >= needed) break;
                }
            }
            float w = 1f + good;
            any.add(s, w);
            if (room >= needed) roomy.add(s, w);
        }
        StarSystemAPI home = !roomy.isEmpty() ? roomy.pick() : any.pick();
        if (home == null) {
            plan.problem = "There is no star system without colonies left that has a planet to settle.";
            return plan;
        }
        plan.home = home;
        List<PlanetAPI> picks = new ArrayList<>();
        for (PlanetAPI p : cands.get(home)) {
            if (picks.size() >= needed) break;
            picks.add(p);
        }
        addNeighbors(home, picks, needed);
        finish(plan, picks, size);
        return plan;
    }

    /** Fills up with the best planets of the nearest other systems, empty systems first. */
    private static void addNeighbors(StarSystemAPI home, List<PlanetAPI> picks, int needed) {
        if (picks.size() >= needed) return;
        List<StarSystemAPI> near = new ArrayList<>();
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (s == home || excluded(s)) continue;
            if (Misc.getDistanceLY(s.getLocation(), home.getLocation()) <= NEIGHBOR_LY) near.add(s);
        }
        final Map<StarSystemAPI, Float> key = new HashMap<>();
        for (StarSystemAPI s : near) {
            key.put(s, Misc.getDistanceLY(s.getLocation(), home.getLocation()) + (hasColonies(s) ? NEIGHBOR_LY : 0f));
        }
        near.sort(Comparator.comparing(key::get));
        for (StarSystemAPI s : near) {
            for (PlanetAPI p : candidates(s)) {
                if (picks.size() >= needed) return;
                picks.add(p);
            }
        }
    }

    private static void finish(Plan plan, List<PlanetAPI> picks, Size size) {
        for (int i = 0; i < picks.size() && i < size.sizes.length; i++) {
            plan.planets.add(picks.get(i));
            plan.sizes.add(size.sizes[i]);
        }
    }

    // ------------------------------------------------------------------ founding

    /** A random real faction (not pirates) whose ships and doctrine a new faction copies. */
    public static FactionAPI randomTemplate(Random random) {
        WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<>(random);
        for (FactionAPI f : FactionSlots.templateCandidates()) picker.add(f);
        FactionAPI f = picker.pick();
        return f != null ? f : Global.getSector().getFaction(Factions.INDEPENDENT);
    }

    /** Founds the faction in the slot. Returns the one-line report. */
    public static String spawn(FactionAPI slot, String name, FactionAPI template, Plan plan, Random random) {
        List<PlanetAPI> planets = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < plan.planets.size(); i++) {
            // Anything could have happened while a popup was open.
            if (candidate(plan.planets.get(i))) {
                planets.add(plan.planets.get(i));
                sizes.add(plan.sizes.get(i));
            }
        }
        if (planets.isEmpty()) return "Did not create " + name + ": its planets were colonized in the meantime.";

        // Identity, ships and Nexerelin status.
        FactionCharacter.clear(slot);
        FactionIdentity.setName(slot, name);
        slot.setShipNamePrefixOverride(FactionIdentity.shipPrefix(name));
        int flag = FactionIdentity.randomFlagIndex(random);
        String[] f = FactionIdentity.playerFlags().get(flag);
        FactionIdentity.setFlag(slot, f[0], f[1]);
        FactionIdentity.setLore(slot, null);
        if (template == null || !FactionSlots.isGoodTemplate(template)) template = randomTemplate(random);
        FactionSlots.copyTech(template, slot);
        slot.getMemoryWithoutUpdate().set(FactionSlots.MEM_TEMPLATE, template.getId());
        slot.getMemoryWithoutUpdate().set(FactionSlots.MEM_SPAWNED, true);
        FactionSlots.setPlayable(slot, true);
        initRelations(slot);

        // Colonies, built the way Nexerelin builds its own colony expeditions' colonies, then finished at once.
        MarketAPI capital = null;
        List<String> founded = new ArrayList<>();
        for (int i = 0; i < planets.size(); i++) {
            PlanetAPI p = planets.get(i);
            MarketAPI m = p.getMarket();
            ColonyExpeditionIntel.createColonyStatic(m, p, slot, false, false, sizes.get(i));
            for (Industry ind : new ArrayList<>(m.getIndustries())) {
                if (ind.isBuilding() || ind.isUpgrading()) ind.finishBuildingOrUpgrading();
            }
            if (capital == null) capital = m;
            founded.add(p.getName() + " (" + WsUtil.systemName(p) + ", size " + m.getSize() + ")");
        }
        SectorManager.factionRespawned(slot, capital);
        slot.setShowInIntelTab(true);
        if (NexConfig.syncPlayerRelationsWithCommisioner) NexUtilsReputation.syncPlayerRelationshipsToFaction();
        WsUtil.invalidateCache();
        return "Founded " + slot.getDisplayNameWithArticle() + " (" + FactionSlots.colorOf(slot) + ", player flag #"
                + (flag + 1) + ", ships and tactics like " + template.getDisplayNameWithArticle() + ") with "
                + founded.size() + (founded.size() == 1 ? " colony: " : " colonies: ") + String.join(", ", founded)
                + ". Its lore is empty: 'factionlore " + WsUtil.token(slot.getDisplayName()) + " <text>' writes it.";
    }

    /**
     * Starts out like the independents: friends with nobody, hostile to whoever the independents fight
     * (pirates, the Luddic Path...), neutral to you. Nexerelin's diplomacy takes it from there.
     */
    public static void initRelations(FactionAPI f) {
        FactionAPI indie = Global.getSector().getFaction(Factions.INDEPENDENT);
        for (FactionAPI o : Global.getSector().getAllFactions()) {
            if (o == f) continue;
            float rel = 0f;
            if (!o.isPlayerFaction() && o != indie && indie != null) rel = indie.getRelationship(o.getId());
            f.setRelationship(o.getId(), rel);
        }
    }
}
