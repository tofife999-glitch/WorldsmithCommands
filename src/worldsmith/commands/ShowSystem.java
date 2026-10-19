package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.ConditionGenDataSpec;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ShowSystem implements BaseCommandWithSuggestion {

    /** Condition generator groups (condition_gen_data.csv) that count as resources. */
    private static final Set<String> RESOURCE_GROUPS =
            new HashSet<>(Arrays.asList("ore", "rare_ore", "volatiles", "organics", "farmland", "surface"));
    private static Map<String, Float> nexValues = null;

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        StarSystemAPI system;
        String a = args.trim();
        if (a.isEmpty()) {
            system = WsUtil.currentSystem(context);
            if (system == null) {
                WsUtil.print("You are not in a star system. Usage: showsystem [system]");
                return CommandResult.BAD_SYNTAX;
            }
        } else {
            List<StarSystemAPI> exact = WsUtil.exactSystems(a);
            if (exact.size() > 1) {
                WsUtil.print("Several systems match '" + a + "':");
                for (StarSystemAPI s : exact) WsUtil.printRaw("  - " + s.getName() + " (ID " + s.getId() + ")");
                return CommandResult.ERROR;
            }
            system = exact.size() == 1 ? exact.get(0) : CommandUtils.findBestSystemMatch(a.replace('_', ' '));
            if (system == null) system = CommandUtils.findBestSystemMatch(a);
            if (system == null) {
                WsUtil.print("No star system found matching '" + a + "'.");
                return CommandResult.ERROR;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(system.getName()).append(" (ID ").append(system.getId()).append(")\n");
        sb.append("  Stars: ").append(starTypes(system));
        sb.append(" | Stable locations: ").append(Misc.getNumStableLocations(system));
        sb.append(" | Gate: ").append(system.getEntitiesWithTag(Tags.GATE).isEmpty() ? "no" : "yes").append("\n");

        Map<SectorEntityToken, Integer> depth = new HashMap<>();
        List<SectorEntityToken> bodies = orderedBodies(system, depth);
        if (bodies.isEmpty()) sb.append("  (no planets or stations)\n");
        for (SectorEntityToken e : bodies) {
            int d = depth.containsKey(e) ? depth.get(e) : 0;
            sb.append("  ");
            for (int i = 0; i < d; i++) sb.append("  ");
            sb.append("- ").append(line(e)).append("\n");
        }
        Console.showMessage(sb.toString());
        return CommandResult.SUCCESS;
    }

    private static String starTypes(StarSystemAPI system) {
        List<String> out = new ArrayList<>();
        PlanetAPI[] stars = {system.getStar(), system.getSecondary(), system.getTertiary()};
        for (PlanetAPI s : stars) {
            if (s != null) out.add(s.getName() + " (" + s.getSpec().getName() + ")");
        }
        if (out.isEmpty()) return "none (nebula)";
        return String.join(", ", out);
    }

    /** Planets by distance from the center, each followed by the moons and stations that orbit it. */
    private static List<SectorEntityToken> orderedBodies(StarSystemAPI system, Map<SectorEntityToken, Integer> depth) {
        Set<SectorEntityToken> all = new LinkedHashSet<>();
        for (PlanetAPI p : system.getPlanets()) {
            if (!p.isStar()) all.add(p);
        }
        for (SectorEntityToken e : system.getAllEntities()) {
            if (WsUtil.isStation(e)) all.add(e);
        }
        final Vector2f center = system.getCenter() != null ? system.getCenter().getLocation() : new Vector2f(0f, 0f);
        List<SectorEntityToken> roots = new ArrayList<>();
        for (SectorEntityToken e : all) {
            SectorEntityToken f = e.getOrbitFocus();
            if (f == null || !all.contains(f)) roots.add(e);
        }
        roots.sort((x, y) -> Float.compare(Misc.getDistance(x.getLocation(), center),
                Misc.getDistance(y.getLocation(), center)));
        List<SectorEntityToken> out = new ArrayList<>();
        for (SectorEntityToken r : roots) addWithChildren(r, 0, all, out, depth);
        return out;
    }

    private static void addWithChildren(final SectorEntityToken parent, int d, Set<SectorEntityToken> all,
                                        List<SectorEntityToken> out, Map<SectorEntityToken, Integer> depth) {
        if (depth.containsKey(parent)) return;
        out.add(parent);
        depth.put(parent, d);
        List<SectorEntityToken> kids = new ArrayList<>();
        for (SectorEntityToken e : all) {
            if (e.getOrbitFocus() == parent) kids.add(e);
        }
        kids.sort((x, y) -> Float.compare(Misc.getDistance(x.getLocation(), parent.getLocation()),
                Misc.getDistance(y.getLocation(), parent.getLocation())));
        for (SectorEntityToken k : kids) addWithChildren(k, d + 1, all, out, depth);
    }

    private static String line(SectorEntityToken e) {
        MarketAPI m = e.getMarket();
        String type;
        if (e instanceof PlanetAPI) type = ((PlanetAPI) e).getSpec().getName();
        else type = "station";
        boolean surveyed = m != null && m.getSurveyLevel() == MarketAPI.SurveyLevel.FULL;
        boolean colony = WsUtil.isColony(m);
        String hazard;
        if (m == null || !(e instanceof PlanetAPI)) hazard = "-";
        else if (surveyed || colony) hazard = Math.round(m.getHazardValue() * 100f) + "%";
        else hazard = "?";
        String summary;
        if (!(e instanceof PlanetAPI)) summary = "-";
        else if (!surveyed && !colony) summary = "unsurveyed";
        else summary = summary(m);
        return e.getName() + " | " + type + " | hazard " + hazard + " | " + WsUtil.owner(e) + " | " + summary
                + " | ID " + e.getId();
    }

    private static String shortName(String conditionId) {
        String n = Global.getSettings().getMarketConditionSpec(conditionId).getName();
        n = n.toLowerCase(Locale.ROOT);
        if (n.endsWith(" deposits")) n = n.substring(0, n.length() - " deposits".length());
        return n;
    }

    private static ConditionGenDataSpec gen(String id) {
        return (ConditionGenDataSpec) Global.getSettings().getSpec(ConditionGenDataSpec.class, id, true);
    }

    private static Map<String, Float> nexValues() {
        if (nexValues != null) return nexValues;
        Map<String, Float> map = new HashMap<>();
        try {
            JSONObject o = Global.getSettings().getJSONObject("nex_colonyConditionValues");
            if (o != null) {
                java.util.Iterator<?> it = o.keys();
                while (it.hasNext()) {
                    String k = (String) it.next();
                    map.put(k, (float) o.getDouble(k));
                }
            }
        } catch (Exception ex) {
            // setting missing: fall back to the generator's rank
        }
        nexValues = map;
        return map;
    }

    private static float resourceValue(String id, ConditionGenDataSpec g) {
        Float v = nexValues().get(id);
        if (v != null) return v;
        return g == null ? 0f : g.getRank() * 0.01f;
    }

    static String summary(MarketAPI m) {
        List<String> parts = new ArrayList<>();
        if (m.hasCondition(Conditions.HABITABLE)) parts.add("habitable");
        String ruins = null;
        List<String> resources = new ArrayList<>();
        final Map<String, Float> value = new HashMap<>();
        String worst = null;
        float worstHazard = 0f;
        for (MarketConditionAPI c : m.getConditions()) {
            String id = c.getId();
            if (Global.getSettings().getMarketConditionSpec(id) == null) continue;
            ConditionGenDataSpec g = gen(id);
            if (id.startsWith("ruins_") || (g != null && "ruins".equals(g.getGroup()))) ruins = shortName(id);
            if (g != null && g.getGroup() != null && RESOURCE_GROUPS.contains(g.getGroup())) {
                resources.add(id);
                value.put(id, resourceValue(id, g));
            }
            if (g != null && g.getHazard() > worstHazard) {
                worstHazard = g.getHazard();
                worst = id;
            }
        }
        if (ruins != null) parts.add(ruins);
        resources.sort((a, b) -> Float.compare(value.get(b), value.get(a)));
        for (int i = 0; i < Math.min(2, resources.size()); i++) parts.add(shortName(resources.get(i)));
        if (m.getHazardValue() >= 2f && worst != null) parts.add(shortName(worst));
        return parts.isEmpty() ? "-" : String.join(", ", parts);
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.systemSuggestions();
        return new ArrayList<>();
    }
}
