package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.MagFieldGenPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.TerrainGenDataSpec;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin.MagneticFieldParams;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.OrbitCheck;
import worldsmith.WsUtil;

import java.awt.Color;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Adds an asteroid belt, a dust or ice ring, or a magnetic field around a star or planet (vanilla recipes). */
public class AddRing implements BaseCommandWithSuggestion {
    private static final String USAGE = "addring [planet] <belt|dust|ice|magfield>";
    /** Around a planet, your distance is used if it lies within this many units beyond its surface. */
    private static final float MAX_MANUAL_DISTANCE = 1500f;

    private static String resolve(String raw) {
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "belt": case "asteroids": case "asteroid_belt": return "belt";
            case "dust": case "ring": case "dust_ring": return "dust";
            case "ice": case "ice_ring": return "ice";
            case "magfield": case "magnetic": case "magnetic_field": case "field": return "magfield";
            default: return null;
        }
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        String kind = parts.isEmpty() ? null : resolve(parts.get(parts.size() - 1));
        if (kind == null) {
            WsUtil.print("Usage: " + USAGE + "   (belt = asteroid belt, dust/ice = ring, magfield = magnetic field)");
            return CommandResult.BAD_SYNTAX;
        }
        parts.remove(parts.size() - 1);

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        SectorEntityToken focus;
        if (!parts.isEmpty()) {
            focus = WsUtil.findBody(WsUtil.join(parts, 0, parts.size()), WsUtil.Kind.ANY, "planet or star");
            if (focus == null) return CommandResult.ERROR;
            if (!(focus instanceof PlanetAPI)) {
                WsUtil.print(focus.getName() + " is a station; rings only go around stars and planets.");
                return CommandResult.ERROR;
            }
        } else {
            SectorEntityToken d = WsUtil.dialogTarget(context);
            if (d instanceof PlanetAPI) {
                focus = d;
            } else if (fleet.getContainingLocation() instanceof StarSystemAPI
                    && ((StarSystemAPI) fleet.getContainingLocation()).getStar() != null) {
                focus = ((StarSystemAPI) fleet.getContainingLocation()).getStar();
            } else {
                WsUtil.print("You must be in a system with a star, or name a planet. Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
        }
        if (!(focus.getContainingLocation() instanceof StarSystemAPI)) {
            WsUtil.print(focus.getName() + " is not in a star system.");
            return CommandResult.ERROR;
        }
        StarSystemAPI sys = (StarSystemAPI) focus.getContainingLocation();
        boolean aroundStar = WsUtil.isStar(focus);
        Random random = new Random();

        float fleetDist = fleet.getContainingLocation() == sys
                ? Misc.getDistance(fleet.getLocation(), focus.getLocation()) : -1f;
        if (aroundStar && fleetDist < 0) {
            WsUtil.print("Fly into " + sys.getName() + " first: rings around a star are placed at your distance from it.");
            return CommandResult.ERROR;
        }
        float width = kind.equals("belt") ? 200f : (kind.equals("magfield") ? MagFieldGenPlugin.WIDTH_STAR : 256f);
        float distance;
        String where;
        if (kind.equals("magfield") && !aroundStar) {
            // Vanilla planet fields hug the planet.
            width = focus.getRadius() + MagFieldGenPlugin.WIDTH_PLANET;
            distance = width / 2f;
            where = "around the planet";
            for (SectorEntityToken s : OrbitCheck.satellites(focus, OrbitCheck.relevant(sys))) {
                if (OrbitCheck.parentOf(s) != focus || OrbitCheck.ring(s) != null) continue;
                float d = Misc.getDistance(s.getLocation(), focus.getLocation()) - s.getRadius();
                if (d < focus.getRadius() + MagFieldGenPlugin.WIDTH_PLANET + 50f) {
                    WsUtil.print("Can't add a magnetic field: " + OrbitCheck.label(s) + " orbits too close to "
                            + focus.getName() + ".");
                    return CommandResult.ERROR;
                }
            }
        } else if (aroundStar || (fleetDist > focus.getRadius() + 150f + width / 2f
                && fleetDist < focus.getRadius() + MAX_MANUAL_DISTANCE)) {
            distance = fleetDist;
            where = "at your distance";
        } else {
            float current = OrbitCheck.extent(focus, OrbitCheck.relevant(sys));
            distance = Math.max(focus.getRadius() + 150f + width / 2f, current + OrbitCheck.MARGIN + width / 2f + 50f);
            where = "in the first free orbit";
        }
        if (kind.equals("belt") && distance > 2000f) width += 100f * Math.min(3, (int) (distance / 2000f));

        if (!(kind.equals("magfield") && !aroundStar)) {
            String overlap = OrbitCheck.check(sys, focus, distance, width / 2f, new HashSet<SectorEntityToken>());
            if (overlap == null && !aroundStar)
                overlap = OrbitCheck.checkParentFootprint(sys, focus, distance + width / 2f + OrbitCheck.MARGIN,
                        new HashSet<SectorEntityToken>());
            if (overlap != null) {
                WsUtil.print("Can't add it " + where + " (distance " + Math.round(distance) + "): " + overlap);
                return CommandResult.ERROR;
            }
        }

        String what;
        switch (kind) {
            case "belt": {
                // As AsteroidBeltGenPlugin: belt terrain plus two asteroid ring visuals.
                float orbitDays = distance / (15f + 5f * random.nextFloat());
                int count = (int) (orbitDays * (0.25f + 0.5f * random.nextFloat()));
                if (count > 100) count = (int) (100f + (count - 100f) * 0.25f);
                if (count > 250) count = 250;
                sys.addAsteroidBelt(focus, count, distance, width, orbitDays * 0.75f, orbitDays * 1.5f, Terrain.ASTEROID_BELT, null);
                sys.addRingBand(focus, "misc", "rings_asteroids0", 256f, random.nextInt(2), Color.white, 256f,
                        distance - width * 0.25f, orbitDays * 1.05f, null, null);
                sys.addRingBand(focus, "misc", "rings_asteroids0", 256f, random.nextInt(4), Color.white, 256f,
                        distance + width * 0.25f, orbitDays, null, null);
                what = "an asteroid belt";
                break;
            }
            case "dust":
            case "ice": {
                // As RingGenPlugin: one ring band with ring terrain.
                String dataId = kind.equals("ice") ? "ring_ice" : "ring_dust";
                TerrainGenDataSpec data = (TerrainGenDataSpec) Global.getSettings().getSpec(TerrainGenDataSpec.class, dataId, true);
                Color color = Color.white;
                if (data != null && data.getMinColor() != null && data.getMaxColor() != null)
                    color = StarSystemGenerator.getColor(data.getMinColor(), data.getMaxColor());
                float orbitDays = distance / (15f + 5f * random.nextFloat());
                sys.addRingBand(focus, "misc", kind.equals("ice") ? "rings_ice0" : "rings_dust0", 256f, random.nextInt(4),
                        color, 256f, distance, orbitDays, Terrain.RING, null);
                what = kind.equals("ice") ? "an ice ring" : "a dust ring";
                break;
            }
            default: {
                // As MagFieldGenPlugin.
                Color base = MagFieldGenPlugin.baseColors[random.nextInt(MagFieldGenPlugin.baseColors.length)];
                Color[] aurora = MagFieldGenPlugin.auroraColors[random.nextInt(MagFieldGenPlugin.auroraColors.length)];
                float visStart = aroundStar ? distance - width / 2f : focus.getRadius();
                float visEnd = aroundStar ? distance + width / 2f : focus.getRadius() + MagFieldGenPlugin.WIDTH_PLANET + 50f;
                float auroraProb = aroundStar ? 1f
                        : (((PlanetAPI) focus).getSpec().getAtmosphereThickness() > 0 ? 0.25f + 0.75f * random.nextFloat() : 0f);
                SectorEntityToken field = sys.addTerrain(Terrain.MAGNETIC_FIELD, new MagneticFieldParams(width, distance,
                        focus, visStart, visEnd, base, auroraProb, aurora));
                field.setCircularOrbit(focus, 0, 0, 100);
                what = "a magnetic field";
            }
        }
        WsUtil.invalidateCache();
        WsUtil.print("Added " + what + " around " + focus.getName() + " in " + sys.getName() + " " + where
                + " (" + Math.round(distance) + ").");
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        List<String> l = WsUtil.list("belt", "dust", "ice", "magfield");
        if (parameter == 0) l.addAll(WsUtil.bodySuggestions(WsUtil.Kind.PLANET));
        return l;
    }
}
