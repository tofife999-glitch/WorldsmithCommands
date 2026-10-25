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

import java.util.ArrayList;
import java.util.List;

/**
 * Rebuilds a star system saved with exportsystem at your position in hyperspace. If a system with its name exists
 * (as when importing into the save it came from), the copy gets a free name like "New Corvus", unless you give one.
 * Without a file name it lists the systems you exported.
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
            String list = SystemFile.exportList();
            WsUtil.print((list == null ? "You haven't exported any systems yet (exportsystem does that)."
                    : "Systems you exported: " + list + ".") + " Usage: " + USAGE + "   (in hyperspace)");
            return CommandResult.SUCCESS;
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

        final String file = SystemFile.fileName(parts.get(0));
        String path = SystemFile.commonPath(file);
        if (!Global.getSettings().fileExistsInCommon(path)) {
            String list = SystemFile.exportList();
            WsUtil.print("There is no system file called '" + file + "'. "
                    + (list == null ? "" : "Systems you exported: " + list + ". ")
                    + "A file from someone else goes into Starsector/saves/common/worldsmith/ as <name>.json.data.");
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

        String exported = json.optJSONObject("system").optString("name");
        String given = WsUtil.join(parts, 1, parts.size()).replace('_', ' ').trim();
        String note = "";
        String name;
        if (!given.isEmpty()) {
            if (!WsUtil.exactSystems(given).isEmpty()) {
                WsUtil.print("A star system called '" + given + "' already exists. Pick another name.");
                return CommandResult.ERROR;
            }
            name = given;
        } else if (!WsUtil.exactSystems(exported).isEmpty()) {
            // Importing into the save it came from: the copy gets a free name instead of stopping here.
            name = freeName(exported);
            note = " " + exported + " already exists here, so the copy is called " + name + " (or give a name: "
                    + USAGE.replace("<file>", file) + ").";
        } else {
            name = exported;
        }
        final String newName = name.equals(exported) ? null : name;

        if (yes) {
            WsUtil.cancelPending("importsystem");
            if (!note.isEmpty()) WsUtil.print(note.trim());
            build(json, newName, loc);
            return CommandResult.SUCCESS;
        }
        String question = "Build the star system " + name + " from " + file + " here in hyperspace?";
        WsUtil.print(question + note);
        WsUtil.confirm("importsystem", question, "importsystem " + file + " " + name.replace(' ', '_') + " yes",
                new Script() {
                    @Override
                    public void run() {
                        build(json, newName, loc);
                    }
                });
        return CommandResult.SUCCESS;
    }

    /** "New Corvus", or "New Corvus 2", 3... if that is taken too. */
    private static String freeName(String base) {
        String candidate = "New " + base;
        for (int n = 2; !WsUtil.exactSystems(candidate).isEmpty(); n++) candidate = "New " + base + " " + n;
        return candidate;
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
        List<String> out = new ArrayList<>();
        if (parameter == 0) for (String[] e : SystemFile.knownExports()) out.add(e[0]);
        return out;
    }
}
