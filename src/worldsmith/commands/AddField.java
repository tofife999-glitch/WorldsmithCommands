package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin.AsteroidFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.OrbitCheck;
import worldsmith.WsUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Adds an asteroid field or a salvageable debris field at your position, orbiting the star, the way the sector
 * generator makes them. removeobject field removes one again.
 */
public class AddField implements BaseCommandWithSuggestion {
    private static final String USAGE = "addfield <asteroids|debris> [small|large]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        if (parts.isEmpty() || parts.size() > 2) {
            WsUtil.print("Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        String kind = parts.get(0).toLowerCase(Locale.ROOT);
        boolean asteroids = kind.startsWith("asteroid");
        if (!asteroids && !kind.equals("debris")) {
            WsUtil.print("The field must be 'asteroids' or 'debris'. Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        String size = parts.size() > 1 ? parts.get(1).toLowerCase(Locale.ROOT) : "";
        if (!size.isEmpty() && !size.equals("small") && !size.equals("large")) {
            WsUtil.print("The size must be 'small' or 'large'. Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        LocationAPI loc = fleet.getContainingLocation();
        if (!(loc instanceof StarSystemAPI)) {
            WsUtil.print("You must be inside a star system.");
            return CommandResult.WRONG_CONTEXT;
        }
        StarSystemAPI sys = (StarSystemAPI) loc;
        SectorEntityToken focus = sys.getStar() != null ? sys.getStar() : sys.getCenter();
        Random random = new Random();
        // Vanilla's asteroid fields are 200-400 wide, battle debris about 200; 'small' and 'large' pick the ends.
        float radius = size.equals("small") ? 200f : size.equals("large") ? 450f : 250f + 150f * random.nextFloat();
        float distance = Misc.getDistance(fleet.getLocation(), focus.getLocation());
        float angle = Misc.getAngleInDegrees(focus.getLocation(), fleet.getLocation());
        String overlap = OrbitCheck.check(sys, focus, distance, radius + 100f, new HashSet<SectorEntityToken>());
        if (overlap != null) {
            WsUtil.print("Can't place a " + Math.round(radius) + "-wide field here (distance " + Math.round(distance)
                    + "): " + overlap + " Fly to an emptier spot and try again.");
            return CommandResult.ERROR;
        }
        float orbitDays = distance / (20f + random.nextFloat() * 5f);
        SectorEntityToken field;
        if (asteroids) {
            int count = Math.max(10, Math.min(100, (int) (radius * radius * 3.14f / 80000f)));
            field = sys.addTerrain(Terrain.ASTEROID_FIELD,
                    new AsteroidFieldParams(radius, radius + 100f, count, count, 4f, 16f, null));
        } else {
            DebrisFieldParams params = new DebrisFieldParams(radius, -1f, 10000000f, 0f);
            params.source = DebrisFieldSource.GEN;
            field = Misc.addDebrisField(sys, params, random);
            // Visible right away and no discovery reward: you made it.
            field.setDiscoverable(null);
            field.setDiscoveryXP(null);
        }
        field.setCircularOrbit(focus, angle, distance, orbitDays);
        WsUtil.invalidateCache();
        WsUtil.print("Added " + (asteroids ? "an asteroid field" : "a debris field you can salvage") + " ("
                + Math.round(radius) + " wide) at your position, orbiting " + focus.getName() + " in " + sys.getName()
                + ". Remove it again with: removeobject field");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.list("asteroids", "debris");
        if (parameter == 1) return WsUtil.list("small", "large");
        return WsUtil.list();
    }
}
