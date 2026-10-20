package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.impl.campaign.procgen.ConditionGenDataSpec;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.Console;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Plain-language explanations of the Worldsmith commands, their options and filters. */
public class WsHelp implements BaseCommandWithSuggestion {
    private static final Map<String, String> TOPICS = new LinkedHashMap<>();

    static {
        TOPICS.put("names",
                "Names: type them as shown on the map. Case doesn't matter and underscores count as spaces, so"
                        + " Corvus_IV = Corvus IV (Tab completes names this way). Planet, station and market IDs work"
                        + " too. Commands only act on an exact match; if several things share a name you get a list"
                        + " with their IDs. Without a name, most commands use the planet whose dialog is open.");
        TOPICS.put("findplanet",
                "findplanet <filters> searches every planet and moon and lists the nearest 20 matches. Combine"
                        + " any filters:\n"
                        + "  <condition>     a condition ID like farmland_rich, or a group like farmland, ore,\n"
                        + "                  rare_ore, organics, volatiles, ruins (any strength counts).\n"
                        + "                  'wshelp conditions' lists them all.\n"
                        + "  -<condition>    must NOT have it, e.g. -toxic_atmosphere\n"
                        + "  habitable       has the Habitable condition\n"
                        + "  uncolonized / colonized\n"
                        + "  type:<type>     planet type, e.g. type:jungle or type:giant ('planettypes' lists them)\n"
                        + "  hazard<150      hazard rating below 150% (also <=, >, >=). This is the hazard shown\n"
                        + "                  on the planet; higher hazard means more upkeep and slower growth.\n"
                        + "  within:20       at most 20 light-years from your fleet (the distance the sector map\n"
                        + "                  shows when you hover over a system). within:40 searches twice as far.\n"
                        + "  owner:<faction> colonies of that faction\n"
                        + "  all             also search planets you haven't surveyed (shows their conditions)\n"
                        + "Example: findplanet habitable farmland -toxic_atmosphere hazard<150 within:20");
        TOPICS.put("addobject",
                "addobject <type> places an object at your position, orbiting the star:\n"
                        + "  stable_location   an empty stable orbit; you can build a relay, buoy or array there\n"
                        + "  comm_relay        helps the stability of colonies in the system\n"
                        + "  nav_buoy          makes fleets travel faster in the system\n"
                        + "  sensor_array      gives better sensor range in the system\n"
                        + "  ..._makeshift     weaker player-built versions of the three above\n"
                        + "  inactive_gate     a gate; it works like other gates once you can activate gates\n"
                        + "Relays, buoys and arrays help whoever owns them; they start unclaimed, so you claim them\n"
                        + "like any other.\n"
                        + "Short names work too: stable, relay, buoy, sensor, gate. Remove with removeobject.");
        TOPICS.put("addring",
                "addring [planet] <belt|dust|ice|magfield> adds terrain that works like vanilla's:\n"
                        + "  belt       an asteroid belt with asteroids\n"
                        + "  dust, ice  a ring (dust or ice look)\n"
                        + "  magfield   a magnetic field\n"
                        + "Around the star it goes at your distance from the star. Around a planet it goes at your\n"
                        + "distance if you are near the planet, otherwise in the first free orbit; a magnetic field\n"
                        + "around a planet hugs the planet. It refuses if it would overlap another orbit.\n"
                        + "Remove with removering.");
        TOPICS.put("resize",
                "resizeplanet [planet] <size|percent|auto>. The size is the planet's radius, in the same units\n"
                        + "as orbit distances; planetinfo shows the current size. Keeping the same number changes\n"
                        + "nothing; twice the number (or 200%) makes it twice as wide, 50% half as wide.\n"
                        + "Vanilla sizes: rocky and habitable planets about 90-220, gas giants 250-400, moons\n"
                        + "usually 60-150. Allowed: 30-1000. 'auto' picks a normal size for the planet's type.\n"
                        + "Examples: resizeplanet Corvus_IV 200   resizeplanet Corvus_IV 150%   resizeplanet Corvus_IV auto");
        TOPICS.put("rename",
                "rename <current name> <new name>     e.g. rename Glith_I Hakeball\n"
                        + "rename <current name> > <new name>   use > if you're unsure where the current name ends\n"
                        + "rename <new name>                    with a planet's or station's dialog open\n"
                        + "rename system <system> <new name>    a whole star system: its star and planets named\n"
                        + "                                     after it (Glith I -> Hakeball I) are renamed too\n"
                        + "rename star <star> <new name>        only that star\n"
                        + "Typing a system's name without 'system' also renames the system when its star has the\n"
                        + "same name.");
        TOPICS.put("remove",
                "removeplanet <planet> and removemoon <moon>: delete an uninhabited planet or moon (asks first).\n"
                        + "  Refuses colonies (use destroycolony in its dialog first) and planets other things orbit.\n"
                        + "removering: removes the belt, ring or magnetic field around the star nearest to your\n"
                        + "  position. removering <planet> removes all of that planet's rings; add belt, ring or\n"
                        + "  magfield to remove only that kind.\n"
                        + "removeobject: removes the nearest stable location, relay, buoy, sensor array or gate in\n"
                        + "  your system (asks first). removeobject <type> takes the nearest of that type,\n"
                        + "  removeobject <name or ID> a specific one.");
        TOPICS.put("newsystem",
                "newsystem <name> [star type] [full|empty]: in hyperspace, creates a star system at your\n"
                        + "position. Without a star type it picks one at random ('startypes' lists them). A popup asks\n"
                        + "whether to generate planets, moons, rings and stable locations like a random system, or\n"
                        + "keep it empty; 'full' or 'empty' answers that in advance. The star is named\n"
                        + "'<name> Prime' so it can't be mixed up with the system. It must be at least 1.5\n"
                        + "light-years from other systems.");
        TOPICS.put("factions",
                "factionpower <faction>: colonies, stability, unrest, disrupted industries, garrison damage,\n"
                        + "  Nexerelin invasion points (attacks happen when these build up) and troop reserves, plus any\n"
                        + "  Worldsmith effects and how long they still last. 'factionpower <faction> clear' ends them.\n"
                        + "weakenfaction / strengthenfaction <faction> [1-3]: shows what will happen to each colony,\n"
                        + "  asks Yes/Cancel, then applies it. Everything wears off by itself; nothing is permanent.\n"
                        + "  Weaken 1: unrest for about 6 months.\n"
                        + "  Weaken 2: + 6-month growth freeze, weaker garrisons, defenses disrupted, fewer\n"
                        + "            invasion points.\n"
                        + "  Weaken 3: up to 3 years: 3-year growth freeze, stability -3 fading by 1 per year,\n"
                        + "            military and ship industries disrupted 1-2 years, drained troop reserves\n"
                        + "            and invasion points (their attacks pause for a year or more).\n"
                        + "  Strengthen 1: clears unrest and disruptions, lifts Worldsmith growth freezes.\n"
                        + "  Strengthen 2: + repaired garrisons, more troop reserves and invasion points,\n"
                        + "                stability +2 for 6 months.\n"
                        + "  Strengthen 3: + stability +3 fading by 1 per year, many more invasion points.");
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        String a = args.trim().toLowerCase(Locale.ROOT);
        if (a.startsWith("conditions") || a.startsWith("condition")) {
            listConditions(a.contains(" ") ? a.substring(a.indexOf(' ') + 1).trim() : "");
            return CommandResult.SUCCESS;
        }
        String key = resolve(a);
        if (key == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(WsUtil.PREFIX).append("Worldsmith help. Type 'wshelp <topic>':\n");
            sb.append("  names, findplanet, conditions, addobject, addring, resize, rename, remove, newsystem, factions\n");
            sb.append("'help worldsmith' lists all commands; 'help <command>' explains one command.");
            if (!a.isEmpty()) sb.insert(0, WsUtil.PREFIX + "No help topic '" + a + "'.\n");
            Console.showMessage(sb.toString());
            return CommandResult.SUCCESS;
        }
        Console.showMessage(WsUtil.PREFIX + TOPICS.get(key));
        return CommandResult.SUCCESS;
    }

    private static String resolve(String a) {
        if (a.isEmpty()) return null;
        if (TOPICS.containsKey(a)) return a;
        switch (a) {
            case "filters": case "filter": case "search": return "findplanet";
            case "objects": case "object": case "removeobject": return "addobject";
            case "rings": case "ring": case "belt": return "addring";
            case "resizeplanet": case "size": case "sizes": return "resize";
            case "removeplanet": case "removering": case "removemoon": case "removing": return "remove";
            case "system": case "systems": return "newsystem";
            case "faction": case "factionpower": case "weakenfaction": case "strengthenfaction": return "factions";
            default: return null;
        }
    }

    /** Condition IDs and names, grouped like the planet generator groups them. */
    private static void listConditions(String filter) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        List<ConditionGenDataSpec> specs = new ArrayList<>(Global.getSettings().getAllSpecs(ConditionGenDataSpec.class));
        specs.sort((x, y) -> Float.compare(x.getOrder(), y.getOrder()));
        for (ConditionGenDataSpec g : specs) {
            if (g.getId().endsWith(ConditionGenDataSpec.NO_PICK_SUFFIX)) continue;
            MarketConditionSpecAPI spec = Global.getSettings().getMarketConditionSpec(g.getId());
            String name = spec == null ? g.getId() : spec.getName();
            String group = g.getGroup() == null ? "other" : g.getGroup();
            if (!filter.isEmpty() && !g.getId().contains(filter) && !name.toLowerCase(Locale.ROOT).contains(filter)
                    && !group.contains(filter)) continue;
            if (!groups.containsKey(group)) groups.put(group, new ArrayList<String>());
            groups.get(group).add(g.getId() + " (" + name + ")");
        }
        if (groups.isEmpty()) {
            WsUtil.print("No conditions match '" + filter + "'.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(WsUtil.PREFIX).append("Planet conditions by group. In findplanet, a group name (like farmland)")
                .append(" matches any condition of that group:\n");
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            sb.append("  ").append(e.getKey()).append(": ").append(String.join(", ", e.getValue())).append("\n");
        }
        Console.showMessage(sb.toString());
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) {
            List<String> l = new ArrayList<>(TOPICS.keySet());
            l.add("conditions");
            return l;
        }
        return new ArrayList<>();
    }
}
