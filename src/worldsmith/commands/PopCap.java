package worldsmith.commands;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.FactionEffects;
import worldsmith.WsUtil;

import java.util.List;

public class PopCap implements BaseCommandWithSuggestion {
    /** Read by Nexerelin's NPC colony growth with getLong (ColonyManager.MEMORY_KEY_GROWTH_LIMIT). */
    public static final String MEM_GROWTH_LIMIT = "$nex_colony_growth_limit";
    private static final String USAGE = "popcap [colony] <size|off>";

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
        String last = parts.remove(parts.size() - 1);
        boolean off = last.equalsIgnoreCase("off");
        if (!off && !WsUtil.isInt(last)) {
            WsUtil.print("The last word must be a size or 'off'. Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        int cap = off ? 0 : Integer.parseInt(last);
        int maxCap = Math.max(10, Misc.MAX_COLONY_SIZE);
        if (!off && (cap < 1 || cap > maxCap)) {
            WsUtil.print("Size cap must be between 1 and " + maxCap + ".");
            return CommandResult.BAD_SYNTAX;
        }
        SectorEntityToken target = WsUtil.resolveTarget(WsUtil.join(parts, 0, parts.size()), context,
                WsUtil.Kind.COLONY, "colony", USAGE);
        if (target == null) return CommandResult.ERROR;
        MarketAPI market = target.getMarket();
        StatBonus mod = market.getStats().getDynamic().getMod(Stats.MAX_MARKET_SIZE);

        // An explicit cap replaces any timed Worldsmith growth freeze (both use Nex's growth limit key).
        market.getMemoryWithoutUpdate().unset(FactionEffects.MEM_FREEZE);
        if (off) {
            market.getMemoryWithoutUpdate().unset(MEM_GROWTH_LIMIT);
            mod.unmodify(WsUtil.MOD_SOURCE);
            WsUtil.print("Removed the population cap of " + market.getName() + " in " + WsUtil.systemName(target)
                    + ". Max size is now " + Misc.getMaxMarketSize(market) + " (current size " + market.getSize() + ").");
            return CommandResult.SUCCESS;
        }

        market.getMemoryWithoutUpdate().set(MEM_GROWTH_LIMIT, Long.valueOf(cap));
        mod.unmodify(WsUtil.MOD_SOURCE);
        float base = Misc.MAX_COLONY_SIZE;
        float before = mod.computeEffective(base);
        float delta = cap - before;
        mod.modifyFlatAlways(WsUtil.MOD_SOURCE, delta, "Population cap (Worldsmith Console Commands)");
        float after = mod.computeEffective(base);
        // Other mods may apply percent/mult modifiers; scale the flat part so the result still lands on the cap.
        if (Math.round(after) != cap && delta != 0f) {
            float perUnit = (after - before) / delta;
            if (perUnit > 0f) {
                mod.modifyFlatAlways(WsUtil.MOD_SOURCE, (cap - before) / perUnit,
                        "Population cap (Worldsmith Console Commands)");
            }
        }
        int max = Misc.getMaxMarketSize(market);
        String note = market.getSize() > cap ? " It stays at its current size " + market.getSize()
                + " (popcap never shrinks colonies)." : "";
        WsUtil.print("Capped " + market.getName() + " in " + WsUtil.systemName(target) + " at size " + cap
                + ". Max size is now " + max + "." + note);
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        List<String> l = parameter == 0 ? WsUtil.bodySuggestions(WsUtil.Kind.COLONY) : WsUtil.list();
        l.addAll(WsUtil.list("off", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
        return l;
    }
}
