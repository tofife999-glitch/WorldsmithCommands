package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lwjgl.input.Keyboard;
import worldsmith.FactionIdentity;
import worldsmith.FactionSlots;
import worldsmith.WsDialog;
import worldsmith.WsUtil;

import java.util.List;
import java.util.Random;

/**
 * Changes any faction's flag. Without an option it opens a picker that shows the flags one by one; typed options
 * pick a random or numbered player flag, copy another faction's flag, or reset it.
 */
public class FactionFlag implements BaseCommandWithSuggestion {
    private static final String USAGE = "factionflag <faction> [random|<number>|<other faction>|reset]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        List<String[]> flags = FactionIdentity.playerFlags();
        if (parts.size() == 1 && parts.get(0).equalsIgnoreCase("list")) {
            WsUtil.print(flags.size() + " player flags; 'factionflag <faction>' shows them as pictures. By number:");
            for (int i = 0; i < flags.size(); i++) {
                String logo = flags.get(i)[0];
                WsUtil.printRaw("  #" + (i + 1) + "  " + logo.substring(logo.lastIndexOf('/') + 1));
            }
            return CommandResult.SUCCESS;
        }
        if (parts.isEmpty() || parts.size() > 2) {
            WsUtil.print("Usage: " + USAGE + "   (write multi-word faction names with underscores)");
            return CommandResult.BAD_SYNTAX;
        }
        FactionAPI f = WsUtil.parseFaction(parts.get(0));
        if (f == null) return CommandResult.ERROR;
        if (FactionSlots.isUnused(f)) {
            WsUtil.print(f.getId() + " is an unused faction slot; spawnfaction fills it.");
            return CommandResult.ERROR;
        }
        if (parts.size() == 1) {
            WsUtil.openDialog("factionflag", new FlagPicker(f, flags), "factionflag " + f.getId() + " <number>");
            WsUtil.print("Close the console to pick a flag for " + f.getDisplayNameWithArticle()
                    + " from the pictures (leave any open dialog first).");
            return CommandResult.SUCCESS;
        }
        String option = parts.get(1);
        String result;
        if (option.equalsIgnoreCase("reset")) {
            FactionIdentity.resetFlag(f);
            result = "its own flag again";
        } else if (option.equalsIgnoreCase("random")) {
            int i = FactionIdentity.randomFlagIndex(new Random());
            FactionIdentity.setFlag(f, flags.get(i)[0], flags.get(i)[1]);
            result = "player flag #" + (i + 1);
        } else if (WsUtil.isInt(option)) {
            int i = Integer.parseInt(option) - 1;
            if (i < 0 || i >= flags.size()) {
                WsUtil.print("There are " + flags.size() + " player flags; pick 1-" + flags.size() + ", or leave the number"
                        + " out to see them as pictures.");
                return CommandResult.BAD_SYNTAX;
            }
            FactionIdentity.setFlag(f, flags.get(i)[0], flags.get(i)[1]);
            result = "player flag #" + (i + 1);
        } else {
            FactionAPI other = WsUtil.parseFaction(option);
            if (other == null) return CommandResult.ERROR;
            FactionIdentity.setFlag(f, other.getLogo(), other.getCrest());
            result = "the flag of " + other.getDisplayNameWithArticle();
        }
        WsUtil.print("Changed the flag of " + f.getDisplayNameWithArticle() + " (" + f.getId() + ") to " + result + ".");
        return CommandResult.SUCCESS;
    }

    /** Shows the player flags one at a time, starting with the faction's current one. */
    private static final class FlagPicker extends WsDialog {
        private static final Object USE = new Object();
        private static final Object NEXT = new Object();
        private static final Object PREVIOUS = new Object();
        private static final Object RANDOM = new Object();
        private static final Object OWN = new Object();
        private final FactionAPI faction;
        private final List<String[]> flags;
        private final Random random = new Random();
        private int index = 0;

        FlagPicker(FactionAPI faction, List<String[]> flags) {
            this.faction = faction;
            this.flags = flags;
            for (int i = 0; i < flags.size(); i++) {
                if (flags.get(i)[0].equals(faction.getLogo())) index = i;
            }
        }

        @Override
        protected void start() {
            show();
        }

        private void show() {
            String[] flag = flags.get(index);
            boolean current = flag[0].equals(faction.getLogo());
            page("Flag for " + faction.getDisplayNameWithArticle() + ": player flag #" + (index + 1) + " of "
                    + flags.size() + (current ? " (its current flag)." : "."));
            FactionIdentity.loadTexture(flag[0]);
            float w = 410f;
            float h = 256f;
            SpriteAPI sprite = Global.getSettings().getSprite(flag[0]);
            if (sprite != null && sprite.getWidth() > 0 && sprite.getHeight() > 0) {
                w = sprite.getWidth();
                h = sprite.getHeight();
            }
            dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual(flag[0], w, h));
            option("Use this flag", USE);
            option("Next flag", NEXT);
            option("Previous flag", PREVIOUS);
            option("Random flag", RANDOM);
            option("Its own flag", OWN);
            cancelOption("Cancel");
            dialog.getOptionPanel().setShortcut(NEXT, Keyboard.KEY_RIGHT, false, false, false, false);
            dialog.getOptionPanel().setShortcut(PREVIOUS, Keyboard.KEY_LEFT, false, false, false, false);
        }

        @Override
        protected void chosen(Object data) {
            if (data == NEXT) {
                index = (index + 1) % flags.size();
                show();
            } else if (data == PREVIOUS) {
                index = (index + flags.size() - 1) % flags.size();
                show();
            } else if (data == RANDOM) {
                index = random.nextInt(flags.size());
                show();
            } else if (data == USE) {
                String[] flag = flags.get(index);
                FactionIdentity.setFlag(faction, flag[0], flag[1]);
                close();
                WsUtil.report("Changed the flag of " + faction.getDisplayNameWithArticle() + " to player flag #" + (index + 1) + ".");
            } else if (data == OWN) {
                FactionIdentity.resetFlag(faction);
                close();
                WsUtil.report("Gave " + faction.getDisplayNameWithArticle() + " its own flag back.");
            }
        }
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) {
            List<String> l = WsUtil.factionSuggestions();
            l.add("list");
            return l;
        }
        if (parameter == 1) {
            List<String> l = WsUtil.list("random", "reset");
            l.addAll(WsUtil.factionSuggestions());
            return l;
        }
        return WsUtil.list();
    }
}
