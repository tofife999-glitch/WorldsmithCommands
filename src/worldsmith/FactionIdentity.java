package worldsmith;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.loading.Description;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A faction's name, flag and lore. Names and flags use the same vanilla override fields the game uses for the
 * player's own faction. Lore is kept as plain text in the faction's memory and written into the faction's
 * description (the text on the Intel screen's faction page) whenever a save loads.
 */
public final class FactionIdentity {
    public static final String MEM_LORE = "$worldsmith_lore";
    /** Original description texts changed in this game session, so loading another save restores them. */
    private static final Map<String, String> ORIGINAL_LORE = new HashMap<>();
    private static final Set<String> LOADED_TEXTURES = new HashSet<>();
    private static List<String[]> playerFlags = null;

    private FactionIdentity() {
    }

    // ------------------------------------------------------------------ names

    /**
     * Sets a faction's display name everywhere (vanilla derives the long name and the person/entity name
     * prefixes from the same override). "The Free Worlds" becomes "Free Worlds" with the article "the".
     */
    public static void setName(FactionAPI f, String name) {
        String display = name.trim();
        if (display.toLowerCase(Locale.ROOT).startsWith("the ") && display.length() > 4) display = display.substring(4).trim();
        f.setDisplayNameOverride(display);
        f.setDisplayNameWithArticleOverride("the " + display);
        f.setDisplayIsOrAreOverride(isPlural(display) ? "are" : "is");
        f.setPersonNamePrefixAOrAnOverride("aeiouAEIOU".indexOf(display.charAt(0)) >= 0 ? "an" : "a");
    }

    public static void resetName(FactionAPI f) {
        f.setDisplayNameOverride(null);
        f.setDisplayNameWithArticleOverride(null);
        f.setDisplayIsOrAreOverride(null);
        f.setPersonNamePrefixAOrAnOverride(null);
    }

    /** "Free Worlds" -> are, "Hakeball Republic" -> is. */
    private static boolean isPlural(String name) {
        String last = name.substring(name.lastIndexOf(' ') + 1).toLowerCase(Locale.ROOT);
        return last.length() > 3 && last.endsWith("s") && !last.endsWith("ss") && !last.endsWith("us");
    }

    /** Ship name prefix from the initials, like vanilla's HSS or TTS: "Hakeball Republic" -> HRS. */
    public static String shipPrefix(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : name.trim().split("\\s+")) {
            String lw = w.toLowerCase(Locale.ROOT);
            if (lw.equals("the") || lw.equals("of") || lw.equals("and") || lw.isEmpty()) continue;
            char c = Character.toUpperCase(w.charAt(0));
            if (Character.isLetter(c)) sb.append(c);
            if (sb.length() == 2) break;
        }
        if (sb.length() == 0) return "FSS";
        if (sb.length() == 1) sb.append('S');
        return sb.append('S').toString();
    }

    // ------------------------------------------------------------------ flags

    /** Every flag the player can pick for their own faction (vanilla's and any added by mods): {logo, crest}. */
    public static List<String[]> playerFlags() {
        if (playerFlags != null) return playerFlags;
        List<String[]> out = new ArrayList<>();
        try {
            JSONObject json = Global.getSettings().getMergedJSON("data/world/factions/player.faction");
            JSONArray flags = json.optJSONArray("flags");
            for (int i = 0; flags != null && i < flags.length(); i++) {
                JSONObject o = flags.optJSONObject(i);
                if (o == null) continue;
                String logo = o.optString("logo", null);
                String crest = o.optString("crest", logo);
                if (logo != null && !logo.isEmpty()) out.add(new String[]{logo, crest});
            }
        } catch (Exception ex) {
            Global.getLogger(FactionIdentity.class).warn("Worldsmith: could not read the player flag list", ex);
        }
        if (out.isEmpty()) {
            for (int i = 1; i <= 17; i++) {
                String n = String.format(Locale.ROOT, "%02d", i);
                out.add(new String[]{"graphics/factions/custom/player_logo" + n + "_flag.png",
                        "graphics/factions/custom/player_logo" + n + "_crest.png"});
            }
        }
        playerFlags = out;
        return out;
    }

    /** A random player flag, preferring ones no faction is using. Returns its index in playerFlags(). */
    public static int randomFlagIndex(Random random) {
        List<String[]> flags = playerFlags();
        Set<String> used = new HashSet<>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f.getLogo() != null) used.add(f.getLogo());
        }
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < flags.size(); i++) {
            if (!used.contains(flags.get(i)[0])) free.add(i);
        }
        if (free.isEmpty()) return random.nextInt(flags.size());
        return free.get(random.nextInt(free.size()));
    }

    public static void setFlag(FactionAPI f, String logo, String crest) {
        loadTexture(logo);
        loadTexture(crest);
        f.setFactionLogoOverride(logo);
        f.setFactionCrestOverride(crest);
    }

    public static void resetFlag(FactionAPI f) {
        f.setFactionLogoOverride(null);
        f.setFactionCrestOverride(null);
    }

    private static void loadTexture(String path) {
        if (path == null || !LOADED_TEXTURES.add(path)) return;
        try {
            Global.getSettings().loadTexture(path);
        } catch (Exception ex) {
            Global.getLogger(FactionIdentity.class).warn("Worldsmith: could not load " + path, ex);
        }
    }

    // ------------------------------------------------------------------ lore

    /** The faction's own description entry, or null if it has none (or the game returned someone else's). */
    private static Description description(String factionId) {
        try {
            Description d = Global.getSettings().getDescription(factionId, Description.Type.FACTION);
            if (d == null || !d.getUID().equals(factionId + "_" + Description.Type.FACTION.name())) return null;
            return d;
        } catch (Exception ex) {
            return null;
        }
    }

    public static boolean hasDescription(FactionAPI f) {
        return description(f.getId()) != null;
    }

    /** The faction's lore, or "" if it has none (the game shows "No description... yet" for an empty one). */
    public static String lore(FactionAPI f) {
        Description d = description(f.getId());
        String t = d == null ? null : d.getText1();
        return t == null || t.trim().isEmpty() || t.startsWith("No description...") ? "" : t;
    }

    /** Replaces the faction's lore; null restores its original text. False if it has no description entry. */
    public static boolean setLore(FactionAPI f, String text) {
        Description d = description(f.getId());
        if (d == null) return false;
        if (text == null) {
            f.getMemoryWithoutUpdate().unset(MEM_LORE);
            if (ORIGINAL_LORE.containsKey(f.getId())) d.setText1(ORIGINAL_LORE.get(f.getId()));
            return true;
        }
        if (!ORIGINAL_LORE.containsKey(f.getId())) ORIGINAL_LORE.put(f.getId(), d.getText1());
        d.setText1(text);
        f.getMemoryWithoutUpdate().set(MEM_LORE, text);
        return true;
    }

    /** Called on every game load: original texts back, then this save's lore and flags applied. */
    public static void onGameLoad() {
        for (Map.Entry<String, String> e : ORIGINAL_LORE.entrySet()) {
            Description d = description(e.getKey());
            if (d != null) d.setText1(e.getValue());
        }
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            String lore = f.getMemoryWithoutUpdate().getString(MEM_LORE);
            if (lore != null) setLore(f, lore);
            if (f.getFactionLogoOverride() != null) loadTexture(f.getFactionLogoOverride());
            if (f.getFactionCrestOverride() != null) loadTexture(f.getFactionCrestOverride());
        }
    }
}
