package worldsmith.commands;

import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.Console;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lists every star type, including modded ones. */
public class StarTypes implements BaseCommandWithSuggestion {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        String filter = WsUtil.norm(args);
        List<String> normal = new ArrayList<>();
        List<String> giants = new ArrayList<>();
        List<String> dwarfs = new ArrayList<>();
        List<String> special = new ArrayList<>();
        int count = 0;
        for (PlanetSpecAPI s : WsUtil.starTypes()) {
            String id = s.getPlanetType();
            String name = s.getName();
            if (!filter.isEmpty() && !WsUtil.norm(id).contains(filter) && !WsUtil.norm(name).contains(filter))
                continue;
            count++;
            String entry = "  " + id + " - " + name;
            String lower = (id + " " + name).toLowerCase(Locale.ROOT);
            if (WsUtil.isSpecialStar(s)) {
                special.add(entry + " (can't be used by newsystem/changestar)");
            } else if (!WsUtil.isUsableStarType(s)) {
                special.add(entry + " (no generator data: can't be used by newsystem/changestar)");
            } else if (lower.contains("giant")) {
                giants.add(entry);
            } else if (lower.contains("dwarf")) {
                dwarfs.add(entry);
            } else {
                normal.add(entry);
            }
        }
        if (count == 0) {
            WsUtil.print("No star types match '" + args.trim() + "'.");
            return CommandResult.SUCCESS;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(WsUtil.PREFIX).append(count).append(" star types")
                .append(filter.isEmpty() ? "" : " matching '" + args.trim() + "'").append(" (type ID - name):\n");
        section(sb, "Normal stars", normal);
        section(sb, "Giants", giants);
        section(sb, "Dwarfs", dwarfs);
        section(sb, "Special", special);
        Console.showMessage(sb.toString());
        return CommandResult.SUCCESS;
    }

    private static void section(StringBuilder sb, String title, List<String> lines) {
        if (lines.isEmpty()) return;
        sb.append(title).append(":\n");
        for (String l : lines) sb.append(l).append("\n");
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.starTypeSuggestions();
        return new ArrayList<>();
    }
}
