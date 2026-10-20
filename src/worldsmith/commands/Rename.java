package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.FactionIdentity;
import worldsmith.FactionSlots;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Rename implements BaseCommandWithSuggestion {
    private static final String USAGE = "rename <current name> <new name>";

    /** What the current name may refer to. */
    private enum Mode { ANY, SYSTEM, STAR }

    private static void printHowTo() {
        WsUtil.print("How to use rename:");
        WsUtil.printRaw("  rename <current name> <new name>     e.g.  rename Glith_I Hakeball   (Tab completes the current name)");
        WsUtil.printRaw("  rename <current name> > <new name>   e.g.  rename Glith I > Hakeball (use > if you're unsure)");
        WsUtil.printRaw("  rename <new name>                    with a planet's or station's dialog open");
        WsUtil.printRaw("  rename system <system> <new name>    renames a star system, plus its star and planets named after it");
        WsUtil.printRaw("  rename star <star> <new name>        renames only that star");
        WsUtil.printRaw("  rename faction <faction> <new name>  renames a faction everywhere ('reset' gives back its own name)");
    }

    /** rename faction <faction> [>] <new name>: the faction is an ID or exact name, the rest is the new name. */
    private static CommandResult renameFaction(String a) {
        FactionAPI faction = null;
        String newName;
        int gt = a.indexOf('>');
        if (gt >= 0) {
            faction = WsUtil.parseFaction(a.substring(0, gt).trim());
            if (faction == null) return CommandResult.ERROR;
            newName = cleanNewName(a.substring(gt + 1));
        } else {
            List<String> parts = WsUtil.splitArgs(a);
            int k = parts.size() - 1;
            for (; k >= 1; k--) {
                String n = WsUtil.norm(WsUtil.join(parts, 0, k));
                for (FactionAPI f : Global.getSector().getAllFactions()) {
                    if (WsUtil.norm(f.getDisplayName()).equals(n) || WsUtil.norm(f.getId()).equals(n)) faction = f;
                }
                if (faction != null) break;
            }
            if (faction == null) {
                if (parts.size() < 2) {
                    WsUtil.print("Give the faction and its new name: rename faction <faction> <new name>");
                    return CommandResult.BAD_SYNTAX;
                }
                k = 1;
                faction = WsUtil.parseFaction(parts.get(0));
                if (faction == null) return CommandResult.ERROR;
            }
            newName = cleanNewName(WsUtil.join(parts, k, parts.size()));
        }
        if (FactionSlots.isUnused(faction)) {
            WsUtil.print(faction.getId() + " is an unused faction slot; spawnfaction fills it.");
            return CommandResult.ERROR;
        }
        String old = faction.getDisplayName();
        if (newName.equalsIgnoreCase("reset")) {
            if (FactionSlots.isSlot(faction)) {
                WsUtil.print(old + " was created by spawnfaction and has no other name to go back to.");
                return CommandResult.ERROR;
            }
            FactionIdentity.resetName(faction);
            WsUtil.invalidateCache();
            WsUtil.print("Renamed " + old + " back to " + faction.getDisplayName() + " (" + faction.getId() + ").");
            return CommandResult.SUCCESS;
        }
        if (newName.isEmpty() || newName.length() > 40) {
            WsUtil.print("The new name must be 1-40 characters.");
            return CommandResult.BAD_SYNTAX;
        }
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f != faction && WsUtil.norm(f.getDisplayName()).equals(WsUtil.norm(newName))) {
                WsUtil.print("There already is a faction called '" + f.getDisplayName() + "'.");
                return CommandResult.ERROR;
            }
        }
        FactionIdentity.setName(faction, newName);
        WsUtil.invalidateCache();
        WsUtil.print("Renamed the faction " + old + " to " + faction.getDisplayName() + " (ID " + faction.getId()
                + "; 'rename faction " + faction.getId() + " reset' undoes it).");
        return CommandResult.SUCCESS;
    }

    private static String cleanNewName(String s) {
        return s.trim().replace('_', ' ').replaceAll("\\s+", " ");
    }

    private static List<SectorEntityToken> stars() {
        List<SectorEntityToken> out = new ArrayList<>();
        for (SectorEntityToken e : WsUtil.allBodies()) {
            if (WsUtil.isStar(e)) out.add(e);
        }
        return out;
    }

    private static boolean names(String oldName, Mode mode) {
        switch (mode) {
            case SYSTEM: return !WsUtil.exactSystems(oldName).isEmpty();
            case STAR: return !WsUtil.exactMatches(oldName, stars()).isEmpty();
            default: return !WsUtil.exactMatches(oldName, WsUtil.allBodies()).isEmpty() || !WsUtil.exactSystems(oldName).isEmpty();
        }
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String a = args.trim();
        Mode mode = Mode.ANY;
        List<String> words = WsUtil.splitArgs(a);
        if (words.size() >= 2 && words.get(0).equalsIgnoreCase("faction")) {
            return renameFaction(a.substring(words.get(0).length()).trim());
        }
        if (words.size() >= 2 && (words.get(0).equalsIgnoreCase("system") || words.get(0).equalsIgnoreCase("star"))) {
            mode = words.get(0).equalsIgnoreCase("system") ? Mode.SYSTEM : Mode.STAR;
            a = a.substring(words.get(0).length()).trim();
        }
        if (a.isEmpty() || a.equals(">")) {
            printHowTo();
            return CommandResult.BAD_SYNTAX;
        }
        int gt = a.indexOf('>');
        if (gt >= 0) {
            // Explicit form: <current name> > <new name>
            String oldName = a.substring(0, gt).trim();
            String newName = cleanNewName(a.substring(gt + 1));
            if (newName.isEmpty()) {
                WsUtil.print("The new name is missing after '>'.");
                printHowTo();
                return CommandResult.BAD_SYNTAX;
            }
            if (oldName.isEmpty() && mode == Mode.ANY) {
                SectorEntityToken d = WsUtil.resolveTarget("", context, WsUtil.Kind.ANY, "planet or station", USAGE);
                if (d == null) return CommandResult.ERROR;
                renameBody(d, newName);
                return CommandResult.SUCCESS;
            }
            return renameNamed(oldName, newName, mode);
        }

        // No '>': with a planet/station dialog open, everything typed is the new name for it.
        if (mode == Mode.ANY) {
            SectorEntityToken d = WsUtil.dialogTarget(context);
            if (d != null) {
                renameBody(d, cleanNewName(a));
                return CommandResult.SUCCESS;
            }
        }
        // Otherwise the longest leading part that is exactly the name (or ID) of a planet, station or
        // system is the current name, and the rest is the new name.
        List<String> parts = WsUtil.splitArgs(a);
        for (int k = parts.size() - 1; k >= 1; k--) {
            String oldName = WsUtil.join(parts, 0, k);
            if (names(oldName, mode)) return renameNamed(oldName, cleanNewName(WsUtil.join(parts, k, parts.size())), mode);
        }
        if (parts.size() == 1) {
            WsUtil.print("Only one name was given" + (mode == Mode.ANY ? " and no planet dialog is open" : "")
                    + ", so I don't know what to rename.");
        } else {
            String what = mode == Mode.SYSTEM ? "star system" : (mode == Mode.STAR ? "star" : "planet, station or system");
            WsUtil.print("The start of '" + a + "' isn't the name of any " + what + ".");
            printDidYouMean(parts.get(0));
        }
        printHowTo();
        return CommandResult.BAD_SYNTAX;
    }

    private static CommandResult renameNamed(String oldName, String newName, Mode mode) {
        if (newName.isEmpty()) {
            printHowTo();
            return CommandResult.BAD_SYNTAX;
        }
        if (mode == Mode.SYSTEM) {
            List<StarSystemAPI> systems = WsUtil.exactSystems(oldName);
            if (systems.size() == 1) {
                renameSystem(systems.get(0), newName);
                return CommandResult.SUCCESS;
            }
            if (systems.isEmpty()) {
                printDidYouMean(oldName);
            } else {
                WsUtil.print("Several systems match '" + oldName + "'. Use the system ID:");
                for (StarSystemAPI s : systems) WsUtil.printRaw("  - " + s.getName() + " (ID " + s.getId() + ")");
            }
            return CommandResult.ERROR;
        }
        if (mode == Mode.STAR) {
            List<SectorEntityToken> hits = WsUtil.exactMatches(oldName, stars());
            if (hits.size() == 1) {
                renameBody(hits.get(0), newName);
                return CommandResult.SUCCESS;
            }
            if (hits.isEmpty()) {
                printDidYouMean(oldName);
            } else {
                WsUtil.print(hits.size() + " stars are called '" + oldName + "'. Use the ID:");
                for (SectorEntityToken b : hits) WsUtil.printRaw("  - " + WsUtil.describe(b));
            }
            return CommandResult.ERROR;
        }

        List<SectorEntityToken> bodies = WsUtil.exactMatches(oldName, WsUtil.allBodies());
        List<StarSystemAPI> systems = WsUtil.exactSystems(oldName);

        if (systems.size() > 1) {
            WsUtil.print("Several systems match '" + oldName + "'. Use the system ID:");
            for (StarSystemAPI s : systems) WsUtil.printRaw("  - " + s.getName() + " (ID " + s.getId() + ")");
            return CommandResult.ERROR;
        }
        if (systems.size() == 1) {
            StarSystemAPI sys = systems.get(0);
            boolean onlyItsStars = true;
            for (SectorEntityToken b : bodies) {
                if (!(WsUtil.isStar(b) && b.getContainingLocation() == sys)) onlyItsStars = false;
            }
            if (onlyItsStars) {
                // A system and its star with the same name: the system is renamed (and the star with it).
                renameSystem(sys, newName);
                return CommandResult.SUCCESS;
            }
            WsUtil.print("'" + oldName + "' is both a star system and a planet or station. Say which one you mean:");
            WsUtil.printRaw("  - rename system " + oldName + " > " + newName);
            for (SectorEntityToken b : bodies) WsUtil.printRaw("  - rename " + b.getId() + " > " + newName + "   (" + WsUtil.describe(b) + ")");
            return CommandResult.ERROR;
        }
        if (bodies.size() > 1) {
            WsUtil.print(bodies.size() + " bodies are called '" + oldName + "'. Nothing was renamed; use the ID:");
            for (SectorEntityToken b : bodies) WsUtil.printRaw("  - " + WsUtil.describe(b));
            return CommandResult.ERROR;
        }
        if (bodies.isEmpty()) {
            printDidYouMean(oldName);
            return CommandResult.ERROR;
        }
        renameBody(bodies.get(0), newName);
        return CommandResult.SUCCESS;
    }

    /** Up to 5 suggestions from planets, stations, stars and star systems. */
    private static void printDidYouMean(String query) {
        List<String> lines = new ArrayList<>();
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (WsUtil.similarity(query, s.getBaseName()) <= 1) lines.add("system: " + s.getName() + " (ID " + s.getId() + ")");
            if (lines.size() >= 2) break;
        }
        for (SectorEntityToken e : WsUtil.didYouMean(query, WsUtil.allBodies(), 5 - lines.size())) {
            lines.add(WsUtil.describe(e));
        }
        if (lines.isEmpty()) {
            WsUtil.print("Nothing named '" + query + "' found.");
            return;
        }
        WsUtil.print("Nothing named '" + query + "'. Did you mean:");
        for (String l : lines) WsUtil.printRaw("  - " + l);
    }

    private static Pattern word(String s) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(s) + "(?![\\p{L}\\p{N}])");
    }

    private static String replaceWord(String text, String oldS, String newS) {
        if (text == null) return null;
        return word(oldS).matcher(text).replaceAll(Matcher.quoteReplacement(newS));
    }

    private static void renameBody(SectorEntityToken e, String newName) {
        String old = e.getName();
        e.setName(newName);
        MarketAPI m = e.getMarket();
        if (m != null) m.setName(newName);
        List<String> extra = new ArrayList<>();
        if (e instanceof PlanetAPI && e.getContainingLocation() instanceof StarSystemAPI) {
            PlanetAPI p = (PlanetAPI) e;
            StarSystemAPI sys = (StarSystemAPI) e.getContainingLocation();
            for (JumpPointAPI jp : WsUtil.hyperJumpPointsInto(sys)) {
                if (!WsUtil.leadsTo(jp, e)) continue;
                String n = null;
                if (p.isGasGiant()) n = newName + " Gravity Well";
                else if (p.isStar()) n = newName + ", " + p.getSpec().getName();
                if (n != null && !n.equals(jp.getName())) {
                    extra.add("hyperspace jump point '" + jp.getName() + "' -> '" + n + "'");
                    jp.setName(n);
                }
            }
            for (SectorEntityToken t : sys.getJumpPoints()) {
                if (!(t instanceof JumpPointAPI)) continue;
                JumpPointAPI jp = (JumpPointAPI) t;
                if (jp.getRelatedPlanet() != e && jp.getOrbitFocus() != e) continue;
                String n = replaceWord(jp.getName(), old, newName);
                if (n != null && !n.equals(jp.getName())) {
                    extra.add("jump point '" + jp.getName() + "' -> '" + n + "'");
                    jp.setName(n);
                }
            }
        }
        WsUtil.invalidateCache();
        WsUtil.print("Renamed " + old + " to " + newName + (m != null ? " (planet and market)" : "") + " in "
                + WsUtil.systemName(e) + "." + (extra.isEmpty() ? "" : " Also renamed: " + String.join("; ", extra) + "."));
    }

    private static void renameSystem(StarSystemAPI sys, String newBase) {
        String oldBase = sys.getBaseName();
        String oldFull = sys.getName();
        List<String> changed = new ArrayList<>();

        sys.setBaseName(newBase); // also resets the full name to "<base> Star System" / "<base> Nebula"
        String full = word(oldBase).matcher(oldFull).find() ? replaceWord(oldFull, oldBase, newBase) : oldFull;
        sys.setName(full);
        changed.add("system '" + oldFull + "' -> '" + sys.getName() + "'");

        // Generic body names that start with the old system name: "Corvus IV" -> "<new> IV", "Corvus IV-A", ...
        Pattern prefix = Pattern.compile("^" + Pattern.quote(oldBase) + "(?=$|[\\s\\-,])");
        for (SectorEntityToken e : new ArrayList<>(sys.getAllEntities())) {
            if (e instanceof CampaignFleetAPI) continue;
            if (e instanceof JumpPointAPI) {
                String n = replaceWord(e.getName(), oldBase, newBase);
                if (n != null && !n.equals(e.getName())) {
                    changed.add("jump point '" + e.getName() + "' -> '" + n + "'");
                    e.setName(n);
                }
                continue;
            }
            if (e instanceof CampaignTerrainAPI) {
                CampaignTerrainAPI t = (CampaignTerrainAPI) e;
                String tn = t.getPlugin() == null ? null : t.getPlugin().getTerrainName();
                if (tn != null && prefix.matcher(tn).find()) {
                    String n = prefix.matcher(tn).replaceFirst(Matcher.quoteReplacement(newBase));
                    t.getPlugin().setTerrainName(n);
                    changed.add("'" + tn + "' -> '" + n + "'");
                }
                continue;
            }
            String name = e.getName();
            if (name == null || !prefix.matcher(name).find()) continue;
            String n = prefix.matcher(name).replaceFirst(Matcher.quoteReplacement(newBase));
            e.setName(n);
            MarketAPI m = e.getMarket();
            boolean marketToo = false;
            if (m != null && m.getName() != null && prefix.matcher(m.getName()).find()) {
                m.setName(prefix.matcher(m.getName()).replaceFirst(Matcher.quoteReplacement(newBase)));
                marketToo = true;
            }
            changed.add("'" + name + "' -> '" + n + "'" + (marketToo ? " (+ market)" : ""));
        }

        // Hyperspace side: jump points into this system, the hyperspace anchor, and their dialog labels.
        for (JumpPointAPI jp : WsUtil.hyperJumpPointsInto(sys)) {
            String n = replaceWord(jp.getName(), oldBase, newBase);
            if (n != null && !n.equals(jp.getName())) {
                changed.add("hyperspace jump point '" + jp.getName() + "' -> '" + n + "'");
                jp.setName(n);
            }
            if (jp.getDestinations() == null) continue;
            for (JumpPointAPI.JumpDestination d : jp.getDestinations()) {
                String label = d.getLabelInInteractionDialog();
                if (label != null && word(oldBase).matcher(label).find())
                    d.setLabelInInteractionDialog(replaceWord(label, oldBase, newBase));
            }
        }
        SectorEntityToken anchor = sys.getHyperspaceAnchor();
        if (anchor != null && anchor.getName() != null && word(oldBase).matcher(anchor.getName()).find()) {
            String n = replaceWord(anchor.getName(), oldBase, newBase);
            changed.add("hyperspace anchor '" + anchor.getName() + "' -> '" + n + "'");
            anchor.setName(n);
        }
        WsUtil.invalidateCache();
        WsUtil.print("Renamed star system " + oldBase + " to " + newBase + " (" + changed.size() + " names changed):");
        for (String c : changed) WsUtil.printRaw("  - " + c);
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        // Only the current name is completed; the new name is free text.
        boolean factionMode = !previous.isEmpty() && previous.get(0).equalsIgnoreCase("faction");
        boolean keyword = !previous.isEmpty() && (factionMode
                || previous.get(0).equalsIgnoreCase("system") || previous.get(0).equalsIgnoreCase("star"));
        if (parameter > 1 || (parameter == 1 && !keyword)) return new ArrayList<>();
        if (factionMode) return WsUtil.factionSuggestions();
        List<String> l = WsUtil.bodySuggestions(WsUtil.Kind.ANY);
        l.addAll(WsUtil.systemSuggestions());
        if (parameter == 0) l.addAll(WsUtil.list("system", "star", "faction"));
        return l;
    }
}
