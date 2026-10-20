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
 * How the fleets of a faction made with spawnfaction are built and fight. Presets stack (aggressive + carriers +
 * elite), a new one replaces one that sets the same thing, and 'reset' goes back to its model faction's doctrine.
 */
public class FactionDoctrine implements BaseCommandWithSuggestion {
    private static final String USAGE = "factiondoctrine <faction> [aggressive|cautious|carriers|phase|warships|elite|swarm|capitals|reset]";

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
            List<String> presets = FactionCharacter.doctrinePresets(f);
            WsUtil.print("Doctrine of " + who + ": " + FactionCharacter.doctrineSummary(f.getDoctrine()) + ". Presets: "
                    + (presets.isEmpty() ? "none" : String.join(", ", presets)) + ". Usage: " + USAGE);
            return CommandResult.SUCCESS;
        }
        String preset = parts.get(1).toLowerCase(Locale.ROOT);
        if (!FactionCharacter.DOCTRINES.contains(preset)) {
            WsUtil.print("Unknown preset '" + parts.get(1) + "'. Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        FactionCharacter.addDoctrinePreset(f, preset);
        WsUtil.print((preset.equals("reset") ? "Reset the doctrine of " : "Applied '" + preset + "' to ") + who + ": "
                + FactionCharacter.doctrineSummary(f.getDoctrine()) + ". New fleets follow it.");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.factionSuggestions();
        if (parameter == 1) return new ArrayList<>(FactionCharacter.DOCTRINES);
        return WsUtil.list();
    }
}
