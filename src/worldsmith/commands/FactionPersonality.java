package worldsmith.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.FactionCharacter;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How a faction made with spawnfaction behaves: warlike or peaceful (Nexerelin diplomacy traits), a pirate
 * faction or lawful, expansionist or isolationist (Nexerelin colony expeditions), or back to normal.
 */
public class FactionPersonality implements BaseCommandWithSuggestion {
    private static final String USAGE = "factionpersonality <faction> [warlike|peaceful|pirate|lawful|expansionist|isolationist|normal]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        if (parts.isEmpty() || parts.size() > 2) {
            WsUtil.print("Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        FactionAPI f = FactionCharacter.spawnedFaction(parts.get(0));
        if (f == null) return CommandResult.ERROR;
        String who = f.getDisplayNameWithArticle();
        if (parts.size() == 1) {
            WsUtil.print(Character.toUpperCase(who.charAt(0)) + who.substring(1) + ": " + FactionCharacter.describe(f)
                    + ". Usage: " + USAGE);
            return CommandResult.SUCCESS;
        }
        String p = parts.get(1).toLowerCase(Locale.ROOT);
        if (!FactionCharacter.PERSONALITIES.contains(p)) {
            WsUtil.print("Unknown personality '" + parts.get(1) + "'. Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        String what = FactionCharacter.applyPersonality(f, p);
        WsUtil.print(Character.toUpperCase(who.charAt(0)) + who.substring(1) + " is now " + what + ".");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.factionSuggestions();
        if (parameter == 1) return new ArrayList<>(FactionCharacter.PERSONALITIES);
        return WsUtil.list();
    }
}
