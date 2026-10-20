package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.OrbitCheck;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Places a vanilla stable location, objective (relay, buoy, sensor array) or inactive gate at your position. */
public class AddObject implements BaseCommandWithSuggestion {
    private static final String USAGE = "addobject <type>";
    /** Reach used for the overlap check; these objects are small. */
    private static final float OBJECT_REACH = 50f;
    static final Map<String, String> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put("stable_location", Entities.STABLE_LOCATION);
        TYPES.put("comm_relay", Entities.COMM_RELAY);
        TYPES.put("nav_buoy", Entities.NAV_BUOY);
        TYPES.put("sensor_array", Entities.SENSOR_ARRAY);
        TYPES.put("comm_relay_makeshift", Entities.COMM_RELAY_MAKESHIFT);
        TYPES.put("nav_buoy_makeshift", Entities.NAV_BUOY_MAKESHIFT);
        TYPES.put("sensor_array_makeshift", Entities.SENSOR_ARRAY_MAKESHIFT);
        TYPES.put("inactive_gate", Entities.INACTIVE_GATE);
    }

    static String resolve(String raw) {
        String t = raw.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (TYPES.containsKey(t)) return t;
        switch (t) {
            case "stable": case "stablelocation": return "stable_location";
            case "relay": return "comm_relay";
            case "buoy": return "nav_buoy";
            case "sensor": case "array": return "sensor_array";
            case "makeshift_relay": return "comm_relay_makeshift";
            case "makeshift_buoy": return "nav_buoy_makeshift";
            case "makeshift_sensor": case "makeshift_array": return "sensor_array_makeshift";
            case "gate": return "inactive_gate";
            default: return null;
        }
    }

    private static boolean isObjective(String key) {
        return !key.equals("stable_location") && !key.equals("inactive_gate");
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String key = args.trim().isEmpty() ? null : resolve(args.trim());
        if (key == null) {
            WsUtil.print("Usage: " + USAGE + "   types: " + String.join(", ", TYPES.keySet())
                    + " (short: stable, relay, buoy, sensor, gate)");
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
        if (focus == null) {
            WsUtil.print(sys.getName() + " has no star or center to orbit.");
            return CommandResult.ERROR;
        }
        if (key.equals("inactive_gate") && !sys.getEntitiesWithTag(Tags.GATE).isEmpty()) {
            WsUtil.print(sys.getName() + " already has a gate.");
            return CommandResult.ERROR;
        }
        float distance = Misc.getDistance(fleet.getLocation(), focus.getLocation());
        float angle = Misc.getAngleInDegrees(focus.getLocation(), fleet.getLocation());
        String overlap = OrbitCheck.check(sys, focus, distance, OBJECT_REACH, new HashSet<SectorEntityToken>());
        if (overlap != null) {
            WsUtil.print("Can't place it here (distance " + Math.round(distance) + "): " + overlap
                    + " Fly to an emptier spot and try again.");
            return CommandResult.ERROR;
        }

        // Same calls vanilla uses for these objects (BaseThemeGenerator.addNonSalvageEntity, core systems).
        SectorEntityToken entity = sys.addCustomEntity(null, null, TYPES.get(key), Factions.NEUTRAL);
        float orbitDays = distance / (20f + new Random().nextFloat() * 5f);
        if (isObjective(key)) entity.setCircularOrbitPointingDown(focus, angle, distance, orbitDays);
        else entity.setCircularOrbit(focus, angle, distance, orbitDays);
        WsUtil.invalidateCache();
        WsUtil.print("Added " + entity.getName() + " (" + key + ") orbiting " + focus.getName() + " in " + sys.getName()
                + " at distance " + Math.round(distance) + (isObjective(key) ? "; it starts unclaimed." : "."));
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return new ArrayList<>(TYPES.keySet());
        return new ArrayList<>();
    }
}
