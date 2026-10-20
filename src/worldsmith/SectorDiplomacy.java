package worldsmith;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsReputation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Sector-wide war and peace, done through Nexerelin's own bookkeeping where it has some. */
public final class SectorDiplomacy {
    /** Relationship set for total war: hostile with a margin, like Nexerelin's own war declarations. */
    public static final float WAR = DiplomacyManager.RELATIONSHIP_HOSTILE_WITH_MARGIN;

    private SectorDiplomacy() {
    }

    /**
     * Factions that own colonies (Nexerelin's live factions plus independents and anyone else with a visible
     * market), sorted by name. The player's faction only with {@code withPlayer}, pirate factions only with
     * {@code withPirates}.
     */
    public static List<FactionAPI> colonyFactions(boolean withPlayer, boolean withPirates) {
        Set<String> ids = new LinkedHashSet<>(SectorManager.getLiveFactionIdsCopy());
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!m.isHidden() && !m.isPlanetConditionMarketOnly()) ids.add(m.getFactionId());
        }
        if (withPlayer) ids.add(Factions.PLAYER);
        ids.remove(Factions.NEUTRAL);
        List<FactionAPI> out = new ArrayList<>();
        for (String id : ids) {
            FactionAPI f = Global.getSector().getFaction(id);
            if (f == null) continue;
            if (f.isPlayerFaction() && !withPlayer) continue;
            if (!withPirates && NexUtilsFaction.isPirateFaction(id)) continue;
            out.add(f);
        }
        out.sort(Comparator.comparing(FactionAPI::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public static boolean atWar(FactionAPI a, FactionAPI b) {
        return a.isHostileTo(b);
    }

    /** Display name, with "you" for your own faction (whose plain display name is just "Your"). */
    public static String label(FactionAPI f) {
        if (!f.isPlayerFaction()) return f.getDisplayName();
        return f.getDisplayNameOverride() == null ? "you" : "you (" + f.getDisplayName() + ")";
    }

    /** Your employer's ID if you hold a commission, else null. */
    public static String commissioner() {
        String id = Misc.getCommissionFactionId();
        return id == null || Factions.PLAYER.equals(id) ? null : id;
    }

    /** Dissolves every Nexerelin alliance. Returns how many there were. */
    public static int dissolveAlliances() {
        List<Alliance> all = AllianceManager.getAllianceList();
        for (Alliance a : all) AllianceManager.getManager().dissolveAlliance(a, true);
        return all.size();
    }

    /** Every pair hostile. Returns how many pairs went to war. */
    public static int totalWar(List<FactionAPI> factions) {
        int wars = 0;
        for (int i = 0; i < factions.size(); i++) {
            for (int j = i + 1; j < factions.size(); j++) {
                FactionAPI a = factions.get(i);
                FactionAPI b = factions.get(j);
                if (a.getRelationship(b.getId()) <= WAR) continue;
                boolean was = a.isHostileTo(b);
                a.setRelationship(b.getId(), WAR);
                if (!was) wars++;
            }
        }
        DiplomacyManager.getManager().setLastWarTimestamp(Global.getSector().getClock().getTimestamp());
        afterChanges();
        return wars;
    }

    /**
     * Every pair at least neutral. Factions that were at war get Nexerelin's usual ceasefire, so they don't
     * declare war again right away. Returns how many wars ended.
     */
    public static int totalPeace(List<FactionAPI> factions) {
        int ended = 0;
        for (int i = 0; i < factions.size(); i++) {
            for (int j = i + 1; j < factions.size(); j++) {
                FactionAPI a = factions.get(i);
                FactionAPI b = factions.get(j);
                if (a.getRelationship(b.getId()) >= 0f) continue;
                boolean was = a.isHostileTo(b);
                a.setRelationship(b.getId(), 0f);
                if (!was) continue;
                ended++;
                if (SectorManager.isFactionAlive(a.getId()) && SectorManager.isFactionAlive(b.getId())) {
                    DiplomacyManager.getManager().getDiplomacyBrain(a.getId()).addCeasefire(b.getId());
                    DiplomacyManager.getManager().getDiplomacyBrain(b.getId()).addCeasefire(a.getId());
                }
            }
        }
        afterChanges();
        return ended;
    }

    /** If you hold a commission, Nexerelin keeps your relations in step with your employer's. */
    private static void afterChanges() {
        if (NexConfig.syncPlayerRelationsWithCommisioner) NexUtilsReputation.syncPlayerRelationshipsToFaction();
    }
}
