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
    /** Minimum empty space kept between the new planet and any other orbit. */
    private static final float MARGIN = 100f;

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

        String overlap = checkOverlap(sys, star, orbitRadius, radius);
        if (overlap != null) {
            WsUtil.print("Can't place a planet here (orbit " + Math.round(orbitRadius) + ", planet radius "
                    + Math.round(radius) + "): " + overlap + " Fly to an emptier spot and try again.");
            return CommandResult.ERROR;
        }

        float orbitDays = orbitRadius / (20f + random.nextFloat() * 5f); // as StarSystemGenerator.addPlanet()
        PlanetAPI planet = sys.addPlanet("worldsmith_" + Misc.genUID(), star, name, type, angle, radius,
                orbitRadius, orbitDays);
        try {
            // Same tinting as StarSystemGenerator.addPlanet(); purely cosmetic.
            if (gen.getMinColor() != null && gen.getMaxColor() != null) {
                Color color = StarSystemGenerator.getColor(gen.getMinColor(), gen.getMaxColor());
                planet.getSpec().setPlanetColor(color);
                if (planet.getSpec().getAtmosphereThickness() > 0 && planet.getSpec().getAtmosphereColor() != null) {
                    Color atmo = Misc.interpolateColor(planet.getSpec().getAtmosphereColor(), color, 0.25f);
                    atmo = Misc.setAlpha(atmo, planet.getSpec().getAtmosphereColor().getAlpha());
                    planet.getSpec().setAtmosphereColor(atmo);
                }
                planet.applySpecChanges();
            }
        } catch (Exception ex) {
            Global.getLogger(AddPlanet.class).warn("Worldsmith: planet tint failed (cosmetic)", ex);
        }
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

    /**
     * Random planet type, weighted like the sector generator: first a category by its frequency, then a type
     * within it by frequency, both scaled by the multipliers for the system's star age and star type.
     */
    static String randomType(Random random, StarSystemAPI sys) {
        String age = PlanetGen.age(sys).name();
        String starType = sys.getStar() == null ? null : sys.getStar().getTypeId();
        Map<String, Float> catWeight = new HashMap<>();
        for (CategoryGenDataSpec c : Global.getSettings().getAllSpecs(CategoryGenDataSpec.class)) {
            float w = c.getFrequency() * c.getMultiplier(age);
            if (starType != null) w *= c.getMultiplier(starType);
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

    private static boolean nameTaken(StarSystemAPI sys, String name) {
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

    /** For ring-shaped things (ring/belt/corona terrain, ring band visuals): {focus, inner, outer}; else null. */
    private static Object[] ring(SectorEntityToken e) {
        if (e instanceof CampaignTerrainAPI && ((CampaignTerrainAPI) e).getPlugin() instanceof BaseRingTerrain) {
            BaseRingTerrain.RingParams rp = ((BaseRingTerrain) ((CampaignTerrainAPI) e).getPlugin()).getRingParams();
            if (rp == null) return null;
            return new Object[]{rp.relatedEntity, rp.middleRadius - rp.bandWidthInEngine / 2f,
                    rp.middleRadius + rp.bandWidthInEngine / 2f};
        }
        if (e instanceof RingBandAPI) {
            RingBandAPI r = (RingBandAPI) e;
            return new Object[]{r.getFocus(), r.getMiddleRadius() - r.getBandWidthInEngine() / 2f,
                    r.getMiddleRadius() + r.getBandWidthInEngine() / 2f};
        }
        return null;
    }

    private static boolean ignored(SectorEntityToken e) {
        // Fleets move around; single asteroids belong to a belt that is checked as a whole.
        return e instanceof CampaignFleetAPI || e instanceof AsteroidAPI;
    }

    private static SectorEntityToken parentOf(SectorEntityToken e) {
        Object[] r = ring(e);
        if (r != null) return (SectorEntityToken) r[0];
        return e.getOrbitFocus();
    }

    /** Outer edge of a body including everything that orbits it (moons, stations, rings, magnetic fields). */
    private static float extent(SectorEntityToken body, List<SectorEntityToken> all, Set<SectorEntityToken> seen) {
        float ext = body.getRadius();
        if (!seen.add(body)) return ext;
        for (SectorEntityToken e : all) {
            if (parentOf(e) != body) continue;
            Object[] r = ring(e);
            float reach;
            if (r != null) reach = (Float) r[2];
            else if (e instanceof CampaignTerrainAPI) continue;
            else reach = Misc.getDistance(e.getLocation(), body.getLocation()) + extent(e, all, seen);
            ext = Math.max(ext, reach);
        }
        return ext;
    }

    private static String label(SectorEntityToken e) {
        if (e instanceof CampaignTerrainAPI) {
            CampaignTerrainAPI t = (CampaignTerrainAPI) e;
            String n = t.getPlugin() == null ? null : t.getPlugin().getTerrainName();
            if (n != null && !n.isEmpty()) return n;
            Object[] r = ring(e);
            if (r != null && r[0] != null) return "the terrain around " + ((SectorEntityToken) r[0]).getName();
            return "terrain " + e.getId();
        }
        if (e instanceof RingBandAPI) return "a ring band";
        return e.getName() != null && !e.getName().isEmpty() ? e.getName() : e.getId();
    }

    /** Null if the orbit is free, otherwise why not. */
    static String checkOverlap(StarSystemAPI sys, PlanetAPI star, float orbitRadius, float radius) {
        float lo = orbitRadius - radius - MARGIN;
        float hi = orbitRadius + radius + MARGIN;
        if (lo < star.getRadius() + 200f)
            return "too close to " + star.getName() + " (stay at least "
                    + Math.round(star.getRadius() + 200f + radius + MARGIN) + " from its center).";
        List<SectorEntityToken> all = new ArrayList<>();
        for (SectorEntityToken e : sys.getAllEntities()) {
            if (!ignored(e)) all.add(e);
        }
        SectorEntityToken center = sys.getCenter();
        for (SectorEntityToken e : all) {
            if (e == star) continue;
            SectorEntityToken f = parentOf(e);
            if (f == null || (f != star && f != center)) continue;
            float eLo;
            float eHi;
            Object[] r = ring(e);
            if (r != null) {
                if (f != star) continue; // rings around the system center are measured from a different point
                eLo = (Float) r[1];
                eHi = (Float) r[2];
            } else if (e instanceof CampaignTerrainAPI) {
                continue; // nebula patches and other non-ring terrain
            } else {
                float d = Misc.getDistance(e.getLocation(), star.getLocation());
                float ext = extent(e, all, new HashSet<>());
                eLo = d - ext;
                eHi = d + ext;
            }
            if (eHi >= lo && eLo <= hi) {
                return "it would overlap " + label(e) + " (" + Math.round(Math.max(0f, eLo)) + "-"
                        + Math.round(eHi) + " from the star; the new planet needs " + Math.round(lo) + "-"
                        + Math.round(hi) + ").";
            }
        }
        return null;
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
