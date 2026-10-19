package worldsmith.commands;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsMarket;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.WsUtil;

import java.util.List;

/**
 * Sends a real Nexerelin colony expedition, set up the same way as ColonyManager.spawnColonyExpedition(),
 * but with a chosen faction and target. The expedition is Nexerelin's own intel object.
 */
public class NpcExpedition implements BaseCommandWithSuggestion {
    private static final String USAGE = "npcexpedition [planet] <faction>";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        if (parts.isEmpty()) {
            WsUtil.print("Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        String factionToken = parts.remove(parts.size() - 1);
        SectorEntityToken target = WsUtil.resolveTarget(WsUtil.join(parts, 0, parts.size()), context,
                WsUtil.Kind.UNCOLONIZED, "uncolonized planet", USAGE);
        if (target == null) return CommandResult.ERROR;
        FactionAPI faction = WsUtil.parseFaction(factionToken);
        if (faction == null) return CommandResult.ERROR;
        if (faction.isPlayerFaction()) {
            WsUtil.print("Expeditions are for NPC factions. For your own faction use: npccolony "
                    + target.getName() + " player");
            return CommandResult.ERROR;
        }

        PlanetAPI planet = (PlanetAPI) target;
        MarketAPI market = planet.getMarket();
        if (market == null || !market.isPlanetConditionMarketOnly() || planet.getStarSystem() == null) {
            WsUtil.print(planet.getName() + " can't be the target of a colony expedition (no uncolonized planet"
                    + " market, or not in a star system).");
            return CommandResult.ERROR;
        }
        ColonyManager manager = ColonyManager.getManager();
        if (manager == null) {
            WsUtil.print("Nexerelin's colony manager is not running in this save.");
            return CommandResult.ERROR;
        }
        if (manager.getExistingColonyTargets().contains(planet)) {
            WsUtil.print(planet.getName() + " is already the target of a colony expedition (see the intel screen).");
            return CommandResult.ERROR;
        }

        MarketAPI source = nearestSource(faction, planet);
        if (source == null) {
            WsUtil.print(WsUtil.factionLabel(faction) + " has no colony to send an expedition from.");
            return CommandResult.ERROR;
        }

        // Same numbers as ColonyManager.spawnColonyExpedition().
        float fp = 80 + 30 * manager.getNumDeadExpeditions();
        if (fp > ColonyManager.MAX_EXPEDITION_FP) fp = ColonyManager.MAX_EXPEDITION_FP;
        float organizeTime = InvasionFleetManager.getOrganizeTime(fp + 30) + 30;

        ColonyExpeditionIntel intel = new ColonyExpeditionIntel(faction, source, market, fp, organizeTime);
        intel.init();
        if (intel.isEnding() || intel.isEnded()) {
            WsUtil.print("Nexerelin cancelled the expedition right away: it found no usable jump point to reach "
                    + planet.getName() + " in " + WsUtil.systemName(planet) + ".");
            return CommandResult.ERROR;
        }
        float ly = Misc.getDistanceLY(source.getLocationInHyperspace(), planet.getLocationInHyperspace());
        WsUtil.print("Sent a " + WsUtil.factionLabel(faction) + " colony expedition (" + Math.round(fp) + " FP) from "
                + source.getName() + " (" + Math.round(ly) + " LY away) to " + planet.getName() + " in "
                + WsUtil.systemName(planet) + "; it organizes for about " + Math.round(organizeTime) + " days.");
        return CommandResult.SUCCESS;
    }

    /** Nearest colony of the faction, preferring ones with a working spaceport, then ones that aren't hidden. */
    private static MarketAPI nearestSource(FactionAPI faction, PlanetAPI planet) {
        MarketAPI best = null;
        float bestScore = Float.MAX_VALUE;
        for (MarketAPI m : NexUtilsFaction.getFactionMarkets(faction.getId())) {
            if (m.getPrimaryEntity() == null) continue;
            float d = Misc.getDistanceLY(m.getLocationInHyperspace(), planet.getLocationInHyperspace());
            float score = d;
            if (!NexUtilsMarket.hasWorkingSpaceport(m)) score += 100000f;
            if (m.isHidden()) score += 10000f;
            if (score < bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) {
            List<String> l = WsUtil.bodySuggestions(WsUtil.Kind.UNCOLONIZED);
            l.addAll(WsUtil.factionSuggestions());
            return l;
        }
        return WsUtil.factionSuggestions();
    }
}
