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
        TOPICS.put("objects",
                "addobject <type> places one of these at your position; removeobject [type|name] removes one\n"
                        + "(asks first):\n"
                        + "  stable_location   an empty stable orbit; a relay, buoy or array can be built there\n"
                        + "  comm_relay        helps the stability of colonies in the system\n"
                        + "  nav_buoy          makes fleets travel faster in the system\n"
                        + "  sensor_array      gives better sensor range in the system\n"
                        + "  ..._makeshift     weaker player-built versions of the three above\n"
                        + "  inactive_gate     a gate of the gate network\n"
                        + "  asteroid_field    an asteroid field (with its asteroids)\n"
                        + "  debris_field      a debris field you can salvage\n"
                        + "  jumppoint         a jump point made with addjumppoint (both ends go)\n"
                        + "Short names work too: stable, relay, buoy, sensor, gate, field, jumppoint. Without a name\n"
                        + "removeobject removes the nearest one where you are.\n"
                        + "addfield <asteroids|debris> [small|large] adds a field at your position; fields may overlap\n"
                        + "anything.");
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
                        + "Stars follow their system's name, so rename the system to rename its star.\n"
                        + "rename faction <faction> <new name>  a faction, everywhere its name appears; 'reset' as\n"
                        + "                                     the new name gives back its own name\n"
                        + "Typing a system's name without 'system' also renames the system when its star has the\n"
                        + "same name.");
        TOPICS.put("remove",
                "removeplanet <planet> and removemoon <moon>: delete an uninhabited planet or moon (asks first).\n"
                        + "  Refuses colonies (use destroycolony in its dialog first) and planets other things orbit.\n"
                        + "  It also removes the second or third star of a system (never the main star).\n"
                        + "removering: removes the belt, ring or magnetic field around the star nearest to your\n"
                        + "  position. removering <planet> removes all of that planet's rings; add belt, ring or\n"
                        + "  magfield to remove only that kind.\n"
                        + "removeobject: removes the nearest stable location, relay, buoy, sensor array or gate in\n"
                        + "  your system (asks first). removeobject <type> takes the nearest of that type,\n"
                        + "  removeobject <name or ID> a specific one.");
        TOPICS.put("newsystem",
                "newsystem <name> [star type] [full|empty]: in hyperspace, creates a star system at your\n"
                        + "position. Without a star type it picks one at random ('startypes' lists them); black holes\n"
                        + "and neutron stars work too. A popup asks whether to generate planets, moons, rings and\n"
                        + "stable locations like a random system, or keep it empty; 'full' or 'empty' answers that in\n"
                        + "advance. It gets an inner and a fringe jump point plus the star's own. The star is named\n"
                        + "'<name> Prime'. It must be at least 1.5 light-years from other systems.");
        TOPICS.put("stars",
                "addstar [star type] [name]: adds a companion star at your position, orbiting the main\n"
                        + "  star; the system becomes a binary (or, with a third star, trinary) system. Remove it\n"
                        + "  again with removeplanet.\n"
                        + "Black holes get an event horizon (it pulls fleets in) and neutron stars a pulsar beam (it\n"
                        + "  sweeps the system and drains combat readiness), like in randomly generated systems.\n"
                        + "recolor [planet] <color>: tints a planet, moon or star (red, teal, purple... or #RRGGBB,\n"
                        + "  random, reset). Only the look changes.");
        TOPICS.put("hyperspace",
                "hyperclouds clear [radius]: removes the hyperspace clouds and storms around you.\n"
                        + "hyperclouds add [radius]: grows new clouds around you; storms form in them over time.\n"
                        + "The radius is in light-years (0.5-5, default 1). Star systems keep their clear space.\n"
                        + "addjumppoint [name]: a two-way jump point where you are. In a system it leads out to\n"
                        + "hyperspace; in hyperspace it leads into the nearest system (up to 3 light-years away).\n"
                        + "removeobject jumppoint removes both ends again.");
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
                        + "  Strengthen 3: + stability +3 fading by 1 per year, many more invasion points.\n"
                        + "New factions, flags and lore: see 'wshelp newfaction'.");
        TOPICS.put("newfaction",
                "spawnfaction <name> [here|random] [small|medium|large] [color] [like <faction>]\n"
                        + "  Founds a new faction. A popup asks where (your system or a random empty place) and how\n"
                        + "  big (small: 1 colony, medium: 3, large: 5), then shows the planets before anything\n"
                        + "  happens ('Pick another place' rolls again). It gets a random player flag, empty lore,\n"
                        + "  and the ships, weapons and way of fighting of an existing faction: 'like hegemony'\n"
                        + "  copies the Hegemony's, otherwise a random faction's.\n"
                        + "  Nexerelin runs it like any other faction: diplomacy, wars, invasions, alliances.\n"
                        + "  There are 8 slots, one per color; 'spawnfaction list' shows them. A slot frees up again\n"
                        + "  once its faction has no colonies left.\n"
                        + "factionflag <faction>: pick any faction's flag from pictures in a popup (or type random,\n"
                        + "  a number, another faction, or reset).\n"
                        + "Character of a spawned faction (kept in the save):\n"
                        + "  factionships <faction> lowtech|midline|hightech|pirate|all|like <faction>\n"
                        + "    (like <faction>: the same ships, weapons and way of fighting as that faction)\n"
                        + "  factiondoctrine <faction> aggressive|cautious|carriers|phase|warships|elite|swarm|capitals|reset\n"
                        + "  factionpersonality <faction> warlike|peaceful|pirate|lawful|expansionist|isolationist|normal\n"
                        + "  The README explains every option.\n"
                        + "factionlore <faction> <text>: the text on its Intel faction page. 'add <text>' adds a\n"
                        + "  paragraph, a | starts a new paragraph, 'reset' restores it. Don't use ; in the text (the\n"
                        + "  console splits commands at ;).\n"
                        + "rename faction <faction> <new name>: renames any faction.\n"
                        + "Note: once a save has run with this mod, keep the mod installed for that save.");
        TOPICS.put("war",
                "wars [faction]: who is at war with whom, alliances and war weariness; with a faction, its\n"
                        + "  relations with everyone.\n"
                        + "totalwar [withplayer]: every faction with colonies at war with every other; alliances\n"
                        + "  break up. You stay out unless 'withplayer' (a commission ties you to your employer).\n"
                        + "totalpeace [all] [withplayer]: everyone at peace; pirates only with 'all'.\n"
                        + "armada <faction> [system] [small|medium|large]: that faction's war fleet attacks the\n"
                        + "  system's biggest colony for about two months, then leaves. Without a war, only the\n"
                        + "  armada turns hostile for the attack.\n"
                        + "rebellion [colony] [backing faction]: a Nexerelin rebellion in one colony.\n"
                        + "Already in other mods, for one pair of factions:\n"
                        + "  setrelation / adjustrelation <faction> [other] <amount>   (Console Commands)\n"
                        + "  creatediplomacyevent <a> <b> declare_war|peace_treaty|ceasefire   (Nexerelin)\n"
                        + "  createalliance <a> <b>, leavealliance <faction>, spawninvasionfleet,\n"
                        + "  spawnraidfleet, setmarketowner   (Nexerelin)");
        TOPICS.put("apocalypse",
                "apocalypse <remnant|bombard|revolution> [all]: asks Yes/Cancel first.\n"
                        + "  remnant     an elite Remnant armada (alpha cores) jumps into every inhabited system,\n"
                        + "              attacks it for about two months, then withdraws and vanishes.\n"
                        + "  bombard     every colony is saturation-bombed into ruins; story-critical worlds survive.\n"
                        + "              This can't be undone.\n"
                        + "  revolution  Nexerelin rebellions break out in every colony of size 3 or more. Rebels\n"
                        + "              that win take the colony over (or make it independent).\n"
                        + "Your own colonies are spared unless you add 'all'. Save first.");
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
            sb.append("  names, findplanet, conditions, objects, addring, resize, rename, remove, newsystem,\n");
            sb.append("  stars, hyperspace, factions, newfaction, war, apocalypse\n");
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
            case "object": case "removeobject": case "addobject": case "addentity": case "addfield": case "fields":
            case "field": return "objects";
            case "rings": case "ring": case "belt": return "addring";
            case "resizeplanet": case "size": case "sizes": return "resize";
            case "removeplanet": case "removering": case "removemoon": case "removing": return "remove";
            case "system": case "systems": return "newsystem";
            case "star": case "addstar": case "recolor": case "color": case "colors": return "stars";
            case "hyperclouds": case "clouds": case "storms": case "addjumppoint": case "jumppoint":
            case "jumppoints": return "hyperspace";
            case "faction": case "factionpower": case "weakenfaction": case "strengthenfaction": return "factions";
            case "spawnfaction": case "factionflag": case "factionlore": case "flag": case "flags": case "lore":
            case "factionships": case "factiondoctrine": case "factionpersonality": case "doctrine":
            case "personality": return "newfaction";
            case "wars": case "totalwar": case "totalpeace": case "peace": case "diplomacy": case "armada":
            case "rebellion": return "war";
            case "remnant": case "bombard": case "revolution": return "apocalypse";
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
