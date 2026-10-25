package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.SectorDiplomacy;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * A derelict ship to salvage (and maybe recover) next to you, made the way the sector generator places them: a
 * random ship of a size and faction, or a ship of the hull you name.
 */
public class AddWreck implements BaseCommandWithSuggestion {
    private static final String USAGE = "addwreck [small|medium|large|civilian] [faction|ship]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (!(fleet.getContainingLocation() instanceof StarSystemAPI)) {
            WsUtil.print("Fly into a star system first: the wreck appears where you are.");
            return CommandResult.WRONG_CONTEXT;
        }
        StarSystemAPI sys = (StarSystemAPI) fleet.getContainingLocation();
        List<String> parts = WsUtil.splitArgs(args);
        DerelictShipEntityPlugin.DerelictType size = null;
        List<String> rest = new ArrayList<>();
        for (String p : parts) {
            DerelictShipEntityPlugin.DerelictType t = size(p);
            if (t != null && size == null) size = t;
            else rest.add(p);
        }
        String query = WsUtil.join(rest, 0, rest.size());
        Random random = new Random();
        float sMods = DerelictShipEntityPlugin.getDefaultSModProb();

        DerelictShipEntityPlugin.DerelictShipData params;
        String from;
        ShipHullSpecAPI hull = query.isEmpty() ? null : findHull(query);
        if (hull != null) {
            params = DerelictShipEntityPlugin.createHull(hull.getHullId(), random, sMods);
            from = null;
        } else {
            FactionAPI faction = query.isEmpty() ? randomFaction(random) : findFaction(query);
            if (faction == null) {
                List<String> similar = similarHulls(query);
                WsUtil.print("No ship or faction called '" + query + "'."
                        + (similar.isEmpty() ? "" : " Ships with similar names: " + String.join(", ", similar) + ".")
                        + " Usage: " + USAGE);
                return CommandResult.ERROR;
            }
            params = DerelictShipEntityPlugin.createRandom(faction.getId(), size, random, sMods);
            if (params == null || params.ship == null || params.ship.variantId == null) {
                WsUtil.print(faction.getDisplayName() + " has no " + (size == null ? "" : sizeName(size) + " ")
                        + "ships to leave a wreck. Try another size or faction.");
                return CommandResult.ERROR;
            }
            from = faction.getDisplayNameWithArticle();
        }
        if (params == null || params.ship == null || params.ship.variantId == null) {
            WsUtil.print("The " + hull.getHullName() + " has no ready-made loadout, so it can't be placed as a wreck.");
            return CommandResult.ERROR;
        }

        SectorEntityToken focus = sys.getStar() != null ? sys.getStar() : sys.getCenter();
        float distance = Math.max(Misc.getDistance(focus.getLocation(), fleet.getLocation()), focus.getRadius() + 200f);
        float angle = Misc.getAngleInDegrees(focus.getLocation(), fleet.getLocation());
        SectorEntityToken wreck = BaseThemeGenerator.addSalvageEntity(random, sys, Entities.WRECK, Factions.NEUTRAL, params);
        wreck.setCircularOrbit(focus, angle, distance, distance / (20f + random.nextFloat() * 5f));
        // Visible right away and no discovery reward: you made it.
        wreck.setDiscoverable(null);
        wreck.setSensorProfile(null);
        wreck.setDiscoveryXP(null);
        WsUtil.invalidateCache();

        String shipName = params.ship.variantId;
        try {
            shipName = Global.getSettings().getVariant(params.ship.variantId).getHullSpec().getHullName();
        } catch (Exception ignored) {
            // keep the variant id
        }
        String condition = params.ship.condition == null ? "" : params.ship.condition.name().toLowerCase(Locale.ROOT) + " ";
        WsUtil.print("Added a derelict " + shipName + " (" + condition + "condition" + (from == null ? "" : ", a ship of "
                + from) + ") next to you in " + sys.getName() + ". Fly to it to salvage it, or recover the ship.");
        return CommandResult.SUCCESS;
    }

    private static DerelictShipEntityPlugin.DerelictType size(String word) {
        switch (word.toLowerCase(Locale.ROOT)) {
            case "small": case "frigate": return DerelictShipEntityPlugin.DerelictType.SMALL;
            case "medium": case "destroyer": return DerelictShipEntityPlugin.DerelictType.MEDIUM;
            case "large": case "cruiser": case "capital": return DerelictShipEntityPlugin.DerelictType.LARGE;
            case "civilian": return DerelictShipEntityPlugin.DerelictType.CIVILIAN;
            default: return null;
        }
    }

    private static String sizeName(DerelictShipEntityPlugin.DerelictType t) {
        return t.name().toLowerCase(Locale.ROOT);
    }

    /** Hulls that can be a wreck: real ships with at least one loadout. */
    private static List<ShipHullSpecAPI> wreckHulls() {
        List<ShipHullSpecAPI> out = new ArrayList<>();
        for (ShipHullSpecAPI h : Global.getSettings().getAllShipHullSpecs()) {
            if (h.getHullSize() == ShipAPI.HullSize.FIGHTER) continue;
            if (h.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)
                    || h.getHints().contains(ShipHullSpecAPI.ShipTypeHints.MODULE)) continue;
            List<String> variants = Global.getSettings().getHullIdToVariantListMap().get(h.getHullId());
            if (variants == null || variants.isEmpty()) continue;
            out.add(h);
        }
        return out;
    }

    private static ShipHullSpecAPI findHull(String query) {
        String q = WsUtil.norm(query);
        for (ShipHullSpecAPI h : wreckHulls()) {
            if (WsUtil.norm(h.getHullId()).equals(q) || WsUtil.norm(h.getHullName()).equals(q)) return h;
        }
        return null;
    }

    private static List<String> similarHulls(String query) {
        String q = WsUtil.norm(query);
        List<String> out = new ArrayList<>();
        for (ShipHullSpecAPI h : wreckHulls()) {
            if (WsUtil.norm(h.getHullName()).contains(q) || WsUtil.norm(h.getHullId()).contains(q)) {
                out.add(h.getHullName() + " (" + h.getHullId() + ")");
                if (out.size() >= 5) break;
            }
        }
        return out;
    }

    private static FactionAPI findFaction(String query) {
        String q = WsUtil.norm(query);
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (WsUtil.norm(f.getId()).equals(q) || WsUtil.norm(f.getDisplayName()).equals(q)) return f;
        }
        return CommandUtils.findBestFactionMatch(query.replace('_', ' '));
    }

    private static FactionAPI randomFaction(Random random) {
        WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<>(random);
        for (FactionAPI f : SectorDiplomacy.colonyFactions(false, true)) picker.add(f);
        FactionAPI indie = Global.getSector().getFaction(Factions.INDEPENDENT);
        if (indie != null) picker.add(indie);
        return picker.pick();
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        List<String> out = WsUtil.list("small", "medium", "large", "civilian");
        out.addAll(WsUtil.factionSuggestions());
        for (ShipHullSpecAPI h : wreckHulls()) out.add(h.getHullId());
        return out;
    }
}
