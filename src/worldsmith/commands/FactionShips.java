package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.FactionCharacter;
import worldsmith.FactionSlots;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Which ships a faction made with spawnfaction builds: one tech style, everything, or another faction's. */
public class FactionShips implements BaseCommandWithSuggestion {
    private static final String USAGE = "factionships <faction> [lowtech|midline|hightech|pirate|all|like <faction>]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        if (parts.isEmpty()) {
            WsUtil.print("Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        FactionAPI f = FactionCharacter.spawnedFaction(parts.get(0));
        if (f == null) return CommandResult.ERROR;
        String who = f.getDisplayNameWithArticle();
        if (parts.size() == 1) {
            WsUtil.print(Character.toUpperCase(who.charAt(0)) + who.substring(1) + " builds " + f.getKnownShips().size()
                    + " kinds of ships (" + FactionCharacter.describe(f) + "). Change it with: " + USAGE);
            return CommandResult.SUCCESS;
        }
        String style = parts.get(1).toLowerCase(Locale.ROOT);
        if (style.equals("like")) {
            if (parts.size() < 3) {
                WsUtil.print("Name the faction to copy: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
            FactionAPI model = WsUtil.parseFaction(parts.get(2));
            if (model == null) return CommandResult.ERROR;
            if (!FactionSlots.isGoodTemplate(model)) {
                WsUtil.print(model.getDisplayName() + " can't be copied (it has no ships of its own, or is you).");
                return CommandResult.ERROR;
            }
            f.getMemoryWithoutUpdate().set(FactionSlots.MEM_TEMPLATE, model.getId());
            f.getMemoryWithoutUpdate().unset(FactionCharacter.MEM_SHIPS);
            FactionSlots.copyTech(model, f);
            FactionCharacter.applyDoctrine(f);
            WsUtil.print(Character.toUpperCase(who.charAt(0)) + who.substring(1) + " now builds the ships of "
                    + model.getDisplayNameWithArticle() + " (" + f.getKnownShips().size() + " kinds) and fights like them"
                    + " (plus any factiondoctrine presets).");
            return CommandResult.SUCCESS;
        }
        if (!FactionCharacter.SHIP_STYLES.contains(style)) {
            WsUtil.print("Unknown ship style '" + parts.get(1) + "'. Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        int count = FactionCharacter.applyShips(f, style);
        if (count == 0) {
            WsUtil.print("Fewer than 5 " + style + " ships are in use in this sector, too few to build fleets from."
                    + " Nothing was changed.");
            return CommandResult.ERROR;
        }
        f.getMemoryWithoutUpdate().set(FactionCharacter.MEM_SHIPS, style);
        WsUtil.print(Character.toUpperCase(who.charAt(0)) + who.substring(1) + " now builds only " + style + " ships ("
                + count + " kinds, e.g. " + examples(FactionCharacter.styleShips(style)) + "). New fleets use them;"
                + " fleets already out keep their ships.");
        return CommandResult.SUCCESS;
    }

    private static String examples(Set<String> ids) {
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            ShipHullSpecAPI hull = Global.getSettings().getHullSpec(id);
            if (hull == null || hull.isDHull()) continue;
            names.add(hull.getHullName());
            if (names.size() >= 5) break;
        }
        return String.join(", ", names);
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.factionSuggestions();
        if (parameter == 1) {
            List<String> l = new ArrayList<>(FactionCharacter.SHIP_STYLES);
            l.add("like");
            return l;
        }
        return WsUtil.factionSuggestions();
    }
}
