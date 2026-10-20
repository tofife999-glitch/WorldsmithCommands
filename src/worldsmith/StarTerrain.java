package worldsmith;

import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.StarGenDataSpec;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.impl.campaign.terrain.PulsarBeamTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * What a star brings along, set up the way StarSystemGenerator does it: a corona for normal stars, an event
 * horizon for black holes, and a small corona plus a rotating pulsar beam for neutron stars.
 */
public final class StarTerrain {
    private StarTerrain() {
    }

    public static boolean isBlackHole(PlanetAPI star) {
        return star.getSpec().isBlackHole() || star.getSpec().getPlanetType().toLowerCase(Locale.ROOT).contains("black_hole");
    }

    public static boolean isNeutron(PlanetAPI star) {
        return star.getSpec().isPulsar() || star.getSpec().getPlanetType().toLowerCase(Locale.ROOT).contains("neutron");
    }

    /** The corona thickness the generator would roll for this star type and radius. */
    public static float rollCorona(StarGenDataSpec data, float radius, Random random) {
        float corona = radius * (data.getCoronaMult() + data.getCoronaVar() * (random.nextFloat() - 0.5f));
        return Math.max(corona, data.getCoronaMin());
    }

    /** Removes the star's corona, event horizon and pulsar beam. Returns how many terrain pieces went. */
    public static int remove(StarSystemAPI sys, PlanetAPI star) {
        int n = 0;
        for (CampaignTerrainAPI t : new ArrayList<>(sys.getTerrainCopy())) {
            CampaignTerrainPlugin p = t.getPlugin();
            if (!(p instanceof StarCoronaTerrainPlugin) && !(p instanceof PulsarBeamTerrainPlugin)) continue;
            BaseRingTerrain.RingParams rp = ((BaseRingTerrain) p).getRingParams();
            if (rp == null || rp.relatedEntity != star) continue;
            sys.removeEntity(t);
            n++;
        }
        return n;
    }

    /**
     * Adds the star's terrain for its type with the given corona thickness. Returns how far it reaches from
     * the star's center (a pulsar beam is long and thin, so only its small corona counts).
     */
    public static float add(StarSystemAPI sys, PlanetAPI star, StarGenDataSpec data, float corona, Random random) {
        float flare = data.getMinFlare() + (data.getMaxFlare() - data.getMinFlare()) * random.nextFloat();
        float r = star.getRadius();
        if (isBlackHole(star)) {
            SectorEntityToken horizon = sys.addTerrain(Terrain.EVENT_HORIZON, new StarCoronaTerrainPlugin.CoronaParams(
                    r + corona, (r + corona) / 2f, star, data.getSolarWind(), flare, data.getCrLossMult()));
            horizon.setCircularOrbit(star, 0, 0, 100);
            return r + corona;
        }
        if (isNeutron(star)) {
            sys.addCorona(star, 300, 3, 0, 3);
            SectorEntityToken beam = sys.addTerrain(Terrain.PULSAR_BEAM, new StarCoronaTerrainPlugin.CoronaParams(
                    r + corona, (r + corona) / 2f, star, data.getSolarWind(), flare, data.getCrLossMult()));
            beam.setCircularOrbit(star, 0, 0, 100);
            return r + 300;
        }
        sys.addCorona(star, corona, data.getSolarWind(), flare, data.getCrLossMult());
        return r + corona;
    }

    /** How far a star's terrain reaches, for orbit checks before it exists. */
    public static float reach(String type, float radius, float corona) {
        String t = type.toLowerCase(Locale.ROOT);
        if (t.contains("neutron")) return radius + 300;
        return radius + corona;
    }

    /** System light like StarSystemGenerator: the main star's light, blended with any companion stars. */
    public static Color systemLight(StarSystemAPI sys, Random random) {
        List<PlanetAPI> stars = new ArrayList<>();
        if (sys.getStar() != null) stars.add(sys.getStar());
        if (sys.getSecondary() != null) stars.add(sys.getSecondary());
        if (sys.getTertiary() != null) stars.add(sys.getTertiary());
        Color result = null;
        for (PlanetAPI s : stars) {
            StarGenDataSpec d = WsUtil.starData(s.getSpec().getPlanetType());
            if (d == null) continue;
            Color c = Misc.interpolateColor(d.getLightColorMin(), d.getLightColorMax(), random.nextFloat());
            result = result == null ? c : Misc.interpolateColor(result, c, 0.5f);
        }
        return result != null ? result : sys.getLightColor();
    }
}
