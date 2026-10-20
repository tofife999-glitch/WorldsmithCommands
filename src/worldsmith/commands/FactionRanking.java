package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.campaign.econ.GroundPoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.SectorDiplomacy;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Ranks every faction with colonies by size, colonies, industry, military, wars or invasion points. */
public class FactionRanking implements BaseCommandWithSuggestion {
    private static final String[] KEYS = {"size", "colonies", "industry", "military", "wars", "invasion"};

    private static final class Row {
        FactionAPI faction;
        int colonies;
        int size;
        int industry;
        int military;
        int stations;
        int wars;
        float invasion;
        float troops;

        float key(String k) {
            switch (k) {
                case "colonies": return colonies;
                case "industry": return industry;
                case "military": return military;
                case "wars": return wars;
                case "invasion": return invasion;
                default: return size;
            }
        }
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String key = args.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) key = "size";
        boolean known = false;
        for (String k : KEYS) known |= k.equals(key);
        if (!known) {
            WsUtil.print("Usage: factionranking [" + String.join("|", KEYS) + "]");
            return CommandResult.BAD_SYNTAX;
        }
        List<FactionAPI> factions = SectorDiplomacy.colonyFactions(true, true);
        Map<FactionAPI, Row> rows = new HashMap<>();
        for (FactionAPI f : factions) {
            Row r = new Row();
            r.faction = f;
            rows.put(f, r);
        }
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            Row r = rows.get(m.getFaction());
            if (r == null || m.isPlanetConditionMarketOnly() || m.isHidden()) continue;
            r.colonies++;
            r.size += m.getSize();
            for (Industry ind : m.getIndustries()) {
                if (ind.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY)) r.industry++;
                if (ind.getSpec().hasTag(Industries.TAG_MILITARY) || ind.getSpec().hasTag(Industries.TAG_PATROL)
                        || ind.getSpec().hasTag(Industries.TAG_COMMAND)) r.military++;
                if (ind.getSpec().hasTag(Industries.TAG_STATION)) r.stations++;
            }
        }
        InvasionFleetManager ifm = InvasionFleetManager.getManager();
        GroundPoolManager gpm = GroundPoolManager.getManager();
        for (Row r : rows.values()) {
            for (FactionAPI o : factions) {
                if (o != r.faction && SectorDiplomacy.atWar(r.faction, o)) r.wars++;
            }
            if (ifm != null) r.invasion = ifm.getSpawnCounter(r.faction.getId());
            if (gpm != null) r.troops = gpm.getCurrentPool(r.faction.getId());
        }
        List<Row> sorted = new ArrayList<>();
        for (Row r : rows.values()) {
            if (r.colonies > 0 || r.faction.isPlayerFaction()) sorted.add(r);
        }
        final String k = key;
        sorted.sort(Comparator.comparingDouble((Row r) -> -r.key(k)).thenComparing(r -> r.faction.getDisplayName()));
        StringBuilder sb = new StringBuilder(WsUtil.PREFIX + "Faction ranking by " + key + " ("
                + "other orders: " + String.join(", ", KEYS) + "):");
        int place = 0;
        for (Row r : sorted) {
            place++;
            sb.append("\n  ").append(place).append(". ").append(SectorDiplomacy.label(r.faction)).append(": ")
                    .append(r.colonies).append(r.colonies == 1 ? " colony" : " colonies").append(", total size ").append(r.size)
                    .append(", ").append(r.industry).append(" heavy industry, ").append(r.military).append(" military, ")
                    .append(r.stations).append(" stations, ").append(r.wars).append(r.wars == 1 ? " war" : " wars");
            if (r.invasion >= 1f) sb.append(", invasion points ").append(Math.round(r.invasion));
            if (r.troops >= 1f && r.troops < 1000000f) sb.append(", troops ").append(Math.round(r.troops));
        }
        Console.showMessage(sb.toString());
        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.list(KEYS);
        return WsUtil.list();
    }
}
