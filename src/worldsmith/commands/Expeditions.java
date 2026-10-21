package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.ExpeditionShares;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Nexerelin's colony expeditions: every faction's share, a faction's share set in percent (or off), and how often
 * expeditions happen at all. Kept in the save.
 */
public class Expeditions implements BaseCommandWithSuggestion {
    private static final String USAGE = "expeditions [faction] [percent|off|reset]   or   expeditions frequency <percent>"
            + "   or   expeditions reset";
    private static final float MIN_FREQUENCY = 10f;
    private static final float MAX_FREQUENCY = 1000f;

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
            printList();
            return CommandResult.SUCCESS;
        }
        String first = parts.get(0).toLowerCase(Locale.ROOT);
        if (first.equals("reset") && parts.size() == 1) {
            ExpeditionShares.clearShares();
            ExpeditionShares.setFrequency(null);
            WsUtil.print("Every faction has its usual share of the colony expeditions again, and they come as often as"
                    + " Nexerelin normally sends them.");
            return CommandResult.SUCCESS;
        }
        if (first.equals("frequency")) return frequency(parts);

        FactionAPI f = WsUtil.parseFaction(parts.get(0));
        if (f == null) return CommandResult.ERROR;
        if (f.isPlayerFaction()) {
            WsUtil.print("Nexerelin doesn't send expeditions for your own faction. Use colonizeplanet or spawncolonyfleet"
                    + " (Additional Console Commands) instead.");
            return CommandResult.ERROR;
        }
        if (parts.size() == 1) {
            WsUtil.print(line(f) + ". Usage: " + USAGE);
            return CommandResult.SUCCESS;
        }
        String opt = parts.get(1).toLowerCase(Locale.ROOT);
        Float percent;
        if (opt.equals("off")) percent = 0f;
        else if (opt.equals("reset")) percent = null;
        else {
            percent = percent(opt);
            if (percent == null || percent < 0f || percent > 100f) {
                WsUtil.print("The share must be a percentage from 0% to 100% (like 30%), 'off' or 'reset'. Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
        }
        if (!ExpeditionShares.canChange(f)) {
            WsUtil.print(f.getDisplayName() + " has no Nexerelin settings of its own (it shares Nexerelin's defaults with"
                    + " other such factions), so its share can't be changed on its own. Nothing was changed.");
            return CommandResult.ERROR;
        }
        ExpeditionShares.setShare(f, percent);

        String name = f.getDisplayName();
        String msg;
        if (percent == null) msg = name + " has its usual share again: " + pct(ExpeditionShares.actualShare(f)) + ".";
        else if (percent == 0f) msg = name + " sends no colony expeditions now.";
        else if (percent == 100f && ExpeditionShares.setTotal() <= 100.5f)
            msg = "Only " + name + " sends colony expeditions now.";
        else msg = name + " now sends " + pct(ExpeditionShares.actualShare(f)) + " of the colony expeditions.";
        if (percent != null && percent > 0f) {
            if (!SectorManager.isFactionAlive(f.getId())) {
                msg = name + " is set to " + Math.round(percent) + "%, but it has no colonies, so it sends none until it"
                        + " has one.";
            } else if (ExpeditionShares.setTotal() > 100.5f) {
                msg += " The shares you set add up to " + Math.round(ExpeditionShares.setTotal()) + "%, so they were"
                        + " scaled down to fit, and factions without a set share send none.";
            } else if (Math.abs(ExpeditionShares.actualShare(f) * 100f - percent) > 1f) {
                msg += " Only factions with a set share can send expeditions, so they split them all.";
            } else if (percent < 100f) {
                msg += " The other factions share the rest in their usual proportions.";
            }
        }
        if (!NexConfig.enableColonyExpeditions)
            msg += " Colony expeditions are switched off in Nexerelin's settings, though (LunaLib: Mod Settings).";
        WsUtil.print(msg);
        return CommandResult.SUCCESS;
    }

    private static CommandResult frequency(List<String> parts) {
        if (parts.size() == 1) {
            WsUtil.print("Colony expeditions come at " + Math.round(ExpeditionShares.frequency()) + "% of Nexerelin's"
                    + " normal rate: " + interval() + ". Usage: expeditions frequency <percent>");
            return CommandResult.SUCCESS;
        }
        Float percent = percent(parts.get(1).toLowerCase(Locale.ROOT));
        if (percent == null || percent < MIN_FREQUENCY || percent > MAX_FREQUENCY) {
            WsUtil.print("The frequency must be a percentage from " + Math.round(MIN_FREQUENCY) + "% to "
                    + Math.round(MAX_FREQUENCY) + "%: 200% = twice as often, 50% = half as often.");
            return CommandResult.BAD_SYNTAX;
        }
        ExpeditionShares.setFrequency(percent);
        String msg = "Colony expeditions now come at " + Math.round(percent) + "% of Nexerelin's normal rate: "
                + interval() + ".";
        if (!NexConfig.enableColonyExpeditions)
            msg += " They are switched off in Nexerelin's settings, though (LunaLib: Mod Settings).";
        WsUtil.print(msg);
        return CommandResult.SUCCESS;
    }

    private static void printList() {
        if (!NexConfig.enableColonyExpeditions) {
            WsUtil.print("Colony expeditions are switched off in Nexerelin's settings (LunaLib: Mod Settings). The shares"
                    + " would be:");
        } else {
            WsUtil.print("Nexerelin sends a colony expedition " + interval() + " (frequency "
                    + Math.round(ExpeditionShares.frequency()) + "%), from a faction picked by these shares:");
        }
        List<FactionAPI> live = new ArrayList<>();
        for (String id : SectorManager.getLiveFactionIdsCopy()) {
            FactionAPI f = Global.getSector().getFaction(id);
            if (f != null && !f.isPlayerFaction()) live.add(f);
        }
        Collections.sort(live, (a, b) -> Float.compare(ExpeditionShares.actualShare(b), ExpeditionShares.actualShare(a)));
        for (FactionAPI f : live) WsUtil.printRaw("  " + line(f));
        if (ExpeditionShares.setTotal() > 100.5f) {
            WsUtil.printRaw("The shares you set add up to " + Math.round(ExpeditionShares.setTotal())
                    + "%, so they are scaled down to fit.");
        }
        WsUtil.printRaw("Change one: expeditions <faction> <percent|off|reset>. How often: expeditions frequency"
                + " <percent>. All back to normal: expeditions reset.");
    }

    /** "Hegemony: 30% (set to 30%)". */
    private static String line(FactionAPI f) {
        String s = f.getDisplayName() + ": " + pct(ExpeditionShares.actualShare(f));
        if (ExpeditionShares.isSet(f)) {
            float set = ExpeditionShares.sharePercent(f);
            s += set <= 0f ? " (off)" : " (set to " + Math.round(set) + "%)";
        }
        if (!SectorManager.isFactionAlive(f.getId())) s += ", no colonies";
        return s;
    }

    private static String interval() {
        return "about every " + Math.max(1, Math.round(NexConfig.colonyExpeditionInterval)) + " days";
    }

    private static String pct(float share) {
        float p = share * 100f;
        if (p > 0f && p < 0.5f) return "less than 1%";
        return Math.round(p) + "%";
    }

    /** "30%" or "30" as 30; null if it isn't a number. */
    private static Float percent(String s) {
        String t = s.endsWith("%") ? s.substring(0, s.length() - 1) : s;
        try {
            float v = Float.parseFloat(t);
            return Float.isNaN(v) || Float.isInfinite(v) ? null : v;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) {
            List<String> out = WsUtil.factionSuggestions();
            out.add(0, "reset");
            out.add(0, "frequency");
            return out;
        }
        if (parameter == 1) {
            if (!previous.isEmpty() && previous.get(0).equalsIgnoreCase("frequency"))
                return WsUtil.list("50%", "150%", "200%", "300%");
            return WsUtil.list("off", "reset", "10%", "25%", "50%", "100%");
        }
        return WsUtil.list();
    }
}
