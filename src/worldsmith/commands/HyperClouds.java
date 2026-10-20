package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTiledTerrain;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;
import worldsmith.WsUtil;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Clears hyperspace clouds (and the storms in them) around you, or grows new clouds. Edits the same cloud map
 * the sector generator draws, which the game saves; storms form in new clouds by themselves over time.
 */
public class HyperClouds implements BaseCommandWithSuggestion {
    private static final String USAGE = "hyperclouds <clear|add> [radius in light-years, 0.5-5, default 1]";
    private static final float MAX_LY = 5f;

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        if (parts.isEmpty() || parts.size() > 2
                || !(parts.get(0).equalsIgnoreCase("clear") || parts.get(0).equalsIgnoreCase("add"))) {
            WsUtil.print("Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        boolean add = parts.get(0).equalsIgnoreCase("add");
        float ly = 1f;
        if (parts.size() == 2) {
            try {
                ly = Float.parseFloat(parts.get(1).toLowerCase(Locale.ROOT).replace("ly", ""));
            } catch (NumberFormatException ex) {
                WsUtil.print("The radius must be a number of light-years, like 2 or 1.5. Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
            if (ly < 0.5f || ly > MAX_LY) {
                WsUtil.print("The radius must be between 0.5 and " + Math.round(MAX_LY) + " light-years.");
                return CommandResult.BAD_SYNTAX;
            }
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (!fleet.getContainingLocation().isHyperspace()) {
            WsUtil.print("You must be in hyperspace.");
            return CommandResult.WRONG_CONTEXT;
        }
        if (Misc.isInAbyss(fleet.getLocation())) {
            WsUtil.print("You are in the Abyss, which has no clouds to edit.");
            return CommandResult.ERROR;
        }
        HyperspaceTerrainPlugin plugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
        int changed = edit(plugin, fleet.getLocation(), ly * Misc.getUnitsPerLightYear(), add, new Random());
        WsUtil.print((add ? "Grew clouds over " : "Cleared clouds from ") + changed + " patches of hyperspace within "
                + WsUtil.ly(ly) + " of you." + (add ? " Star systems keep their clear space; storms can form in"
                + " the new clouds over time." : ""));
        return CommandResult.SUCCESS;
    }

    /** Sets every cloud cell within {@code radius} of {@code center}; returns how many cells changed. */
    private static int edit(HyperspaceTerrainPlugin plugin, Vector2f center, float radius, boolean add, Random random) {
        int[][] tiles = plugin.getTiles();
        HyperspaceTerrainPlugin.CellStateTracker[][] storms = plugin.getActiveCells();
        BaseTiledTerrain.TileParams params = plugin.getParams();
        float ts = plugin.getTileSize();
        float width = tiles.length * ts;
        float height = tiles[0].length * ts;
        float cx = plugin.getEntity().getLocation().x;
        float cy = plugin.getEntity().getLocation().y;
        int variants = Math.max(1, params.tW * params.tH);
        int changed = 0;
        int minX = (int) ((width / 2f + center.x - radius - cx) / ts);
        int maxX = (int) ((width / 2f + center.x + radius - cx) / ts);
        int minY = (int) ((height / 2f + center.y - radius - cy) / ts);
        int maxY = (int) ((height / 2f + center.y + radius - cy) / ts);
        for (int i = Math.max(0, minX); i <= Math.min(tiles.length - 1, maxX); i++) {
            for (int j = Math.max(0, minY); j <= Math.min(tiles[0].length - 1, maxY); j++) {
                Vector2f cell = new Vector2f(cx - width / 2f + (i + 0.5f) * ts, cy - height / 2f + (j + 0.5f) * ts);
                if (Misc.getDistance(cell, center) > radius) continue;
                if (add) {
                    if (tiles[i][j] >= 0 || Misc.isInAbyss(cell) || nearSystem(cell, ts)) continue;
                    tiles[i][j] = random.nextInt(variants);
                } else {
                    if (tiles[i][j] < 0) continue;
                    tiles[i][j] = -1;
                    if (storms != null) storms[i][j] = null;
                }
                changed++;
            }
        }
        plugin.forceClearSampleCache();
        return changed;
    }

    /** Vanilla keeps a clear bubble around every star system; new clouds respect it. */
    private static boolean nearSystem(Vector2f p, float ts) {
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (Misc.getDistance(p, s.getLocation()) < s.getMaxRadiusInHyperspace() + ts * 2f) return true;
        }
        return false;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.list("clear", "add");
        if (parameter == 1) return WsUtil.list("0.5", "1", "2", "3", "5");
        return WsUtil.list();
    }
}
