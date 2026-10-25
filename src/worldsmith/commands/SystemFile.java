package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.RingBandAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.ConditionGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import com.fs.starfarer.api.impl.campaign.procgen.StarGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidFieldTerrainPlugin.AsteroidFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.MagneticFieldTerrainPlugin.MagneticFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.PulsarBeamTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;
import worldsmith.JumpPoints;
import worldsmith.StarTerrain;
import worldsmith.WsUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Star systems as JSON files in Starsector's saves/common/worldsmith folder: everything a system is made of, written
 * so a modder can rebuild it in code (the fields mirror the game's addPlanet, addRingBand, addAsteroidBelt... calls),
 * and read back by importsystem, which rebuilds the stars, planets with their conditions, rings, belts, fields,
 * relays, buoys, arrays, stable locations, gates and jump points. Colonies, stations and wrecks are written for
 * reference but not rebuilt.
 */
final class SystemFile {
    static final String FORMAT = "worldsmith-system";
    static final int VERSION = 1;
    private static final String FOLDER = "worldsmith/";

    private static final Set<String> OBJECTIVES = new HashSet<>(Arrays.asList(
            Entities.COMM_RELAY, Entities.NAV_BUOY, Entities.SENSOR_ARRAY,
            Entities.COMM_RELAY_MAKESHIFT, Entities.NAV_BUOY_MAKESHIFT, Entities.SENSOR_ARRAY_MAKESHIFT));
    /** Objects importsystem rebuilds; other kinds (stations, wrecks, salvage, mod objects) are only listed. */
    private static final Set<String> IMPORTABLE = new HashSet<>(OBJECTIVES);

    static {
        IMPORTABLE.add(Entities.STABLE_LOCATION);
        IMPORTABLE.add(Entities.INACTIVE_GATE);
    }

    private SystemFile() {
    }

    // ------------------------------------------------------------------ files

    /** A safe file name: "Corvus" -> "corvus", "my system.json.data" -> "my_system". */
    static String fileName(String raw) {
        String n = raw.trim().toLowerCase(Locale.ROOT);
        if (n.endsWith(".data")) n = n.substring(0, n.length() - 5);
        if (n.endsWith(".json")) n = n.substring(0, n.length() - 5);
        n = n.replaceAll("[^a-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        return n.isEmpty() ? "system" : n;
    }

    /** The name the game's file functions take (they add ".data" on disk). */
    static String commonPath(String file) {
        return FOLDER + file + ".json";
    }

    private static final String INDEX = FOLDER + "exports.json";

    /** The exports made with exportsystem that still exist, as {file, system name}, newest first. */
    static List<String[]> knownExports() {
        List<String[]> out = new ArrayList<>();
        try {
            if (!Global.getSettings().fileExistsInCommon(INDEX)) return out;
            JSONArray files = new JSONObject(Global.getSettings().readTextFileFromCommon(INDEX)).optJSONArray("files");
            for (int i = 0; files != null && i < files.length(); i++) {
                JSONObject f = files.optJSONObject(i);
                if (f == null || f.optString("file").isEmpty()) continue;
                if (Global.getSettings().fileExistsInCommon(commonPath(f.optString("file"))))
                    out.add(new String[]{f.optString("file"), f.optString("system", f.optString("file"))});
            }
        } catch (Exception ex) {
            Global.getLogger(SystemFile.class).warn("Worldsmith: could not read the export list", ex);
        }
        return out;
    }

    /** Puts an export at the top of the list importsystem shows. */
    static void remember(String file, String systemName) {
        try {
            JSONArray files = new JSONArray();
            JSONObject first = new JSONObject();
            first.put("file", file);
            first.put("system", systemName);
            files.put(first);
            for (String[] e : knownExports()) {
                if (e[0].equals(file)) continue;
                JSONObject o = new JSONObject();
                o.put("file", e[0]);
                o.put("system", e[1]);
                files.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("files", files);
            Global.getSettings().writeTextFileToCommon(INDEX, root.toString(2));
        } catch (Exception ex) {
            Global.getLogger(SystemFile.class).warn("Worldsmith: could not update the export list", ex);
        }
    }

    /** "alexandretta (Alexandretta), corvus (Corvus)", or null if none are known. */
    static String exportList() {
        List<String> parts = new ArrayList<>();
        for (String[] e : knownExports()) parts.add(e[0] + " (" + e[1] + ")");
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    /** Where the file is, as the player finds it. */
    static String diskPath(String file) {
        return "Starsector/saves/common/worldsmith/" + file + ".json.data";
    }

    // ------------------------------------------------------------------ small helpers

    private static double r1(float v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static JSONArray color(Color c) {
        JSONArray a = new JSONArray();
        a.put(c.getRed());
        a.put(c.getGreen());
        a.put(c.getBlue());
        a.put(c.getAlpha());
        return a;
    }

    private static Color color(JSONArray a, Color fallback) {
        if (a == null || a.length() < 3) return fallback;
        return new Color(clamp(a.optInt(0)), clamp(a.optInt(1)), clamp(a.optInt(2)), a.length() > 3 ? clamp(a.optInt(3)) : 255);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static float num(JSONObject o, String key, float fallback) {
        double d = o.optDouble(key, fallback);
        return Double.isNaN(d) || Double.isInfinite(d) ? fallback : (float) d;
    }

    private static String modVersion() {
        try {
            ModSpecAPI spec = Global.getSettings().getModManager().getModSpec(WsUtil.MOD_ID);
            return spec == null ? "" : spec.getVersion();
        } catch (Exception ex) {
            return "";
        }
    }

    private static boolean planetTypeExists(String type) {
        for (PlanetSpecAPI s : Global.getSettings().getAllPlanetSpecs()) {
            if (s.getPlanetType().equals(type)) return true;
        }
        return false;
    }

    /**
     * Conditions a planet can have without a colony: the ones the planet generator rolls (resources, ruins,
     * atmosphere, gravity...). Colony conditions like Pather cells or a free port are left out.
     */
    private static Set<String> naturalConditions() {
        Set<String> out = new HashSet<>();
        for (ConditionGenDataSpec g : Global.getSettings().getAllSpecs(ConditionGenDataSpec.class)) {
            if (g.getId().endsWith(ConditionGenDataSpec.NO_PICK_SUFFIX)) continue;
            try {
                if (Global.getSettings().getMarketConditionSpec(g.getId()) != null) out.add(g.getId());
            } catch (Exception ignored) {
                // a generator entry without a condition behind it
            }
        }
        return out;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (Exception ex) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ export

    /** Stars first, then everything in the order of what it orbits (parents before moons). */
    private static List<PlanetAPI> orderedBodies(StarSystemAPI sys) {
        List<PlanetAPI> left = new ArrayList<>(sys.getPlanets());
        List<PlanetAPI> out = new ArrayList<>();
        if (sys.getStar() != null && left.remove(sys.getStar())) out.add(sys.getStar());
        boolean progress = true;
        while (!left.isEmpty() && progress) {
            progress = false;
            for (Iterator<PlanetAPI> it = left.iterator(); it.hasNext(); ) {
                PlanetAPI p = it.next();
                SectorEntityToken f = p.getOrbitFocus();
                if (!(f instanceof PlanetAPI) || out.contains(f)) {
                    out.add(p);
                    it.remove();
                    progress = true;
                }
            }
        }
        out.addAll(left);
        return out;
    }

    private static JSONObject orbit(StarSystemAPI sys, SectorEntityToken e, Map<SectorEntityToken, String> ids)
            throws JSONException {
        JSONObject o = new JSONObject();
        SectorEntityToken f = e.getOrbitFocus();
        String fid = f == null ? null : ids.get(f);
        if (fid == null && f != null && f == sys.getCenter()) fid = "center";
        if (fid != null && e.getOrbit() != null) {
            o.put("focus", fid);
            o.put("radius", r1(Misc.getDistance(e.getLocation(), f.getLocation())));
            o.put("angle", r1(Misc.getAngleInDegrees(f.getLocation(), e.getLocation())));
            float days = e.getCircularOrbitPeriod();
            o.put("days", r1(days == 0f ? 365f : days));
        } else {
            o.put("x", r1(e.getLocation().x));
            o.put("y", r1(e.getLocation().y));
        }
        return o;
    }

    private static String focusId(StarSystemAPI sys, SectorEntityToken f, Map<SectorEntityToken, String> ids) {
        String id = f == null ? null : ids.get(f);
        return id != null ? id : "center";
    }

    /** The corona (or event horizon, or pulsar beam) thickness beyond the star's surface. */
    private static float corona(StarSystemAPI sys, PlanetAPI star) {
        float corona = 0f;
        for (CampaignTerrainAPI t : sys.getTerrainCopy()) {
            CampaignTerrainPlugin p = t.getPlugin();
            if (!(p instanceof BaseRingTerrain)) continue;
            BaseRingTerrain.RingParams rp = ((BaseRingTerrain) p).getRingParams();
            if (rp == null || rp.relatedEntity != star) continue;
            float thickness = rp.bandWidthInEngine - star.getRadius();
            // A neutron star's beam carries its size; its small corona is always the same.
            if (p instanceof PulsarBeamTerrainPlugin) return Math.max(0f, thickness);
            if (p instanceof StarCoronaTerrainPlugin) corona = Math.max(corona, thickness);
        }
        return corona;
    }

    private static JSONObject colony(MarketAPI m) throws JSONException {
        JSONObject c = new JSONObject();
        c.put("name", m.getName());
        c.put("faction", m.getFactionId());
        c.put("size", m.getSize());
        c.put("freePort", m.isFreePort());
        JSONArray industries = new JSONArray();
        for (Industry i : m.getIndustries()) industries.put(i.getId());
        c.put("industries", industries);
        return c;
    }

    /** Everything the system is made of. Also returns counts in {@code counts}: bodies, rings, terrain, objects. */
    static JSONObject export(StarSystemAPI sys, int[] counts) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("exportedBy", ("Worldsmith Console Commands " + modVersion()).trim());
        root.put("gameVersion", Global.getSettings().getVersionString());
        root.put("about", "Distances are in-game units from what each thing orbits ('focus'; 'center' is the system's"
                + " center); 'days' is how long one orbit takes (positive = clockwise). importsystem rebuilds the stars,"
                + " planets and their conditions, rings, belts, fields, relays, buoys, arrays, stable locations, gates"
                + " and jump points. Colonies, stations and wrecks are listed for reference only.");

        JSONObject s = new JSONObject();
        s.put("name", sys.getBaseName());
        s.put("id", sys.getId());
        s.put("type", sys.getType() == null ? StarSystemGenerator.StarSystemType.SINGLE.name() : sys.getType().name());
        s.put("age", sys.getAge() == null ? StarAge.AVERAGE.name() : sys.getAge().name());
        s.put("background", sys.getBackgroundTextureFilename());
        if (sys.getLightColor() != null) s.put("lightColor", color(sys.getLightColor()));
        JSONArray hyper = new JSONArray();
        hyper.put(r1(sys.getLocation().x));
        hyper.put(r1(sys.getLocation().y));
        s.put("hyperspaceLocation", hyper);
        s.put("systemwideNebula", Boolean.TRUE.equals(sys.hasSystemwideNebula()));
        root.put("system", s);

        // stars, planets and moons
        Map<SectorEntityToken, String> ids = new HashMap<>();
        JSONArray bodies = new JSONArray();
        for (PlanetAPI p : orderedBodies(sys)) {
            JSONObject o = new JSONObject();
            o.put("kind", p.isStar() ? "star" : "planet");
            o.put("id", p.getId());
            o.put("name", p.getName());
            o.put("type", p.getTypeId());
            o.put("radius", r1(p.getRadius()));
            if (p.isStar()) o.put("corona", r1(corona(sys, p)));
            if (p != sys.getStar() || p.getOrbitFocus() != null) o.put("orbit", orbit(sys, p, ids));
            if (!p.isStar()) {
                MarketAPI m = p.getMarket();
                JSONArray conditions = new JSONArray();
                if (m != null) for (MarketConditionAPI c : m.getConditions()) conditions.put(c.getId());
                o.put("conditions", conditions);
                if (m != null && !m.isPlanetConditionMarketOnly()) o.put("colony", colony(m));
            }
            bodies.put(o);
            ids.put(p, p.getId());
        }
        root.put("bodies", bodies);

        // ring bands (the visible rings)
        JSONArray bands = new JSONArray();
        for (SectorEntityToken e : sys.getAllEntities()) {
            if (!(e instanceof RingBandAPI)) continue;
            RingBandAPI r = (RingBandAPI) e;
            JSONObject o = new JSONObject();
            o.put("focus", focusId(sys, r.getFocus(), ids));
            o.put("category", r.getCategory() != null ? r.getCategory() : r.getSpriteCategory());
            o.put("texture", r.getKey() != null ? r.getKey() : r.getSpriteKey());
            o.put("textureWidth", r1(r.getBandWidthInTexture()));
            o.put("bandIndex", r.getBandIndex());
            if (r.getColor() != null) o.put("color", color(r.getColor()));
            o.put("width", r1(r.getBandWidthInEngine()));
            o.put("radius", r1(r.getMiddleRadius()));
            o.put("days", r1(r.getOrbitDays()));
            bands.put(o);
        }
        root.put("ringBands", bands);

        // terrain: belts, ring terrain, magnetic fields, asteroid and debris fields
        JSONArray terrain = new JSONArray();
        for (CampaignTerrainAPI t : sys.getTerrainCopy()) {
            CampaignTerrainPlugin p = t.getPlugin();
            if (p instanceof StarCoronaTerrainPlugin || p instanceof PulsarBeamTerrainPlugin) continue; // part of a star
            JSONObject o = new JSONObject();
            BaseRingTerrain.RingParams rp = p instanceof BaseRingTerrain ? ((BaseRingTerrain) p).getRingParams() : null;
            if (rp instanceof MagneticFieldParams) {
                MagneticFieldParams mp = (MagneticFieldParams) rp;
                o.put("kind", "magnetic_field");
                o.put("focus", focusId(sys, mp.relatedEntity, ids));
                o.put("width", r1(mp.bandWidthInEngine));
                o.put("radius", r1(mp.middleRadius));
                o.put("innerRadius", r1(mp.innerRadius));
                o.put("outerRadius", r1(mp.outerRadius));
                if (mp.baseColor != null) o.put("baseColor", color(mp.baseColor));
                o.put("auroraFrequency", r1(mp.auroraFrequency));
                if (mp.auroraColorRange != null) {
                    JSONArray aurora = new JSONArray();
                    for (Color c : mp.auroraColorRange) aurora.put(color(c));
                    o.put("auroraColors", aurora);
                }
            } else if (rp instanceof AsteroidFieldParams) {
                AsteroidFieldParams ap = (AsteroidFieldParams) rp;
                o.put("kind", "asteroid_field");
                o.put("size", r1(ap.bandWidthInEngine));
                o.put("asteroids", ap.numAsteroids > 0 ? ap.numAsteroids : ap.maxAsteroids);
                o.put("minAsteroidSize", r1(ap.minSize));
                o.put("maxAsteroidSize", r1(ap.maxSize));
                o.put("orbit", orbit(sys, t, ids));
            } else if (rp instanceof DebrisFieldParams) {
                DebrisFieldParams dp = (DebrisFieldParams) rp;
                o.put("kind", "debris_field");
                o.put("size", r1(dp.bandWidthInEngine));
                o.put("density", r1(dp.baseDensity));
                o.put("lastsDays", r1(dp.lastsDays));
                o.put("glowsDays", r1(dp.glowsDays));
                o.put("orbit", orbit(sys, t, ids));
            } else if (rp != null && Terrain.ASTEROID_BELT.equals(t.getType())) {
                o.put("kind", "asteroid_belt");
                o.put("focus", focusId(sys, rp.relatedEntity, ids));
                o.put("radius", r1(rp.middleRadius));
                o.put("width", r1(rp.bandWidthInEngine));
                if (rp.name != null) o.put("name", rp.name);
                int count = 0;
                float minDays = Float.MAX_VALUE, maxDays = 0f;
                for (SectorEntityToken a : sys.getAsteroids()) {
                    if (a.getOrbitFocus() != rp.relatedEntity || rp.relatedEntity == null) continue;
                    float d = Misc.getDistance(a.getLocation(), rp.relatedEntity.getLocation());
                    if (Math.abs(d - rp.middleRadius) > rp.bandWidthInEngine / 2f + 100f) continue;
                    count++;
                    float days = Math.abs(a.getCircularOrbitPeriod());
                    if (days > 0f) {
                        minDays = Math.min(minDays, days);
                        maxDays = Math.max(maxDays, days);
                    }
                }
                o.put("asteroids", count);
                if (maxDays > 0f) {
                    o.put("minDays", r1(minDays));
                    o.put("maxDays", r1(maxDays));
                }
            } else if (rp != null && Terrain.RING.equals(t.getType())) {
                o.put("kind", "ring_terrain");
                o.put("focus", focusId(sys, rp.relatedEntity, ids));
                o.put("radius", r1(rp.middleRadius));
                o.put("width", r1(rp.bandWidthInEngine));
                if (rp.name != null) o.put("name", rp.name);
            } else {
                o.put("kind", "other");
                o.put("terrainType", t.getType());
            }
            terrain.put(o);
        }
        root.put("terrain", terrain);

        // objects: relays, buoys, arrays, stable locations, gates, stations, wrecks...
        JSONArray objects = new JSONArray();
        for (CustomCampaignEntityAPI e : sys.getCustomEntities()) {
            JSONObject o = new JSONObject();
            o.put("type", e.getCustomEntityType());
            o.put("name", e.getName());
            o.put("faction", e.getFaction() == null ? Factions.NEUTRAL : e.getFaction().getId());
            o.put("orbit", orbit(sys, e, ids));
            MarketAPI m = e.getMarket();
            if (m != null && !m.isPlanetConditionMarketOnly()) o.put("colony", colony(m));
            o.put("rebuiltByImport", IMPORTABLE.contains(e.getCustomEntityType()));
            objects.put(o);
        }
        root.put("objects", objects);

        // jump points inside the system
        JSONArray jumps = new JSONArray();
        for (SectorEntityToken t : sys.getJumpPoints()) {
            if (!(t instanceof JumpPointAPI)) continue;
            JumpPointAPI jp = (JumpPointAPI) t;
            JSONObject o = new JSONObject();
            o.put("name", jp.getName());
            o.put("orbit", orbit(sys, jp, ids));
            if (jp.getRelatedPlanet() != null) o.put("planet", jp.getRelatedPlanet().getId());
            jumps.put(o);
        }
        root.put("jumpPoints", jumps);

        counts[0] = bodies.length();
        counts[1] = bands.length();
        counts[2] = terrain.length();
        counts[3] = objects.length();
        return root;
    }

    // ------------------------------------------------------------------ import

    /** "Corvus IV" becomes "Hakeball IV" when Corvus is imported as Hakeball. */
    private static String rename(String n, String oldName, String newName) {
        if (n == null || n.isEmpty() || oldName.equals(newName)) return n;
        if (n.equals(oldName)) return newName;
        if (n.startsWith(oldName + " ") || n.startsWith(oldName + ",")) return newName + n.substring(oldName.length());
        return n;
    }

    /** The main star's entry: the first star that orbits nothing, else the first star. Null if there is none. */
    static JSONObject mainStar(JSONObject root) {
        JSONArray bodies = root.optJSONArray("bodies");
        if (bodies == null) return null;
        JSONObject first = null;
        for (int i = 0; i < bodies.length(); i++) {
            JSONObject b = bodies.optJSONObject(i);
            if (b == null || !"star".equals(b.optString("kind"))) continue;
            if (!b.has("orbit") || "center".equals(b.optJSONObject("orbit") == null ? "" : b.optJSONObject("orbit").optString("focus")))
                return b;
            if (first == null) first = b;
        }
        return first;
    }

    /** Null if the file can be imported here, else why not. */
    static String problem(JSONObject root) {
        if (!FORMAT.equals(root.optString("format"))) return "This file isn't a Worldsmith system export.";
        if (root.optInt("version", 0) > VERSION)
            return "This file was made by a newer version of Worldsmith Console Commands; update the mod to import it.";
        if (root.optJSONObject("system") == null || root.optJSONObject("system").optString("name", "").isEmpty())
            return "The file has no system name.";
        JSONObject star = mainStar(root);
        if (star == null) return "The file has no star.";
        if (!planetTypeExists(star.optString("type"))) {
            return "Its star type '" + star.optString("type") + "' comes from a mod that isn't installed here.";
        }
        return null;
    }

    private static void place(SectorEntityToken e, JSONObject orbit, Map<String, SectorEntityToken> ids,
                              SectorEntityToken fallback, boolean pointDown) {
        if (orbit != null && orbit.has("x") && !orbit.has("focus")) {
            e.setFixedLocation(num(orbit, "x", 0f), num(orbit, "y", 0f));
            return;
        }
        SectorEntityToken focus = orbit == null ? null : ids.get(orbit.optString("focus", "center"));
        if (focus == null) focus = fallback;
        float radius = orbit == null ? 2000f : num(orbit, "radius", 2000f);
        float angle = orbit == null ? 0f : num(orbit, "angle", 0f);
        float days = orbit == null ? 0f : num(orbit, "days", 0f);
        if (days == 0f) days = Math.max(1f, radius / 22.5f);
        if (pointDown) e.setCircularOrbitPointingDown(focus, angle, radius, days);
        else e.setCircularOrbit(focus, angle, radius, days);
    }

    /** Builds the system at {@code loc} in hyperspace. Call {@link #problem} first. Returns what was built. */
    static String importSystem(JSONObject root, String newName, Vector2f loc) throws JSONException {
        JSONObject s = root.getJSONObject("system");
        String oldName = s.getString("name");
        String name = newName != null && !newName.isEmpty() ? newName : oldName;
        JSONObject mainStar = mainStar(root);
        Random random = new Random();

        StarSystemAPI sys = Global.getSector().createStarSystem(name);
        try {
            sys.getLocation().set(loc.x, loc.y);
            // Only the game's own backgrounds: one from a mod that isn't installed would break the system's look.
            String bg = s.optString("background", "");
            sys.setBackgroundTextureFilename(bg.matches("graphics/backgrounds/background\\d+\\.jpg")
                    ? bg : "graphics/backgrounds/background4.jpg");
            sys.setAge(enumOr(StarAge.class, s.optString("age"), StarAge.AVERAGE));

            // the main star, in the middle
            String starType = mainStar.getString("type");
            float radius = num(mainStar, "radius", 500f);
            float corona = num(mainStar, "corona", radius * 0.5f);
            StarGenDataSpec data = WsUtil.starData(starType);
            PlanetAPI star;
            if (data != null) {
                float flare = data.getMinFlare() + (data.getMaxFlare() - data.getMinFlare()) * random.nextFloat();
                star = sys.initStar("worldsmith_" + Misc.genUID(), starType, radius, corona, data.getSolarWind(), flare,
                        data.getCrLossMult());
                if (StarTerrain.isBlackHole(star) || StarTerrain.isNeutron(star)) {
                    StarTerrain.remove(sys, star);
                    StarTerrain.add(sys, star, data, corona, random);
                }
            } else {
                star = sys.initStar("worldsmith_" + Misc.genUID(), starType, radius, corona);
            }
            star.setName(rename(mainStar.optString("name", name + " Prime"), oldName, name));
            Color light = data != null ? Misc.interpolateColor(data.getLightColorMin(), data.getLightColorMax(), random.nextFloat())
                    : Color.white;
            sys.setLightColor(color(s.optJSONArray("lightColor"), light));
            Map<String, SectorEntityToken> ids = new HashMap<>();
            ids.put("center", star);
            if (!mainStar.optString("id").isEmpty()) ids.put(mainStar.optString("id"), star);

            // In close binaries both stars circle the system's center. Here the main star sits in the middle, so
            // whatever circled the center goes out by the main star's own distance to keep clear of it.
            JSONObject mainOrbit = mainStar.optJSONObject("orbit");
            float centerOffset = mainOrbit == null ? 0f : num(mainOrbit, "radius", 0f);

            // other stars, planets and moons, in the file's order (what something orbits comes first)
            Set<String> natural = naturalConditions();
            int stars = 1, planets = 0, leftOut = 0;
            List<String> skipped = new ArrayList<>();
            JSONArray bodies = root.optJSONArray("bodies");
            for (int i = 0; bodies != null && i < bodies.length(); i++) {
                JSONObject b = bodies.optJSONObject(i);
                if (b == null || b == mainStar) continue;
                String bname = rename(b.optString("name", "Unnamed"), oldName, name);
                JSONObject o = b.optJSONObject("orbit");
                SectorEntityToken focus = o == null ? null : ids.get(o.optString("focus", "center"));
                if (focus == null) {
                    skipped.add(bname + " (what it orbits wasn't rebuilt)");
                    continue;
                }
                String type = b.optString("type");
                if (!planetTypeExists(type)) {
                    skipped.add(bname + " (type '" + type + "' from a mod that isn't installed)");
                    continue;
                }
                float orbitRadius = num(o, "radius", 2000f);
                if ("center".equals(o.optString("focus"))) orbitRadius += centerOffset;
                float days = num(o, "days", 0f);
                if (days == 0f) days = Math.max(1f, orbitRadius / 22.5f);
                PlanetAPI p = sys.addPlanet("worldsmith_" + Misc.genUID(), focus, bname, type, num(o, "angle", 0f),
                        num(b, "radius", 100f), orbitRadius, days);
                if ("star".equals(b.optString("kind"))) {
                    StarGenDataSpec d = WsUtil.starData(type);
                    if (d != null) StarTerrain.add(sys, p, d, num(b, "corona", p.getRadius() * 0.5f), random);
                    if (sys.getSecondary() == null) sys.setSecondary(p);
                    else if (sys.getTertiary() == null) sys.setTertiary(p);
                    stars++;
                } else {
                    if (p.getMarket() == null) Misc.initConditionMarket(p);
                    MarketAPI m = p.getMarket();
                    JSONArray conditions = b.optJSONArray("conditions");
                    if (m != null && conditions != null) {
                        for (int k = 0; k < conditions.length(); k++) {
                            String c = conditions.optString(k, "");
                            if (c.isEmpty() || m.hasCondition(c)) continue;
                            if (!natural.contains(c)) {
                                leftOut++;
                                continue;
                            }
                            m.addCondition(c);
                        }
                        m.reapplyConditions();
                    }
                    planets++;
                }
                if (!b.optString("id").isEmpty()) ids.put(b.optString("id"), p);
            }
            if (stars >= 3) sys.setType(StarSystemGenerator.StarSystemType.TRINARY_2FAR);
            else if (stars == 2) sys.setType(StarSystemGenerator.StarSystemType.BINARY_FAR);
            else sys.setType(StarSystemGenerator.StarSystemType.SINGLE);

            // visible rings
            int rings = 0;
            JSONArray bands = root.optJSONArray("ringBands");
            for (int i = 0; bands != null && i < bands.length(); i++) {
                JSONObject r = bands.optJSONObject(i);
                SectorEntityToken focus = r == null ? null : ids.get(r.optString("focus", "center"));
                if (focus == null) continue;
                sys.addRingBand(focus, r.optString("category", "misc"), r.optString("texture", "rings_dust0"),
                        num(r, "textureWidth", 256f), r.optInt("bandIndex", 0), color(r.optJSONArray("color"), Color.white),
                        num(r, "width", 256f), num(r, "radius", 2000f), num(r, "days", 100f));
                rings++;
            }

            // belts, ring terrain, magnetic fields, asteroid and debris fields
            int belts = 0, fields = 0, otherTerrain = 0;
            JSONArray terrain = root.optJSONArray("terrain");
            for (int i = 0; terrain != null && i < terrain.length(); i++) {
                JSONObject t = terrain.optJSONObject(i);
                if (t == null) continue;
                String kind = t.optString("kind");
                SectorEntityToken focus = ids.get(t.optString("focus", "center"));
                String tname = t.has("name") ? rename(t.optString("name"), oldName, name) : null;
                boolean needsFocus = kind.equals("asteroid_belt") || kind.equals("ring_terrain") || kind.equals("magnetic_field");
                if (needsFocus && focus == null) {
                    otherTerrain++;
                    continue;
                }
                switch (kind) {
                    case "asteroid_belt": {
                        float r = num(t, "radius", 2000f);
                        float min = num(t, "minDays", r / 25f), max = num(t, "maxDays", r / 20f);
                        sys.addAsteroidBelt(focus, Math.max(1, t.optInt("asteroids", 50)), r, num(t, "width", 256f),
                                Math.max(1f, min), Math.max(min, max), Terrain.ASTEROID_BELT, tname);
                        belts++;
                        break;
                    }
                    case "ring_terrain": {
                        SectorEntityToken ring = sys.addTerrain(Terrain.RING, new BaseRingTerrain.RingParams(
                                num(t, "width", 256f), num(t, "radius", 2000f), focus, tname));
                        ring.setCircularOrbit(focus, 0, 0, 100);
                        break;
                    }
                    case "magnetic_field": {
                        Color base = color(t.optJSONArray("baseColor"), new Color(50, 20, 100, 40));
                        JSONArray aurora = t.optJSONArray("auroraColors");
                        float width = num(t, "width", 300f), r = num(t, "radius", focus.getRadius() + 200f);
                        float inner = num(t, "innerRadius", r - width / 2f), outer = num(t, "outerRadius", r + width / 2f);
                        float freq = num(t, "auroraFrequency", 0.5f);
                        MagneticFieldParams mp;
                        if (aurora != null && aurora.length() > 0) {
                            Color[] colors = new Color[aurora.length()];
                            for (int k = 0; k < colors.length; k++) colors[k] = color(aurora.optJSONArray(k), Color.white);
                            mp = new MagneticFieldParams(width, r, focus, inner, outer, base, freq, colors);
                        } else {
                            mp = new MagneticFieldParams(width, r, focus, inner, outer, base, freq);
                        }
                        SectorEntityToken field = sys.addTerrain(Terrain.MAGNETIC_FIELD, mp);
                        field.setCircularOrbit(focus, 0, 0, 100);
                        fields++;
                        break;
                    }
                    case "asteroid_field": {
                        float size = num(t, "size", 400f);
                        int count = Math.max(1, t.optInt("asteroids", 30));
                        SectorEntityToken field = sys.addTerrain(Terrain.ASTEROID_FIELD, new AsteroidFieldParams(size,
                                size + 100f, count, count, num(t, "minAsteroidSize", 4f), num(t, "maxAsteroidSize", 16f), tname));
                        place(field, t.optJSONObject("orbit"), ids, star, false);
                        fields++;
                        break;
                    }
                    case "debris_field": {
                        float lasts = num(t, "lastsDays", 0f);
                        DebrisFieldParams dp = new DebrisFieldParams(num(t, "size", 300f), num(t, "density", -1f),
                                lasts > 0f ? lasts : 10000000f, num(t, "glowsDays", 0f));
                        dp.source = DebrisFieldTerrainPlugin.DebrisFieldSource.GEN;
                        SectorEntityToken field = Misc.addDebrisField(sys, dp, random);
                        field.setDiscoverable(null);
                        field.setDiscoveryXP(null);
                        place(field, t.optJSONObject("orbit"), ids, star, false);
                        fields++;
                        break;
                    }
                    default:
                        otherTerrain++;
                }
            }

            // relays, buoys, arrays, stable locations and gates; they start unclaimed, like addobject's
            int objects = 0, otherObjects = 0;
            JSONArray objs = root.optJSONArray("objects");
            for (int i = 0; objs != null && i < objs.length(); i++) {
                JSONObject o = objs.optJSONObject(i);
                if (o == null) continue;
                String type = o.optString("type");
                if (!IMPORTABLE.contains(type)) {
                    otherObjects++;
                    continue;
                }
                String oname = o.optString("name", "");
                CustomCampaignEntityAPI e = sys.addCustomEntity(null, oname.isEmpty() ? null : rename(oname, oldName, name),
                        type, Factions.NEUTRAL);
                place(e, o.optJSONObject("orbit"), ids, star, OBJECTIVES.contains(type));
                objects++;
            }

            // jump points that orbit a star; gravity wells at planets come from the game's own generator
            int jumps = 0;
            JSONArray jps = root.optJSONArray("jumpPoints");
            for (int i = 0; jps != null && i < jps.length(); i++) {
                JSONObject j = jps.optJSONObject(i);
                JSONObject o = j == null ? null : j.optJSONObject("orbit");
                if (o == null || !o.has("focus")) continue;
                SectorEntityToken focus = ids.get(o.optString("focus"));
                if (focus == null || (focus instanceof PlanetAPI && !((PlanetAPI) focus).isStar())) continue;
                JumpPoints.inSystem(sys, rename(j.optString("name", "Jump-point"), oldName, name),
                        num(o, "angle", 0f), num(o, "radius", 3000f), random);
                jumps++;
            }
            if (jumps == 0) {
                JumpPoints.addStandard(sys, random);
                jumps = 2;
            }
            NewSystem.finish(sys, loc);

            StringBuilder msg = new StringBuilder("Imported " + sys.getName() + " at your position in hyperspace: "
                    + stars + " star(s), " + planets + " planets and moons (uncolonized, unsurveyed, with their"
                    + " conditions), " + rings + " ring(s), " + belts + " asteroid belt(s), " + fields + " field(s), "
                    + objects + " object(s) and " + jumps + " jump point(s).");
            if (objects > 0) msg.append(" Relays, buoys and arrays start unclaimed.");
            if (leftOut > 0) msg.append(" Left out: ").append(leftOut).append(" colony condition(s), like population.");
            if (otherObjects > 0)
                msg.append(" Not rebuilt: ").append(otherObjects).append(" station(s), wreck(s) or other object(s).");
            if (otherTerrain > 0) msg.append(" Not rebuilt: ").append(otherTerrain).append(" nebula or other terrain.");
            if (!skipped.isEmpty()) msg.append(" Skipped: ").append(String.join(", ", skipped)).append(".");
            return msg.toString();
        } catch (RuntimeException | JSONException ex) {
            // Half a system is worse than none.
            Global.getSector().removeStarSystem(sys);
            throw ex;
        }
    }
}
