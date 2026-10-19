package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;

public class NpcColony implements BaseCommandWithSuggestion {
    private static final String USAGE = "npccolony [planet] <faction> [size 1-10, default 3]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        int size = 3;
        if (parts.size() >= 2 && WsUtil.isInt(parts.get(parts.size() - 1))) {
            size = Integer.parseInt(parts.remove(parts.size() - 1));
            if (size < 1 || size > 10) {
                WsUtil.print("Size must be between 1 and 10.");
                return CommandResult.BAD_SYNTAX;
            }
        }
        if (parts.isEmpty()) {
            WsUtil.print("Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        String factionToken = parts.remove(parts.size() - 1);
        SectorEntityToken target = WsUtil.resolveTarget(WsUtil.join(parts, 0, parts.size()), context,
                WsUtil.Kind.UNCOLONIZED, "uncolonized planet", USAGE);
        if (target == null) return CommandResult.ERROR;
        FactionAPI faction = WsUtil.parseFaction(factionToken);
        if (faction == null) return CommandResult.ERROR;

        PlanetAPI planet = (PlanetAPI) target;
        MarketAPI market = planet.getMarket();
        if (market == null || !market.isPlanetConditionMarketOnly()) {
            WsUtil.print(planet.getName() + " has no uncolonized planet market; it cannot be colonized this way.");
            return CommandResult.ERROR;
        }
        if (planet.getStarSystem() == null) {
            WsUtil.print(planet.getName() + " is not in a star system; Nexerelin can only colonize planets in systems.");
            return CommandResult.ERROR;
        }
        String oldName = planet.getName();
        try {
            ColonyExpeditionIntel.createColonyStatic(market, planet, faction, false, faction.isPlayerFaction(), size);
        } catch (Exception ex) {
            Global.getLogger(NpcColony.class).error("Worldsmith: createColonyStatic failed", ex);
            WsUtil.print("Nexerelin failed while founding the colony on " + oldName + ": " + ex
                    + " (details in starsector.log).");
            return CommandResult.ERROR;
        } finally {
            WsUtil.invalidateCache();
        }
        String renamed = oldName.equals(planet.getName()) ? "" : " (Nexerelin renamed it from " + oldName + ")";
        WsUtil.print("Founded a size " + market.getSize() + " colony for " + WsUtil.factionLabel(faction) + " on "
                + planet.getName() + renamed + " in " + WsUtil.systemName(planet) + ".");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) {
            List<String> l = WsUtil.bodySuggestions(WsUtil.Kind.UNCOLONIZED);
            l.addAll(WsUtil.factionSuggestions());
            return l;
        }
        List<String> l = WsUtil.factionSuggestions();
        if (parameter >= 1) l.addAll(WsUtil.list("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
        return l;
    }
}
