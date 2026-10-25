package worldsmith.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.IllegalGoods;
import worldsmith.WsUtil;

import java.util.List;
import java.util.Locale;

/**
 * What a faction considers illegal: list it, make a commodity illegal or legal, or undo the changes. Works for every
 * faction; kept in the save.
 */
public class FactionIllegal implements BaseCommandWithSuggestion {
    private static final String USAGE = "factionillegal <faction> [<commodity> illegal|legal | reset]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        if (parts.isEmpty()) {
            WsUtil.print("Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        FactionAPI f = WsUtil.parseFaction(parts.get(0));
        if (f == null) return CommandResult.ERROR;
        String name = f.getDisplayName();

        if (parts.size() == 1) {
            List<String> now = IllegalGoods.illegalNow(f);
            String msg = now.isEmpty() ? name + " considers nothing illegal."
                    : name + " considers these illegal: " + String.join(", ", now) + ".";
            String changes = IllegalGoods.changes(f);
            if (changes != null) msg += " Changed here: " + changes + ".";
            WsUtil.print(msg + " Usage: " + USAGE);
            return CommandResult.SUCCESS;
        }
        if (parts.size() == 2 && parts.get(1).equalsIgnoreCase("reset")) {
            int n = IllegalGoods.reset(f);
            WsUtil.print(n == 0 ? "Nothing to undo: " + name + "'s laws weren't changed with factionillegal."
                    : name + " has its own laws again (" + n + " change(s) undone).");
            return CommandResult.SUCCESS;
        }
        String last = parts.get(parts.size() - 1).toLowerCase(Locale.ROOT);
        if (parts.size() < 3 || !IllegalGoods.WORDS.contains(last)) {
            WsUtil.print("Say what to change and how, e.g. factionillegal " + f.getId() + " drugs legal. Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        String query = WsUtil.join(parts, 1, parts.size() - 1);
        CommoditySpecAPI c = IllegalGoods.find(query);
        if (c == null) {
            WsUtil.print("No commodity called '" + query + "'. Type factionillegal " + f.getId()
                    + " to see what it bans, or press Tab after the faction for the commodity IDs.");
            return CommandResult.ERROR;
        }
        boolean illegal = last.equals("illegal");
        if (!IllegalGoods.set(f, c.getId(), illegal)) {
            WsUtil.print(c.getName() + " already is " + (illegal ? "illegal" : "legal") + " for " + name + ".");
            return CommandResult.SUCCESS;
        }
        WsUtil.print(c.getName() + " is now " + (illegal
                ? "illegal for " + name + ": its markets trade it only on the black market, and its patrols treat it as contraband."
                : "legal for " + name + ": its markets trade it openly, and its patrols no longer look for it."));
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.factionSuggestions();
        if (parameter == 1) {
            List<String> out = IllegalGoods.ids();
            out.add(0, "reset");
            return out;
        }
        return WsUtil.list("illegal", "legal");
    }
}
