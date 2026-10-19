package worldsmith.commands;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.procgen.PlanetGenDataSpec;
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

/** Changes a planet's size; "auto" picks a normal size for its type. */
public class ResizePlanet implements BaseCommandWithSuggestion {
    private static final String USAGE = "resizeplanet [planet] <size|auto>";
    private static final float MIN_SIZE = 30f;
    private static final float MAX_SIZE = 1000f;

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        if (parts.isEmpty()) {
            WsUtil.print("Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        String last = parts.remove(parts.size() - 1);
        boolean auto = last.equalsIgnoreCase("auto");
        if (!auto && !WsUtil.isInt(last)) {
            WsUtil.print("The last word must be a size (like 150) or 'auto'. Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        SectorEntityToken target = WsUtil.resolveTarget(WsUtil.join(parts, 0, parts.size()), context,
                WsUtil.Kind.PLANET, "planet or moon", USAGE);
        if (target == null) return CommandResult.ERROR;
        PlanetAPI planet = (PlanetAPI) target;

        float newRadius;
        if (auto) {
            PlanetGenDataSpec gen = WsUtil.genData(planet.getSpec().getPlanetType());
            if (gen == null || gen.getMaxRadius() <= 0f) {
                WsUtil.print(planet.getSpec().getName() + " has no normal size range; give a number instead.");
                return CommandResult.ERROR;
            }
            float min = gen.getMinRadius();
            float max = Math.max(min, gen.getMaxRadius());
            newRadius = min + new Random().nextFloat() * (max - min);
        } else {
            newRadius = Integer.parseInt(last);
            if (newRadius < MIN_SIZE || newRadius > MAX_SIZE) {
                WsUtil.print("Size must be between " + Math.round(MIN_SIZE) + " and " + Math.round(MAX_SIZE)
                        + " (vanilla rocky planets are about 90-220, gas giants 250-400).");
                return CommandResult.BAD_SYNTAX;
            }
        }
        float oldRadius = planet.getRadius();
        LocationAPI loc = planet.getContainingLocation();
        List<SectorEntityToken> all = OrbitCheck.relevant(loc);

        // Nothing that orbits it may end up inside it.
        List<String> inside = new ArrayList<>();
        for (SectorEntityToken s : all) {
            if (OrbitCheck.parentOf(s) != planet) continue;
            Object[] ring = OrbitCheck.ring(s);
            float inner = ring != null ? (Float) ring[1]
                    : Misc.getDistance(s.getLocation(), planet.getLocation()) - s.getRadius();
            if (ring != null && inner <= 0f) continue; // fields that hug the planet itself
            if (inner < newRadius + 50f) inside.add(OrbitCheck.label(s));
        }
        if (!inside.isEmpty()) {
            WsUtil.print("Can't make " + planet.getName() + " that big: " + String.join(", ", inside)
                    + " would end up inside it.");
            return CommandResult.ERROR;
        }
        // Growing must still fit among its neighbours.
        float oldReach = OrbitCheck.extent(planet, all); // max(own radius, reach of what orbits it)
        float newReach = Math.max(newRadius, oldReach);
        if (newReach > oldReach && planet.getOrbitFocus() != null) {
            Set<SectorEntityToken> exclude = new HashSet<>(OrbitCheck.satellites(planet, all));
            exclude.add(planet);
            SectorEntityToken focus = planet.getOrbitFocus();
            String overlap = OrbitCheck.check(loc, focus,
                    Misc.getDistance(planet.getLocation(), focus.getLocation()), newReach, exclude);
            if (overlap != null) {
                WsUtil.print("Can't make " + planet.getName() + " that big here: " + overlap);
                return CommandResult.ERROR;
            }
        }
        planet.setRadius(newRadius);
        WsUtil.print("Resized " + planet.getName() + " in " + WsUtil.systemName(planet) + " from " + Math.round(oldRadius)
                + " to " + Math.round(newRadius) + ".");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        List<String> l = WsUtil.list("auto", "100", "150", "200", "300", "400");
        if (parameter == 0) l.addAll(WsUtil.bodySuggestions(WsUtil.Kind.PLANET));
        return l;
    }
}
