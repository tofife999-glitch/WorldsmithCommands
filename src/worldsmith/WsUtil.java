package worldsmith;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.PlanetGenDataSpec;
import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.Console;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared helpers: name matching, target lookup, faction parsing, tab-completion caches and confirmations.
 * Nothing in here is ever stored in the save: caches are static fields and confirmations are transient scripts.
 */
public final class WsUtil {
    public static final String MOD_ID = "worldsmith_commands";
    /** Source id for every stat modifier this mod applies. */
    public static final String MOD_SOURCE = "worldsmith_commands";
    public static final String PREFIX = "[Worldsmith] ";

    /** Which bodies a command accepts as targets. */
    public enum Kind {
        /** Any planet, moon, star or station. */
        ANY,
        /** Non-star planets and moons. */
        PLANET,
        /** Planets and moons that nobody has colonized. */
        UNCOLONIZED,
        /** Planets and stations with a real (non condition-only) market. */
        COLONY
    }

    private WsUtil() {
    }

    // ------------------------------------------------------------------ output

    public static void print(String msg) {
        Console.showMessage(PREFIX + msg);
    }

    public static void printRaw(String msg) {
        Console.showMessage(msg);
    }

    // ------------------------------------------------------------------ names

    /** Lowercase, underscores as spaces, single spaces, trimmed. */
    public static String norm(String s) {
        if (s == null) return "";
        return s.replace('_', ' ').toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** How a name is offered in tab completion: spaces shown as underscores. */
    public static String token(String name) {
        return name.trim().replaceAll("\\s+", "_");
    }

    public static List<String> splitArgs(String args) {
        String t = args == null ? "" : args.trim();
        if (t.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(t.split("\\s+")));
    }

    public static String join(List<String> parts, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public static boolean isInt(String s) {
        if (s == null || s.isEmpty() || s.length() > 6) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    public static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    /** Lower is better; Integer.MAX_VALUE means "not similar". */
    public static int similarity(String query, String candidate) {
        String q = norm(query);
        String c = norm(candidate);
        if (q.isEmpty() || c.isEmpty()) return Integer.MAX_VALUE;
        if (c.startsWith(q)) return 0;
        if (c.contains(q)) return 1;
        int d = levenshtein(q, c);
        int limit = Math.max(2, Math.min(q.length(), c.length()) / 2);
        return d <= limit ? 1 + d : Integer.MAX_VALUE;
    }

    // ------------------------------------------------------------------ describing things

    public static String systemName(SectorEntityToken e) {
        LocationAPI loc = e.getContainingLocation();
        if (loc == null) return "nowhere";
        if (loc.isHyperspace()) return "Hyperspace";
        return loc.getName();
    }

    public static boolean isColony(MarketAPI m) {
        return m != null && !m.isPlanetConditionMarketOnly();
    }

    public static String owner(SectorEntityToken e) {
        MarketAPI m = e.getMarket();
        if (isColony(m)) return m.getFaction().getDisplayName() + " size " + m.getSize();
        return "unclaimed";
    }

    public static String describe(SectorEntityToken e) {
        return e.getName() + " (" + systemName(e) + ", " + owner(e) + ", ID " + e.getId() + ")";
    }

    public static String factionLabel(FactionAPI f) {
        return f.getDisplayName() + " (" + f.getId() + ")";
    }

    // ------------------------------------------------------------------ bodies

    public static boolean isStar(SectorEntityToken e) {
        return e instanceof PlanetAPI && ((PlanetAPI) e).isStar();
    }

    public static boolean isStation(SectorEntityToken e) {
        return !(e instanceof PlanetAPI) && (e.hasTag(Tags.STATION) || isColony(e.getMarket()));
    }

    public static boolean isUncolonizedPlanet(SectorEntityToken e) {
        if (!(e instanceof PlanetAPI) || isStar(e)) return false;
        MarketAPI m = e.getMarket();
        return m == null || m.isPlanetConditionMarketOnly();
    }

    public static boolean matchesKind(SectorEntityToken e, Kind kind) {
        switch (kind) {
            case PLANET:
                return e instanceof PlanetAPI && !isStar(e);
            case UNCOLONIZED:
                return isUncolonizedPlanet(e);
            case COLONY:
                return isColony(e.getMarket());
            default:
                return true;
        }
    }

    /** All planets, moons, stars and stations in the sector (fleets and loose objects excluded). */
    public static List<SectorEntityToken> allBodies() {
        Set<SectorEntityToken> out = new LinkedHashSet<>();
        SectorAPI sector = Global.getSector();
        for (StarSystemAPI sys : sector.getStarSystems()) {
            out.addAll(sys.getPlanets());
            out.addAll(sys.getEntitiesWithTag(Tags.STATION));
        }
        for (MarketAPI m : sector.getEconomy().getMarketsCopy()) {
            if (m.getPrimaryEntity() != null) out.add(m.getPrimaryEntity());
        }
        return new ArrayList<>(out);
    }

    public static List<SectorEntityToken> bodies(Kind kind) {
        List<SectorEntityToken> out = new ArrayList<>();
        for (SectorEntityToken e : allBodies()) {
            if (matchesKind(e, kind)) out.add(e);
        }
        return out;
    }

    private static boolean idMatch(SectorEntityToken e, String raw) {
        if (e.getId() != null && e.getId().equalsIgnoreCase(raw)) return true;
        MarketAPI m = e.getMarket();
        return m != null && m.getId() != null && m.getId().equalsIgnoreCase(raw);
    }

    private static boolean nameMatch(SectorEntityToken e, String n) {
        if (norm(e.getName()).equals(n)) return true;
        if (norm(e.getId()).equals(n)) return true;
        MarketAPI m = e.getMarket();
        if (m != null) {
            if (norm(m.getName()).equals(n)) return true;
            if (norm(m.getId()).equals(n)) return true;
        }
        return false;
    }

    /** All exact matches (IDs first: if exactly one ID matches, only that one is returned). */
    public static List<SectorEntityToken> exactMatches(String query, List<SectorEntityToken> candidates) {
        String raw = query.trim();
        List<SectorEntityToken> byId = new ArrayList<>();
        for (SectorEntityToken e : candidates) {
            if (idMatch(e, raw)) byId.add(e);
        }
        if (byId.size() == 1) return byId;
        String n = norm(query);
        List<SectorEntityToken> out = new ArrayList<>();
        for (SectorEntityToken e : candidates) {
            if (nameMatch(e, n)) out.add(e);
        }
        return out;
    }

    public static List<SectorEntityToken> didYouMean(String query, List<SectorEntityToken> candidates, int max) {
        final List<SectorEntityToken> hits = new ArrayList<>();
        final java.util.Map<SectorEntityToken, Integer> score = new java.util.HashMap<>();
        for (SectorEntityToken e : candidates) {
            int s = similarity(query, e.getName());
            if (s == Integer.MAX_VALUE) continue;
            hits.add(e);
            score.put(e, s);
        }
        hits.sort(Comparator.comparingInt((SectorEntityToken e) -> score.get(e))
                .thenComparing(e -> e.getName().length()));
        return hits.size() > max ? hits.subList(0, max) : hits;
    }

    /**
     * Finds the one body named by the query. Prints a list and returns null on ambiguity, or "did you mean"
     * suggestions (searching every body, so it can also say "X exists but is colonized") on no match.
     */
    public static SectorEntityToken findBody(String query, Kind kind, String whatLabel) {
        List<SectorEntityToken> candidates = bodies(kind);
        List<SectorEntityToken> exact = exactMatches(query, candidates);
        if (exact.size() == 1) return exact.get(0);
        if (exact.size() > 1) {
            print(exact.size() + " matches for '" + query + "'. Nothing was changed; use the ID instead:");
            for (SectorEntityToken e : exact) printRaw("  - " + describe(e));
            return null;
        }
        // Not a valid target: maybe it exists but is the wrong kind.
        if (kind != Kind.ANY) {
            List<SectorEntityToken> other = exactMatches(query, allBodies());
            for (SectorEntityToken e : other) {
                print(e.getName() + " (" + systemName(e) + ") is not a valid target here: " + whyNot(e, kind));
            }
            if (!other.isEmpty()) return null;
        }
        List<SectorEntityToken> dym = didYouMean(query, candidates, 5);
        if (dym.isEmpty()) {
            print("No " + whatLabel + " named '" + query + "' found.");
        } else {
            print("No " + whatLabel + " named '" + query + "'. Did you mean:");
            for (SectorEntityToken e : dym) printRaw("  - " + describe(e));
        }
        return null;
    }

    public static String whyNot(SectorEntityToken e, Kind kind) {
        if (isStar(e)) return "it is a star.";
        if (!(e instanceof PlanetAPI) && kind != Kind.COLONY) return "it is a station, not a planet.";
        if (kind == Kind.UNCOLONIZED && isColony(e.getMarket()))
            return "it is already a colony (" + owner(e) + ").";
        if (kind == Kind.COLONY) return "it is not a colony.";
        return "wrong kind of body.";
    }

    /** The planet/station whose dialog is open, if it fits the kind. */
    public static SectorEntityToken dialogTarget(CommandContext context) {
        if (context == null) return null;
        SectorEntityToken e = context.getEntityInteractedWith();
        if (e == null) return null;
        if (e instanceof PlanetAPI || e.getMarket() != null || e.hasTag(Tags.STATION)) return e;
        return null;
    }

    /**
     * Resolves the target of a command: the given name if any, otherwise the open dialog's planet/station.
     * Prints what went wrong and returns null if there is no usable target.
     */
    public static SectorEntityToken resolveTarget(String name, CommandContext context, Kind kind,
                                                  String whatLabel, String usage) {
        if (name != null && !name.trim().isEmpty()) return findBody(name, kind, whatLabel);
        SectorEntityToken e = dialogTarget(context);
        if (e == null) {
            print("No " + whatLabel + " given and no planet dialog is open.");
            printRaw("Usage: " + usage);
            return null;
        }
        if (!matchesKind(e, kind)) {
            print(e.getName() + " (" + systemName(e) + ") is not a valid target here: " + whyNot(e, kind));
            return null;
        }
        return e;
    }

    // ------------------------------------------------------------------ systems

    public static StarSystemAPI currentSystem(CommandContext context) {
        SectorEntityToken d = context == null ? null : context.getEntityInteractedWith();
        if (d != null && d.getContainingLocation() instanceof StarSystemAPI)
            return (StarSystemAPI) d.getContainingLocation();
        if (Global.getSector().getPlayerFleet() == null) return null;
        LocationAPI loc = Global.getSector().getPlayerFleet().getContainingLocation();
        return loc instanceof StarSystemAPI ? (StarSystemAPI) loc : null;
    }

    /** Exact (case-insensitive, underscores as spaces) match on system base name, full name or ID. */
    public static List<StarSystemAPI> exactSystems(String query) {
        String n = norm(query);
        List<StarSystemAPI> out = new ArrayList<>();
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (norm(s.getBaseName()).equals(n) || norm(s.getName()).equals(n) || norm(s.getId()).equals(n)
                    || s.getId().equalsIgnoreCase(query.trim()))
                out.add(s);
        }
        return out;
    }

    // ------------------------------------------------------------------ factions

    /** ID or display name, typo-tolerant, underscores as spaces. Prints an error and returns null if not found. */
    public static FactionAPI parseFaction(String token) {
        if (token == null || token.isEmpty()) return null;
        FactionAPI f = Global.getSector().getFaction(token);
        if (f == null) f = Global.getSector().getFaction(token.toLowerCase(Locale.ROOT));
        if (f == null) f = CommandUtils.findBestFactionMatch(token);
        if (f == null) f = CommandUtils.findBestFactionMatch(token.replace('_', ' '));
        if (f == null) print("Unknown faction '" + token + "'. Type 'list factions' to see faction IDs.");
        return f;
    }

    // ------------------------------------------------------------------ planet types

    public static boolean isSelectableType(PlanetSpecAPI s) {
        return s != null && !s.isStar() && !s.isBlackHole() && !s.isNebulaCenter();
    }

    public static List<PlanetSpecAPI> planetTypes() {
        List<PlanetSpecAPI> out = new ArrayList<>();
        for (PlanetSpecAPI s : Global.getSettings().getAllPlanetSpecs()) {
            if (isSelectableType(s)) out.add(s);
        }
        out.sort(Comparator.comparing(PlanetSpecAPI::getPlanetType));
        return out;
    }

    public static PlanetGenDataSpec genData(String type) {
        return (PlanetGenDataSpec) Global.getSettings().getSpec(PlanetGenDataSpec.class, type, true);
    }

    /** Resolves a planet type by ID or display name. Prints a list on ambiguity / suggestions on no match. */
    public static PlanetSpecAPI findPlanetType(String query) {
        List<PlanetSpecAPI> types = planetTypes();
        for (PlanetSpecAPI s : types) {
            if (s.getPlanetType().equalsIgnoreCase(query)) return s;
        }
        String n = norm(query);
        List<PlanetSpecAPI> byName = new ArrayList<>();
        for (PlanetSpecAPI s : types) {
            if (norm(s.getName()).equals(n) || norm(s.getPlanetType()).equals(n)) byName.add(s);
        }
        if (byName.size() == 1) return byName.get(0);
        if (byName.size() > 1) {
            print("Several planet types are called '" + query + "'. Use the type ID:");
            for (PlanetSpecAPI s : byName) printRaw("  - " + s.getPlanetType() + " (" + s.getName() + ")");
            return null;
        }
        final java.util.Map<PlanetSpecAPI, Integer> score = new java.util.HashMap<>();
        List<PlanetSpecAPI> hits = new ArrayList<>();
        for (PlanetSpecAPI s : types) {
            int a = similarity(query, s.getPlanetType());
            int b = similarity(query, s.getName());
            int best = Math.min(a, b);
            if (best == Integer.MAX_VALUE) continue;
            score.put(s, best);
            hits.add(s);
        }
        hits.sort(Comparator.comparingInt(score::get));
        if (hits.isEmpty()) {
            print("Unknown planet type '" + query + "'. Type 'planettypes' to list all types.");
        } else {
            print("Unknown planet type '" + query + "'. Did you mean:");
            for (int i = 0; i < Math.min(5, hits.size()); i++)
                printRaw("  - " + hits.get(i).getPlanetType() + " (" + hits.get(i).getName() + ")");
        }
        return null;
    }

    // ------------------------------------------------------------------ tab completion caches

    /** Our own commands call invalidateCache(); this only bounds staleness from changes made by other mods. */
    private static final long CACHE_MS = 30000L;
    private static WeakReference<SectorAPI> cacheSector = new WeakReference<>(null);
    private static long cacheTime = 0L;
    private static final Map<Kind, List<String>> bodyNames = new EnumMap<>(Kind.class);
    private static List<String> systemNames = Collections.emptyList();
    private static List<String> factionIds = Collections.emptyList();
    private static List<String> typeIds = null;

    /** Call after a command changes names or bodies so tab completion is fresh. */
    public static void invalidateCache() {
        cacheTime = 0L;
    }

    private static void refreshCache() {
        try {
            rebuildCacheIfStale();
        } catch (Exception ex) {
            // Tab completion must never break the console; keep whatever lists we had.
            Global.getLogger(WsUtil.class).warn("Worldsmith: could not refresh tab completion lists", ex);
        }
    }

    private static void rebuildCacheIfStale() {
        SectorAPI sector = Global.getSector();
        long now = System.currentTimeMillis();
        if (sector == null || sector.getEconomy() == null) return;
        if (cacheSector.get() == sector && now - cacheTime < CACHE_MS) return;
        cacheSector = new WeakReference<>(sector);
        cacheTime = now;

        Map<Kind, Set<String>> sets = new EnumMap<>(Kind.class);
        for (Kind k : Kind.values()) sets.put(k, new LinkedHashSet<>());
        for (SectorEntityToken e : allBodies()) {
            if (e.getName() == null) continue;
            String t = token(e.getName());
            for (Kind k : Kind.values()) {
                if (matchesKind(e, k)) sets.get(k).add(t);
            }
        }
        bodyNames.clear();
        for (Kind k : Kind.values()) {
            List<String> l = new ArrayList<>(sets.get(k));
            Collections.sort(l, String.CASE_INSENSITIVE_ORDER);
            bodyNames.put(k, Collections.unmodifiableList(l));
        }

        Set<String> sys = new LinkedHashSet<>();
        for (StarSystemAPI s : sector.getStarSystems()) sys.add(token(s.getBaseName()));
        List<String> sl = new ArrayList<>(sys);
        Collections.sort(sl, String.CASE_INSENSITIVE_ORDER);
        systemNames = Collections.unmodifiableList(sl);

        List<String> fl = new ArrayList<>();
        for (FactionAPI f : sector.getAllFactions()) fl.add(f.getId());
        Collections.sort(fl);
        factionIds = Collections.unmodifiableList(fl);
    }

    public static List<String> bodySuggestions(Kind kind) {
        refreshCache();
        List<String> l = bodyNames.get(kind);
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    public static List<String> systemSuggestions() {
        refreshCache();
        return new ArrayList<>(systemNames);
    }

    public static List<String> factionSuggestions() {
        refreshCache();
        return new ArrayList<>(factionIds);
    }

    public static List<String> typeSuggestions() {
        if (typeIds == null) {
            List<String> l = new ArrayList<>();
            for (PlanetSpecAPI s : planetTypes()) l.add(s.getPlanetType());
            typeIds = Collections.unmodifiableList(l);
        }
        return new ArrayList<>(typeIds);
    }

    public static List<String> list(String... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    // ------------------------------------------------------------------ confirmations

    /** True if the last argument is "yes" (typed confirmation). */
    public static boolean endsWithYes(List<String> parts) {
        return !parts.isEmpty() && parts.get(parts.size() - 1).equalsIgnoreCase("yes");
    }

    /**
     * Shows a Yes/Cancel popup once the console (and any open dialog) is closed, the same way
     * Console.showDialogOnClose defers dialogs. Uses a transient script, so nothing is written to the save.
     * Cancel does nothing. A newer request with the same key replaces an older one that is still waiting.
     */
    public static void confirm(String key, String question, String retypeHint, Script onYes) {
        cancelPending(key);
        Global.getSector().addTransientScript(new ConfirmScript(key, question, retypeHint, onYes));
        print("Close the console to get a Yes/Cancel popup (leave any open planet dialog first), or type: "
                + retypeHint);
    }

    /** Drops a popup that is still waiting, e.g. because the user confirmed by typing "yes" instead. */
    public static void cancelPending(String key) {
        for (EveryFrameScript s : new ArrayList<>(Global.getSector().getTransientScripts())) {
            if (s instanceof ConfirmScript && ((ConfirmScript) s).key.equals(key)) {
                ((ConfirmScript) s).done = true;
                Global.getSector().removeTransientScript(s);
            }
        }
    }

    private static final class ConfirmScript implements EveryFrameScript {
        private static final int MAX_TRIES = 600;
        private final String key;
        private final String question;
        private final String retypeHint;
        private final Script onYes;
        private boolean done = false;
        private int failedTries = 0;

        ConfirmScript(String key, String question, String retypeHint, Script onYes) {
            this.key = key;
            this.question = question;
            this.retypeHint = retypeHint;
            this.onYes = onYes;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public boolean runWhilePaused() {
            return true;
        }

        @Override
        public void advance(float amount) {
            if (done) return;
            CampaignUIAPI ui = Global.getSector().getCampaignUI();
            if (ui == null || ui.isShowingDialog() || ui.isShowingMenu()) return;
            boolean shown;
            try {
                shown = ui.showConfirmDialog(question, "Yes", "Cancel", onYes, new Script() {
                    @Override
                    public void run() {
                    }
                });
            } catch (Exception ex) {
                Global.getLogger(WsUtil.class).warn("Worldsmith: confirm dialog failed", ex);
                shown = false;
            }
            if (shown) {
                done = true;
            } else if (++failedTries >= MAX_TRIES) {
                done = true;
                ui.addMessage("Worldsmith: could not show the confirmation. Type instead: " + retypeHint);
            }
        }
    }

    /** Runs a confirmed action and reports exceptions to the console log instead of crashing. */
    public static void runSafely(String what, Runnable r) {
        try {
            r.run();
        } catch (Exception ex) {
            Global.getLogger(WsUtil.class).error("Worldsmith: " + what + " failed", ex);
            CampaignUIAPI ui = Global.getSector().getCampaignUI();
            String msg = "Worldsmith: " + what + " failed: " + ex;
            try {
                Console.showMessage(msg);
            } catch (Exception ignored) {
            }
            if (ui != null) ui.addMessage(msg);
        }
    }

    /** Message for after a confirmed action (console may be closed, so also post to the campaign log). */
    public static void report(String msg) {
        try {
            Console.showMessage(PREFIX + msg);
        } catch (Exception ignored) {
        }
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui != null) ui.addMessage(PREFIX + msg);
    }
}
