package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.FactionEffects;
import worldsmith.OrbitCheck;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Everything about one planet, moon or station in one place. */
public class PlanetInfo implements BaseCommandWithSuggestion {
    private static final String USAGE = "planetinfo [planet]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        SectorEntityToken e = WsUtil.resolveTarget(args.trim(), context, WsUtil.Kind.ANY, "planet or station", USAGE);
        if (e == null) return CommandResult.ERROR;
        MarketAPI m = e.getMarket();
        boolean colony = WsUtil.isColony(m);
        boolean surveyed = m != null && m.getSurveyLevel() == MarketAPI.SurveyLevel.FULL;
        boolean known = surveyed || colony;

        StringBuilder sb = new StringBuilder();
        String type = e instanceof PlanetAPI
                ? ((PlanetAPI) e).getSpec().getName() + ", type " + ((PlanetAPI) e).getSpec().getPlanetType()
                : "station";
        sb.append(WsUtil.PREFIX).append(e.getName()).append(" (").append(type).append(") in ")
                .append(WsUtil.systemName(e)).append("\n");
        sb.append("  ID ").append(e.getId());
        if (m != null) sb.append(" | market ID ").append(m.getId());
        sb.append("\n");

        // Size and orbit
        sb.append("  Radius ").append(Math.round(e.getRadius()));
        SectorEntityToken focus = e.getOrbitFocus();
        if (focus != null) {
            float d = Misc.getDistance(e.getLocation(), focus.getLocation());
            sb.append(" | orbits ").append(focus.getName() == null ? focus.getId() : focus.getName())
                    .append(" at distance ").append(Math.round(d));
            if (e.getOrbit() != null && e.getOrbit().getOrbitalPeriod() > 0)
                sb.append(", ").append(Math.round(e.getOrbit().getOrbitalPeriod())).append(" days per orbit");
        }
        sb.append("\n");

        // Owner and growth
        if (colony) {
            sb.append("  Owner: ").append(m.getFaction().getDisplayName()).append(" | size ").append(m.getSize())
                    .append(" (max ").append(Misc.getMaxMarketSize(m)).append(")")
                    .append(" | stability ").append(Math.round(m.getStabilityValue()));
            if (m.getMemoryWithoutUpdate().contains(FactionEffects.MEM_FREEZE)) {
                sb.append(" | growth frozen by Worldsmith for ")
                        .append(months(m.getMemoryWithoutUpdate().getExpire(FactionEffects.MEM_FREEZE))).append(" more");
            } else if (m.getMemoryWithoutUpdate().contains(PopCap.MEM_GROWTH_LIMIT)) {
                sb.append(" | popcap ").append(m.getMemoryWithoutUpdate().getLong(PopCap.MEM_GROWTH_LIMIT));
            }
            if (m.getMemoryWithoutUpdate().contains(ColonyExpeditionIntel.MEMORY_KEY_COLONY))
                sb.append(" | Nex NPC colony");
            sb.append("\n");
            List<String> inds = new ArrayList<>();
            for (Industry ind : m.getIndustries()) {
                String s = ind.getCurrentName();
                if (ind.isDisrupted()) s += " (disrupted)";
                inds.add(s);
            }
            sb.append("  Industries: ").append(inds.isEmpty() ? "none" : String.join(", ", inds)).append("\n");
        } else {
            sb.append("  Owner: unclaimed").append(surveyed ? "" : " | not fully surveyed").append("\n");
        }

        // Hazard breakdown and conditions
        if (m != null && known) {
            MutableStat hazard = m.getHazard();
            List<String> parts = new ArrayList<>();
            parts.add("base " + pct(hazard.getBaseValue()));
            for (MutableStat.StatMod mod : hazard.getFlatMods().values()) {
                if (mod.value == 0f) continue;
                String what = mod.desc != null ? mod.desc : mod.source;
                parts.add(what + " " + (mod.value > 0 ? "+" : "") + pct(mod.value));
            }
            sb.append("  Hazard ").append(pct(m.getHazardValue())).append(": ").append(String.join(", ", parts)).append("\n");
            List<String> conds = new ArrayList<>();
            for (MarketConditionAPI c : m.getConditions()) {
                if (Global.getSettings().getMarketConditionSpec(c.getId()) == null) continue;
                conds.add(c.getName());
            }
            sb.append("  Conditions: ").append(conds.isEmpty() ? "none" : String.join(", ", conds)).append("\n");
            if (e instanceof PlanetAPI) sb.append("  Summary: ").append(ShowSystem.summary(m)).append("\n");
        } else if (m != null) {
            sb.append("  Conditions and hazard: unsurveyed (the 'survey' command reveals them)\n");
        }

        // What orbits it
        List<SectorEntityToken> all = OrbitCheck.relevant(e.getContainingLocation());
        Set<SectorEntityToken> sats = OrbitCheck.satellites(e, all);
        List<String> names = new ArrayList<>();
        for (SectorEntityToken s : sats) {
            if (OrbitCheck.parentOf(s) == e) names.add(OrbitCheck.label(s));
        }
        sb.append("  Orbiting it: ").append(names.isEmpty() ? "nothing" : String.join(", ", names));
        Console.showMessage(sb.toString());
        return CommandResult.SUCCESS;
    }

    private static String pct(float v) {
        return Math.round(v * 100f) + "%";
    }

    static String months(float days) {
        int mo = Math.max(1, Math.round(days / 30f));
        return mo + (mo == 1 ? " month" : " months");
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.bodySuggestions(WsUtil.Kind.ANY);
        return new ArrayList<>();
    }
}
