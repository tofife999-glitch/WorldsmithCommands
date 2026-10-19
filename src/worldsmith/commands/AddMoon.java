package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.procgen.PlanetGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.OrbitCheck;
import worldsmith.PlanetGen;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/** Adds a moon around a planet, sized and generated like vanilla moons. */
public class AddMoon implements BaseCommandWithSuggestion {
    private static final String USAGE = "addmoon <planet> [type|random] [name]";
    /** Your distance from the planet is used if it lies within this many units beyond its surface. */
    private static final float MAX_MANUAL_DISTANCE = 1500f;

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        SectorEntityToken[] found = new SectorEntityToken[1];
        int k = parts.isEmpty() ? 0 : WsUtil.leadingBody(parts, WsUtil.Kind.PLANET, found);
        if (k < 0) return CommandResult.ERROR;
        PlanetAPI parent;
        if (k > 0) {
            parent = (PlanetAPI) found[0];
            parts = new ArrayList<>(parts.subList(k, parts.size()));
        } else {
            SectorEntityToken d = WsUtil.dialogTarget(context);
            if (!(d instanceof PlanetAPI) || WsUtil.isStar(d)) {
                if (!parts.isEmpty()) {
                    // Not a known planet: show "did you mean" for the first word.
                    WsUtil.findBody(parts.get(0), WsUtil.Kind.PLANET, "planet");
                }
                WsUtil.printRaw("Name the planet the moon should orbit (or open its dialog). Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
            parent = (PlanetAPI) d;
        }
        if (!(parent.getContainingLocation() instanceof StarSystemAPI)) {
            WsUtil.print(parent.getName() + " is not in a star system.");
            return CommandResult.ERROR;
        }
        StarSystemAPI sys = (StarSystemAPI) parent.getContainingLocation();
        Random random = new Random();

        String type;
        if (parts.isEmpty() || parts.get(0).equalsIgnoreCase("random")) {
            if (!parts.isEmpty()) parts.remove(0);
            type = AddPlanet.randomType(random, sys, "is_moon");
            if (type == null) type = AddPlanet.randomType(random, sys, null);
            if (type == null) {
                WsUtil.print("Could not pick a random moon type (no generator data found).");
                return CommandResult.ERROR;
            }
        } else {
            PlanetSpecAPI spec = WsUtil.findPlanetType(parts.get(0));
            if (spec == null) {
                WsUtil.printRaw("After the planet comes a planet type or 'random'. Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
            parts.remove(0);
            type = spec.getPlanetType();
        }
        String why = PlanetGen.whyCannotRoll(sys, parent, type);
        if (why != null) {
            WsUtil.print("Can't add a " + type + " moon to " + parent.getName() + ": " + why + ".");
            return CommandResult.ERROR;
        }
        String name = WsUtil.join(parts, 0, parts.size()).replace('_', ' ').trim();
        if (name.isEmpty()) name = nextMoonName(sys, parent);
        else if (AddPlanet.nameTaken(sys, name)) {
            WsUtil.print("Something in " + sys.getName() + " is already called '" + name + "'. Pick another name.");
            return CommandResult.ERROR;
        }

        // Size like StarSystemGenerator.addPlanet() does for moons.
        PlanetGenDataSpec gen = WsUtil.genData(type);
        float minR = gen.getMinRadius();
        float maxR = gen.getMaxRadius();
        if (maxR <= 0f) {
            minR = 100f;
            maxR = 150f;
        }
        if (maxR < minR) maxR = minR;
        float radius = minR + random.nextFloat() * (maxR - minR);
        radius *= StarSystemGenerator.MOON_RADIUS_MIN_FRACTION_OF_NORMAL + random.nextFloat()
                * (StarSystemGenerator.MOON_RADIUS_MAX_FRACTION_OF_NORMAL - StarSystemGenerator.MOON_RADIUS_MIN_FRACTION_OF_NORMAL);
        if (radius < StarSystemGenerator.MIN_MOON_RADIUS) radius = StarSystemGenerator.MIN_MOON_RADIUS;
        if (parent.getRadius() > StarSystemGenerator.MIN_MOON_RADIUS / StarSystemGenerator.MOON_RADIUS_MAX_FRACTION_OF_PARENT) {
            float max = parent.getRadius() * StarSystemGenerator.MOON_RADIUS_MAX_FRACTION_OF_PARENT;
            if (radius > max) radius = max;
        }

        // Orbit: your distance from the planet if you are near it, otherwise the first free orbit.
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        float distance;
        float angle;
        String where;
        float fleetDist = fleet.getContainingLocation() == sys
                ? Misc.getDistance(fleet.getLocation(), parent.getLocation()) : -1f;
        if (fleetDist > parent.getRadius() + radius + 200f && fleetDist < parent.getRadius() + MAX_MANUAL_DISTANCE) {
            distance = fleetDist;
            angle = Misc.getAngleInDegrees(parent.getLocation(), fleet.getLocation());
            where = "at your distance";
        } else {
            float current = OrbitCheck.extent(parent, OrbitCheck.relevant(sys));
            distance = Math.max(parent.getRadius() + StarSystemGenerator.STARTING_RADIUS_MOON_BASE
                    + random.nextFloat() * StarSystemGenerator.STARTING_RADIUS_MOON_RANGE + radius,
                    current + OrbitCheck.MARGIN + radius + 50f);
            angle = random.nextFloat() * 360f;
            where = "in the first free orbit";
        }
        String overlap = OrbitCheck.check(sys, parent, distance, radius, new HashSet<SectorEntityToken>());
        if (overlap == null)
            overlap = OrbitCheck.checkParentFootprint(sys, parent, distance + radius + OrbitCheck.MARGIN,
                    new HashSet<SectorEntityToken>());
        if (overlap != null) {
            WsUtil.print("Can't place a moon " + where + " (distance " + Math.round(distance) + " from " + parent.getName()
                    + ", moon radius " + Math.round(radius) + "): " + overlap);
            return CommandResult.ERROR;
        }

        float orbitDays = distance / (20f + random.nextFloat() * 5f); // as StarSystemGenerator.addPlanet()
        PlanetAPI moon = sys.addPlanet("worldsmith_" + Misc.genUID(), parent, name, type, angle, radius, distance, orbitDays);
        AddPlanet.tint(moon, gen);
        try {
            PlanetGen.rollConditions(moon, true);
        } catch (Exception ex) {
            Global.getLogger(AddMoon.class).error("Worldsmith: addmoon condition roll failed", ex);
            sys.removeEntity(moon);
            WsUtil.print("Adding the moon failed while rolling conditions (" + ex + "); nothing was added.");
            return CommandResult.ERROR;
        }
        WsUtil.invalidateCache();
        WsUtil.print("Added moon " + name + " (" + moon.getSpec().getName() + ", " + type + ") orbiting " + parent.getName()
                + " in " + sys.getName() + " " + where + " (" + Math.round(distance) + "); conditions: "
                + PlanetGen.conditionNames(moon.getMarket()) + ".");
        return CommandResult.SUCCESS;
    }

    /** Vanilla-style moon names: "Corvus IV-A", "Corvus IV-B", ... */
    static String nextMoonName(StarSystemAPI sys, PlanetAPI parent) {
        for (char c = 'A'; c <= 'Z'; c++) {
            String n = parent.getName() + "-" + c;
            if (!AddPlanet.nameTaken(sys, n)) return n;
        }
        return parent.getName() + "-" + Misc.genUID().substring(0, 3);
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.bodySuggestions(WsUtil.Kind.PLANET);
        List<String> l = WsUtil.typeSuggestions();
        l.add("random");
        return l;
    }
}
