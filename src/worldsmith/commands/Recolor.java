package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.WsUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Tints a planet, moon or star: a named color, a hex color, a random one, or back to its type's standard colors.
 * Purely cosmetic; the colors are part of the planet and stay in the save.
 */
public class Recolor implements BaseCommandWithSuggestion {
    private static final String USAGE = "recolor [planet] <color|#RRGGBB|random|reset>";
    private static final Map<String, Color> NAMED = new LinkedHashMap<>();

    static {
        NAMED.put("red", new Color(230, 70, 60));
        NAMED.put("orange", new Color(240, 150, 60));
        NAMED.put("yellow", new Color(240, 220, 90));
        NAMED.put("gold", new Color(220, 180, 80));
        NAMED.put("green", new Color(100, 210, 100));
        NAMED.put("teal", new Color(60, 200, 190));
        NAMED.put("cyan", new Color(110, 230, 240));
        NAMED.put("blue", new Color(80, 130, 245));
        NAMED.put("purple", new Color(160, 100, 235));
        NAMED.put("pink", new Color(240, 120, 190));
        NAMED.put("white", new Color(250, 250, 250));
        NAMED.put("grey", new Color(150, 150, 155));
        NAMED.put("brown", new Color(150, 105, 70));
        NAMED.put("black", new Color(45, 45, 50));
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        if (parts.isEmpty()) {
            WsUtil.print("Usage: " + USAGE + "   colors: " + String.join(", ", NAMED.keySet()));
            return CommandResult.BAD_SYNTAX;
        }
        String colorWord = parts.remove(parts.size() - 1).toLowerCase(Locale.ROOT);
        Color color = null;
        boolean reset = colorWord.equals("reset");
        if (!reset) {
            color = parse(colorWord);
            if (color == null) {
                WsUtil.print("The last word must be a color (" + String.join(", ", NAMED.keySet())
                        + "), a hex color like #3080ff, 'random' or 'reset'. Usage: " + USAGE);
                return CommandResult.BAD_SYNTAX;
            }
        }
        String name = WsUtil.join(parts, 0, parts.size());
        SectorEntityToken target = name.isEmpty() ? WsUtil.dialogTarget(context) : WsUtil.findBody(name, WsUtil.Kind.ANY, "planet, moon or star");
        if (target == null) {
            if (name.isEmpty()) WsUtil.print("No planet given and no planet dialog is open. Usage: " + USAGE);
            return CommandResult.ERROR;
        }
        if (!(target instanceof PlanetAPI)) {
            WsUtil.print(target.getName() + " is a station; only planets, moons and stars can be recolored.");
            return CommandResult.ERROR;
        }
        PlanetAPI planet = (PlanetAPI) target;
        PlanetSpecAPI spec = planet.getSpec();
        if (reset) {
            PlanetSpecAPI base = baseSpec(spec.getPlanetType());
            if (base == null) {
                WsUtil.print("Could not find the standard colors of " + spec.getName() + ".");
                return CommandResult.ERROR;
            }
            spec.setPlanetColor(base.getPlanetColor());
            spec.setAtmosphereColor(base.getAtmosphereColor());
            spec.setCloudColor(base.getCloudColor());
            spec.setIconColor(base.getIconColor());
            if (planet.isStar()) spec.setCoronaColor(base.getCoronaColor());
        } else {
            spec.setPlanetColor(color);
            if (spec.getAtmosphereColor() != null) spec.setAtmosphereColor(blend(spec.getAtmosphereColor(), color, 0.6f));
            if (spec.getCloudColor() != null) spec.setCloudColor(blend(spec.getCloudColor(), color, 0.3f));
            if (spec.getIconColor() != null) spec.setIconColor(blend(spec.getIconColor(), color, 0.7f));
            if (planet.isStar() && spec.getCoronaColor() != null) spec.setCoronaColor(blend(spec.getCoronaColor(), color, 0.7f));
        }
        planet.applySpecChanges();
        WsUtil.print((reset ? "Gave " + planet.getName() + " the standard colors of a " + spec.getName()
                : "Recolored " + planet.getName() + " " + hex(color)) + " (" + WsUtil.systemName(planet) + ").");
        return CommandResult.SUCCESS;
    }

    /** Mixes {@code tint} into {@code base}, keeping base's transparency. */
    private static Color blend(Color base, Color tint, float amount) {
        return Misc.setAlpha(Misc.interpolateColor(base, tint, amount), base.getAlpha());
    }

    private static Color parse(String w) {
        if (NAMED.containsKey(w)) return NAMED.get(w);
        if (w.equals("gray")) return NAMED.get("grey");
        if (w.equals("random")) {
            Random r = new Random();
            return Color.getHSBColor(r.nextFloat(), 0.45f + 0.35f * r.nextFloat(), 0.8f + 0.2f * r.nextFloat());
        }
        String h = w.startsWith("#") ? w.substring(1) : w;
        if (h.length() != 6) return null;
        try {
            return new Color(Integer.parseInt(h, 16));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String hex(Color c) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static PlanetSpecAPI baseSpec(String type) {
        for (PlanetSpecAPI s : Global.getSettings().getAllPlanetSpecs()) {
            if (s.getPlanetType().equals(type)) return s;
        }
        return null;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        List<String> l = new ArrayList<>(NAMED.keySet());
        l.add("random");
        l.add("reset");
        if (parameter == 0) l.addAll(WsUtil.bodySuggestions(WsUtil.Kind.ANY));
        return l;
    }
}
