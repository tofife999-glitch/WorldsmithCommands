package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidSource;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RingBandAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.OrbitCheck;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Removes asteroid belts, dust/ice rings and magnetic fields (never coronas, event horizons or pulsar beams):
 * around the star, the one nearest to your fleet; around a named planet, all of them or one kind.
 */
public class RemoveRing implements BaseCommandWithSuggestion {
    private static final String USAGE = "removering [planet] [belt|ring|magfield]";
    /** Around a star, the band must be at most this far from your fleet's distance to the star. */
    private static final float MAX_MISS = 500f;

    /** "belt", "ring" or "magfield" for removable things; null for anything else. */
    private static String kindOf(SectorEntityToken e) {
        if (e instanceof CampaignTerrainAPI) {
            String t = ((CampaignTerrainAPI) e).getType();
            if (Terrain.ASTEROID_BELT.equals(t)) return "belt";
            if (Terrain.RING.equals(t)) return "ring";
            if (Terrain.MAGNETIC_FIELD.equals(t)) return "magfield";
            return null;
        }
        if (e instanceof RingBandAPI) {
            String key = ((RingBandAPI) e).getKey();
            return key != null && key.contains("asteroid") ? "belt" : "ring";
        }
        return null;
    }

    private static String resolveKind(String raw) {
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "belt": case "asteroids": case "asteroid_belt": return "belt";
            case "ring": case "rings": case "dust": case "ice": return "ring";
            case "magfield": case "magnetic": case "magnetic_field": case "field": return "magfield";
            case "all": return "all";
            default: return null;
        }
    }

    private static float[] band(SectorEntityToken e) {
        Object[] r = OrbitCheck.ring(e);
        if (r != null) return new float[]{(Float) r[1], (Float) r[2]};
        return null;
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        String kind = null;
        if (!parts.isEmpty()) {
            kind = resolveKind(parts.get(parts.size() - 1));
            if (kind != null) parts.remove(parts.size() - 1);
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

        SectorEntityToken focus;
        boolean nearestOnly;
        if (!parts.isEmpty()) {
            focus = WsUtil.findBody(WsUtil.join(parts, 0, parts.size()), WsUtil.Kind.ANY, "planet or star");
            if (focus == null) return CommandResult.ERROR;
            nearestOnly = WsUtil.isStar(focus);
        } else {
            LocationAPI loc = fleet.getContainingLocation();
            if (!(loc instanceof StarSystemAPI) || ((StarSystemAPI) loc).getStar() == null) {
                WsUtil.print("You must be in a system with a star, or name a planet. Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
            focus = ((StarSystemAPI) loc).getStar();
            nearestOnly = true;
        }
        LocationAPI loc = focus.getContainingLocation();
        if (nearestOnly && fleet.getContainingLocation() != loc) {
            WsUtil.print("Fly into " + WsUtil.systemName(focus) + " first: around a star, the ring nearest to you is removed.");
            return CommandResult.ERROR;
        }

        List<SectorEntityToken> candidates = new ArrayList<>();
        for (SectorEntityToken e : loc.getAllEntities()) {
            String k = kindOf(e);
            if (k == null || OrbitCheck.parentOf(e) != focus) continue;
            if (kind != null && !kind.equals("all") && !kind.equals(k)) continue;
            candidates.add(e);
        }
        if (candidates.isEmpty()) {
            WsUtil.print("There is no " + (kind == null || kind.equals("all") ? "belt, ring or magnetic field" : kind)
                    + " around " + focus.getName() + ".");
            return CommandResult.ERROR;
        }

        List<SectorEntityToken> remove = new ArrayList<>();
        if (nearestOnly) {
            float target = Misc.getDistance(fleet.getLocation(), focus.getLocation());
            SectorEntityToken nearest = null;
            float miss = Float.MAX_VALUE;
            for (SectorEntityToken e : candidates) {
                float[] b = band(e);
                if (b == null) continue;
                float m = target < b[0] ? b[0] - target : (target > b[1] ? target - b[1] : 0f);
                if (m < miss) {
                    miss = m;
                    nearest = e;
                }
            }
            if (nearest == null || miss > MAX_MISS) {
                WsUtil.print("No belt, ring or magnetic field near your position around " + focus.getName()
                        + ". Fly onto the one you want to remove and try again.");
                return CommandResult.ERROR;
            }
            // Take the whole ring: everything of the same kind whose band overlaps the nearest one.
            String k = kindOf(nearest);
            float[] nb = band(nearest);
            for (SectorEntityToken e : candidates) {
                float[] b = band(e);
                if (b == null || !k.equals(kindOf(e))) continue;
                if (b[1] >= nb[0] - 50f && b[0] <= nb[1] + 50f) remove.add(e);
            }
        } else {
            remove.addAll(candidates);
        }

        int belts = 0;
        int rings = 0;
        int fields = 0;
        int asteroids = 0;
        for (SectorEntityToken e : remove) {
            String k = kindOf(e);
            if (e instanceof CampaignTerrainAPI) {
                if ("belt".equals(k)) {
                    belts++;
                    Object plugin = ((CampaignTerrainAPI) e).getPlugin();
                    for (SectorEntityToken a : new ArrayList<>(loc.getAsteroids())) {
                        AsteroidSource src = Misc.getAsteroidSource(a);
                        if (src != null && src == plugin) {
                            loc.removeEntity(a);
                            asteroids++;
                        }
                    }
                } else if ("ring".equals(k)) {
                    rings++;
                } else {
                    fields++;
                }
            }
            loc.removeEntity(e);
        }
        WsUtil.invalidateCache();
        List<String> what = new ArrayList<>();
        if (belts > 0) what.add(belts + (belts == 1 ? " asteroid belt" : " asteroid belts") + " (" + asteroids + " asteroids)");
        if (rings > 0) what.add(rings + (rings == 1 ? " ring" : " rings"));
        if (fields > 0) what.add(fields + (fields == 1 ? " magnetic field" : " magnetic fields"));
        if (what.isEmpty()) what.add("ring visuals");
        WsUtil.print("Removed " + String.join(", ", what) + " around " + focus.getName() + " in " + WsUtil.systemName(focus)
                + (focus instanceof PlanetAPI && !WsUtil.isStar(focus) ? "." : " (the one nearest to you)."));
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        List<String> l = WsUtil.list("belt", "ring", "magfield", "all");
        if (parameter == 0) l.addAll(WsUtil.bodySuggestions(WsUtil.Kind.PLANET));
        return l;
    }
}
