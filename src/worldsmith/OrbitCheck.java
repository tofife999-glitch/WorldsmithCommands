package worldsmith;

import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RingBandAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Orbit overlap checks shared by every command that places or moves something. */
public final class OrbitCheck {
    /** Minimum empty space kept between a new orbit and any other orbit. */
    public static final float MARGIN = 100f;

    private OrbitCheck() {
    }

    /** For ring-shaped things (ring/belt/corona terrain, ring band visuals): {focus, inner, outer}; else null. */
    public static Object[] ring(SectorEntityToken e) {
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

    /** Fleets move around; single asteroids belong to a belt that is checked as a whole. */
    public static boolean ignored(SectorEntityToken e) {
        return e instanceof CampaignFleetAPI || e instanceof AsteroidAPI;
    }

    public static SectorEntityToken parentOf(SectorEntityToken e) {
        Object[] r = ring(e);
        if (r != null) return (SectorEntityToken) r[0];
        return e.getOrbitFocus();
    }

    public static List<SectorEntityToken> relevant(LocationAPI loc) {
        List<SectorEntityToken> out = new ArrayList<>();
        for (SectorEntityToken e : loc.getAllEntities()) {
            if (!ignored(e)) out.add(e);
        }
        return out;
    }

    /** Everything that orbits the body, directly or indirectly (moons, stations, rings, jump points...). */
    public static Set<SectorEntityToken> satellites(SectorEntityToken body, List<SectorEntityToken> all) {
        Set<SectorEntityToken> out = new LinkedHashSet<>();
        List<SectorEntityToken> todo = new ArrayList<>();
        todo.add(body);
        while (!todo.isEmpty()) {
            SectorEntityToken cur = todo.remove(todo.size() - 1);
            for (SectorEntityToken e : all) {
                if (e != body && !out.contains(e) && parentOf(e) == cur) {
                    out.add(e);
                    todo.add(e);
                }
            }
        }
        return out;
    }

    /** Outer edge of a body including everything that orbits it (moons, stations, rings, magnetic fields). */
    public static float extent(SectorEntityToken body, List<SectorEntityToken> all) {
        return extent(body, all, new HashSet<SectorEntityToken>());
    }

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

    public static String label(SectorEntityToken e) {
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

    private static boolean isSystemStar(LocationAPI loc, SectorEntityToken focus) {
        return loc instanceof StarSystemAPI && ((StarSystemAPI) loc).getStar() == focus;
    }

    /**
     * Null if something reaching {@code reach} from its own center can orbit {@code focus} at distance {@code d}
     * without overlapping anything else around that focus; otherwise the reason. {@code exclude} is ignored
     * (e.g. the planet being moved and its own moons).
     */
    public static String check(LocationAPI loc, SectorEntityToken focus, float d, float reach,
                               Set<SectorEntityToken> exclude) {
        float lo = d - reach - MARGIN;
        float hi = d + reach + MARGIN;
        boolean star = focus instanceof PlanetAPI && ((PlanetAPI) focus).isStar();
        float minDist = focus.getRadius() + (star ? 200f : 100f);
        if (lo < minDist)
            return "too close to " + focus.getName() + " (stay at least " + Math.round(minDist + reach + MARGIN)
                    + " from its center).";
        SectorEntityToken center = loc instanceof StarSystemAPI ? ((StarSystemAPI) loc).getCenter() : null;
        boolean alsoCenter = isSystemStar(loc, focus) && center != null && center != focus;
        List<SectorEntityToken> all = relevant(loc);
        for (SectorEntityToken e : all) {
            if (e == focus || exclude.contains(e)) continue;
            SectorEntityToken f = parentOf(e);
            if (f == null || (f != focus && !(alsoCenter && f == center))) continue;
            float eLo;
            float eHi;
            Object[] r = ring(e);
            if (r != null) {
                if (f != focus) continue; // rings around the system center are measured from a different point
                eLo = (Float) r[1];
                eHi = (Float) r[2];
            } else if (e instanceof CampaignTerrainAPI) {
                continue; // nebula patches and other non-ring terrain
            } else {
                float dd = Misc.getDistance(e.getLocation(), focus.getLocation());
                float ext = extent(e, all);
                eLo = dd - ext;
                eHi = dd + ext;
            }
            if (eHi >= lo && eLo <= hi) {
                return "it would overlap " + label(e) + " (" + Math.round(Math.max(0f, eLo)) + "-" + Math.round(eHi)
                        + " from " + focus.getName() + "; this needs " + Math.round(lo) + "-" + Math.round(hi) + ").";
            }
        }
        return null;
    }

    /**
     * When something is added to or moved around a planet, the planet's own footprint around its star can grow.
     * Null if that still fits among its neighbours; otherwise the reason.
     */
    public static String checkParentFootprint(LocationAPI loc, SectorEntityToken parent, float newReach,
                                              Set<SectorEntityToken> exclude) {
        SectorEntityToken grand = parent.getOrbitFocus();
        if (grand == null) return null;
        List<SectorEntityToken> all = relevant(loc);
        float current = extent(parent, all);
        if (newReach <= current) return null;
        Set<SectorEntityToken> ex = new HashSet<>(exclude);
        ex.add(parent);
        ex.addAll(satellites(parent, all));
        float d = Misc.getDistance(parent.getLocation(), grand.getLocation());
        String why = check(loc, grand, d, newReach, ex);
        return why == null ? null : "around " + grand.getName() + ", " + parent.getName() + " would then reach too far: " + why;
    }
}
