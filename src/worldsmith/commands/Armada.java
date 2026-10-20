package worldsmith.commands;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.Armadas;
import worldsmith.FactionSlots;
import worldsmith.WsUtil;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Sends a faction's war fleet into a star system: it arrives at a jump point, attacks the biggest colony
 * there and raids the system for about two months, then withdraws. If that faction isn't at war with the colony's
 * owner, the armada alone is made hostile to them for the attack (no war is declared).
 */
public class Armada implements BaseCommandWithSuggestion {
    private static final String USAGE = "armada <faction> [system] [small|medium|large]";

    private static float fleetPoints(String size) {
        switch (size) {
            case "small": return 120f;
            case "large": return 350f;
            default: return 220f;
        }
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        if (parts.isEmpty()) {
            WsUtil.print("Usage: " + USAGE + "   (without a system: the one you are in)");
            return CommandResult.BAD_SYNTAX;
        }
        String size = "medium";
        String last = parts.get(parts.size() - 1).toLowerCase(Locale.ROOT);
        if (parts.size() > 1 && (last.equals("small") || last.equals("medium") || last.equals("large"))) {
            size = last;
            parts.remove(parts.size() - 1);
        }
        FactionAPI faction = WsUtil.parseFaction(parts.remove(0));
        if (faction == null) return CommandResult.ERROR;
        if (FactionSlots.isUnused(faction) || faction.isNeutralFaction() || faction.getKnownShips().isEmpty()) {
            WsUtil.print(faction.getDisplayName() + " has no ships of its own to send.");
            return CommandResult.ERROR;
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
        MarketAPI target = Armadas.biggestColony(sys);
        String name = faction.getDisplayName() + " Armada";
        CampaignFleetAPI fleet = Armadas.launch(faction, sys, target, fleetPoints(size), FleetTypes.TASK_FORCE, name,
                60f, false, new Random());
        if (fleet == null) {
            WsUtil.print("The game could not put together a fleet for " + faction.getDisplayName() + ".");
            return CommandResult.ERROR;
        }
        String goal = target == null ? "raids " + sys.getName()
                : "attacks " + target.getName() + " (" + target.getFaction().getDisplayName() + ") and raids " + sys.getName();
        String note = "";
        if (target != null && target.getFaction() == faction) {
            goal = "guards its own colony " + target.getName() + " and patrols " + sys.getName();
        } else if (target != null && !faction.isHostileTo(target.getFaction())) {
            // Hostile for this attack only, the way vanilla lets mission fleets turn on a faction: no war is declared.
            Misc.makeHostileToFaction(fleet, target.getFactionId(), 90f);
            note = " " + faction.getDisplayNameWithArticle() + " is not at war with " + target.getFaction().getDisplayNameWithArticle()
                    + "; only this armada is hostile to them, no war is declared.";
        }
        WsUtil.print("The " + name + " (" + fleet.getFleetData().getNumMembers() + " ships, " + size + ") jumped into "
                + sys.getName() + " and " + goal + " for about two months, then withdraws." + note);
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.factionSuggestions();
        List<String> l = WsUtil.systemSuggestions();
        l.addAll(WsUtil.list("small", "medium", "large"));
        return l;
    }
}
