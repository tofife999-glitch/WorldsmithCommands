package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.procgen.StarGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.OrbitCheck;
import worldsmith.StarTerrain;
import worldsmith.WsUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Adds a companion star at your position, orbiting the system's main star: the system becomes a binary (or
 * trinary) system, with the companion's corona, event horizon or pulsar beam and blended light.
 */
public class AddStar implements BaseCommandWithSuggestion {
    private static final String USAGE = "addstar [star type|random] [name]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        LocationAPI loc = fleet.getContainingLocation();
        if (!(loc instanceof StarSystemAPI)) {
            WsUtil.print("You must be inside a star system, where the new star should orbit. Usage: " + USAGE);
            return CommandResult.WRONG_CONTEXT;
        }
        StarSystemAPI sys = (StarSystemAPI) loc;
        PlanetAPI main = sys.getStar();
        if (main == null) {
            WsUtil.print(sys.getName() + " has no main star for a companion to orbit.");
            return CommandResult.ERROR;
        }
        if (sys.getSecondary() != null && sys.getTertiary() != null) {
            WsUtil.print(sys.getName() + " already has three stars, the most a system can have.");
            return CommandResult.ERROR;
        }
        Random random = new Random();
        List<String> parts = WsUtil.splitArgs(args);
        PlanetSpecAPI spec = null;
        if (!parts.isEmpty() && !parts.get(0).equalsIgnoreCase("random")) {
            spec = WsUtil.quietStarType(parts.get(0));
            if (spec == null && parts.get(0).toLowerCase(Locale.ROOT).startsWith("star_")) {
                WsUtil.findStarType(parts.get(0)); // explains or suggests
                return CommandResult.ERROR;
            }
        }
        if (spec != null || (!parts.isEmpty() && parts.get(0).equalsIgnoreCase("random"))) parts.remove(0);
        String type = spec != null ? spec.getPlanetType() : randomType(random);
        if (type == null) {
            WsUtil.print("Could not find any usable star type.");
            return CommandResult.ERROR;
        }
        StarGenDataSpec data = WsUtil.starData(type);
        String letter = sys.getSecondary() == null ? "B" : "C";
        String name = WsUtil.join(parts, 0, parts.size()).replace('_', ' ').trim();
        if (name.isEmpty()) name = sys.getBaseName() + " " + letter;
        if (AddPlanet.nameTaken(sys, name)) {
            WsUtil.print("Something in " + sys.getName() + " is already called '" + name + "'. Give the star a name: " + USAGE);
            return CommandResult.ERROR;
        }

        float radius = data.getMinRadius() + random.nextFloat() * (data.getMaxRadius() - data.getMinRadius());
        float corona = StarTerrain.rollCorona(data, radius, random);
        float distance = Misc.getDistance(fleet.getLocation(), main.getLocation());
        float angle = Misc.getAngleInDegrees(main.getLocation(), fleet.getLocation());
        String overlap = OrbitCheck.check(sys, main, distance, StarTerrain.reach(type, radius, corona),
                new HashSet<SectorEntityToken>());
        if (overlap != null) {
            WsUtil.print("Can't place a " + Math.round(radius) + "-wide star with its corona here (distance "
                    + Math.round(distance) + "): " + overlap + " Fly further out and try again.");
            return CommandResult.ERROR;
        }
        float orbitDays = distance / (20f + random.nextFloat() * 5f);
        PlanetAPI star = sys.addPlanet("worldsmith_" + Misc.genUID(), main, name, type, angle, radius, distance, orbitDays);
        StarTerrain.add(sys, star, data, corona, random);
        star.setLightColorOverrideIfStar(Misc.interpolateColor(data.getLightColorMin(), data.getLightColorMax(), random.nextFloat()));
        if (sys.getSecondary() == null) {
            sys.setSecondary(star);
            sys.setType(StarSystemGenerator.StarSystemType.BINARY_FAR);
        } else {
            sys.setTertiary(star);
            sys.setType(StarSystemGenerator.StarSystemType.TRINARY_2FAR);
        }
        sys.setLightColor(StarTerrain.systemLight(sys, random));
        WsUtil.invalidateCache();
        WsUtil.print("Added the " + star.getSpec().getName() + " " + name + " (" + type + ", radius " + Math.round(radius)
                + ") orbiting " + main.getName() + " at distance " + Math.round(distance) + "; " + sys.getName()
                + " is now a " + (sys.getTertiary() == star ? "trinary" : "binary") + " system."
                + (StarTerrain.isNeutron(star) ? " Its pulsar beam sweeps the system." : "")
                + " Remove it again with removeplanet " + WsUtil.token(name) + ".");
        return CommandResult.SUCCESS;
    }

    private static String randomType(Random random) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
        for (PlanetSpecAPI s : WsUtil.starTypes()) {
            if (!WsUtil.isUsableStarType(s)) continue;
            float w = WsUtil.starData(s.getPlanetType()).getFreqAVERAGE();
            if (w > 0f) picker.add(s.getPlanetType(), w);
        }
        return picker.pick();
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) {
            List<String> l = WsUtil.starTypeSuggestions();
            l.add("random");
            return l;
        }
        return WsUtil.list();
    }
}
