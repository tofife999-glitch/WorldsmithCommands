package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.OrbitCheck;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Moves a planet (with everything orbiting it) to your distance and angle from what it orbits. */
public class MovePlanet implements BaseCommandWithSuggestion {
    private static final String USAGE = "moveplanet <planet>";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        if (args.trim().isEmpty()) {
            WsUtil.print("Usage: " + USAGE + "   (fly to where it should orbit first)");
            return CommandResult.BAD_SYNTAX;
        }
        SectorEntityToken target = WsUtil.findBody(args.trim(), WsUtil.Kind.PLANET, "planet or moon");
        if (target == null) return CommandResult.ERROR;
        PlanetAPI planet = (PlanetAPI) target;
        SectorEntityToken focus = planet.getOrbitFocus();
        if (focus == null) {
            WsUtil.print(planet.getName() + " has a fixed position (no orbit), so it can't be moved this way.");
            return CommandResult.ERROR;
        }
        LocationAPI loc = planet.getContainingLocation();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet.getContainingLocation() != loc) {
            WsUtil.print("Fly into " + WsUtil.systemName(planet) + " first, to where " + planet.getName() + " should orbit.");
            return CommandResult.ERROR;
        }
        float distance = Misc.getDistance(fleet.getLocation(), focus.getLocation());
        float angle = Misc.getAngleInDegrees(focus.getLocation(), fleet.getLocation());
        List<SectorEntityToken> all = OrbitCheck.relevant(loc);
        Set<SectorEntityToken> exclude = new HashSet<>(OrbitCheck.satellites(planet, all));
        exclude.add(planet);
        float reach = OrbitCheck.extent(planet, all);
        String overlap = OrbitCheck.check(loc, focus, distance, reach, exclude);
        if (overlap == null && !WsUtil.isStar(focus) && focus instanceof PlanetAPI)
            overlap = OrbitCheck.checkParentFootprint(loc, focus, distance + reach + OrbitCheck.MARGIN, exclude);
        if (overlap != null) {
            WsUtil.print("Can't move " + planet.getName() + " to distance " + Math.round(distance) + " from "
                    + focus.getName() + ": " + overlap);
            return CommandResult.ERROR;
        }
        float oldDistance = Misc.getDistance(planet.getLocation(), focus.getLocation());
        float orbitDays = distance / (20f + new Random().nextFloat() * 5f); // as StarSystemGenerator.addPlanet()
        planet.setCircularOrbit(focus, angle, distance, orbitDays);
        WsUtil.invalidateCache();
        int carried = exclude.size() - 1;
        WsUtil.print("Moved " + planet.getName() + " in " + WsUtil.systemName(planet) + " from distance "
                + Math.round(oldDistance) + " to " + Math.round(distance) + " around " + focus.getName()
                + (carried > 0 ? " (with the " + carried + " things orbiting it)." : "."));
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.bodySuggestions(WsUtil.Kind.PLANET);
        return new ArrayList<>();
    }
}
