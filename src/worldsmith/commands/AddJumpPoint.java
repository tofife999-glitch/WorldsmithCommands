package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;
import worldsmith.JumpPoints;
import worldsmith.WsUtil;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Adds a two-way jump point where your fleet is. Inside a system: a jump point at your position, with its end in
 * hyperspace outside the system. In hyperspace: a jump point at your position into the nearest system, arriving
 * at that system's edge.
 */
public class AddJumpPoint implements BaseCommandWithSuggestion {
    private static final String USAGE = "addjumppoint [name]";
    /** In hyperspace, the nearest system must be at most this far away. */
    private static final float MAX_LY = 3f;

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String name = args.trim().replace('_', ' ').replaceAll("\\s+", " ");
        if (name.isEmpty()) name = "New Jump-point";
        else if (!name.toLowerCase(Locale.ROOT).contains("jump")) name = name + " Jump-point";
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        LocationAPI loc = fleet.getContainingLocation();
        Random random = new Random();

        if (loc instanceof StarSystemAPI) {
            StarSystemAPI sys = (StarSystemAPI) loc;
            SectorEntityToken c = JumpPoints.center(sys);
            float d = Misc.getDistance(fleet.getLocation(), c.getLocation());
            if (d < c.getRadius() + 300f) {
                WsUtil.print("Too close to " + c.getName() + "; fly further out first.");
                return CommandResult.ERROR;
            }
            String near = nearbyJumpPoint(loc, fleet.getLocation());
            if (near != null) {
                WsUtil.print("The jump point " + near + " is right here already; fly a bit away first.");
                return CommandResult.ERROR;
            }
            float angle = Misc.getAngleInDegrees(c.getLocation(), fleet.getLocation());
            JumpPointAPI[] pair = JumpPoints.addPair(sys, name, angle, d, null, random);
            WsUtil.invalidateCache();
            WsUtil.print("Added the jump point " + pair[0].getName() + " at your position in " + sys.getName()
                    + ", with its other end in hyperspace just outside the system (" + pair[1].getName() + ")."
                    + " Remove both ends with removeobject jumppoint.");
            return CommandResult.SUCCESS;
        }
        if (!loc.isHyperspace()) {
            WsUtil.print("You must be in a star system or in hyperspace.");
            return CommandResult.WRONG_CONTEXT;
        }
        if (Misc.isInAbyss(fleet.getLocation())) {
            WsUtil.print("Jump points can't be placed in the Abyss.");
            return CommandResult.ERROR;
        }
        StarSystemAPI nearest = null;
        float best = Float.MAX_VALUE;
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            float ly = Misc.getDistanceLY(fleet.getLocation(), s.getLocation());
            if (ly < best) {
                best = ly;
                nearest = s;
            }
        }
        if (nearest == null || best > MAX_LY) {
            WsUtil.print("No star system within " + WsUtil.ly(MAX_LY) + " of you for the jump point to lead into.");
            return CommandResult.ERROR;
        }
        String near = nearbyJumpPoint(loc, fleet.getLocation());
        if (near != null) {
            WsUtil.print("The jump point " + near + " is right here already; fly a bit away first.");
            return CommandResult.ERROR;
        }
        float angle = Misc.getAngleInDegrees(nearest.getLocation(), fleet.getLocation());
        float radius = JumpPoints.outerEdge(nearest) + 1000f;
        JumpPointAPI[] pair = JumpPoints.addPair(nearest, name, angle, radius, new Vector2f(fleet.getLocation()), random);
        WsUtil.invalidateCache();
        WsUtil.print("Added the jump point " + pair[1].getName() + " here in hyperspace, leading to the edge of "
                + nearest.getName() + " (" + WsUtil.ly(best) + " away). Remove both ends with removeobject jumppoint.");
        return CommandResult.SUCCESS;
    }

    private static String nearbyJumpPoint(LocationAPI loc, Vector2f at) {
        List<SectorEntityToken> jps = loc.getJumpPoints();
        for (SectorEntityToken jp : jps) {
            if (Misc.getDistance(jp.getLocation(), at) < 300f) return jp.getName();
        }
        return null;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        return WsUtil.list();
    }
}
