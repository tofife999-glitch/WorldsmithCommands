package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.rebellion.RebellionCreator;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.NexConfig;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.Armadas;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Three ways to end the world as the sector knows it. Your own colonies are spared unless you add 'all'.
 *   remnant     - a Remnant armada attacks every inhabited system for about two months, then withdraws
 *   bombard     - every colony is saturation-bombed into ruins (story-critical worlds survive)
 *   revolution  - Nexerelin rebellions break out in every colony of size 3 or more at once
 */
public class Apocalypse implements BaseCommandWithSuggestion {
    private static final String USAGE = "apocalypse <remnant|bombard|revolution> [all] [yes]";
    /** Upper limit for one armada, and for all armadas together (more systems make each one smaller). */
    private static final float MAX_FP = 600f;
    private static final float TOTAL_FP = 25000f;

    private enum Kind { REMNANT, BOMBARD, REVOLUTION }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        Kind kind = null;
        boolean all = false;
        boolean yes = false;
        for (String w : WsUtil.splitArgs(args)) {
            String l = w.toLowerCase(Locale.ROOT);
            if (l.equals("yes")) yes = true;
            else if (l.equals("all")) all = true;
            else if (l.equals("remnant") || l.equals("remnants")) kind = Kind.REMNANT;
            else if (l.equals("bombard") || l.equals("bombardment")) kind = Kind.BOMBARD;
            else if (l.equals("revolution") || l.equals("rebellion")) kind = Kind.REVOLUTION;
            else {
                WsUtil.print("Unknown option '" + w + "'. Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
        }
        if (kind == null) {
            WsUtil.print("Usage: " + USAGE + ". Three ways to end the world (your own colonies are spared unless you add 'all'):");
            WsUtil.printRaw("  remnant     a Remnant armada attacks every inhabited system for about two months, then withdraws");
            WsUtil.printRaw("  bombard     every colony is saturation-bombed into ruins (story-critical worlds survive); can't be undone");
            WsUtil.printRaw("  revolution  Nexerelin rebellions break out in every colony of size 3 or more at once");
            return CommandResult.BAD_SYNTAX;
        }
        final Kind k = kind;
        final boolean includePlayer = all;
        List<MarketAPI> targets = targets(all);
        if (targets.isEmpty()) {
            WsUtil.print("There are no colonies to hit.");
            return CommandResult.ERROR;
        }
        String question;
        switch (kind) {
            case REMNANT:
                question = "APOCALYPSE (Remnant): a Remnant armada jumps into each of the " + bySystem(targets).size()
                        + " inhabited systems and attacks their fleets and stations for about two months.";
                break;
            case BOMBARD:
                question = "APOCALYPSE (bombardment): " + targets.size() + " colonies are saturation-bombed into ruins."
                        + " Story-critical worlds survive. This can't be undone.";
                break;
            default:
                question = "APOCALYPSE (revolution): rebellions break out in every colony of size 3 or more ("
                        + targets.size() + " colonies checked). Rebels can win and take colonies over.";
                break;
        }
        question += includePlayer ? " Your own colonies are included!" : " Your own colonies are spared (add 'all' to include them).";
        if (yes) {
            if (isDialogOpen()) {
                WsUtil.print("Leave the open dialog first, then run the command again.");
                return CommandResult.ERROR;
            }
            WsUtil.cancelPending("apocalypse");
            WsUtil.print(run(k, includePlayer));
            return CommandResult.SUCCESS;
        }
        WsUtil.print(question);
        WsUtil.confirm("apocalypse", question + " Continue?",
                "apocalypse " + kind.name().toLowerCase(Locale.ROOT) + (includePlayer ? " all" : "") + " yes",
                new Script() {
                    @Override
                    public void run() {
                        WsUtil.runSafely("apocalypse", new Runnable() {
                            @Override
                            public void run() {
                                WsUtil.report(Apocalypse.run(k, includePlayer));
                            }
                        });
                    }
                });
        return CommandResult.SUCCESS;
    }

    private static boolean isDialogOpen() {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        return ui != null && ui.getCurrentInteractionDialog() != null;
    }

    private static boolean isPlayers(MarketAPI m) {
        return m.isPlayerOwned() || m.getFaction().isPlayerFaction();
    }

    /** Visible colonies (no secret bases), without yours unless {@code all}. */
    private static List<MarketAPI> targets(boolean all) {
        List<MarketAPI> out = new ArrayList<>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.isPlanetConditionMarketOnly() || m.isHidden() || m.getPrimaryEntity() == null) continue;
            if (Factions.NEUTRAL.equals(m.getFactionId())) continue;
            if (!all && isPlayers(m)) continue;
            out.add(m);
        }
        return out;
    }

    private static Map<StarSystemAPI, List<MarketAPI>> bySystem(List<MarketAPI> markets) {
        Map<StarSystemAPI, List<MarketAPI>> out = new LinkedHashMap<>();
        for (MarketAPI m : markets) {
            if (!(m.getContainingLocation() instanceof StarSystemAPI)) continue;
            StarSystemAPI s = (StarSystemAPI) m.getContainingLocation();
            if (!out.containsKey(s)) out.put(s, new ArrayList<MarketAPI>());
            out.get(s).add(m);
        }
        return out;
    }

    private static String run(Kind kind, boolean all) {
        List<MarketAPI> targets = targets(all);
        switch (kind) {
            case REMNANT: return remnant(targets);
            case BOMBARD: return bombard(targets);
            default: return revolution(targets);
        }
    }

    // ------------------------------------------------------------------ remnant

    private static String remnant(List<MarketAPI> targets) {
        Random random = new Random();
        Map<StarSystemAPI, List<MarketAPI>> systems = bySystem(targets);
        Map<StarSystemAPI, Float> want = new LinkedHashMap<>();
        float total = 0f;
        for (Map.Entry<StarSystemAPI, List<MarketAPI>> e : systems.entrySet()) {
            int sizes = 0;
            for (MarketAPI m : e.getValue()) sizes += m.getSize();
            float fp = Math.min(MAX_FP, 150f + 30f * sizes);
            want.put(e.getKey(), fp);
            total += fp;
        }
        // Keeps a huge sector from spawning more ships than the game handles well.
        float mult = total > TOTAL_FP ? TOTAL_FP / total : 1f;
        int fleets = 0;
        int ships = 0;
        for (Map.Entry<StarSystemAPI, List<MarketAPI>> e : systems.entrySet()) {
            MarketAPI biggest = null;
            for (MarketAPI m : e.getValue()) {
                if (biggest == null || m.getSize() > biggest.getSize()) biggest = m;
            }
            CampaignFleetAPI fleet = Armadas.launch(Global.getSector().getFaction(Factions.REMNANTS), e.getKey(), biggest,
                    want.get(e.getKey()) * mult, FleetTypes.PATROL_LARGE, "Remnant Armada", 60f, true, random);
            if (fleet == null) continue;
            fleets++;
            ships += fleet.getFleetData().getNumMembers();
        }
        return "Elite Remnant armadas (alpha-core captains, no damage mods) attack " + fleets + " inhabited systems ("
                + ships + " ships in total). They raid for about two months, then withdraw and vanish.";
    }

    // ------------------------------------------------------------------ bombard

    private static String bombard(List<MarketAPI> targets) {
        int destroyed = 0;
        List<String> spared = new ArrayList<>();
        for (MarketAPI m : targets) {
            if (Misc.isStoryCritical(m)) {
                spared.add(m.getName());
                continue;
            }
            // What a saturation bombardment that destroys a colony does, without the reputation hit for you.
            if (m.hasCondition(Conditions.HABITABLE) && !m.hasCondition(Conditions.POLLUTION)) m.addCondition(Conditions.POLLUTION);
            DecivTracker.decivilize(m, true, false);
            destroyed++;
        }
        WsUtil.invalidateCache();
        return "Saturation-bombed " + destroyed + " colonies into ruins." + (spared.isEmpty() ? ""
                : " Spared " + spared.size() + " story-critical worlds: " + String.join(", ", spared) + ".");
    }

    // ------------------------------------------------------------------ revolution

    private static String revolution(List<MarketAPI> targets) {
        RebellionCreator creator = RebellionCreator.getInstance();
        if (!NexConfig.enableInvasions || creator == null) {
            return "Nexerelin's rebellions are switched off in this game (they need invasions enabled), so nothing happened.";
        }
        int started = 0;
        int already = 0;
        int quiet = 0;
        for (MarketAPI m : targets) {
            if (m.getSize() < 3 || RebellionCreator.NO_REBEL_ON_FACTIONS.contains(m.getFactionId())) continue;
            if (RebellionIntel.isOngoing(m)) {
                already++;
                continue;
            }
            // Nexerelin picks the rebels' backer from the colony's enemies; with no enemies, they fight for independence.
            RebellionIntel r = creator.createRebellion(m, true);
            if (r == null && !Factions.INDEPENDENT.equals(m.getFactionId()))
                r = creator.createRebellion(m, Factions.INDEPENDENT, true);
            if (r != null) started++;
            else quiet++;
        }
        return "Revolution: rebellions broke out in " + started + " colonies" + (already > 0 ? " (" + already
                + " were already rebelling)" : "") + (quiet > 0 ? "; " + quiet + " stayed quiet (nobody to rebel for)" : "")
                + ". Follow them in the Intel screen; rebels that win take the colony over.";
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.list("remnant", "bombard", "revolution");
        return WsUtil.list("all", "yes");
    }
}
