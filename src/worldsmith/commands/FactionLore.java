package worldsmith.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.FactionIdentity;
import worldsmith.FactionSlots;
import worldsmith.WsUtil;

import java.util.List;
import java.util.Locale;

/**
 * Shows or rewrites the lore of a faction: the text on its page in the Intel screen's faction tab. Kept in the
 * save as plain text and put back whenever the save loads; 'reset' brings back the faction's own text.
 */
public class FactionLore implements BaseCommandWithSuggestion {
    private static final String USAGE = "factionlore <faction> [<text>|add <text>|reset]   (a | starts a new paragraph)";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String a = args.trim();
        if (a.isEmpty()) {
            WsUtil.print("Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        int space = a.indexOf(' ');
        String factionToken = space < 0 ? a : a.substring(0, space);
        String rest = space < 0 ? "" : a.substring(space + 1).trim();
        FactionAPI f = WsUtil.parseFaction(factionToken);
        if (f == null) return CommandResult.ERROR;
        if (FactionSlots.isUnused(f)) {
            WsUtil.print(f.getId() + " is an unused faction slot; spawnfaction fills it.");
            return CommandResult.ERROR;
        }
        if (!FactionIdentity.hasDescription(f)) {
            WsUtil.print(f.getDisplayName() + " has no description entry for the game to show, so it has no lore page to edit.");
            return CommandResult.ERROR;
        }
        String who = f.getDisplayNameWithArticle() + " (" + f.getId() + ")";
        if (rest.isEmpty()) {
            String lore = FactionIdentity.lore(f);
            WsUtil.print("Lore of " + who + (lore.isEmpty() ? ": (empty)" : ":\n" + lore));
            WsUtil.printRaw("Change it with: " + USAGE);
            return CommandResult.SUCCESS;
        }
        if (rest.equalsIgnoreCase("reset")) {
            FactionIdentity.setLore(f, null);
            WsUtil.print("The lore of " + who + " is its own text again.");
            return CommandResult.SUCCESS;
        }
        boolean add = rest.toLowerCase(Locale.ROOT).startsWith("add ");
        String text = (add ? rest.substring(4) : rest).replaceAll("\\s*\\|\\s*", "\n\n").trim();
        if (add) {
            String old = FactionIdentity.lore(f);
            if (!old.isEmpty()) text = old + "\n\n" + text;
        }
        FactionIdentity.setLore(f, text);
        WsUtil.print((add ? "Added to" : "Rewrote") + " the lore of " + who + " (" + text.length()
                + " characters). See it on the faction's page in the Intel screen's faction tab.");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.factionSuggestions();
        if (parameter == 1) return WsUtil.list("add", "reset");
        return WsUtil.list();
    }
}
