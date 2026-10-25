package worldsmith;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a faction considers illegal (black market goods, contraband for its patrols). Changes made with
 * factionillegal are remembered in the faction's memory, applied again when a save loads, and can be undone.
 */
public final class IllegalGoods {
    /** Comma-separated commodity ids made illegal / legal with factionillegal. */
    public static final String MEM_MADE_ILLEGAL = "$worldsmith_madeIllegal";
    public static final String MEM_MADE_LEGAL = "$worldsmith_madeLegal";

    private IllegalGoods() {
    }

    private static Set<String> read(FactionAPI f, String key) {
        String s = f.getMemoryWithoutUpdate().getString(key);
        Set<String> out = new LinkedHashSet<>();
        if (s != null) for (String id : s.split(",")) if (!id.trim().isEmpty()) out.add(id.trim());
        return out;
    }

    private static void write(FactionAPI f, String key, Set<String> ids) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        if (ids.isEmpty()) mem.unset(key);
        else mem.set(key, String.join(",", ids));
    }

    /** Commodities that can be traded (not the economy's bookkeeping entries), by name. */
    public static List<CommoditySpecAPI> commodities() {
        List<CommoditySpecAPI> out = new ArrayList<>();
        for (CommoditySpecAPI c : Global.getSettings().getAllCommoditySpecs()) {
            if (!c.isMeta()) out.add(c);
        }
        Collections.sort(out, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    /** By id or name (case-insensitive, underscores as spaces), or the only one whose id or name contains it. */
    public static CommoditySpecAPI find(String query) {
        String q = WsUtil.norm(query);
        CommoditySpecAPI partial = null;
        int partials = 0;
        for (CommoditySpecAPI c : commodities()) {
            if (WsUtil.norm(c.getId()).equals(q) || WsUtil.norm(c.getName()).equals(q)) return c;
            if (WsUtil.norm(c.getId()).contains(q) || WsUtil.norm(c.getName()).contains(q)) {
                partial = c;
                partials++;
            }
        }
        return partials == 1 ? partial : null;
    }

    private static CommoditySpecAPI spec(String id) {
        for (CommoditySpecAPI c : Global.getSettings().getAllCommoditySpecs()) if (c.getId().equals(id)) return c;
        return null;
    }

    public static String label(String id) {
        CommoditySpecAPI c = spec(id);
        return c == null ? id : c.getName() + " (" + id + ")";
    }

    /** What the faction considers illegal now, by name. */
    public static List<String> illegalNow(FactionAPI f) {
        List<String> out = new ArrayList<>();
        for (CommoditySpecAPI c : commodities()) {
            if (f.isIllegal(c.getId())) out.add(c.getName() + " (" + c.getId() + ")");
        }
        return out;
    }

    /** Makes the commodity illegal or legal for the faction. False if it already was. */
    public static boolean set(FactionAPI f, String id, boolean illegal) {
        if (f.isIllegal(id) == illegal) return false;
        if (illegal) f.makeCommodityIllegal(id);
        else f.makeCommodityLegal(id);
        Set<String> madeIllegal = read(f, MEM_MADE_ILLEGAL);
        Set<String> madeLegal = read(f, MEM_MADE_LEGAL);
        // Changing it back cancels the earlier change instead of recording a new one.
        if (illegal) {
            if (!madeLegal.remove(id)) madeIllegal.add(id);
        } else {
            if (!madeIllegal.remove(id)) madeLegal.add(id);
        }
        write(f, MEM_MADE_ILLEGAL, madeIllegal);
        write(f, MEM_MADE_LEGAL, madeLegal);
        return true;
    }

    /** Undoes every change made with factionillegal. Returns how many commodities changed back. */
    public static int reset(FactionAPI f) {
        Set<String> madeIllegal = read(f, MEM_MADE_ILLEGAL);
        Set<String> madeLegal = read(f, MEM_MADE_LEGAL);
        for (String id : madeIllegal) f.makeCommodityLegal(id);
        for (String id : madeLegal) f.makeCommodityIllegal(id);
        forget(f);
        return madeIllegal.size() + madeLegal.size();
    }

    /** Stops remembering the changes without undoing them (when something else sets the faction's laws). */
    public static void forget(FactionAPI f) {
        f.getMemoryWithoutUpdate().unset(MEM_MADE_ILLEGAL);
        f.getMemoryWithoutUpdate().unset(MEM_MADE_LEGAL);
    }

    /** The changes, in words, or null if there are none. */
    public static String changes(FactionAPI f) {
        List<String> parts = new ArrayList<>();
        for (String id : read(f, MEM_MADE_ILLEGAL)) parts.add(label(id) + " made illegal");
        for (String id : read(f, MEM_MADE_LEGAL)) parts.add(label(id) + " made legal");
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    public static void onGameLoad() {
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            for (String id : read(f, MEM_MADE_ILLEGAL)) {
                if (spec(id) != null) f.makeCommodityIllegal(id);
            }
            for (String id : read(f, MEM_MADE_LEGAL)) {
                if (spec(id) != null) f.makeCommodityLegal(id);
            }
        }
    }

    /** For tab completion. */
    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (CommoditySpecAPI c : commodities()) out.add(c.getId());
        return out;
    }

    public static final List<String> WORDS = Collections.unmodifiableList(Arrays.asList("illegal", "legal"));
}
