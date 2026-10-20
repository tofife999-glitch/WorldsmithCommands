package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;
import worldsmith.JumpPoints;
import worldsmith.StarTerrain;
import worldsmith.WsUtil;

import java.util.List;
import java.util.Random;

/**
 * Creates a star system at your position in hyperspace: star, light, corona and jump points, and either empty
 * or filled with planets, moons, rings and stable locations by vanilla's own generator.
 */
public class NewSystem implements BaseCommandWithSuggestion {
    private static final String USAGE = "newsystem <name> [star type] [full|empty]";
    /** Minimum distance to the nearest existing star system. */
    private static final float MIN_DISTANCE_LY = 1.5f;

    private static String randomStarType(Random random) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
        for (PlanetSpecAPI s : WsUtil.starTypes()) {
            if (!WsUtil.isUsableStarType(s)) continue;
            float w = WsUtil.starData(s.getPlanetType()).getFreqAVERAGE();
            if (w > 0f) picker.add(s.getPlanetType(), w);
        }
        return picker.isEmpty() ? null : picker.pick();
    }

    private static String starTypeName(String type) {
        for (PlanetSpecAPI s : WsUtil.starTypes()) {
            if (s.getPlanetType().equals(type)) return s.getName();
        }
        return type;
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        boolean yes = WsUtil.endsWithYes(parts);
        if (yes) parts.remove(parts.size() - 1);
        Boolean fill = null;
        if (!parts.isEmpty()) {
            String last = parts.get(parts.size() - 1);
            if (last.equalsIgnoreCase("full") || last.equalsIgnoreCase("planets")) fill = Boolean.TRUE;
            else if (last.equalsIgnoreCase("empty")) fill = Boolean.FALSE;
            if (fill != null) parts.remove(parts.size() - 1);
        }
        if (parts.isEmpty()) {
            WsUtil.print("Usage: " + USAGE + "   (in hyperspace; 'startypes' lists star types)");
            return CommandResult.BAD_SYNTAX;
        }
        Random random = new Random();
        String type = null;
        String last = parts.get(parts.size() - 1);
        PlanetSpecAPI spec = parts.size() > 1 ? WsUtil.quietStarType(last) : null;
        if (spec != null) {
            type = spec.getPlanetType();
            parts.remove(parts.size() - 1);
        } else if (last.toLowerCase().startsWith("star_") || last.equalsIgnoreCase("random")) {
            if (!last.equalsIgnoreCase("random") && WsUtil.findStarType(last) == null) return CommandResult.ERROR;
            parts.remove(parts.size() - 1);
        }
        if (type == null) type = randomStarType(random);
        if (type == null) {
            WsUtil.print("Could not find any usable star type.");
            return CommandResult.ERROR;
        }
        final String name = WsUtil.join(parts, 0, parts.size()).replace('_', ' ').trim();
        if (name.isEmpty()) {
            WsUtil.print("The system needs a name. Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        if (!WsUtil.exactSystems(name).isEmpty()) {
            WsUtil.print("A star system called '" + name + "' already exists. Pick another name.");
            return CommandResult.ERROR;
        }

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (!fleet.getContainingLocation().isHyperspace()) {
            WsUtil.print("You must be in hyperspace, at the spot where the new system should be.");
            return CommandResult.WRONG_CONTEXT;
        }
        final Vector2f loc = new Vector2f(fleet.getLocation());
        if (Misc.isInAbyss(loc)) {
            WsUtil.print("You are in the Abyss; new systems can't be placed there.");
            return CommandResult.ERROR;
        }
        StarSystemAPI nearest = null;
        float nearestLy = Float.MAX_VALUE;
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            float d = Misc.getDistanceLY(loc, s.getLocation());
            if (d < nearestLy) {
                nearestLy = d;
                nearest = s;
            }
        }
        if (nearest != null && nearestLy < MIN_DISTANCE_LY) {
            WsUtil.print("Too close to " + nearest.getName() + " (" + WsUtil.ly(nearestLy) + "); keep at least "
                    + WsUtil.ly(MIN_DISTANCE_LY) + " away from other systems.");
            return CommandResult.ERROR;
        }

        final String starType = type;
        final Boolean chosenFill = fill;
        final String retype = "newsystem " + name.replace(' ', '_') + " " + starType;
        if (yes) {
            WsUtil.cancelPending("newsystem");
            if (chosenFill != null) {
                WsUtil.print(create(name, starType, loc, chosenFill));
            } else {
                WsUtil.print("Close the console to choose: generate planets, or keep the system empty.");
                askFill(name, starType, loc, retype);
            }
            return CommandResult.SUCCESS;
        }
        String question = "Create the star system " + name + " (" + starTypeName(starType) + ") here in hyperspace?";
        WsUtil.print(question);
        WsUtil.confirm("newsystem", question, retype + (chosenFill == null ? " full|empty" : (chosenFill ? " full" : " empty")) + " yes",
                new Script() {
                    @Override
                    public void run() {
                        if (chosenFill != null) {
                            createReported(name, starType, loc, chosenFill);
                        } else {
                            askFill(name, starType, loc, retype);
                        }
                    }
                });
        return CommandResult.SUCCESS;
    }

    /** Second popup: fill the new system with planets etc., or keep it empty. */
    private static void askFill(final String name, final String starType, final Vector2f loc, String retype) {
        WsUtil.popup("newsystem-fill", "Fill " + name + " with planets, moons, rings and stable locations, like a"
                        + " randomly generated system? (Keep empty: only the star and its jump points.)",
                "Generate planets", "Keep empty", retype + " full|empty yes",
                new Script() {
                    @Override
                    public void run() {
                        createReported(name, starType, loc, true);
                    }
                },
                new Script() {
                    @Override
                    public void run() {
                        createReported(name, starType, loc, false);
                    }
                });
    }

    private static void createReported(final String name, final String starType, final Vector2f loc, final boolean fill) {
        WsUtil.runSafely("newsystem", new Runnable() {
            @Override
            public void run() {
                if (!WsUtil.exactSystems(name).isEmpty()) {
                    WsUtil.report("Did not create " + name + ": a system with that name exists now.");
                    return;
                }
                WsUtil.report(create(name, starType, loc, fill));
            }
        });
    }

    /** Builds the system the way StarSystemGenerator does for a single star. */
    static String create(String name, String type, Vector2f loc, boolean fill) {
        Random random = new Random();
        StarSystemAPI sys = Global.getSector().createStarSystem(name);
        sys.getLocation().set(loc.x, loc.y);
        sys.setBackgroundTextureFilename(random.nextBoolean()
                ? "graphics/backgrounds/background2.jpg" : "graphics/backgrounds/background4.jpg");

        StarGenDataSpec data = WsUtil.starData(type);
        float radius = data.getMinRadius() + random.nextFloat() * (data.getMaxRadius() - data.getMinRadius());
        float corona = StarTerrain.rollCorona(data, radius, random);
        float flare = data.getMinFlare() + (data.getMaxFlare() - data.getMinFlare()) * random.nextFloat();
        PlanetAPI star = sys.initStar("worldsmith_" + Misc.genUID(), type, radius, corona, data.getSolarWind(),
                flare, data.getCrLossMult());
        if (StarTerrain.isBlackHole(star) || StarTerrain.isNeutron(star)) {
            // Event horizon or pulsar beam instead of the plain corona, as the sector generator does it.
            StarTerrain.remove(sys, star);
            StarTerrain.add(sys, star, data, corona, random);
        }
        // Its own name, so the star and the system can't be mixed up (vanilla companions are "<name> B", "C").
        star.setName(name + " Prime");
        sys.setLightColor(Misc.interpolateColor(data.getLightColorMin(), data.getLightColorMax(), random.nextFloat()));
        StarAge age = data.getAge();
        if (age == null || !(age == StarAge.YOUNG || age == StarAge.AVERAGE || age == StarAge.OLD)) age = StarAge.AVERAGE;
        sys.setAge(age);
        sys.setType(StarSystemGenerator.StarSystemType.SINGLE);

        String contents = "empty except for the star";
        if (fill) {
            // Vanilla's own generator for planets, moons, rings, belts and magnetic fields.
            int min = Math.max(1, Math.round(data.getMinOrbits()));
            int max = Math.max(min, Math.round(data.getMaxOrbits()));
            float start = star.getRadius() + StarSystemGenerator.STARTING_RADIUS_STAR_BASE
                    + StarSystemGenerator.STARTING_RADIUS_STAR_RANGE * random.nextFloat();
            // Structural names ("<system> I", "<system> II"...), so a later system rename carries over to them.
            StarSystemGenerator.addOrbitingEntities(sys, star, age, min, max, start, 1, false);
            StarSystemGenerator.addStableLocations(sys, 1 + random.nextInt(2));
            int planets = 0;
            for (PlanetAPI p : sys.getPlanets()) {
                if (!p.isStar()) planets++;
            }
            contents = planets + " planets and moons, plus rings and stable locations, all unsurveyed";
        }

        // An inner and a fringe jump point like generated systems have; autogenerate then gives each its
        // hyperspace end, plus the star's entrance and gravity wells at gas giants.
        JumpPoints.addStandard(sys, random);
        sys.generateAnchorIfNeeded();
        sys.autogenerateHyperspaceJumpPoints(true, false);

        // Clear the hyperspace clouds around it, as StarSystemGenerator does for new constellations.
        HyperspaceTerrainPlugin plugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
        NebulaEditor editor = new NebulaEditor(plugin);
        float minRadius = plugin.getTileSize() * 2f;
        float r = sys.getMaxRadiusInHyperspace();
        editor.clearArc(loc.x, loc.y, 0, r + minRadius * 0.5f, 0, 360f);
        editor.clearArc(loc.x, loc.y, 0, r + minRadius, 0, 360f, 0.25f);

        WsUtil.invalidateCache();
        return "Created star system " + sys.getName() + " with the star " + star.getName() + " (" + star.getSpec().getName()
                + ") at your position in hyperspace: " + contents + ", and " + WsUtil.hyperJumpPointsInto(sys).size()
                + " jump points from hyperspace.";
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.list();
        List<String> l = WsUtil.starTypeSuggestions();
        l.addAll(WsUtil.list("random", "full", "empty"));
        return l;
    }
}
