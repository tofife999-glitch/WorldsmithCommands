package worldsmith.commands;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.FactionAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.SectorDiplomacy;
import worldsmith.WsUtil;

import java.util.List;
import java.util.Locale;

/** Every faction with colonies makes peace with every other one (pirates only with 'all'). */
public class TotalPeace implements BaseCommandWithSuggestion {
    private static final String USAGE = "totalpeace [all] [withplayer] [yes]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        boolean yes = false;
        boolean withPlayer = false;
        boolean withPirates = false;
        for (String w : WsUtil.splitArgs(args)) {
            String l = w.toLowerCase(Locale.ROOT);
            if (l.equals("yes")) yes = true;
            else if (l.equals("withplayer") || l.equals("player")) withPlayer = true;
            else if (l.equals("all") || l.equals("pirates")) withPirates = true;
            else {
                WsUtil.print("Unknown option '" + w + "'. Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
        }
        final boolean includePlayer = withPlayer;
        final boolean includePirates = withPirates;
        int factions = SectorDiplomacy.colonyFactions(includePlayer, includePirates).size();
        String question = "TOTAL PEACE: all " + factions + " factions with colonies make peace with each other"
                + (includePirates ? ", pirates included" : " (pirates stay hostile; add 'all' to include them)")
                + (includePlayer ? ", and with you" : "") + ". Wars end with Nexerelin's usual ceasefire; new wars can"
                + " break out again later.";
        String retype = "totalpeace" + (includePirates ? " all" : "") + (includePlayer ? " withplayer" : "") + " yes";
        if (yes) {
            WsUtil.cancelPending("totalpeace");
            WsUtil.print(run(includePlayer, includePirates));
            return CommandResult.SUCCESS;
        }
        WsUtil.print(question);
        WsUtil.confirm("totalpeace", question + " Make peace?", retype, new Script() {
            @Override
            public void run() {
                WsUtil.runSafely("totalpeace", new Runnable() {
                    @Override
                    public void run() {
                        WsUtil.report(TotalPeace.run(includePlayer, includePirates));
                    }
                });
            }
        });
        return CommandResult.SUCCESS;
    }

    private static String run(boolean withPlayer, boolean withPirates) {
        List<FactionAPI> factions = SectorDiplomacy.colonyFactions(withPlayer, withPirates);
        int ended = SectorDiplomacy.totalPeace(factions);
        return "Total peace: " + factions.size() + " factions are at peace with each other (" + ended + " wars ended)"
                + (withPirates ? ", pirates included." : "; pirates are still hostile.");
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        return WsUtil.list("all", "withplayer", "yes");
    }
}
