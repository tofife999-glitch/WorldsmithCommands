package worldsmith;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * Jump points the way the sector generator makes them: an in-system jump point that orbits the star, with a
 * hyperspace jump point at the matching spot outside the system. Pairs made by addjumppoint carry the same
 * id in their memory, so removeobject can remove both ends together.
 */
public final class JumpPoints {
    /** Same value on both ends of a pair made by addjumppoint. */
    public static final String MEM_PAIR = "$worldsmith_jumpPair";

    private JumpPoints() {
    }

    public static SectorEntityToken center(StarSystemAPI sys) {
        return sys.getStar() != null ? sys.getStar() : sys.getCenter();
    }

    /** How far the system's planets, rings and objects reach from its center (pulsar beams don't count). */
    public static float outerEdge(StarSystemAPI sys) {
        SectorEntityToken c = center(sys);
        float max = c.getRadius();
        for (SectorEntityToken e : OrbitCheck.relevant(sys)) {
            if (e == c || e instanceof JumpPointAPI) continue;
            Object[] r = OrbitCheck.ring(e);
            float reach;
            if (r != null) {
                SectorEntityToken f = (SectorEntityToken) r[0];
                reach = (Float) r[2] + (f == null || f == c ? 0f : Misc.getDistance(f.getLocation(), c.getLocation()));
            } else if (e instanceof CampaignTerrainAPI) {
                continue;
            } else {
                reach = Misc.getDistance(e.getLocation(), c.getLocation()) + e.getRadius();
            }
            max = Math.max(max, reach);
        }
        return max;
    }

    /** The first orbit radius from {@code from} outwards where a jump point fits between the other orbits. */
    private static float freeRadius(StarSystemAPI sys, float from, float to) {
        SectorEntityToken c = center(sys);
        for (float r = from; r <= to; r += 150f) {
            if (OrbitCheck.check(sys, c, r, 80f, new HashSet<SectorEntityToken>()) == null) return r;
        }
        return to;
    }

    public static JumpPointAPI inSystem(StarSystemAPI sys, String name, float angle, float radius, Random random) {
        JumpPointAPI jp = Global.getFactory().createJumpPoint(null, name);
        jp.setStandardWormholeToHyperspaceVisual();
        jp.setCircularOrbit(center(sys), angle, radius, radius / (15f + random.nextFloat() * 5f));
        sys.addEntity(jp);
        return jp;
    }

    /**
     * An "Inner System Jump-point" and a "Fringe Jump-point" like generated systems have. Call before
     * autogenerateHyperspaceJumpPoints(), which then gives each its hyperspace end.
     */
    public static void addStandard(StarSystemAPI sys, Random random) {
        SectorEntityToken c = center(sys);
        float edge = outerEdge(sys);
        float fringe = Math.max(edge + 1000f, c.getRadius() + 4000f + 2000f * random.nextFloat());
        float inner = freeRadius(sys, c.getRadius() + 1200f, Math.max(c.getRadius() + 1500f, fringe * 0.6f));
        float angle = random.nextFloat() * 360f;
        inSystem(sys, "Inner System Jump-point", angle, inner, random);
        inSystem(sys, "Fringe Jump-point", angle + 120f + random.nextFloat() * 120f, fringe, random);
    }

    /**
     * A new pair: the in-system end at {@code angle} and {@code radius} around the system's center, the
     * hyperspace end at {@code hyperLoc}, or at the matching spot outside the system if that is null.
     */
    public static JumpPointAPI[] addPair(StarSystemAPI sys, String name, float angle, float radius, Vector2f hyperLoc,
                                         Random random) {
        JumpPointAPI inside = inSystem(sys, name, angle, radius, random);
        if (hyperLoc == null) {
            float span = Math.max(radius, outerEdge(sys) + 1000f);
            float hd = Math.max(300f, Math.min(1f, radius / span) * Math.max(400f, sys.getMaxRadiusInHyperspace()));
            hyperLoc = Misc.getUnitVectorAtDegreeAngle(angle);
            hyperLoc.scale(hd);
            Vector2f.add(hyperLoc, sys.getLocation(), hyperLoc);
        }
        LocationAPI hyper = Global.getSector().getHyperspace();
        JumpPointAPI outside = Global.getFactory().createJumpPoint(null, sys.getBaseName() + ", " + name);
        // What the game gives hyperspace jump points that aren't tied to a planet (like fringe jump points):
        // the dark starfield look and a smaller size. The star's look is only for jump points to the star.
        outside.setStandardWormholeToStarfieldVisual();
        outside.setRadius(50f);
        hyper.addEntity(outside);
        outside.setFixedLocation(hyperLoc.x, hyperLoc.y);
        inside.addDestination(new JumpPointAPI.JumpDestination(outside, "Hyperspace"));
        outside.addDestination(new JumpPointAPI.JumpDestination(inside, sys.getBaseName() + ", " + name));
        String pair = Misc.genUID();
        inside.getMemoryWithoutUpdate().set(MEM_PAIR, pair);
        outside.getMemoryWithoutUpdate().set(MEM_PAIR, pair);
        return new JumpPointAPI[]{inside, outside};
    }

    /** Both ends of a pair made by addjumppoint (empty if this jump point isn't one of them). */
    public static List<SectorEntityToken> pairOf(SectorEntityToken jp) {
        List<SectorEntityToken> out = new ArrayList<>();
        String pair = jp.getMemoryWithoutUpdate().getString(MEM_PAIR);
        if (pair == null) return out;
        List<LocationAPI> places = new ArrayList<LocationAPI>(Global.getSector().getStarSystems());
        places.add(Global.getSector().getHyperspace());
        for (LocationAPI loc : places) {
            for (SectorEntityToken t : loc.getJumpPoints()) {
                if (pair.equals(t.getMemoryWithoutUpdate().getString(MEM_PAIR))) out.add(t);
            }
        }
        return out;
    }
}
