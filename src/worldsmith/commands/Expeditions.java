package worldsmith.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.utilities.NexConfig;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.NexPlans;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * How often a faction sends Nexerelin colony expeditions: off, on, often or back to normal. Nexerelin picks the
 * faction of each expedition by weight; this changes the weight, kept in the save.
 */
public class Expeditions implements BaseCommandWithSuggestion {
    private static final String USAGE = "expeditions [faction] [off|on|often|reset]";
    private static final List<String> MODES = WsUtil.list("off", "on", "often", "reset");

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        if (parts.size() > 2) {
            WsUtil.print("Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        if (parts.isEmpty()) {
            WsUtil.print(header());
            List<FactionAPI> live = NexPlans.liveFactions();
            Collections.sort(live, (a, b) -> Float.compare(NexPlans.expansionShare(b), NexPlans.expansionShare(a)));
            for (FactionAPI f : live) WsUtil.printRaw("  " + line(f));
            WsUtil.printRaw("Usage: " + USAGE);
            return CommandResult.SUCCESS;
        }
        FactionAPI f = WsUtil.parseFaction(parts.get(0));
        if (f == null) return CommandResult.ERROR;
        if (f.isPlayerFaction()) {
            WsUtil.print("Your own faction's expansion isn't run by Nexerelin. Use colonizeplanet or spawncolonyfleet"
                    + " (Additional Console Commands) instead.");
            return CommandResult.ERROR;
        }
        if (parts.size() == 1) {
            WsUtil.print(line(f) + ". " + header() + " Usage: " + USAGE);
            return CommandResult.SUCCESS;
        }
        String mode = parts.get(1).toLowerCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            WsUtil.print("Unknown setting '" + parts.get(1) + "'. Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        if (!NexPlans.hasOwnConfig(f)) {
            WsUtil.print(f.getDisplayName() + " has no Nexerelin settings of its own (it shares Nexerelin's defaults with"
                    + " other such factions), so its expeditions can't be changed on their own. Nothing was changed.");
            return CommandResult.ERROR;
        }
        float base = Math.max(1f, NexPlans.normalExpansion(f));
        switch (mode) {
            case "off": NexPlans.setExpansion(f, 0f); break;
            case "on": NexPlans.setExpansion(f, base); break;
            case "often": NexPlans.setExpansion(f, base * 3f); break;
            default: NexPlans.setExpansion(f, null); break;
        }
        String msg = line(f) + ".";
        if (!NexConfig.enableColonyExpeditions)
            msg += " Colony expeditions are switched off in Nexerelin's settings, though, so none are sent.";
        else if (!NexPlans.liveFactions().contains(f))
            msg += " It has no colonies, so it sends none until it has one.";
        WsUtil.print(msg);
        return CommandResult.SUCCESS;
    }

    private static String header() {
        if (!NexConfig.enableColonyExpeditions)
            return "Colony expeditions are switched off in Nexerelin's settings (LunaLib: Mod Settings).";
        return "Nexerelin sends one colony expedition about every " + Math.round(NexConfig.colonyExpeditionInterval)
                + " days (less often while many expedition colonies exist), for a faction picked by these shares:";
    }

    private static String line(FactionAPI f) {
        float v = NexPlans.expansion(f);
        float normal = NexPlans.normalExpansion(f);
        String mode;
        if (!f.getMemoryWithoutUpdate().contains(NexPlans.MEM_EXPANSION)) mode = normal > 0f ? "normal" : "normal: never";
        else if (v <= 0f) mode = "off";
        else if (v > Math.max(1f, normal) * 1.5f) mode = "often";
        else mode = "on";
        int share = Math.round(NexPlans.expansionShare(f) * 100f);
        return f.getDisplayName() + ": " + share + "% of the expeditions (" + mode + ")";
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.factionSuggestions();
        if (parameter == 1) return new ArrayList<>(MODES);
        return WsUtil.list();
    }
}
