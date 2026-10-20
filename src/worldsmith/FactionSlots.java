package worldsmith;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtilsFaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The 8 blank faction slots this mod ships (data/world/factions/worldsmith_faction1-8). A slot is "spawned" once
 * spawnfaction has filled it; that state, its template faction and everything else live in the faction's own
 * memory and vanilla fields, so the save only ever holds plain data.
 */
public final class FactionSlots {
    public static final String ID_PREFIX = "worldsmith_faction";
    public static final String[] COLORS = {"red", "orange", "yellow", "green", "teal", "blue", "purple", "pink"};
    /** Set on a slot faction once spawnfaction has filled it. */
    public static final String MEM_SPAWNED = "$worldsmith_spawned";
    /** ID of the faction whose ships, weapons and doctrine the slot uses. */
    public static final String MEM_TEMPLATE = "$worldsmith_template";

    private FactionSlots() {
    }

    public static String slotId(int index) {
        return ID_PREFIX + (index + 1);
    }

    public static boolean isSlot(FactionAPI f) {
        return f != null && f.getId().startsWith(ID_PREFIX);
    }

    public static String colorOf(FactionAPI f) {
        for (int i = 0; i < COLORS.length; i++) {
            if (slotId(i).equals(f.getId())) return COLORS[i];
        }
        return "?";
    }

    public static boolean isSpawned(FactionAPI f) {
        return isSlot(f) && f.getMemoryWithoutUpdate().getBoolean(MEM_SPAWNED);
    }

    /** A slot nobody has filled yet: hidden, no name, no colonies. */
    public static boolean isUnused(FactionAPI f) {
        return isSlot(f) && !isSpawned(f);
    }

    public static boolean hasColonies(FactionAPI f) {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.getFaction() == f && !m.isPlanetConditionMarketOnly()) return true;
        }
        return false;
    }

    /** A slot is free if it was never used, or its faction has died out (no colonies left). */
    public static boolean isFree(FactionAPI f) {
        return isUnused(f) || (!hasColonies(f) && !SectorManager.isFactionAlive(f.getId()));
    }

    public static List<FactionAPI> slots() {
        List<FactionAPI> out = new ArrayList<>();
        for (int i = 0; i < COLORS.length; i++) {
            FactionAPI f = Global.getSector().getFaction(slotId(i));
            if (f != null) out.add(f);
        }
        return out;
    }

    public static FactionAPI byColor(String color) {
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i].equals(color.toLowerCase(Locale.ROOT))) return Global.getSector().getFaction(slotId(i));
        }
        return null;
    }

    public static boolean isColor(String word) {
        for (String c : COLORS) {
            if (c.equalsIgnoreCase(word)) return true;
        }
        return false;
    }

    /**
     * Nexerelin only treats "playable" factions as real factions (diplomacy, invasions, respawning). The slots are
     * shipped non-playable so unused ones stay out of every list; this switches it on for a spawned slot. The
     * setting lives in Nexerelin's config objects, not in the save, so it is re-applied on every game load.
     */
    public static void setPlayable(FactionAPI f, boolean playable) {
        NexFactionConfig conf = NexConfig.getFactionConfig(f.getId(), false);
        if (conf != null) conf.playableFaction = playable;
    }

    /** Ships, fighters, weapons, hullmods, priorities and doctrine of {@code from}. */
    public static void copyTech(FactionAPI from, FactionAPI to) {
        for (String s : new ArrayList<>(to.getKnownShips())) to.removeKnownShip(s);
        for (String s : from.getKnownShips()) to.addKnownShip(s, false);
        for (String s : new ArrayList<>(to.getKnownFighters())) to.removeKnownFighter(s);
        for (String s : from.getKnownFighters()) to.addKnownFighter(s, false);
        for (String s : new ArrayList<>(to.getKnownWeapons())) to.removeKnownWeapon(s);
        for (String s : from.getKnownWeapons()) to.addKnownWeapon(s, false);
        for (String s : new ArrayList<>(to.getKnownHullMods())) to.removeKnownHullMod(s);
        for (String s : from.getKnownHullMods()) to.addKnownHullMod(s);

        for (String s : new ArrayList<>(to.getPriorityShips())) to.removePriorityShip(s);
        for (String s : from.getPriorityShips()) to.addPriorityShip(s);
        for (String s : new ArrayList<>(to.getPriorityFighters())) to.removePriorityFighter(s);
        for (String s : from.getPriorityFighters()) to.addPriorityFighter(s);
        for (String s : new ArrayList<>(to.getPriorityWeapons())) to.removePriorityWeapon(s);
        for (String s : from.getPriorityWeapons()) to.addPriorityWeapon(s);
        for (String s : new ArrayList<>(to.getPriorityHullMods())) to.removePriorityHullMod(s);
        for (String s : from.getPriorityHullMods()) to.addPriorityHullMod(s);

        to.getHullFrequency().clear();
        to.getHullFrequency().putAll(from.getHullFrequency());
        from.getDoctrine().copyToDoctrine(to.getDoctrine());
        to.clearShipRoleCache();
    }

    /** Factions a new faction may copy ships and doctrine from: real factions with colonies, no pirates. */
    public static List<FactionAPI> templateCandidates() {
        List<FactionAPI> out = new ArrayList<>();
        for (String id : SectorManager.getLiveFactionIdsCopy()) {
            FactionAPI f = Global.getSector().getFaction(id);
            if (f == null || !isGoodTemplate(f)) continue;
            if (NexUtilsFaction.isPirateFaction(id)) continue;
            out.add(f);
        }
        return out;
    }

    public static boolean isGoodTemplate(FactionAPI f) {
        return f != null && !f.isPlayerFaction() && !f.isNeutralFaction() && !isSlot(f)
                && !Factions.PLAYER.equals(f.getId()) && !f.getKnownShips().isEmpty();
    }

    /**
     * Called on every game load. The game resets an NPC faction's doctrine (and, in dev mode, its known ships)
     * from its faction file when a save loads, and Nexerelin's config objects start fresh, so spawned slots get
     * their template's ships and doctrine and their Nexerelin settings back; unused slots stay inactive.
     */
    public static void onGameLoad() {
        for (FactionAPI f : slots()) {
            boolean spawned = isSpawned(f);
            setPlayable(f, spawned);
            if (!spawned) continue;
            MemoryAPI mem = f.getMemoryWithoutUpdate();
            String templateId = mem.getString(MEM_TEMPLATE);
            FactionAPI template = templateId == null ? null : Global.getSector().getFaction(templateId);
            if (template != null && isGoodTemplate(template)) {
                try {
                    copyTech(template, f);
                } catch (Exception ex) {
                    Global.getLogger(FactionSlots.class).warn("Worldsmith: could not refresh ships of " + f.getId(), ex);
                }
            }
        }
    }
}
