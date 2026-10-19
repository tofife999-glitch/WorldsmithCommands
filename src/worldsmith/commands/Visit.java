package worldsmith.commands;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;

public class Visit implements BaseCommandWithSuggestion {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        if (args.trim().isEmpty()) {
            WsUtil.print("Usage: visit <planet|station|star>");
            return CommandResult.BAD_SYNTAX;
        }
        SectorEntityToken e = WsUtil.findBody(args.trim(), WsUtil.Kind.ANY, "planet, station or star");
        if (e == null) return CommandResult.ERROR;
        Console.showDialogOnClose(e);
        WsUtil.print("Opening the dialog of " + e.getName() + " in " + WsUtil.systemName(e)
                + " when you close the console.");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.bodySuggestions(WsUtil.Kind.ANY);
        return new ArrayList<>();
    }
}
