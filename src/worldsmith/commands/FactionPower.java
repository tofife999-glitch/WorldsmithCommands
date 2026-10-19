package worldsmith.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.FactionEffects;
import worldsmith.WsUtil;

import java.util.List;

/** Read-only faction report; "clear" ends all Worldsmith effects on that faction early. */
public class FactionPower implements BaseCommandWithSuggestion {
    private static final String USAGE = "factionpower <faction> [clear]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        boolean clear = !parts.isEmpty() && parts.get(parts.size() - 1).equalsIgnoreCase("clear");
        if (clear) parts.remove(parts.size() - 1);
        if (parts.isEmpty()) {
            WsUtil.print("Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        FactionAPI faction = WsUtil.parseFaction(WsUtil.join(parts, 0, parts.size()).replace(' ', '_'));
        if (faction == null) return CommandResult.ERROR;
        if (clear) {
            WsUtil.print(FactionEffects.clear(faction));
            return CommandResult.SUCCESS;
        }
        Console.showMessage(FactionEffects.report(faction));
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.factionSuggestions();
        return WsUtil.list("clear");
    }
}
