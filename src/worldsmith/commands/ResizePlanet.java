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

/**
 * Changes a planet's size. The size is its radius in the game's distance units (the same units as orbit
 * distances): a number sets it directly, "150%" scales the current size (200% = twice as big), "auto" picks a
 * normal size for its type.
 */
public class ResizePlanet implements BaseCommandWithSuggestion {
    private static final String USAGE = "resizeplanet [planet] <percent|auto>   e.g. 150% (bigger), 50% (smaller) or auto";
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
        String digits = last.endsWith("%") ? last.substring(0, last.length() - 1) : last;
        if (!auto && !WsUtil.isInt(digits)) {
            WsUtil.print("The last word must be a percentage of the current size (150% = 1.5 times as wide,"
                    + " 50% = half as wide) or 'auto'. Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        SectorEntityToken target = WsUtil.resolveTarget(WsUtil.join(parts, 0, parts.size()), context,
                WsUtil.Kind.PLANET, "planet or moon", USAGE);
        if (target == null) return CommandResult.ERROR;
        PlanetAPI planet = (PlanetAPI) target;
        float oldRadius = planet.getRadius();

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
            newRadius = oldRadius * Integer.parseInt(digits) / 100f;
            if (newRadius < MIN_SIZE || newRadius > MAX_SIZE) {
                WsUtil.print(planet.getName() + " can be made between " + (int) Math.ceil(100f * MIN_SIZE / oldRadius)
                        + "% and " + (int) Math.floor(100f * MAX_SIZE / oldRadius) + "% of its current size.");
                return CommandResult.BAD_SYNTAX;
            }
        }
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
        WsUtil.print("Resized " + planet.getName() + " in " + WsUtil.systemName(planet) + " to "
                + Math.round(100f * newRadius / oldRadius) + "% of its old size"
                + (auto ? " (a normal size for its type)." : "."));
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        List<String> l = WsUtil.list("auto", "50%", "75%", "150%", "200%");
        if (parameter == 0) l.addAll(WsUtil.bodySuggestions(WsUtil.Kind.PLANET));
        return l;
    }
}
