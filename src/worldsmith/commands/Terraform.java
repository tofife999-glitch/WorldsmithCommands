package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.PlanetGen;
import worldsmith.WsUtil;

import java.util.List;
import java.util.Random;

public class Terraform implements BaseCommandWithSuggestion {
    private static final String USAGE = "terraform [planet] <type> [keep]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        boolean keep = !parts.isEmpty() && parts.get(parts.size() - 1).equalsIgnoreCase("keep");
        if (keep) parts.remove(parts.size() - 1);
        if (parts.isEmpty()) {
            WsUtil.print("Usage: " + USAGE + "  (type 'planettypes' to list types)");
            return CommandResult.BAD_SYNTAX;
        }
        String typeToken = parts.remove(parts.size() - 1);
        String name = WsUtil.join(parts, 0, parts.size());
        WsUtil.Kind kind = keep ? WsUtil.Kind.PLANET : WsUtil.Kind.UNCOLONIZED;
        SectorEntityToken target = WsUtil.resolveTarget(name, context, kind,
                keep ? "planet" : "uncolonized planet", USAGE);
        if (target == null) {
            if (!keep && namesAColony(name, context))
                WsUtil.printRaw("Colonies can only change type with 'keep' (conditions stay as they are).");
            return CommandResult.ERROR;
        }
        PlanetSpecAPI spec = WsUtil.findPlanetType(typeToken);
        if (spec == null) return CommandResult.ERROR;

        PlanetAPI planet = (PlanetAPI) target;
        String oldType = planet.getSpec().getPlanetType();
        String oldTypeName = planet.getSpec().getName();
        String newType = spec.getPlanetType();
        Random random = new Random();

        if (keep) {
            planet.changeType(newType, random);
            WsUtil.print("Changed " + planet.getName() + " in " + WsUtil.systemName(planet) + " from " + oldTypeName
                    + " to " + planet.getSpec().getName() + " (" + newType + "); conditions kept.");
            return CommandResult.SUCCESS;
        }

        String why = PlanetGen.whyCannotRoll(planet, newType);
        if (why != null) {
            WsUtil.print("Can't terraform " + planet.getName() + ": " + why + ". Add 'keep' to change only the type.");
            return CommandResult.ERROR;
        }
        MarketAPI market = planet.getMarket();
        boolean surveyed = market != null && market.getSurveyLevel() == MarketAPI.SurveyLevel.FULL;
        PlanetGen.Snapshot before = PlanetGen.snapshot(market);
        List<String> special = PlanetGen.specialConditionNames(market);
        try {
            planet.changeType(newType, random);
            PlanetGen.rollConditions(planet, surveyed);
        } catch (Exception ex) {
            Global.getLogger(Terraform.class).error("Worldsmith: terraform failed, restoring", ex);
            try {
                planet.changeType(oldType, random);
                PlanetGen.restore(market, before);
            } catch (Exception ignored) {
            }
            WsUtil.print("Terraforming " + planet.getName() + " failed (" + ex + "); the old type and conditions"
                    + " were restored. You can still use 'keep' to change only the type.");
            return CommandResult.ERROR;
        }
        String conds = surveyed ? PlanetGen.conditionNames(planet.getMarket()) : "unsurveyed";
        String removed = special.isEmpty() ? "" : " Removed special conditions: " + String.join(", ", special) + ".";
        WsUtil.print("Terraformed " + planet.getName() + " in " + WsUtil.systemName(planet) + " from " + oldTypeName
                + " to " + planet.getSpec().getName() + " (" + newType + "); new conditions: " + conds + "." + removed);
        return CommandResult.SUCCESS;
    }

    private static boolean namesAColony(String name, CommandContext context) {
        if (name.isEmpty()) {
            SectorEntityToken d = WsUtil.dialogTarget(context);
            return d != null && WsUtil.isColony(d.getMarket());
        }
        return !WsUtil.exactMatches(name, WsUtil.bodies(WsUtil.Kind.COLONY)).isEmpty();
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        List<String> l;
        if (parameter == 0) {
            l = WsUtil.bodySuggestions(WsUtil.Kind.PLANET);
            l.addAll(WsUtil.typeSuggestions());
        } else {
            l = WsUtil.typeSuggestions();
            l.add("keep");
        }
        return l;
    }
}
