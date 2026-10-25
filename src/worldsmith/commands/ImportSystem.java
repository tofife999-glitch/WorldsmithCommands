package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import org.json.JSONObject;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;
import worldsmith.WsUtil;

import java.util.List;

/**
 * Rebuilds a star system saved with exportsystem at your position in hyperspace, under its own name or a new one.
 */
public class ImportSystem implements BaseCommandWithSuggestion {
    private static final String USAGE = "importsystem <file> [new name]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        boolean yes = WsUtil.endsWithYes(parts);
        if (yes) parts.remove(parts.size() - 1);
        if (parts.isEmpty()) {
            WsUtil.print("Usage: " + USAGE + "   (in hyperspace; the file is a system saved with exportsystem, in"
                    + " Starsector/saves/common/worldsmith/)");
            return CommandResult.BAD_SYNTAX;
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (!fleet.getContainingLocation().isHyperspace()) {
            WsUtil.print("You must be in hyperspace, at the spot where the system should be.");
            return CommandResult.WRONG_CONTEXT;
        }
        final Vector2f loc = new Vector2f(fleet.getLocation());
        String problem = NewSystem.placementProblem(loc);
        if (problem != null) {
            WsUtil.print(problem);
            return CommandResult.ERROR;
        }

        String file = SystemFile.fileName(parts.get(0));
        String path = SystemFile.commonPath(file);
        if (!Global.getSettings().fileExistsInCommon(path)) {
            WsUtil.print("There is no file " + SystemFile.diskPath(file) + ". Systems saved with exportsystem are there;"
                    + " a file from someone else goes there too, named <name>.json.data.");
            return CommandResult.ERROR;
        }
        final JSONObject json;
        try {
            json = new JSONObject(Global.getSettings().readTextFileFromCommon(path));
        } catch (Exception ex) {
            WsUtil.print("Could not read " + SystemFile.diskPath(file) + ": it isn't valid JSON (" + ex.getMessage() + ").");
            return CommandResult.ERROR;
        }
        String why = SystemFile.problem(json);
        if (why != null) {
            WsUtil.print(why);
            return CommandResult.ERROR;
        }
        String given = WsUtil.join(parts, 1, parts.size()).replace('_', ' ').trim();
        final String name = given.isEmpty() ? json.optJSONObject("system").optString("name") : given;
        if (!WsUtil.exactSystems(name).isEmpty()) {
            WsUtil.print("A star system called '" + name + "' already exists. Give it a new name: importsystem " + file
                    + " <new name>");
            return CommandResult.ERROR;
        }
        final String newName = given.isEmpty() ? null : given;
        if (yes) {
            WsUtil.cancelPending("importsystem");
            build(json, newName, loc);
            return CommandResult.SUCCESS;
        }
        String question = "Build the star system " + name + " from " + file + " here in hyperspace?";
        WsUtil.print(question);
        WsUtil.confirm("importsystem", question, "importsystem " + file + (newName == null ? "" : " " + newName.replace(' ', '_'))
                + " yes", new Script() {
            @Override
            public void run() {
                build(json, newName, loc);
            }
        });
        return CommandResult.SUCCESS;
    }

    private static void build(final JSONObject json, final String newName, final Vector2f loc) {
        WsUtil.runSafely("importsystem", new Runnable() {
            @Override
            public void run() {
                try {
                    WsUtil.report(SystemFile.importSystem(json, newName, loc));
                } catch (Exception ex) {
                    Global.getLogger(ImportSystem.class).error("Worldsmith: import failed", ex);
                    WsUtil.report("Could not build the system, so nothing was added: " + ex.getMessage());
                }
            }
        });
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        return WsUtil.list();
    }
}
