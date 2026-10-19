package worldsmith.commands;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.FactionEffects;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;

/** Shared flow of weakenfaction and strengthenfaction: preview, confirm, apply, summary. */
abstract class FactionLevelCommand implements BaseCommandWithSuggestion {

    /** True for weakenfaction, false for strengthenfaction. */
    abstract boolean weaken();

    private String commandName() {
        return weaken() ? "weakenfaction" : "strengthenfaction";
    }

    private static String duration(int level) {
        return level >= 3 ? "up to 3 years" : (level == 2 ? "up to 6 months" : "a few months");
    }

    private List<String> lines(FactionAPI faction, int level, boolean apply) {
        List<String> out = new ArrayList<>();
        for (MarketAPI m : FactionEffects.colonies(faction)) {
            out.add(weaken() ? FactionEffects.weakenColony(m, level, apply) : FactionEffects.strengthenColony(m, level, apply));
        }
        String wide = weaken() ? FactionEffects.weakenFaction(faction, level, apply)
                : FactionEffects.strengthenFaction(faction, level, apply);
        if (wide != null) out.add(wide);
        return out;
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        final String usage = commandName() + " <faction> [level 1-3]   (3 lasts up to 3 years)";
        List<String> parts = WsUtil.splitArgs(args);
        boolean yes = WsUtil.endsWithYes(parts);
        if (yes) parts.remove(parts.size() - 1);
        int level = 1;
        if (!parts.isEmpty() && WsUtil.isInt(parts.get(parts.size() - 1))) {
            level = Integer.parseInt(parts.remove(parts.size() - 1));
            if (level < 1 || level > 3) {
                WsUtil.print("The level must be 1, 2 or 3. Usage: " + usage);
                return CommandResult.BAD_SYNTAX;
            }
        }
        if (parts.isEmpty()) {
            WsUtil.print("Usage: " + usage);
            return CommandResult.BAD_SYNTAX;
        }
        final FactionAPI faction = WsUtil.parseFaction(WsUtil.join(parts, 0, parts.size()).replace(' ', '_'));
        if (faction == null) return CommandResult.ERROR;
        int colonies = FactionEffects.colonies(faction).size();
        if (colonies == 0) {
            WsUtil.print(WsUtil.factionLabel(faction) + " has no colonies, so there is nothing to " + (weaken() ? "weaken." : "strengthen."));
            return CommandResult.ERROR;
        }
        final int lvl = level;
        String verb = weaken() ? "Weaken" : "Strengthen";
        String header = verb + " " + WsUtil.factionLabel(faction) + ", level " + lvl + " (effects last " + duration(lvl)
                + "), " + colonies + (colonies == 1 ? " colony:" : " colonies:");
        final String key = commandName() + ":" + faction.getId();
        if (yes) {
            WsUtil.cancelPending(key);
            WsUtil.print(header);
            for (String l : lines(faction, lvl, true)) WsUtil.printRaw("  - " + l);
            return CommandResult.SUCCESS;
        }
        WsUtil.print(header);
        for (String l : lines(faction, lvl, false)) WsUtil.printRaw("  - " + l);
        String question = verb + " " + faction.getDisplayName() + " at level " + lvl + "? This affects " + colonies
                + (colonies == 1 ? " colony" : " colonies") + " for " + duration(lvl) + " (see the console for details).";
        WsUtil.confirm(key, question, commandName() + " " + faction.getId() + " " + lvl + " yes", new Script() {
            @Override
            public void run() {
                WsUtil.runSafely(commandName(), new Runnable() {
                    @Override
                    public void run() {
                        List<String> done = lines(faction, lvl, true);
                        for (String l : done) Console.showMessage("  - " + l);
                        WsUtil.report((weaken() ? "Weakened " : "Strengthened ") + faction.getDisplayName() + " (level "
                                + lvl + "): " + FactionEffects.colonies(faction).size() + " colonies affected, effects last "
                                + duration(lvl) + ". Details are in the console.");
                    }
                });
            }
        });
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.factionSuggestions();
        return WsUtil.list("1", "2", "3");
    }
}
