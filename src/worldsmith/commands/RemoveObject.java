package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Removes a stable location, comm relay, nav buoy, sensor array (normal or makeshift) or inactive gate:
 * the same kinds of objects addobject creates. Always asks first.
 */
public class RemoveObject implements BaseCommandWithSuggestion {
    private static final String USAGE = "removeobject [type|name]   (types: stable, relay, buoy, sensor, gate)";

    private static boolean removable(SectorEntityToken e) {
        return e.getCustomEntityType() != null && AddObject.TYPES.containsValue(e.getCustomEntityType());
    }

    private static String typeKey(SectorEntityToken e) {
        for (Map.Entry<String, String> t : AddObject.TYPES.entrySet()) {
            if (t.getValue().equals(e.getCustomEntityType())) return t.getKey();
        }
        return e.getCustomEntityType();
    }

    private static List<SectorEntityToken> allObjects() {
        List<SectorEntityToken> out = new ArrayList<>();
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            for (SectorEntityToken e : s.getAllEntities()) {
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
        String typeKey = query.isEmpty() ? null : AddObject.resolve(query);
        if (query.isEmpty() || typeKey != null) {
            // Nearest object (of that type) in the system you are in.
            float best = Float.MAX_VALUE;
            for (SectorEntityToken e : here.getAllEntities()) {
                if (!removable(e)) continue;
                if (typeKey != null && !AddObject.TYPES.get(typeKey).equals(e.getCustomEntityType())) continue;
                float d = Misc.getDistance(fleet.getLocation(), e.getLocation());
                if (d < best) {
                    best = d;
                    target = e;
                }
            }
            if (target == null) {
                WsUtil.print("There is no " + (typeKey == null ? "removable object" : typeKey) + " in "
                        + (here.isHyperspace() ? "hyperspace" : here.getName()) + ". Usage: " + USAGE);
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
                WsUtil.print("No stable location, relay, buoy, sensor array or gate called '" + query + "'. Usage: " + USAGE);
                return CommandResult.ERROR;
            }
            target = hits.get(0);
        }

        final SectorEntityToken entity = target;
        final String key = "removeobject:" + entity.getId();
        boolean gate = Entities.INACTIVE_GATE.equals(entity.getCustomEntityType());
        String owner = entity.getFaction() == null || entity.getFaction().isNeutralFaction() ? ""
                : ", owned by " + entity.getFaction().getDisplayName();
        String question = "Remove " + entity.getName() + " (" + typeKey(entity) + owner + ") from " + WsUtil.systemName(entity) + "?"
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
        loc.removeEntity(entity);
        WsUtil.invalidateCache();
        return "Removed " + entity.getName() + " (" + typeKey(entity) + ") from " + where + ".";
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) {
            List<String> l = new ArrayList<>(AddObject.TYPES.keySet());
            l.addAll(WsUtil.list("stable", "relay", "buoy", "sensor", "gate"));
            return l;
        }
        return WsUtil.list("yes");
    }
}
