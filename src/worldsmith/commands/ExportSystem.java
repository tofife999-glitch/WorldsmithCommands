package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.json.JSONObject;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.WsUtil;

import java.util.List;

/**
 * Writes a star system to a JSON file in Starsector's saves/common/worldsmith folder: for modders who want to build
 * it into their own mod, and for importsystem, which rebuilds it in another save or game.
 */
public class ExportSystem implements BaseCommandWithSuggestion {
    private static final String USAGE = "exportsystem [system]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String query = args.trim();
        StarSystemAPI sys = query.isEmpty() ? WsUtil.currentSystem(context) : WsUtil.findSystemOrStar(query);
        if (sys == null) {
            if (query.isEmpty()) WsUtil.print("Fly into a star system first, or name one. Usage: " + USAGE);
            return CommandResult.ERROR;
        }
        String file = SystemFile.fileName(sys.getBaseName());
        String path = SystemFile.commonPath(file);
        boolean existed = Global.getSettings().fileExistsInCommon(path);
        int[] counts = new int[4];
        try {
            JSONObject json = SystemFile.export(sys, counts);
            Global.getSettings().writeTextFileToCommon(path, json.toString(2));
            SystemFile.remember(file, sys.getName());
        } catch (Exception ex) {
            Global.getLogger(ExportSystem.class).error("Worldsmith: export of " + sys.getId() + " failed", ex);
            WsUtil.print("Could not write the file: " + ex.getMessage());
            return CommandResult.ERROR;
        }
        WsUtil.print("Exported " + sys.getName() + " (" + counts[0] + " stars, planets and moons, " + counts[1]
                + " rings, " + counts[2] + " belts and fields, " + counts[3] + " objects) to " + SystemFile.diskPath(file)
                + (existed ? ", replacing the earlier export with that name" : "") + ". It is a JSON file: remove the"
                + " '.data' the game adds to open it in an editor. 'importsystem " + file + "' rebuilds it in hyperspace.");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.systemSuggestions();
        return WsUtil.list();
    }
}
