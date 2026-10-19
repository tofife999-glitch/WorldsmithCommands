package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.impl.campaign.procgen.ConditionGenDataSpec;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Searches every planet and moon in the sector for the given filters, nearest first. */
public class FindPlanet implements BaseCommandWithSuggestion {
    private static final int MAX_RESULTS = 20;
    private static final Pattern HAZARD = Pattern.compile("hazard(<=|>=|<|>|=)(\\d+)%?");

    private static void printHelp() {
        WsUtil.print("Usage: findplanet <filters>   e.g. findplanet habitable farmland hazard<150 within:20");
        WsUtil.printRaw("  Filters (combine any): a condition ID or group (farmland_rich, farmland, ore, ruins_vast, ruins),");
        WsUtil.printRaw("  -condition to exclude one (-toxic_atmosphere), habitable, uncolonized, colonized, type:<planet type>,");
        WsUtil.printRaw("  hazard<150 (also <=, >, >=), within:<light-years from you>, owner:<faction>,");
        WsUtil.printRaw("  all (also search planets you haven't surveyed; without it, only surveyed planets and colonies).");
    }

    /** Condition IDs a filter word stands for: an exact ID or name, a generator group, or an ID prefix. */
    private static Set<String> resolveConditions(String token) {
        Set<String> out = new LinkedHashSet<>();
        String t = token.toLowerCase(Locale.ROOT);
        String n = WsUtil.norm(token);
        for (MarketConditionSpecAPI spec : Global.getSettings().getAllMarketConditionSpecs()) {
            if (spec.getId().equalsIgnoreCase(t) || WsUtil.norm(spec.getName()).equals(n)) out.add(spec.getId());
        }
        if (!out.isEmpty()) return out;
        for (ConditionGenDataSpec g : Global.getSettings().getAllSpecs(ConditionGenDataSpec.class)) {
            if (g.getId().endsWith(ConditionGenDataSpec.NO_PICK_SUFFIX)) continue;
            if (t.equals(g.getGroup()) || g.getId().startsWith(t + "_")) out.add(g.getId());
        }
        return out;
    }

    private static List<String> conditionIdSuggestions(String token) {
        List<String> hits = new ArrayList<>();
        for (MarketConditionSpecAPI spec : Global.getSettings().getAllMarketConditionSpecs()) {
            int s = WsUtil.similarity(token, spec.getId());
            if (s != Integer.MAX_VALUE) hits.add(spec.getId());
            if (hits.size() >= 5) break;
        }
        return hits;
    }

    private static boolean hasAny(MarketAPI m, Set<String> ids) {
        if (m == null) return false;
        for (MarketConditionAPI c : m.getConditions()) {
            if (ids.contains(c.getId())) return true;
        }
        return false;
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> tokens = WsUtil.splitArgs(args);
        if (tokens.isEmpty()) {
            printHelp();
            return CommandResult.BAD_SYNTAX;
        }

        boolean includeUnsurveyed = false;
        Boolean wantColony = null;
        String typeFilter = null;
        FactionAPI owner = null;
        float within = -1f;
        String hazardOp = null;
        float hazardValue = 0f;
        List<Set<String>> required = new ArrayList<>();
        Set<String> excluded = new LinkedHashSet<>();

        for (String raw : tokens) {
            String t = raw.toLowerCase(Locale.ROOT);
            Matcher hm = HAZARD.matcher(t);
            if (t.equals("all")) {
                includeUnsurveyed = true;
            } else if (t.equals("uncolonized") || t.equals("uncolonised")) {
                wantColony = false;
            } else if (t.equals("colonized") || t.equals("colonised") || t.equals("colony")) {
                wantColony = true;
            } else if (t.startsWith("type:")) {
                typeFilter = WsUtil.norm(raw.substring(5));
            } else if (t.startsWith("owner:") || t.startsWith("faction:")) {
                owner = WsUtil.parseFaction(raw.substring(raw.indexOf(':') + 1));
                if (owner == null) return CommandResult.ERROR;
                wantColony = true;
            } else if (t.startsWith("within:")) {
                try {
                    within = Float.parseFloat(t.substring(7));
                } catch (NumberFormatException ex) {
                    WsUtil.print("'" + raw + "' needs a number of light-years, e.g. within:15");
                    return CommandResult.BAD_SYNTAX;
                }
            } else if (hm.matches()) {
                hazardOp = hm.group(1);
                hazardValue = Float.parseFloat(hm.group(2));
            } else if (t.startsWith("-") || t.startsWith("no:")) {
                String c = t.startsWith("-") ? raw.substring(1) : raw.substring(3);
                Set<String> ids = resolveConditions(c);
                if (ids.isEmpty()) {
                    WsUtil.print("Unknown condition '" + c + "'. Close matches: " + String.join(", ", conditionIdSuggestions(c)));
                    return CommandResult.BAD_SYNTAX;
                }
                excluded.addAll(ids);
            } else {
                Set<String> ids = resolveConditions(raw);
                if (ids.isEmpty()) {
                    WsUtil.print("Unknown filter '" + raw + "'. Close condition IDs: "
                            + String.join(", ", conditionIdSuggestions(raw)));
                    printHelp();
                    return CommandResult.BAD_SYNTAX;
                }
                required.add(ids);
            }
        }

        Vector2f here = Global.getSector().getPlayerFleet().getLocationInHyperspace();
        final Map<SectorEntityToken, Float> dist = new HashMap<>();
        List<SectorEntityToken> hits = new ArrayList<>();
        for (SectorEntityToken e : WsUtil.bodies(WsUtil.Kind.PLANET)) {
            MarketAPI m = e.getMarket();
            boolean colony = WsUtil.isColony(m);
            boolean surveyed = m != null && m.getSurveyLevel() == MarketAPI.SurveyLevel.FULL;
            if (!includeUnsurveyed && !surveyed && !colony) continue;
            if (wantColony != null && wantColony != colony) continue;
            if (owner != null && (m == null || m.getFaction() != owner)) continue;
            if (typeFilter != null) {
                PlanetAPI p = (PlanetAPI) e;
                String id = WsUtil.norm(p.getSpec().getPlanetType());
                String name = WsUtil.norm(p.getSpec().getName());
                if (!id.contains(typeFilter) && !name.contains(typeFilter)) continue;
            }
            if (hazardOp != null) {
                if (m == null) continue;
                float h = m.getHazardValue() * 100f;
                boolean ok;
                switch (hazardOp) {
                    case "<": ok = h < hazardValue; break;
                    case "<=": ok = h <= hazardValue; break;
                    case ">": ok = h > hazardValue; break;
                    case ">=": ok = h >= hazardValue; break;
                    default: ok = Math.abs(h - hazardValue) < 0.5f;
                }
                if (!ok) continue;
            }
            boolean all = true;
            for (Set<String> ids : required) {
                if (!hasAny(m, ids)) {
                    all = false;
                    break;
                }
            }
            if (!all) continue;
            if (!excluded.isEmpty() && hasAny(m, excluded)) continue;
            float d = Misc.getDistanceLY(here, e.getLocationInHyperspace());
            if (within >= 0f && d > within) continue;
            dist.put(e, d);
            hits.add(e);
        }

        if (hits.isEmpty()) {
            WsUtil.print("No planets match those filters" + (includeUnsurveyed ? "." : " (add 'all' to include unsurveyed planets)."));
            return CommandResult.SUCCESS;
        }
        hits.sort((a, b) -> Float.compare(dist.get(a), dist.get(b)));
        StringBuilder sb = new StringBuilder();
        sb.append(WsUtil.PREFIX).append(hits.size()).append(hits.size() == 1 ? " planet matches" : " planets match")
                .append(hits.size() > MAX_RESULTS ? " (nearest " + MAX_RESULTS + " shown)" : "").append(":\n");
        for (int i = 0; i < Math.min(MAX_RESULTS, hits.size()); i++) {
            SectorEntityToken e = hits.get(i);
            MarketAPI m = e.getMarket();
            boolean known = m != null && (m.getSurveyLevel() == MarketAPI.SurveyLevel.FULL || WsUtil.isColony(m));
            String hazard = m == null ? "-" : Math.round(m.getHazardValue() * 100f) + "%";
            sb.append("  - ").append(e.getName()).append(" | ").append(WsUtil.systemName(e)).append(" | ")
                    .append(((PlanetAPI) e).getSpec().getName()).append(" | hazard ").append(hazard).append(" | ")
                    .append(WsUtil.owner(e)).append(" | ")
                    .append(m == null ? "-" : (known ? ShowSystem.summary(m) : "unsurveyed: " + ShowSystem.summary(m)))
                    .append(" | ").append(WsUtil.ly(dist.get(e))).append(" | ID ").append(e.getId()).append("\n");
        }
        Console.showMessage(sb.toString());
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        List<String> l = WsUtil.list("habitable", "uncolonized", "colonized", "ruins", "farmland", "ore", "rare_ore",
                "organics", "volatiles", "hazard<150", "within:10", "type:", "owner:", "all");
        for (ConditionGenDataSpec g : Global.getSettings().getAllSpecs(ConditionGenDataSpec.class)) {
            if (!g.getId().endsWith(ConditionGenDataSpec.NO_PICK_SUFFIX)) l.add(g.getId());
        }
        return l;
    }
}
