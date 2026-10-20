package worldsmith.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.FactionIdentity;
import worldsmith.FactionSlots;
import worldsmith.WsUtil;

import java.util.List;
import java.util.Random;

/** Changes any faction's flag: a random or numbered player flag, another faction's flag, or back to its own. */
public class FactionFlag implements BaseCommandWithSuggestion {
    private static final String USAGE = "factionflag <faction> [random|<number>|<other faction>|reset]   or: factionflag list";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        List<String[]> flags = FactionIdentity.playerFlags();
        if (parts.size() == 1 && parts.get(0).equalsIgnoreCase("list")) {
            WsUtil.print(flags.size() + " player flags (the ones you can pick for your own faction). Use the number:");
            for (int i = 0; i < flags.size(); i++) {
                String logo = flags.get(i)[0];
                WsUtil.printRaw("  #" + (i + 1) + "  " + logo.substring(logo.lastIndexOf('/') + 1));
            }
            return CommandResult.SUCCESS;
        }
        if (parts.isEmpty() || parts.size() > 2) {
            WsUtil.print("Usage: " + USAGE + "   (write multi-word faction names with underscores)");
            return CommandResult.BAD_SYNTAX;
        }
        FactionAPI f = WsUtil.parseFaction(parts.get(0));
        if (f == null) return CommandResult.ERROR;
        if (FactionSlots.isUnused(f)) {
            WsUtil.print(f.getId() + " is an unused faction slot; spawnfaction fills it.");
            return CommandResult.ERROR;
        }
        String option = parts.size() > 1 ? parts.get(1) : "random";
        String result;
        if (option.equalsIgnoreCase("reset")) {
            FactionIdentity.resetFlag(f);
            result = "its own flag again";
        } else if (option.equalsIgnoreCase("random")) {
            int i = FactionIdentity.randomFlagIndex(new Random());
            FactionIdentity.setFlag(f, flags.get(i)[0], flags.get(i)[1]);
            result = "player flag #" + (i + 1);
        } else if (WsUtil.isInt(option)) {
            int i = Integer.parseInt(option) - 1;
            if (i < 0 || i >= flags.size()) {
                WsUtil.print("There are " + flags.size() + " player flags; pick 1-" + flags.size() + " ('factionflag list').");
                return CommandResult.BAD_SYNTAX;
            }
            FactionIdentity.setFlag(f, flags.get(i)[0], flags.get(i)[1]);
            result = "player flag #" + (i + 1);
        } else {
            FactionAPI other = WsUtil.parseFaction(option);
            if (other == null) return CommandResult.ERROR;
            FactionIdentity.setFlag(f, other.getLogo(), other.getCrest());
            result = "the flag of " + other.getDisplayNameWithArticle();
        }
        WsUtil.print("Changed the flag of " + f.getDisplayNameWithArticle() + " (" + f.getId() + ") to " + result + ".");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) {
            List<String> l = WsUtil.factionSuggestions();
            l.add("list");
            return l;
        }
        if (parameter == 1) {
            List<String> l = WsUtil.list("random", "reset");
            l.addAll(WsUtil.factionSuggestions());
            return l;
        }
        return WsUtil.list();
    }
}
