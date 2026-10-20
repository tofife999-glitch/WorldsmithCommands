package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.alliances.Alliance;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.SectorDiplomacy;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Who is at war with whom, the alliances, and how war-weary each faction is. */
public class Wars implements BaseCommandWithSuggestion {
    private static final String USAGE = "wars [faction]";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<FactionAPI> factions = SectorDiplomacy.colonyFactions(true, true);
        String a = args.trim();
        if (!a.isEmpty()) {
            FactionAPI f = WsUtil.parseFaction(a.replace(' ', '_'));
            if (f == null) return CommandResult.ERROR;
            showOne(f, factions);
            return CommandResult.SUCCESS;
        }
        StringBuilder sb = new StringBuilder();
        int wars = 0;
        List<String> peaceful = new ArrayList<>();
        for (FactionAPI f : factions) {
            List<String> enemies = new ArrayList<>();
            for (FactionAPI o : factions) {
                if (o != f && SectorDiplomacy.atWar(f, o)) enemies.add(SectorDiplomacy.label(o));
            }
            wars += enemies.size();
            if (enemies.isEmpty()) {
                peaceful.add(SectorDiplomacy.label(f));
                continue;
            }
            sb.append("\n  ").append(SectorDiplomacy.label(f)).append(details(f)).append(": at war with ")
                    .append(String.join(", ", enemies));
        }
        sb.insert(0, WsUtil.PREFIX + "Wars between the " + factions.size() + " factions with colonies (" + wars / 2
                + " wars; 'wars <faction>' shows one faction's relations):");
        if (!peaceful.isEmpty()) sb.append("\n  At peace with everyone: ").append(String.join(", ", peaceful));
        List<Alliance> alliances = AllianceManager.getAllianceList();
        if (alliances.isEmpty()) {
            sb.append("\nNo alliances.");
        } else {
            sb.append("\nAlliances:");
            for (Alliance al : alliances) {
                List<String> members = new ArrayList<>();
                for (String id : al.getMembersCopy()) members.add(SectorDiplomacy.label(Global.getSector().getFaction(id)));
                sb.append("\n  ").append(al.getName()).append(": ").append(String.join(", ", members));
            }
        }
        Console.showMessage(sb.toString());
        return CommandResult.SUCCESS;
    }

    private static String details(FactionAPI f) {
        List<String> d = new ArrayList<>();
        Alliance al = AllianceManager.getFactionAlliance(f.getId());
        if (al != null) d.add(al.getName());
        float weariness = DiplomacyManager.getWarWeariness(f.getId());
        if (weariness >= 1f) d.add("war weariness " + Math.round(weariness));
        return d.isEmpty() ? "" : " (" + String.join(", ", d) + ")";
    }

    private static void showOne(FactionAPI f, List<FactionAPI> factions) {
        String who = f.isPlayerFaction() ? SectorDiplomacy.label(f) : f.getDisplayNameWithArticle();
        StringBuilder sb = new StringBuilder(WsUtil.PREFIX + "Relations of " + who + " (" + f.getId() + ")"
                + details(f) + ", from -100 to 100:");
        List<FactionAPI> sorted = new ArrayList<>(factions);
        sorted.remove(f);
        sorted.sort((x, y) -> Float.compare(f.getRelationship(x.getId()), f.getRelationship(y.getId())));
        for (FactionAPI o : sorted) {
            float rel = f.getRelationship(o.getId());
            RepLevel level = f.getRelationshipLevel(o);
            sb.append("\n  ").append(Math.round(rel * 100f)).append("  ").append(SectorDiplomacy.label(o)).append(" - ")
                    .append(level.getDisplayName().toLowerCase(Locale.ROOT))
                    .append(SectorDiplomacy.atWar(f, o) ? " (at war)" : "");
        }
        Console.showMessage(sb.toString());
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.factionSuggestions();
        return WsUtil.list();
    }
}
