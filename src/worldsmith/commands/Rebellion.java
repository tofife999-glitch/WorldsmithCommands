package worldsmith.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.campaign.intel.rebellion.RebellionCreator;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.NexConfig;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.FactionSlots;
import worldsmith.WsUtil;

import java.util.List;

/**
 * Starts a Nexerelin rebellion in one colony. The rebels fight for the faction you name, or one Nexerelin
 * picks from the colony's enemies, or for independence. Nexerelin runs the rest: if the rebels win, the
 * colony changes hands.
 */
public class Rebellion implements BaseCommandWithSuggestion {
    private static final String USAGE = "rebellion [colony] [backing faction]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        RebellionCreator creator = RebellionCreator.getInstance();
        if (!NexConfig.enableInvasions || creator == null) {
            WsUtil.print("Nexerelin's rebellions are switched off in this game (they need invasions enabled).");
            return CommandResult.ERROR;
        }
        List<String> parts = WsUtil.splitArgs(args);
        // The colony is the longest leading part that names one; whatever is left is the backing faction.
        SectorEntityToken[] found = new SectorEntityToken[1];
        int used = parts.isEmpty() ? 0 : WsUtil.leadingBody(parts, WsUtil.Kind.COLONY, found);
        if (used < 0) return CommandResult.ERROR;
        SectorEntityToken target;
        if (used > 0) {
            target = found[0];
        } else {
            target = WsUtil.dialogTarget(context);
            if (target == null || !WsUtil.isColony(target.getMarket())) {
                if (!parts.isEmpty()) WsUtil.findBody(parts.get(0), WsUtil.Kind.COLONY, "colony"); // explains or suggests
                else WsUtil.print("No colony given and no colony dialog is open. Usage: " + USAGE);
                return CommandResult.ERROR;
            }
        }
        MarketAPI market = target.getMarket();
        FactionAPI backer = null;
        if (used < parts.size()) {
            backer = WsUtil.parseFaction(WsUtil.join(parts, used, parts.size()).replace(' ', '_'));
            if (backer == null) return CommandResult.ERROR;
            if (backer == market.getFaction() || FactionSlots.isUnused(backer) || backer.isNeutralFaction()) {
                WsUtil.print(backer.getDisplayName() + " can't back a rebellion against " + market.getName() + ".");
                return CommandResult.ERROR;
            }
        }
        if (RebellionIntel.isOngoing(market)) {
            WsUtil.print(market.getName() + " is already in rebellion; follow it in the Intel screen.");
            return CommandResult.ERROR;
        }
        RebellionIntel rebellion = backer != null ? creator.createRebellion(market, backer.getId(), true)
                : creator.createRebellion(market, true);
        if (rebellion == null && backer == null && !Factions.INDEPENDENT.equals(market.getFactionId())) {
            rebellion = creator.createRebellion(market, Factions.INDEPENDENT, true);
        }
        if (rebellion == null) {
            WsUtil.print("Nexerelin did not start a rebellion on " + market.getName()
                    + " (nobody to rebel for). Name a backing faction: " + USAGE);
            return CommandResult.ERROR;
        }
        WsUtil.print("A rebellion broke out on " + market.getName() + " (" + WsUtil.systemName(target) + ", "
                + market.getFaction().getDisplayName() + " size " + market.getSize() + "): " + "the rebels fight for "
                + rebellion.getRebelFaction().getDisplayNameWithArticle() + ". Follow it in the Intel screen; if they win,"
                + " the colony changes hands.");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        List<String> l = WsUtil.factionSuggestions();
        if (parameter == 0) l.addAll(WsUtil.bodySuggestions(WsUtil.Kind.COLONY));
        return l;
    }
}
