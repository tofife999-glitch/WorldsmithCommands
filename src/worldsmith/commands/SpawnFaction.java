package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.FactionSlots;
import worldsmith.FactionSpawner;
import worldsmith.FactionSpawner.Plan;
import worldsmith.FactionSpawner.Size;
import worldsmith.WsDialog;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Founds a new faction in one of the mod's 8 blank faction slots: its own name, a random player flag, ships and
 * doctrine copied from an existing faction, empty lore, and 1-5 colonies. Nexerelin then runs it like any
 * other faction. Where and how big are asked in a dialog unless they are typed.
 */
public class SpawnFaction implements BaseCommandWithSuggestion {
    private static final String USAGE = "spawnfaction <name> [here|random] [small|medium|large] [color] [like <faction>]";

    /** Everything typed, collected before the dialog fills in the rest. */
    private static final class Request {
        String name;
        Boolean here;
        Size size;
        FactionAPI slot;
        FactionAPI template;
        StarSystemAPI system;
        PlanetAPI capital;
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        if (parts.isEmpty() || (parts.size() == 1 && parts.get(0).equalsIgnoreCase("list"))) {
            if (parts.isEmpty()) WsUtil.print("Usage: " + USAGE);
            listSlots();
            return parts.isEmpty() ? CommandResult.BAD_SYNTAX : CommandResult.SUCCESS;
        }
        boolean yes = WsUtil.endsWithYes(parts);
        if (yes) parts.remove(parts.size() - 1);
        Request r = new Request();

        String templateToken = null;
        for (int i = 1; i < parts.size(); i++) {
            if (!parts.get(i).equalsIgnoreCase("like")) continue;
            if (i + 1 >= parts.size()) {
                WsUtil.print("'like' needs a faction after it, e.g. like hegemony. Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
            templateToken = parts.get(i + 1);
            parts.remove(i + 1);
            parts.remove(i);
            break;
        }
        // The name is everything up to the first option word after it.
        int firstOption = parts.size();
        for (int i = 1; i < parts.size(); i++) {
            if (isOption(parts.get(i))) {
                firstOption = i;
                break;
            }
        }
        String color = null;
        for (int i = firstOption; i < parts.size(); i++) {
            String w = parts.get(i).toLowerCase(Locale.ROOT);
            if (w.equals("here") || w.equals("random")) r.here = w.equals("here");
            else if (Size.parse(w) != null) r.size = Size.parse(w);
            else if (FactionSlots.isColor(w)) color = w;
            else {
                WsUtil.print("I didn't understand '" + parts.get(i) + "'. If it belongs to the name, write the name with"
                        + " underscores (The_Large_Union). Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
        }
        r.name = WsUtil.join(parts, 0, firstOption).replace('_', ' ').trim().replaceAll("\\s+", " ");
        if (isOption(r.name) || r.name.equalsIgnoreCase("like")) {
            WsUtil.print("Give the faction a name first. Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        if (r.name.isEmpty() || r.name.length() > 40) {
            WsUtil.print("The faction needs a name of 1-40 characters. Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (WsUtil.norm(f.getDisplayName()).equals(WsUtil.norm(r.name)) || f.getId().equalsIgnoreCase(r.name)) {
                WsUtil.print("There already is a faction called '" + f.getDisplayName() + "'. Pick another name.");
                return CommandResult.ERROR;
            }
        }
        if (templateToken != null) {
            r.template = WsUtil.parseFaction(templateToken);
            if (r.template == null) return CommandResult.ERROR;
            if (!FactionSlots.isGoodTemplate(r.template)) {
                WsUtil.print(r.template.getDisplayName() + " can't be copied (it has no ships of its own, or is you).");
                return CommandResult.ERROR;
            }
        }
        if (color != null) {
            r.slot = FactionSlots.byColor(color);
            if (r.slot == null || !FactionSlots.isFree(r.slot)) {
                WsUtil.print("The " + color + " faction slot is taken. Free colors: " + freeColors() + ".");
                return CommandResult.ERROR;
            }
        } else {
            r.slot = randomFreeSlot(new Random());
            if (r.slot == null) {
                WsUtil.print("All 8 faction slots are taken. A slot frees up again once its faction has no colonies left.");
                listSlots();
                return CommandResult.ERROR;
            }
        }

        r.system = WsUtil.currentSystem(context);
        SectorEntityToken d = WsUtil.dialogTarget(context);
        if (d instanceof PlanetAPI && FactionSpawner.candidate((PlanetAPI) d)) r.capital = (PlanetAPI) d;
        if (Boolean.TRUE.equals(r.here) && r.system == null) {
            WsUtil.print("'here' needs you to be in a star system (or a planet's dialog to be open). Use 'random' instead.");
            return CommandResult.ERROR;
        }

        String retype = "spawnfaction " + WsUtil.token(r.name) + " here|random small|medium|large yes";
        if (yes) {
            if (r.here == null || r.size == null) {
                WsUtil.print("With 'yes', also give here or random and small, medium or large. Or leave out 'yes' to"
                        + " choose in a popup.");
                return CommandResult.BAD_SYNTAX;
            }
            WsUtil.cancelPending("spawnfaction");
            Plan plan = plan(r, new Random());
            if (plan.problem != null) {
                WsUtil.print("Can't found " + r.name + ": " + plan.problem);
                return CommandResult.ERROR;
            }
            WsUtil.print(FactionSpawner.spawn(r.slot, r.name, r.template, plan, new Random()));
            return CommandResult.SUCCESS;
        }
        WsUtil.openDialog("spawnfaction", new SpawnDialog(r), retype);
        WsUtil.print("Close the console to set up " + r.name + " in a popup (leave any open planet dialog first),"
                + " or type: " + retype);
        return CommandResult.SUCCESS;
    }

    private static boolean isOption(String w) {
        String l = w.toLowerCase(Locale.ROOT);
        return l.equals("here") || l.equals("random") || Size.parse(l) != null || FactionSlots.isColor(l);
    }

    private static Plan plan(Request r, Random random) {
        if (Boolean.TRUE.equals(r.here)) return FactionSpawner.planHere(r.system, r.capital, r.size);
        return FactionSpawner.planRandom(r.size, random);
    }

    private static FactionAPI randomFreeSlot(Random random) {
        WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<>(random);
        for (FactionAPI f : FactionSlots.slots()) {
            if (FactionSlots.isFree(f)) picker.add(f);
        }
        return picker.pick();
    }

    private static String freeColors() {
        List<String> l = new ArrayList<>();
        for (FactionAPI f : FactionSlots.slots()) {
            if (FactionSlots.isFree(f)) l.add(FactionSlots.colorOf(f));
        }
        return l.isEmpty() ? "none" : String.join(", ", l);
    }

    private static void listSlots() {
        WsUtil.print("Faction slots (a slot frees up again once its faction has no colonies left):");
        for (FactionAPI f : FactionSlots.slots()) {
            String state;
            if (FactionSlots.isUnused(f)) state = "free";
            else if (FactionSlots.isFree(f)) state = "free again (was " + f.getDisplayName() + ")";
            else state = f.getDisplayName() + " (ID " + f.getId() + ")";
            WsUtil.printRaw("  " + FactionSlots.colorOf(f) + ": " + state);
        }
    }

    /** where -> how big -> preview (with "pick another place" for random) -> found. */
    private static final class SpawnDialog extends WsDialog {
        private static final Object HERE = new Object();
        private static final Object RANDOM = new Object();
        private static final Object FOUND = new Object();
        private static final Object REROLL = new Object();
        private final Request r;
        private final Random random = new Random();
        private Plan plan;

        SpawnDialog(Request r) {
            this.r = r;
        }

        @Override
        protected void start() {
            next();
        }

        private void next() {
            if (r.here == null) {
                page("Where should " + r.name + " settle?");
                if (r.system != null) option("Here, in " + r.system.getName(), HERE);
                option("At a random place in the sector", RANDOM);
                cancelOption("Cancel");
                return;
            }
            if (r.size == null) {
                page("How big should " + r.name + " be?");
                for (Size s : Size.values()) {
                    option(s.key.substring(0, 1).toUpperCase(Locale.ROOT) + s.key.substring(1) + ": " + s.describe(), s);
                }
                cancelOption("Cancel");
                return;
            }
            plan = plan(r, random);
            if (plan.problem != null) {
                page("Can't found " + r.name + ": " + plan.problem);
                cancelOption("Close");
                return;
            }
            String like = r.template != null ? r.template.getDisplayNameWithArticle() : "a random existing faction";
            page("Found " + r.name + " (" + FactionSlots.colorOf(r.slot) + ")?\n"
                    + "Colonies: " + plan.describe() + ".\n"
                    + (plan.planets.size() < r.size.sizes.length ? "Only " + plan.planets.size()
                    + " suitable planets were found nearby.\n" : "")
                    + "Ships and tactics like " + like + ", a random player flag, and empty lore. Nexerelin then runs it"
                    + " like any other faction.");
            option("Found " + r.name, FOUND);
            if (!Boolean.TRUE.equals(r.here)) option("Pick another place", REROLL);
            cancelOption("Cancel");
        }

        @Override
        protected void chosen(Object data) {
            if (data == HERE || data == RANDOM) {
                r.here = data == HERE;
                next();
            } else if (data instanceof Size) {
                r.size = (Size) data;
                next();
            } else if (data == REROLL) {
                next();
            } else if (data == FOUND) {
                close();
                final Plan p = plan;
                WsUtil.runSafely("spawnfaction", new Runnable() {
                    @Override
                    public void run() {
                        if (!FactionSlots.isFree(r.slot)) {
                            WsUtil.report("Did not create " + r.name + ": its faction slot was taken in the meantime.");
                            return;
                        }
                        WsUtil.report(FactionSpawner.spawn(r.slot, r.name, r.template, p, random));
                    }
                });
            }
        }
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.list("list");
        List<String> l = WsUtil.list("here", "random", "small", "medium", "large", "like");
        l.addAll(WsUtil.list(FactionSlots.COLORS));
        if (!previous.isEmpty() && previous.get(previous.size() - 1).equalsIgnoreCase("like"))
            return WsUtil.factionSuggestions();
        return l;
    }
}
