package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.procgen.StarGenDataSpec;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.OrbitCheck;
import worldsmith.PlanetGen;
import worldsmith.WsUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Changes a system's main star type, with its size, corona and light; optionally rerolls planet conditions. */
public class ChangeStar implements BaseCommandWithSuggestion {
    private static final String USAGE = "changestar [system] <star type> [reroll]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        boolean reroll = !parts.isEmpty() && parts.get(parts.size() - 1).equalsIgnoreCase("reroll");
        if (reroll) parts.remove(parts.size() - 1);
        if (parts.isEmpty()) {
            WsUtil.print("Usage: " + USAGE + "   ('startypes' lists star types)");
            return CommandResult.BAD_SYNTAX;
        }
        // The star type may come last or first; the rest names the system (or one of its stars).
        PlanetSpecAPI spec = WsUtil.quietStarType(parts.get(parts.size() - 1));
        if (spec != null) {
            parts.remove(parts.size() - 1);
        } else {
            spec = WsUtil.quietStarType(parts.get(0));
            if (spec != null) parts.remove(0);
        }
        if (spec == null) {
            if (parts.size() == 1) {
                WsUtil.findStarType(parts.get(0)); // explains special stars or suggests similar types
            } else {
                WsUtil.print("No star type found at the start or end of '" + args.trim() + "'.");
            }
            WsUtil.printRaw("Usage: " + USAGE + "   e.g. changestar Corvus star_red_giant  ('startypes' lists the types)");
            return CommandResult.BAD_SYNTAX;
        }

        StarSystemAPI sys;
        if (parts.isEmpty()) {
            sys = WsUtil.currentSystem(context);
            if (sys == null) {
                WsUtil.print("You are not in a star system; name one. Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
        } else {
            sys = WsUtil.findSystemOrStar(WsUtil.join(parts, 0, parts.size()));
            if (sys == null) return CommandResult.ERROR;
        }
        PlanetAPI star = sys.getStar();
        if (star == null) {
            WsUtil.print(sys.getName() + " has no star (nebula).");
            return CommandResult.ERROR;
        }
        if (WsUtil.isSpecialStar(star.getSpec())) {
            WsUtil.print(star.getName() + " is a " + star.getSpec().getName()
                    + "; black holes, neutron stars and pulsars can't be changed (their special effects would stay behind).");
            return CommandResult.ERROR;
        }
        if (star.getSpec().getPlanetType().equals(spec.getPlanetType())) {
            WsUtil.print(star.getName() + " already is a " + spec.getName() + ".");
            return CommandResult.ERROR;
        }

        // Size the new star like the generator does, but never so big that it swallows an orbit.
        StarGenDataSpec data = WsUtil.starData(spec.getPlanetType());
        Random random = new Random();
        List<SectorEntityToken> all = OrbitCheck.relevant(sys);
        float innermost = Float.MAX_VALUE;
        SectorEntityToken innermostThing = null;
        for (SectorEntityToken e : all) {
            if (e == star || OrbitCheck.parentOf(e) != star) continue;
            Object[] ring = OrbitCheck.ring(e);
            float inner;
            if (ring != null) {
                inner = (Float) ring[1];
                if (inner <= star.getRadius()) continue; // the corona and similar star terrain
            } else {
                inner = Misc.getDistance(e.getLocation(), star.getLocation()) - OrbitCheck.extent(e, all);
            }
            if (inner < innermost) {
                innermost = inner;
                innermostThing = e;
            }
        }
        float radius = data.getMinRadius() + random.nextFloat() * (data.getMaxRadius() - data.getMinRadius());
        if (radius + 100f > innermost) radius = data.getMinRadius();
        if (radius + 100f > innermost) {
            WsUtil.print("A " + spec.getName() + " is too big for " + sys.getName() + ": it would swallow "
                    + OrbitCheck.label(innermostThing) + ". Move that further out first (moveplanet).");
            return CommandResult.ERROR;
        }
        float corona = radius * (data.getCoronaMult() + data.getCoronaVar() * (random.nextFloat() - 0.5f));
        if (corona < data.getCoronaMin()) corona = data.getCoronaMin();
        float flare = data.getMinFlare() + (data.getMaxFlare() - data.getMinFlare()) * random.nextFloat();

        String oldTypeName = star.getSpec().getName();
        star.changeType(spec.getPlanetType(), random);
        star.setRadius(radius);
        StarCoronaTerrainPlugin oldCorona = Misc.getCoronaFor(star);
        if (oldCorona != null) sys.removeEntity(oldCorona.getEntity());
        sys.addCorona(star, corona, data.getSolarWind(), flare, data.getCrLossMult());
        sys.setLightColor(lightColor(sys, data, random));

        // Hyperspace jump point names like "Corvus, Yellow Star".
        for (JumpPointAPI jp : WsUtil.hyperJumpPointsInto(sys)) {
            if (WsUtil.leadsTo(jp, star) && jp.getName() != null && jp.getName().endsWith(", " + oldTypeName))
                jp.setName(jp.getName().substring(0, jp.getName().length() - oldTypeName.length()) + spec.getName());
        }

        String extra = "";
        if (innermost < radius + corona && innermostThing != null)
            extra += " " + OrbitCheck.label(innermostThing) + " is now inside the star's corona.";
        if (reroll) {
            int done = 0;
            int skipped = 0;
            for (PlanetAPI p : sys.getPlanets()) {
                if (!WsUtil.isUncolonizedPlanet(p)) continue;
                if (PlanetGen.whyCannotRoll(p, p.getSpec().getPlanetType()) != null) {
                    skipped++;
                    continue;
                }
                MarketAPI m = p.getMarket();
                boolean surveyed = m != null && m.getSurveyLevel() == MarketAPI.SurveyLevel.FULL;
                PlanetGen.Snapshot before = PlanetGen.snapshot(m);
                try {
                    PlanetGen.rollConditions(p, surveyed);
                    done++;
                } catch (Exception ex) {
                    Global.getLogger(ChangeStar.class).error("Worldsmith: reroll failed for " + p.getName(), ex);
                    PlanetGen.restore(m, before);
                    skipped++;
                }
            }
            extra += " Rerolled the conditions of " + done + " uncolonized planets" + (skipped > 0 ? " (" + skipped + " skipped)." : ".");
        } else {
            extra += " Planet conditions were not changed (add 'reroll' to reroll uncolonized planets).";
        }
        WsUtil.print("Changed " + star.getName() + " in " + sys.getName() + " from " + oldTypeName + " to "
                + spec.getName() + " (" + spec.getPlanetType() + ", radius " + Math.round(radius) + ")." + extra);
        return CommandResult.SUCCESS;
    }

    /** System light like StarSystemGenerator: the main star's light, blended with any companion stars. */
    private static Color lightColor(StarSystemAPI sys, StarGenDataSpec mainData, Random random) {
        Color result = Misc.interpolateColor(mainData.getLightColorMin(), mainData.getLightColorMax(), random.nextFloat());
        List<PlanetAPI> others = new ArrayList<>();
        if (sys.getSecondary() != null) others.add(sys.getSecondary());
        if (sys.getTertiary() != null) others.add(sys.getTertiary());
        for (PlanetAPI o : others) {
            StarGenDataSpec d = WsUtil.starData(o.getSpec().getPlanetType());
            if (d == null) continue;
            Color c = Misc.interpolateColor(d.getLightColorMin(), d.getLightColorMax(), random.nextFloat());
            result = Misc.interpolateColor(result, c, 0.5f);
        }
        return result;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        List<String> l = WsUtil.starTypeSuggestions();
        if (parameter == 0) l.addAll(WsUtil.systemSuggestions());
        else l.add("reroll");
        return l;
    }
}
