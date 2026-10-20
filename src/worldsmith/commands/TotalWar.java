package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.AllianceManager;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.SectorDiplomacy;
import worldsmith.WsUtil;

import java.util.List;
import java.util.Locale;

/** Every faction with colonies goes to war with every other one, and all alliances break up. */
public class TotalWar implements BaseCommandWithSuggestion {
    private static final String USAGE = "totalwar [withplayer] [yes]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        boolean yes = false;
        boolean withPlayer = false;
        for (String w : WsUtil.splitArgs(args)) {
            String l = w.toLowerCase(Locale.ROOT);
            if (l.equals("yes")) yes = true;
            else if (l.equals("withplayer") || l.equals("player")) withPlayer = true;
            else {
                WsUtil.print("Unknown option '" + w + "'. Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
        }
        final boolean includePlayer = withPlayer;
        int factions = SectorDiplomacy.colonyFactions(includePlayer, true).size();
        int alliances = AllianceManager.getAllianceList().size();
        String question = "TOTAL WAR: all " + factions + " factions with colonies go to war with each other"
                + (includePlayer ? ", including you" : " (your own faction stays out; add 'withplayer' to join)")
                + ", and all " + alliances + " alliances break up. Nexerelin's diplomacy can make peace again over time.";
        if (yes) {
            WsUtil.cancelPending("totalwar");
            WsUtil.print(run(includePlayer));
            return CommandResult.SUCCESS;
        }
        WsUtil.print(question);
        WsUtil.confirm("totalwar", question + " Start the war?", "totalwar" + (includePlayer ? " withplayer" : "") + " yes",
                new Script() {
                    @Override
                    public void run() {
                        WsUtil.runSafely("totalwar", new Runnable() {
                            @Override
                            public void run() {
                                WsUtil.report(TotalWar.run(includePlayer));
                            }
                        });
                    }
                });
        return CommandResult.SUCCESS;
    }

    private static String run(boolean withPlayer) {
        int alliances = SectorDiplomacy.dissolveAlliances();
        List<FactionAPI> factions = SectorDiplomacy.colonyFactions(withPlayer, true);
        int wars = SectorDiplomacy.totalWar(factions);
        String boss = SectorDiplomacy.commissioner();
        String you = withPlayer ? " You are at war with everyone."
                : boss != null ? " Your commission ties you to " + Global.getSector().getFaction(boss).getDisplayNameWithArticle()
                + ", so you share its wars." : "";
        return "Total war: " + factions.size() + " factions are now at war with each other (" + wars
                + " new wars), " + alliances + " alliances dissolved." + you;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        return WsUtil.list("withplayer", "yes");
    }
}
