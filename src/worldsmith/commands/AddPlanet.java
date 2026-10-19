package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.RingBandAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.procgen.CategoryGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.PlanetGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.OrbitCheck;
import worldsmith.PlanetGen;
import worldsmith.WsUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class AddPlanet implements BaseCommandWithSuggestion {
    private static final String USAGE = "addplanet [type|random] [name]";
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        LocationAPI loc = fleet.getContainingLocation();
        if (!(loc instanceof StarSystemAPI)) {
            WsUtil.print("You must be inside a star system. Usage: " + USAGE);
            return CommandResult.WRONG_CONTEXT;
        }
        StarSystemAPI sys = (StarSystemAPI) loc;
        PlanetAPI star = sys.getStar();
        if (star == null) {
            WsUtil.print(sys.getName() + " has no star for a planet to orbit.");
            return CommandResult.ERROR;
        }
        Random random = new Random();

        List<String> parts = WsUtil.splitArgs(args);
        String type;
        if (parts.isEmpty() || parts.get(0).equalsIgnoreCase("random")) {
            if (!parts.isEmpty()) parts.remove(0);
            type = randomType(random, sys);
            if (type == null) {
                WsUtil.print("Could not pick a random planet type (no generator data found).");
                return CommandResult.ERROR;
            }
        } else {
            PlanetSpecAPI spec = WsUtil.findPlanetType(parts.get(0));
            if (spec == null) {
                WsUtil.printRaw("The first word must be a planet type or 'random'. Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
            parts.remove(0);
            type = spec.getPlanetType();
        }
        String why = PlanetGen.whyCannotRoll(sys, star, type);
        if (why != null) {
            WsUtil.print("Can't add a " + type + " planet here: " + why + ".");
            return CommandResult.ERROR;
        }
        String name = WsUtil.join(parts, 0, parts.size()).replace('_', ' ').trim();
        if (name.isEmpty()) name = nextFreeName(sys);
        else if (nameTaken(sys, name)) {
            WsUtil.print("Something in " + sys.getName() + " is already called '" + name + "'. Pick another name.");
            return CommandResult.ERROR;
        }

        PlanetGenDataSpec gen = WsUtil.genData(type);
        float minR = gen.getMinRadius();
        float maxR = gen.getMaxRadius();
        if (maxR <= 0f) {
            minR = 100f;
            maxR = 150f;
        }
        if (maxR < minR) maxR = minR;
        float radius = minR + random.nextFloat() * (maxR - minR);
        float orbitRadius = Misc.getDistance(fleet.getLocation(), star.getLocation());
        float angle = Misc.getAngleInDegrees(star.getLocation(), fleet.getLocation());

        String overlap = OrbitCheck.check(sys, star, orbitRadius, radius, new HashSet<SectorEntityToken>());
        if (overlap != null) {
            WsUtil.print("Can't place a planet here (orbit " + Math.round(orbitRadius) + ", planet radius "
                    + Math.round(radius) + "): " + overlap + " Fly to an emptier spot and try again.");
            return CommandResult.ERROR;
        }

        float orbitDays = orbitRadius / (20f + random.nextFloat() * 5f); // as StarSystemGenerator.addPlanet()
        PlanetAPI planet = sys.addPlanet("worldsmith_" + Misc.genUID(), star, name, type, angle, radius,
                orbitRadius, orbitDays);
        tint(planet, gen);
        try {
            PlanetGen.rollConditions(planet, true);
        } catch (Exception ex) {
            Global.getLogger(AddPlanet.class).error("Worldsmith: addplanet condition roll failed", ex);
            sys.removeEntity(planet);
            WsUtil.print("Adding the planet failed while rolling conditions (" + ex + "); nothing was added.");
            return CommandResult.ERROR;
        }
        WsUtil.invalidateCache();
        WsUtil.print("Added " + name + " (" + planet.getSpec().getName() + ", " + type + ") orbiting "
                + star.getName() + " in " + sys.getName() + " at distance " + Math.round(orbitRadius)
                + "; conditions: " + PlanetGen.conditionNames(planet.getMarket()) + ".");
        return CommandResult.SUCCESS;
    }

    /** Same tinting as StarSystemGenerator.addPlanet(); purely cosmetic. */
    public static void tint(PlanetAPI planet, PlanetGenDataSpec gen) {
        try {
            if (gen == null || gen.getMinColor() == null || gen.getMaxColor() == null) return;
            Color color = StarSystemGenerator.getColor(gen.getMinColor(), gen.getMaxColor());
            planet.getSpec().setPlanetColor(color);
            if (planet.getSpec().getAtmosphereThickness() > 0 && planet.getSpec().getAtmosphereColor() != null) {
                Color atmo = Misc.interpolateColor(planet.getSpec().getAtmosphereColor(), color, 0.25f);
                atmo = Misc.setAlpha(atmo, planet.getSpec().getAtmosphereColor().getAlpha());
                planet.getSpec().setAtmosphereColor(atmo);
            }
            planet.applySpecChanges();
        } catch (Exception ex) {
            Global.getLogger(AddPlanet.class).warn("Worldsmith: planet tint failed (cosmetic)", ex);
        }
    }

    static String randomType(Random random, StarSystemAPI sys) {
        return randomType(random, sys, null);
    }

    /**
     * Random planet type, weighted like the sector generator: first a category by its frequency, then a type
     * within it by frequency, both scaled by the multipliers for the system's star age and star type, plus an
     * optional extra column of planet_gen_data.csv (e.g. "is_moon" for moons).
     */
    public static String randomType(Random random, StarSystemAPI sys, String extraMult) {
        String age = PlanetGen.age(sys).name();
        String starType = sys.getStar() == null ? null : sys.getStar().getTypeId();
        Map<String, Float> catWeight = new HashMap<>();
        for (CategoryGenDataSpec c : Global.getSettings().getAllSpecs(CategoryGenDataSpec.class)) {
            float w = c.getFrequency() * c.getMultiplier(age);
            if (starType != null) w *= c.getMultiplier(starType);
            if (extraMult != null) w *= c.getMultiplier(extraMult);
            catWeight.put(c.getCategory(), w);
        }
        Map<String, Float> typeWeight = new HashMap<>();
        Map<String, String> typeCat = new HashMap<>();
        Map<String, Float> catTotal = new HashMap<>();
        for (PlanetGenDataSpec p : Global.getSettings().getAllSpecs(PlanetGenDataSpec.class)) {
            if (p.getId() == null || p.getId().isEmpty() || p.getCategory() == null) continue;
            if (!WsUtil.isSelectableType(planetSpec(p.getId()))) continue; // terrain rows, stars etc.
            float w = p.getFrequency() * p.getMultiplier(age);
            if (starType != null) w *= p.getMultiplier(starType);
            if (extraMult != null) w *= p.getMultiplier(extraMult);
            if (w <= 0f) continue;
            typeWeight.put(p.getId(), w);
            typeCat.put(p.getId(), p.getCategory());
            catTotal.merge(p.getCategory(), w, Float::sum);
        }
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
        for (Map.Entry<String, Float> t : typeWeight.entrySet()) {
            String cat = typeCat.get(t.getKey());
            Float cw = catWeight.get(cat);
            float w = cw == null ? t.getValue() : cw * t.getValue() / catTotal.get(cat);
            if (w > 0f) picker.add(t.getKey(), w);
        }
        return picker.isEmpty() ? null : picker.pick();
    }

    private static PlanetSpecAPI planetSpec(String type) {
        for (PlanetSpecAPI s : Global.getSettings().getAllPlanetSpecs()) {
            if (s.getPlanetType().equals(type)) return s;
        }
        return null;
    }

    public static boolean nameTaken(StarSystemAPI sys, String name) {
        String n = WsUtil.norm(name);
        for (SectorEntityToken e : sys.getAllEntities()) {
            if (e.getName() != null && WsUtil.norm(e.getName()).equals(n)) return true;
        }
        return false;
    }

    static String nextFreeName(StarSystemAPI sys) {
        for (int i = 1; i < 100; i++) {
            String n = sys.getBaseName() + " " + Global.getSettings().getRoman(i);
            if (!nameTaken(sys, n)) return n;
        }
        return sys.getBaseName() + " " + Misc.genUID().substring(0, 4);
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) {
            List<String> l = WsUtil.typeSuggestions();
            l.add("random");
            return l;
        }
        return new ArrayList<>();
    }
}
