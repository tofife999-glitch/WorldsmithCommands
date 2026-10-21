package worldsmith;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionDoctrineAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtilsFaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The character of a faction made with spawnfaction: which ships it builds, how its fleets fight, and how it
 * behaves (warlike, peaceful, pirate, expansionist...). Every choice is kept as plain text in the faction's memory
 * and applied again whenever a save loads, because the game resets doctrines and Nexerelin its settings on load.
 */
public final class FactionCharacter {
    public static final String MEM_SHIPS = "$worldsmith_ships";
    public static final String MEM_DOCTRINE = "$worldsmith_doctrine";
    public static final String MEM_PIRATE = "$worldsmith_pirate";
    public static final String MEM_EXPANSION = "$worldsmith_expansion";

    public static final List<String> SHIP_STYLES = Arrays.asList("lowtech", "midline", "hightech", "pirate", "all");
    public static final List<String> DOCTRINES = Arrays.asList(
            "aggressive", "cautious", "carriers", "phase", "warships", "elite", "swarm", "capitals", "reset");
    public static final List<String> PERSONALITIES = Arrays.asList(
            "warlike", "peaceful", "pirate", "lawful", "expansionist", "isolationist", "normal");

    /** Nexerelin diplomacy traits the personalities switch on or off. */
    private static final List<String> WARLIKE = Arrays.asList("paranoid", "temperamental", "stalwart");
    private static final List<String> PEACEFUL = Arrays.asList("pacifist", "weak-willed", "neutralist");
    private static final List<String> MANAGED = Arrays.asList(
            "paranoid", "temperamental", "stalwart", "predatory", "foreverwar", "pacifist", "weak-willed", "neutralist");
    /** What the slots' faction files make illegal; restored when a faction stops being a pirate faction. */
    private static final List<String> NORMAL_ILLEGAL = Arrays.asList("drugs", "organs");

    private FactionCharacter() {
    }

    /** The faction named by the token if it was made with spawnfaction; otherwise prints why not and returns null. */
    public static FactionAPI spawnedFaction(String token) {
        FactionAPI f = WsUtil.parseFaction(token);
        if (f == null) return null;
        if (!FactionSlots.isSpawned(f)) {
            WsUtil.print(f.getDisplayName() + " wasn't made with spawnfaction. Only those factions can be customized:"
                    + " their settings are kept in the save and applied again whenever it loads.");
            return null;
        }
        return f;
    }

    public static String doctrineSummary(FactionDoctrineAPI d) {
        return "warships " + d.getWarships() + ", carriers " + d.getCarriers() + ", phase " + d.getPhaseShips()
                + "; officers " + d.getOfficerQuality() + ", ship quality " + d.getShipQuality() + ", fleet size "
                + d.getNumShips() + "; ship size " + d.getShipSize() + ", aggression " + d.getAggression() + " (1-5 each)";
    }

    // ------------------------------------------------------------------ ships

    /** Everything the factions with colonies (and the independents) know, of one kind. */
    private static Set<String> pool(String kind) {
        Set<String> out = new LinkedHashSet<>();
        List<FactionAPI> sources = new ArrayList<>(SectorDiplomacy.colonyFactions(false, true));
        FactionAPI indie = Global.getSector().getFaction(Factions.INDEPENDENT);
        if (indie != null) sources.add(indie);
        for (FactionAPI f : sources) {
            if (FactionSlots.isSlot(f)) continue;
            switch (kind) {
                case "ships": out.addAll(f.getKnownShips()); break;
                case "weapons": out.addAll(f.getKnownWeapons()); break;
                case "fighters": out.addAll(f.getKnownFighters()); break;
                default: out.addAll(f.getKnownHullMods()); break;
            }
        }
        return out;
    }

    private static boolean fitsStyle(ShipHullSpecAPI hull, String style) {
        String m = hull.getManufacturer() == null ? "" : hull.getManufacturer().toLowerCase(Locale.ROOT);
        switch (style) {
            case "lowtech": return m.contains("low tech");
            case "midline": return m.contains("midline");
            case "hightech": return m.contains("high tech");
            case "pirate": return m.contains("pirate") || m.contains("low tech");
            default: return true;
        }
    }

    /** The hulls a style allows. */
    public static Set<String> styleShips(String style) {
        Set<String> out = new LinkedHashSet<>();
        for (String id : pool("ships")) {
            ShipHullSpecAPI hull = Global.getSettings().getHullSpec(id);
            if (hull != null && fitsStyle(hull, style)) out.add(id);
        }
        return out;
    }

    /**
     * Only the ships of that style, plus every weapon, fighter and hullmod the sector's factions use (so the
     * autofit can fill any mount). Returns how many hulls, or 0 if too few exist to build fleets from.
     */
    public static int applyShips(FactionAPI f, String style) {
        Set<String> ships = styleShips(style);
        if (ships.size() < 5) return 0;
        for (String s : new ArrayList<>(f.getKnownShips())) f.removeKnownShip(s);
        for (String s : ships) f.addKnownShip(s, false);
        for (String s : pool("weapons")) f.addKnownWeapon(s, false);
        for (String s : pool("fighters")) f.addKnownFighter(s, false);
        for (String s : pool("hullmods")) f.addKnownHullMod(s);
        for (String s : new ArrayList<>(f.getPriorityShips())) f.removePriorityShip(s);
        f.getHullFrequency().clear();
        f.clearShipRoleCache();
        return ships.size();
    }

    // ------------------------------------------------------------------ doctrine

    /** Presets that set the same thing replace each other. */
    private static String group(String preset) {
        switch (preset) {
            case "aggressive": case "cautious": return "aggression";
            case "carriers": case "phase": case "warships": return "composition";
            case "elite": case "swarm": return "quality";
            default: return preset;
        }
    }

    private static void applyPreset(FactionDoctrineAPI d, String preset) {
        switch (preset) {
            case "aggressive": d.setAggression(5); break;
            case "cautious": d.setAggression(1); break;
            case "carriers": d.setWarships(2); d.setCarriers(4); d.setPhaseShips(1); break;
            case "phase": d.setWarships(2); d.setCarriers(1); d.setPhaseShips(4); break;
            case "warships": d.setWarships(5); d.setCarriers(1); d.setPhaseShips(1); break;
            case "elite": d.setOfficerQuality(5); d.setShipQuality(5); d.setNumShips(1); break;
            case "swarm": d.setOfficerQuality(1); d.setShipQuality(1); d.setNumShips(5); d.setShipSize(1); break;
            case "capitals": d.setShipSize(5); break;
            default: break;
        }
    }

    public static List<String> doctrinePresets(FactionAPI f) {
        String s = f.getMemoryWithoutUpdate().getString(MEM_DOCTRINE);
        List<String> out = new ArrayList<>();
        if (s != null) for (String p : s.split(",")) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }

    /** Adds a preset (replacing one that sets the same thing) and applies the list on top of the template's doctrine. */
    public static void addDoctrinePreset(FactionAPI f, String preset) {
        List<String> list = doctrinePresets(f);
        if (preset.equals("reset")) {
            list.clear();
        } else {
            for (String p : new ArrayList<>(list)) if (group(p).equals(group(preset))) list.remove(p);
            list.add(preset);
        }
        if (list.isEmpty()) f.getMemoryWithoutUpdate().unset(MEM_DOCTRINE);
        else f.getMemoryWithoutUpdate().set(MEM_DOCTRINE, String.join(",", list));
        FactionAPI template = FactionSlots.template(f);
        if (template != null) template.getDoctrine().copyToDoctrine(f.getDoctrine());
        applyDoctrine(f);
    }

    public static void applyDoctrine(FactionAPI f) {
        for (String p : doctrinePresets(f)) applyPreset(f.getDoctrine(), p);
        f.clearShipRoleCache();
    }

    // ------------------------------------------------------------------ personality

    public static String temperament(FactionAPI f) {
        Set<String> added = DiplomacyTraits.getAddedTraits(f.getId());
        if (added.containsAll(WARLIKE)) return "warlike";
        if (added.containsAll(PEACEFUL)) return "peaceful";
        return "normal";
    }

    /** Warlike or peaceful through Nexerelin's diplomacy traits; null clears both. */
    private static void setTemperament(FactionAPI f, List<String> traits) {
        for (String t : MANAGED) {
            DiplomacyTraits.removeAddedTrait(f.getId(), t);
            DiplomacyTraits.removeRemovedTrait(f.getId(), t);
        }
        if (traits == null) return;
        for (String t : traits) DiplomacyTraits.addAddedTrait(f.getId(), t);
        // Traits that would fight the new ones (from Nexerelin's random traits) are switched off.
        for (String t : MANAGED) {
            if (!traits.contains(t)) DiplomacyTraits.addRemovedTrait(f.getId(), t);
        }
    }

    /** Pirate mode: Nexerelin treats it as a pirate faction, hostile to all but pirates, free ports, nothing illegal. */
    private static void setPirate(FactionAPI f, boolean pirate) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        if (pirate) mem.set(MEM_PIRATE, true);
        else mem.unset(MEM_PIRATE);
        NexFactionConfig conf = NexConfig.getFactionConfig(f.getId(), false);
        if (conf != null) conf.pirateFaction = pirate;
        FactionAPI pirates = Global.getSector().getFaction(Factions.PIRATES);
        if (pirate) {
            for (FactionAPI o : Global.getSector().getAllFactions()) {
                if (o == f || o.isPlayerFaction() || o.isNeutralFaction()) continue;
                float rel = NexUtilsFaction.isPirateFaction(o.getId()) ? 0.3f
                        : pirates != null ? pirates.getRelationship(o.getId()) : -0.6f;
                f.setRelationship(o.getId(), rel);
            }
            for (String c : new ArrayList<>(f.getIllegalCommodities())) f.makeCommodityLegal(c);
        } else {
            FactionSpawner.initRelations(f);
            for (String c : NORMAL_ILLEGAL) f.makeCommodityIllegal(c);
        }
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.getFaction() == f && !m.isPlanetConditionMarketOnly()) m.setFreePort(pirate);
        }
    }

    private static void setExpansion(FactionAPI f, Float chance) {
        if (chance == null) f.getMemoryWithoutUpdate().unset(MEM_EXPANSION);
        else f.getMemoryWithoutUpdate().set(MEM_EXPANSION, chance);
        ExpeditionShares.apply();
    }

    /** Applies a personality; returns what changed, in a few words. */
    public static String applyPersonality(FactionAPI f, String p) {
        switch (p) {
            case "warlike":
                setTemperament(f, WARLIKE);
                return "warlike: declares war easily and tires of it slowly";
            case "peaceful":
                setTemperament(f, PEACEFUL);
                return "peaceful: never starts wars on its own and tires of them quickly";
            case "pirate":
                setPirate(f, true);
                return "a pirate faction: hostile to everyone but pirates (and you as before), free ports, nothing illegal";
            case "lawful":
                setPirate(f, false);
                return "lawful again: normal diplomacy, no free ports, drugs and organs illegal";
            case "expansionist":
                setExpansion(f, 3f);
                return "expansionist: sends colony expeditions often";
            case "isolationist":
                setExpansion(f, 0f);
                return "isolationist: sends no colony expeditions";
            default:
                setTemperament(f, null);
                if (f.getMemoryWithoutUpdate().getBoolean(MEM_PIRATE)) setPirate(f, false);
                setExpansion(f, null);
                return "normal: no special traits, not a pirate faction, ordinary expansion";
        }
    }

    public static String describe(FactionAPI f) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        String ships = mem.getString(MEM_SHIPS);
        FactionAPI template = FactionSlots.template(f);
        List<String> doctrine = doctrinePresets(f);
        String expansion = !mem.contains(MEM_EXPANSION) ? "normal"
                : mem.getFloat(MEM_EXPANSION) <= 0f ? "isolationist" : mem.getFloat(MEM_EXPANSION) > 1.5f ? "expansionist" : "normal";
        return "ships " + (ships != null ? ships : "like " + (template != null ? template.getDisplayNameWithArticle() : "?"))
                + "; doctrine " + (doctrine.isEmpty() ? "as its model faction" : String.join(", ", doctrine))
                + "; " + temperament(f) + (mem.getBoolean(MEM_PIRATE) ? ", pirate" : "") + ", expansion " + expansion;
    }

    /** Called on every game load for spawned factions, after the template's ships and doctrine were copied. */
    public static void onGameLoad(FactionAPI f) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        NexFactionConfig conf = NexConfig.getFactionConfig(f.getId(), false);
        if (conf != null) {
            conf.pirateFaction = mem.getBoolean(MEM_PIRATE);
            conf.colonyExpeditionChance = mem.contains(MEM_EXPANSION) ? mem.getFloat(MEM_EXPANSION) : 1f;
        }
        String ships = mem.getString(MEM_SHIPS);
        if (ships != null) applyShips(f, ships);
        applyDoctrine(f);
    }

    /** A slot that is being filled again starts without the previous faction's character. */
    public static void clear(FactionAPI f) {
        f.getMemoryWithoutUpdate().unset(MEM_SHIPS);
        f.getMemoryWithoutUpdate().unset(MEM_DOCTRINE);
        f.getMemoryWithoutUpdate().unset(MEM_PIRATE);
        setTemperament(f, null);
        f.getMemoryWithoutUpdate().unset(ExpeditionShares.MEM_SHARE);
        setExpansion(f, null);
        reset(f);
    }

    /** Unused slots: Nexerelin settings back to the faction file's. */
    public static void reset(FactionAPI f) {
        NexFactionConfig conf = NexConfig.getFactionConfig(f.getId(), false);
        if (conf != null) {
            conf.pirateFaction = false;
            conf.colonyExpeditionChance = 1f;
        }
    }
}
