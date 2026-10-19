package worldsmith;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.procgen.ConditionGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.PlanetConditionGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarGenDataSpec;

import java.util.ArrayList;
import java.util.List;

/** Condition rolling with the vanilla generator (same pattern as vanilla's Sentinel planet). */
public final class PlanetGen {
    private PlanetGen() {
    }

    private static boolean hasStarData(PlanetAPI star) {
        return star != null
                && Global.getSettings().getSpec(StarGenDataSpec.class, star.getSpec().getPlanetType(), true) != null;
    }

    /**
     * Null if vanilla's PlanetConditionGenerator can roll conditions for a planet of {@code newType} orbiting
     * {@code focus} in {@code sys}; otherwise the reason why not. Mirrors the lookups in
     * PlanetConditionGenerator.createContext(), which throw instead of returning null when data is missing.
     */
    public static String whyCannotRoll(StarSystemAPI sys, SectorEntityToken focus, String newType) {
        if (WsUtil.genData(newType) == null)
            return "planet type '" + newType + "' has no generator data, so conditions can't be rolled for it";
        if (sys == null) return "it is not in a star system";
        if (sys.getStar() == null) return "the system has no star (nebula), so the condition generator can't run";
        if (!hasStarData(sys.getStar()))
            return "the star type '" + sys.getStar().getSpec().getPlanetType() + "' has no generator data";
        PlanetAPI parentStar = sys.getStar();
        if (focus instanceof PlanetAPI) {
            PlanetAPI p1 = (PlanetAPI) focus;
            if (p1.isStar()) {
                parentStar = p1;
            } else {
                if (WsUtil.genData(p1.getSpec().getPlanetType()) == null)
                    return "its parent planet's type '" + p1.getSpec().getPlanetType() + "' has no generator data";
                if (p1.getOrbitFocus() instanceof PlanetAPI && ((PlanetAPI) p1.getOrbitFocus()).isStar())
                    parentStar = (PlanetAPI) p1.getOrbitFocus();
            }
        }
        if (!hasStarData(parentStar))
            return "the star type '" + parentStar.getSpec().getPlanetType() + "' has no generator data";
        return null;
    }

    public static String whyCannotRoll(PlanetAPI planet, String newType) {
        StarSystemAPI sys = planet.getContainingLocation() instanceof StarSystemAPI
                ? (StarSystemAPI) planet.getContainingLocation() : null;
        return whyCannotRoll(sys, planet.getOrbitFocus(), newType);
    }

    public static StarAge age(StarSystemAPI sys) {
        StarAge age = sys.getAge();
        return age == null ? StarAge.AVERAGE : age;
    }

    /** Snapshot of a market's conditions: ids and their surveyed flags, in order. */
    public static final class Snapshot {
        final List<String> ids = new ArrayList<>();
        final List<Boolean> surveyed = new ArrayList<>();
    }

    public static Snapshot snapshot(MarketAPI m) {
        Snapshot s = new Snapshot();
        if (m == null) return s;
        for (MarketConditionAPI c : m.getConditions()) {
            s.ids.add(c.getId());
            s.surveyed.add(c.isSurveyed());
        }
        return s;
    }

    public static void clearConditions(MarketAPI m) {
        if (m == null) return;
        for (String id : snapshot(m).ids) m.removeCondition(id);
    }

    /** Conditions the procgen generator never produces (special ones added by quests or other mods). */
    public static List<String> specialConditionNames(MarketAPI m) {
        List<String> out = new ArrayList<>();
        if (m == null) return out;
        for (MarketConditionAPI c : m.getConditions()) {
            if (c.getId().startsWith("population_")) continue;
            if (Global.getSettings().getSpec(ConditionGenDataSpec.class, c.getId(), true) == null)
                out.add(c.getName() + " (" + c.getId() + ")");
        }
        return out;
    }

    /** Clears the planet's conditions and rolls new ones. Marks all of them surveyed if {@code surveyed}. */
    public static void rollConditions(PlanetAPI planet, boolean surveyed) {
        StarSystemAPI sys = (StarSystemAPI) planet.getContainingLocation();
        clearConditions(planet.getMarket());
        PlanetConditionGenerator.generateConditionsForPlanet(null, planet, age(sys));
        MarketAPI m = planet.getMarket();
        if (m != null && surveyed) {
            m.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
            for (MarketConditionAPI c : m.getConditions()) c.setSurveyed(true);
        }
        if (m != null) m.reapplyConditions();
    }

    /** Puts back a previous set of conditions (used when rolling failed half-way). */
    public static void restore(MarketAPI m, Snapshot s) {
        if (m == null) return;
        clearConditions(m);
        for (int i = 0; i < s.ids.size(); i++) {
            String token = m.addCondition(s.ids.get(i));
            MarketConditionAPI c = m.getSpecificCondition(token);
            if (c != null) c.setSurveyed(s.surveyed.get(i));
        }
        m.reapplyConditions();
    }

    public static String conditionNames(MarketAPI m) {
        List<String> names = new ArrayList<>();
        if (m == null) return "none";
        for (MarketConditionAPI c : m.getConditions()) {
            if (Global.getSettings().getMarketConditionSpec(c.getId()) == null) continue;
            names.add(c.getName());
        }
        return names.isEmpty() ? "none" : String.join(", ", names);
    }
}
