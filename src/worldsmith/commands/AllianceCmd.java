package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.SectorDiplomacy;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lists, dissolves or renames Nexerelin alliances. Nexerelin's own commands create alliances and make one
 * faction leave (createalliance, leavealliance); this adds what they don't cover.
 */
public class AllianceCmd implements BaseCommandWithSuggestion {
    private static final String USAGE = "alliance list | alliance dissolve <alliance or member> | alliance rename <alliance or member> > <new name>";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String a = args.trim();
        String verb = a.isEmpty() ? "list" : a.split("\\s+")[0].toLowerCase(Locale.ROOT);
        String rest = a.isEmpty() ? "" : a.substring(a.split("\\s+")[0].length()).trim();
        switch (verb) {
            case "list":
                list();
                return CommandResult.SUCCESS;
            case "dissolve": {
                Alliance al = find(rest);
                if (al == null) return CommandResult.ERROR;
                String name = al.getName();
                int members = al.getMembersCopy().size();
                AllianceManager.getManager().dissolveAlliance(al, false);
                WsUtil.print("Dissolved the " + name + " (" + members + " members). Its members keep their relations;"
                        + " Nexerelin may form new alliances later.");
                return CommandResult.SUCCESS;
            }
            case "rename": {
                int gt = rest.indexOf('>');
                if (gt < 0) {
                    WsUtil.print("Put a > between the alliance and its new name. Usage: " + USAGE);
                    return CommandResult.BAD_SYNTAX;
                }
                String newName = rest.substring(gt + 1).trim().replace('_', ' ');
                if (newName.isEmpty() || newName.length() > 50) {
                    WsUtil.print("The new name must be 1-50 characters.");
                    return CommandResult.BAD_SYNTAX;
                }
                Alliance al = find(rest.substring(0, gt).trim());
                if (al == null) return CommandResult.ERROR;
                if (AllianceManager.getAllianceByName(newName) != null) {
                    WsUtil.print("There already is an alliance called " + newName + ".");
                    return CommandResult.ERROR;
                }
                String old = al.getName();
                AllianceManager.renameAlliance(al, newName);
                WsUtil.print("Renamed the alliance " + old + " to " + newName + ".");
                return CommandResult.SUCCESS;
            }
            default:
                WsUtil.print("Usage: " + USAGE + "   (Nexerelin's createalliance and leavealliance add and remove members)");
                return CommandResult.BAD_SYNTAX;
        }
    }

    private static void list() {
        List<Alliance> all = AllianceManager.getAllianceList();
        if (all.isEmpty()) {
            WsUtil.print("There are no alliances. Nexerelin's createalliance <faction> <faction> makes one.");
            return;
        }
        StringBuilder sb = new StringBuilder(WsUtil.PREFIX + all.size() + " alliances:");
        for (Alliance al : all) {
            List<String> members = new ArrayList<>();
            for (String id : al.getMembersCopy()) members.add(SectorDiplomacy.label(Global.getSector().getFaction(id)));
            sb.append("\n  ").append(al.getName()).append(" (").append(al.getAlignment().name().toLowerCase(Locale.ROOT))
                    .append("): ").append(String.join(", ", members));
        }
        Console.showMessage(sb.toString());
    }

    /** An alliance by its exact name, or the alliance a faction belongs to. Prints why if none. */
    private static Alliance find(String query) {
        if (query.isEmpty()) {
            WsUtil.print("Name the alliance or one of its members. Usage: " + USAGE);
            return null;
        }
        for (Alliance al : AllianceManager.getAllianceList()) {
            if (WsUtil.norm(al.getName()).equals(WsUtil.norm(query))) return al;
        }
        FactionAPI f = WsUtil.parseFaction(query.replace(' ', '_'));
        if (f == null) return null;
        Alliance al = AllianceManager.getFactionAlliance(f.getId());
        if (al == null) WsUtil.print(f.getDisplayName() + " is not in an alliance ('alliance list' shows them).");
        return al;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.list("list", "dissolve", "rename");
        return WsUtil.factionSuggestions();
    }
}
