package worldsmith.commands;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.utilities.NexConfig;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.WsUtil;

import java.util.List;

/**
 * Sets or clears the market memory flag "$nex_npcColony" that Nexerelin puts on colonies founded by its colony
 * expeditions. Nex reads it for NPC growth (size cap + AI building on growth) and for reduced sat-bomb outrage.
 */
public class TreatAsNpcColony implements BaseCommandWithSuggestion {
    private static final String USAGE = "treatasnpccolony [colony] [off]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        boolean yes = WsUtil.endsWithYes(parts);
        if (yes) parts.remove(parts.size() - 1);
        boolean off = !parts.isEmpty() && parts.get(parts.size() - 1).equalsIgnoreCase("off");
        if (off) parts.remove(parts.size() - 1);
        final String name = WsUtil.join(parts, 0, parts.size());

        final SectorEntityToken target = WsUtil.resolveTarget(name, context, WsUtil.Kind.COLONY, "colony", USAGE);
        if (target == null) return CommandResult.ERROR;
        final MarketAPI market = target.getMarket();
        final String key = "treatasnpccolony:" + market.getId();

        if (off) {
            boolean was = market.getMemoryWithoutUpdate().contains(ColonyExpeditionIntel.MEMORY_KEY_COLONY);
            market.getMemoryWithoutUpdate().unset(ColonyExpeditionIntel.MEMORY_KEY_COLONY);
            WsUtil.cancelPending(key);
            WsUtil.print(market.getName() + " in " + WsUtil.systemName(target) + (was
                    ? " is no longer treated as an NPC expedition colony."
                    : " was not marked as an NPC expedition colony; nothing changed."));
            return CommandResult.SUCCESS;
        }

        final int limit = NexConfig.maxNPCNewColonySize;
        if (!market.isPlayerOwned() && market.getSize() > limit
                && !market.getMemoryWithoutUpdate().contains(PopCap.MEM_GROWTH_LIMIT)) {
            if (yes) {
                WsUtil.cancelPending(key);
            } else {
                String question = market.getName() + " (" + WsUtil.systemName(target) + ") is size "
                        + market.getSize() + ". Colonies like this normally stop growing at size " + limit
                        + " (Nexerelin's maxNPCNewColonySize setting). It keeps its current size but won't grow any"
                        + " more. Treat it as an NPC colony anyway?";
                WsUtil.print(question);
                String retype = "treatasnpccolony " + (name.isEmpty() ? market.getId() : name) + " yes";
                WsUtil.confirm(key, question, retype, new Script() {
                    @Override
                    public void run() {
                        WsUtil.runSafely("treatasnpccolony", new Runnable() {
                            @Override
                            public void run() {
                                WsUtil.report(apply(target, market));
                            }
                        });
                    }
                });
                return CommandResult.SUCCESS;
            }
        }
        WsUtil.print(apply(target, market));
        return CommandResult.SUCCESS;
    }

    private static String apply(SectorEntityToken target, MarketAPI market) {
        market.getMemoryWithoutUpdate().set(ColonyExpeditionIntel.MEMORY_KEY_COLONY, true);
        String extra;
        if (market.isPlayerOwned()) {
            extra = " (Player-owned: Nex's NPC growth cap and AI building don't apply; use popcap to cap it.)";
        } else if (market.getMemoryWithoutUpdate().contains(PopCap.MEM_GROWTH_LIMIT)) {
            extra = " Its popcap (" + market.getMemoryWithoutUpdate().getLong(PopCap.MEM_GROWTH_LIMIT)
                    + ") overrides Nex's NPC colony size limit.";
        } else {
            extra = " Nex NPC growth limit: size " + NexConfig.maxNPCNewColonySize + ".";
        }
        return market.getName() + " in " + WsUtil.systemName(target) + " (" + market.getFaction().getDisplayName()
                + ", size " + market.getSize() + ") is now treated as an NPC expedition colony." + extra;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        List<String> l = parameter == 0 ? WsUtil.bodySuggestions(WsUtil.Kind.COLONY) : WsUtil.list();
        l.add("off");
        return l;
    }
}
