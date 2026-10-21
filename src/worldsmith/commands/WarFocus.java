package worldsmith.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.NexConfig;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.NexPlans;
import worldsmith.WsUtil;

import java.util.List;
import java.util.Locale;

/**
 * Makes a faction aim its Nexerelin invasions (or raids) at one enemy for a while, instead of picking targets among
 * all its enemies. It doesn't add invasions: it spends the faction's own invasion points.
 */
public class WarFocus implements BaseCommandWithSuggestion {
    private static final String USAGE = "warfocus [faction] [enemy|off] [days]";
    private static final int DEFAULT_DAYS = 180;
    private static final int MIN_DAYS = 30;
    private static final int MAX_DAYS = 1825;

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        if (parts.size() > 3) {
            WsUtil.print("Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        if (parts.isEmpty()) {
            List<FactionAPI> list = NexPlans.focusedFactions();
            if (list.isEmpty()) WsUtil.print("No faction has a war focus. Usage: " + USAGE);
            else {
                WsUtil.print("War focuses:");
                for (FactionAPI f : list) WsUtil.printRaw("  " + status(f));
            }
            return CommandResult.SUCCESS;
        }
        FactionAPI f = WsUtil.parseFaction(parts.get(0));
        if (f == null) return CommandResult.ERROR;
        if (parts.size() == 1) {
            WsUtil.print(status(f) + " Usage: " + USAGE);
            return CommandResult.SUCCESS;
        }
        if (parts.get(1).equalsIgnoreCase("off")) {
            FactionAPI old = NexPlans.focus(f);
            NexPlans.clearFocus(f);
            WsUtil.print(old == null ? f.getDisplayName() + " had no war focus."
                    : f.getDisplayName() + " picks its invasion targets by itself again.");
            return CommandResult.SUCCESS;
        }
        if (f.isPlayerFaction()) {
            WsUtil.print("Your own faction doesn't launch Nexerelin invasions by itself.");
            return CommandResult.ERROR;
        }
        FactionAPI enemy = WsUtil.parseFaction(parts.get(1));
        if (enemy == null) return CommandResult.ERROR;
        int days = DEFAULT_DAYS;
        if (parts.size() == 3) {
            if (!WsUtil.isInt(parts.get(2))) {
                WsUtil.print("The last word must be a number of days. Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
            days = Integer.parseInt(parts.get(2));
            if (days < MIN_DAYS || days > MAX_DAYS) {
                WsUtil.print("The days must be between " + MIN_DAYS + " and " + MAX_DAYS + ".");
                return CommandResult.BAD_SYNTAX;
            }
        }
        if (enemy == f) {
            WsUtil.print("A faction can't invade itself.");
            return CommandResult.ERROR;
        }
        if (!SectorManager.isFactionAlive(f.getId()) || !SectorManager.isFactionAlive(enemy.getId())) {
            WsUtil.print((SectorManager.isFactionAlive(f.getId()) ? enemy : f).getDisplayName()
                    + " has no colonies, so there is nothing to invade with or to invade.");
            return CommandResult.ERROR;
        }
        if (!f.isHostileTo(enemy)) {
            WsUtil.print(f.getDisplayName() + " and " + enemy.getDisplayName() + " aren't at war. Start one first, e.g."
                    + " creatediplomacyevent " + f.getId() + " " + enemy.getId() + " declare_war (or totalwar).");
            return CommandResult.ERROR;
        }
        NexPlans.setFocus(f, enemy, days);
        String msg = f.getDisplayName() + " now aims all its invasions and raids at " + enemy.getDisplayNameWithArticle()
                + " for " + days + " days, or until they make peace. " + points(f);
        String blocked = NexPlans.focusBlocked(f);
        if (blocked != null) msg += " Right now nothing launches: " + blocked + ".";
        WsUtil.print(msg);
        return CommandResult.SUCCESS;
    }

    private static String points(FactionAPI f) {
        InvasionFleetManager m = InvasionFleetManager.getManager();
        if (m == null) return "";
        float perDay = f.getMemoryWithoutUpdate().getFloat(InvasionFleetManager.MEMORY_KEY_POINTS_LAST_TICK);
        String s = "Invasion points: " + Math.round(m.getSpawnCounter(f.getId()));
        if (NexPlans.focus(f) != null) {
            s += " of " + Math.round(NexConfig.pointsRequiredForInvasionFleet * NexPlans.FOCUS_THRESHOLD)
                    + " needed for the next one";
        }
        if (perDay > 0f) s += String.format(Locale.ROOT, " (+%d a day)", Math.round(perDay));
        return s + ".";
    }

    private static String status(FactionAPI f) {
        FactionAPI enemy = NexPlans.focus(f);
        if (enemy == null) return f.getDisplayName() + " has no war focus. " + points(f);
        String s = f.getDisplayName() + " -> " + enemy.getDisplayName() + ": " + Math.max(0, Math.round(NexPlans.focusDaysLeft(f)))
                + " days left, " + f.getMemoryWithoutUpdate().getInt(NexPlans.MEM_FOCUS_LAUNCHED) + " launched so far. "
                + points(f);
        String blocked = NexPlans.focusBlocked(f);
        if (blocked != null) s += " Waiting: " + blocked + ".";
        else if (NexPlans.recentlyStuck(f))
            s += " Its last try found no target it could reach; it tries again in a few days.";
        return s;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.factionSuggestions();
        if (parameter == 1) {
            List<String> out = WsUtil.factionSuggestions();
            out.add(0, "off");
            return out;
        }
        if (parameter == 2) return WsUtil.list("90", "180", "365");
        return WsUtil.list();
    }
}
