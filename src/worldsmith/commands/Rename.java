package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Rename implements BaseCommandWithSuggestion {
    private static final String USAGE = "rename <current name> <new name>";

    private static void printHowTo() {
        WsUtil.print("How to use rename:");
        WsUtil.printRaw("  rename <current name> <new name>     e.g.  rename Glith_I Hakeball   (Tab completes the current name)");
        WsUtil.printRaw("  rename <current name> > <new name>   e.g.  rename Glith I > Hakeball (use > if you're unsure)");
        WsUtil.printRaw("  rename <new name>                    with a planet's or station's dialog open");
        WsUtil.printRaw("  Star systems work too: rename Glith > Hakeball renames the system, its star and planets like Glith I.");
    }

    private static String cleanNewName(String s) {
        return s.trim().replace('_', ' ').replaceAll("\\s+", " ");
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String a = args.trim();
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
            if (oldName.isEmpty()) {
                SectorEntityToken d = WsUtil.resolveTarget("", context, WsUtil.Kind.ANY, "planet or station", USAGE);
                if (d == null) return CommandResult.ERROR;
                renameBody(d, newName);
                return CommandResult.SUCCESS;
            }
            return renameNamed(oldName, newName);
        }

        // No '>': with a planet/station dialog open, everything typed is the new name for it.
        SectorEntityToken d = WsUtil.dialogTarget(context);
        if (d != null) {
            renameBody(d, cleanNewName(a));
            return CommandResult.SUCCESS;
        }
        // Otherwise the longest leading part that is exactly the name (or ID) of a planet, station or
        // system is the current name, and the rest is the new name.
        List<String> parts = WsUtil.splitArgs(a);
        for (int k = parts.size() - 1; k >= 1; k--) {
            String oldName = WsUtil.join(parts, 0, k);
            if (!WsUtil.exactMatches(oldName, WsUtil.allBodies()).isEmpty() || !WsUtil.exactSystems(oldName).isEmpty())
                return renameNamed(oldName, cleanNewName(WsUtil.join(parts, k, parts.size())));
        }
        if (parts.size() == 1) {
            WsUtil.print("Only one name was given and no planet dialog is open, so I don't know what to rename.");
        } else {
            WsUtil.print("The start of '" + a + "' isn't the name of any planet, station or system.");
            printDidYouMean(parts.get(0));
        }
        printHowTo();
        return CommandResult.BAD_SYNTAX;
    }

    private static CommandResult renameNamed(String oldName, String newName) {
        if (newName.isEmpty()) {
            printHowTo();
            return CommandResult.BAD_SYNTAX;
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
                renameSystem(sys, newName);
                return CommandResult.SUCCESS;
            }
            WsUtil.print("'" + oldName + "' is both a star system and a body. Use an ID:");
            WsUtil.printRaw("  - system: " + sys.getName() + " (ID " + sys.getId() + ")");
            for (SectorEntityToken b : bodies) WsUtil.printRaw("  - body: " + WsUtil.describe(b));
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

    /**
     * Hyperspace jump points that lead into the system: the system's autogenerated list (which can be null)
     * plus any hyperspace jump point with a destination inside the system, like vanilla's own cleanup code.
     */
    private static List<JumpPointAPI> hyperJumpPointsInto(StarSystemAPI sys) {
        List<JumpPointAPI> out = new ArrayList<>();
        List<JumpPointAPI> auto = sys.getAutogeneratedJumpPointsInHyper();
        if (auto != null) out.addAll(auto);
        for (SectorEntityToken t : Global.getSector().getHyperspace().getJumpPoints()) {
            if (!(t instanceof JumpPointAPI) || out.contains(t)) continue;
            JumpPointAPI jp = (JumpPointAPI) t;
            boolean intoSystem = jp.getDestinationVisualEntity() != null
                    && jp.getDestinationVisualEntity().getContainingLocation() == sys;
            if (jp.getDestinations() != null) {
                for (JumpPointAPI.JumpDestination d : jp.getDestinations()) {
                    if (d.getDestination() != null && d.getDestination().getContainingLocation() == sys) intoSystem = true;
                }
            }
            if (intoSystem) out.add(jp);
        }
        return out;
    }

    private static boolean leadsTo(JumpPointAPI jp, SectorEntityToken target) {
        if (jp.getDestinationVisualEntity() == target) return true;
        if (jp.getDestinations() == null) return false;
        for (JumpPointAPI.JumpDestination d : jp.getDestinations()) {
            if (d.getDestination() == target) return true;
        }
        return false;
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
            for (JumpPointAPI jp : hyperJumpPointsInto(sys)) {
                if (!leadsTo(jp, e)) continue;
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
        for (JumpPointAPI jp : hyperJumpPointsInto(sys)) {
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
        if (parameter != 0) return new ArrayList<>();
        List<String> l = WsUtil.bodySuggestions(WsUtil.Kind.ANY);
        l.addAll(WsUtil.systemSuggestions());
        return l;
    }
}
