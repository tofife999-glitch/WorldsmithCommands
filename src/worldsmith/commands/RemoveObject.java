package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.terrain.AsteroidSource;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.JumpPoints;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Removes a stable location, comm relay, nav buoy, sensor array (normal or makeshift), inactive gate, asteroid
 * field, debris field, or a jump point made with addjumppoint (both ends), whether generated with the sector or
 * added later. Always asks first.
 */
public class RemoveObject implements BaseCommandWithSuggestion {
    private static final String USAGE = "removeobject [type|name]   (types: stable, relay, buoy, sensor, gate, field, jumppoint)";
    /** Fields are terrain, not custom entities: type key -> terrain type. */
    private static final Map<String, String> FIELDS = new LinkedHashMap<>();
    /** Type key meaning "an asteroid field or a debris field". */
    private static final String ANY_FIELD = "field";
    private static final String JUMP_POINT = "jump_point";

    static {
        FIELDS.put("asteroid_field", Terrain.ASTEROID_FIELD);
        FIELDS.put("debris_field", Terrain.DEBRIS_FIELD);
    }

    private static String resolve(String raw) {
        String object = AddObject.resolve(raw);
        if (object != null) return object;
        String t = raw.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (FIELDS.containsKey(t)) return t;
        switch (t) {
            case "field": case "fields": return ANY_FIELD;
            case "asteroids": case "asteroidfield": return "asteroid_field";
            case "debris": case "debrisfield": return "debris_field";
            case "jumppoint": case "jump_point": case "jump-point": case "jp": return JUMP_POINT;
            default: return null;
        }
    }

    private static String fieldType(SectorEntityToken e) {
        return e instanceof CampaignTerrainAPI && FIELDS.containsValue(((CampaignTerrainAPI) e).getType())
                ? ((CampaignTerrainAPI) e).getType() : null;
    }

    /** Only jump points made with addjumppoint: removing other ones could cut a system off. */
    private static boolean isOurJumpPoint(SectorEntityToken e) {
        return e instanceof JumpPointAPI && e.getMemoryWithoutUpdate().contains(JumpPoints.MEM_PAIR);
    }

    private static boolean removable(SectorEntityToken e) {
        return (e.getCustomEntityType() != null && AddObject.TYPES.containsValue(e.getCustomEntityType()))
                || fieldType(e) != null || isOurJumpPoint(e);
    }

    private static String typeKey(SectorEntityToken e) {
        if (isOurJumpPoint(e)) return JUMP_POINT;
        String field = fieldType(e);
        Map<String, String> map = field != null ? FIELDS : AddObject.TYPES;
        String value = field != null ? field : e.getCustomEntityType();
        for (Map.Entry<String, String> t : map.entrySet()) {
            if (t.getValue().equals(value)) return t.getKey();
        }
        return value;
    }

    private static boolean ofType(SectorEntityToken e, String key) {
        return key == null || (ANY_FIELD.equals(key) ? fieldType(e) != null : key.equals(typeKey(e)));
    }

    private static List<SectorEntityToken> allObjects() {
        List<LocationAPI> places = new ArrayList<LocationAPI>(Global.getSector().getStarSystems());
        places.add(Global.getSector().getHyperspace());
        List<SectorEntityToken> out = new ArrayList<>();
        for (LocationAPI loc : places) {
            for (SectorEntityToken e : loc.getAllEntities()) {
                if (removable(e)) out.add(e);
            }
        }
        return out;
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
        String query = WsUtil.join(parts, 0, parts.size());
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        LocationAPI here = fleet.getContainingLocation();

        SectorEntityToken target = null;
        String typeKey = query.isEmpty() ? null : resolve(query);
        if (query.isEmpty() || typeKey != null) {
            // Nearest object (of that type) where you are.
            float best = Float.MAX_VALUE;
            for (SectorEntityToken e : here.getAllEntities()) {
                if (!removable(e) || !ofType(e, typeKey)) continue;
                float d = Misc.getDistance(fleet.getLocation(), e.getLocation());
                if (d < best) {
                    best = d;
                    target = e;
                }
            }
            if (target == null) {
                WsUtil.print("There is no " + (typeKey == null ? "removable object" : typeKey.replace('_', ' ')) + " in "
                        + (here.isHyperspace() ? "hyperspace near you" : here.getName())
                        + (JUMP_POINT.equals(typeKey) ? " (only jump points made with addjumppoint can be removed)" : "")
                        + ". Usage: " + USAGE);
                return CommandResult.ERROR;
            }
        } else {
            List<SectorEntityToken> hits = WsUtil.exactMatches(query, allObjects());
            if (hits.size() > 1) {
                WsUtil.print(hits.size() + " objects are called '" + query + "'. Fly to the one you mean and use"
                        + " 'removeobject <type>', or use its ID:");
                for (SectorEntityToken e : hits) WsUtil.printRaw("  - " + e.getName() + " (" + WsUtil.systemName(e) + ", ID " + e.getId() + ")");
                return CommandResult.ERROR;
            }
            if (hits.isEmpty()) {
                WsUtil.print("No stable location, relay, buoy, sensor array, gate, field or jump point called '" + query
                        + "'. Usage: " + USAGE);
                return CommandResult.ERROR;
            }
            target = hits.get(0);
        }

        final SectorEntityToken entity = target;
        final String key = "removeobject:" + entity.getId();
        boolean gate = Entities.INACTIVE_GATE.equals(entity.getCustomEntityType());
        String owner = entity.getFaction() == null || entity.getFaction().isNeutralFaction() ? ""
                : ", owned by " + entity.getFaction().getDisplayName();
        String question = "Remove " + entity.getName() + " (" + typeKey(entity).replace('_', ' ') + owner + ") from "
                + WsUtil.systemName(entity) + "?"
                + (isOurJumpPoint(entity) ? " Its other end is removed too." : "")
                + (gate ? " Gates are part of the gate network; removing one can affect gate travel and quests." : "");
        if (yes) {
            WsUtil.cancelPending(key);
            WsUtil.print(remove(entity));
            return CommandResult.SUCCESS;
        }
        WsUtil.print(question);
        WsUtil.confirm(key, question, "removeobject " + entity.getId() + " yes", new Script() {
            @Override
            public void run() {
                WsUtil.runSafely("removeobject", new Runnable() {
                    @Override
                    public void run() {
                        WsUtil.report(remove(entity));
                    }
                });
            }
        });
        return CommandResult.SUCCESS;
    }

    private static String remove(SectorEntityToken entity) {
        LocationAPI loc = entity.getContainingLocation();
        if (loc == null || !loc.getAllEntities().contains(entity)) return entity.getName() + " was already removed.";
        String where = WsUtil.systemName(entity);
        if (isOurJumpPoint(entity)) {
            List<SectorEntityToken> ends = JumpPoints.pairOf(entity);
            for (SectorEntityToken jp : ends) jp.getContainingLocation().removeEntity(jp);
            WsUtil.invalidateCache();
            return "Removed the jump point " + entity.getName() + " from " + where + " (" + ends.size() + " ends).";
        }
        int asteroids = 0;
        if (Terrain.ASTEROID_FIELD.equals(fieldType(entity))) {
            // An asteroid field's asteroids are separate entities that belong to it.
            Object plugin = ((CampaignTerrainAPI) entity).getPlugin();
            for (SectorEntityToken a : new ArrayList<>(loc.getAsteroids())) {
                AsteroidSource src = Misc.getAsteroidSource(a);
                if (src != null && src == plugin) {
                    loc.removeEntity(a);
                    asteroids++;
                }
            }
        }
        loc.removeEntity(entity);
        WsUtil.invalidateCache();
        return "Removed " + entity.getName() + " (" + typeKey(entity).replace('_', ' ')
                + (asteroids > 0 ? ", " + asteroids + " asteroids" : "") + ") from " + where + ".";
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) {
            List<String> l = new ArrayList<>(AddObject.TYPES.keySet());
            l.addAll(FIELDS.keySet());
            l.addAll(WsUtil.list("stable", "relay", "buoy", "sensor", "gate", "field", "jumppoint"));
            return l;
        }
        return WsUtil.list("yes");
    }
}
