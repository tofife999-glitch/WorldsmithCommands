package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.procgen.ConditionGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.PlanetGenDataSpec;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.Console;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.List;

public class PlanetTypes implements BaseCommandWithSuggestion {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        String filter = WsUtil.norm(args);
        ConditionGenDataSpec hab = (ConditionGenDataSpec) Global.getSettings()
                .getSpec(ConditionGenDataSpec.class, Conditions.HABITABLE, true);
        List<String> habitable = new ArrayList<>();
        List<String> hostile = new ArrayList<>();
        List<String> giants = new ArrayList<>();
        List<String> other = new ArrayList<>();
        int count = 0;
        for (PlanetSpecAPI s : WsUtil.planetTypes()) {
            String id = s.getPlanetType();
            String name = s.getName();
            if (!filter.isEmpty() && !WsUtil.norm(id).contains(filter) && !WsUtil.norm(name).contains(filter))
                continue;
            String entry = "  " + id + " - " + name;
            PlanetGenDataSpec gen = WsUtil.genData(id);
            String cat = gen == null ? null : gen.getCategory();
            if (s.isGasGiant() || "cat_giant".equals(cat)) giants.add(entry);
            else if (cat == null) other.add(entry + " (no generator data: terraform needs 'keep')");
            else if (getsHabitable(hab, id, cat)) habitable.add(entry);
            else hostile.add(entry);
            count++;
        }
        if (count == 0) {
            WsUtil.print("No planet types match '" + args.trim() + "'.");
            return CommandResult.SUCCESS;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(WsUtil.PREFIX).append(count).append(" planet types")
                .append(filter.isEmpty() ? "" : " matching '" + args.trim() + "'")
                .append(" (type ID - name):\n");
        section(sb, "Habitable", habitable);
        section(sb, "Hostile", hostile);
        section(sb, "Gas giants", giants);
        section(sb, "Other", other);
        Console.showMessage(sb.toString());
        return CommandResult.SUCCESS;
    }

    /** Same rule as the vanilla condition generator: the type's own weight, else its category's, else none. */
    private static boolean getsHabitable(ConditionGenDataSpec hab, String type, String category) {
        if (hab == null) return category.startsWith("cat_hab");
        if (hab.hasMultiplier(type)) return hab.getMultiplier(type) > 0f;
        return hab.hasMultiplier(category) && hab.getMultiplier(category) > 0f;
    }

    private static void section(StringBuilder sb, String title, List<String> lines) {
        if (lines.isEmpty()) return;
        sb.append(title).append(":\n");
        for (String l : lines) sb.append(l).append("\n");
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.typeSuggestions();
        return new ArrayList<>();
    }
}
