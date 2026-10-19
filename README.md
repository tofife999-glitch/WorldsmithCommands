# Worldsmith Commands

Quick console commands for shaping the Starsector sector: inspect star systems, open any planet's
dialog from anywhere, found NPC colonies, cap colony size, terraform, rename, and add or remove
planets.

Version 1.0.0 for **Starsector 0.98a-RC8**. Utility mod: you can add it to or remove it from a
running save.

**Download:** [latest release](https://github.com/tofife999-glitch/WorldsmithCommands/releases/latest).
TriOS and Version Checker detect updates automatically.

## Requirements

| Mod | ID | Built against |
|---|---|---|
| [Console Commands](https://fractalsoftworks.com/forum/index.php?topic=4106.0) | `lw_console` | 4.0.9 |
| [LazyLib](https://fractalsoftworks.com/forum/index.php?topic=5444.0) | `lw_lazylib` | 3.0.0 |
| [Nexerelin](https://fractalsoftworks.com/forum/index.php?topic=9175.0) | `nexerelin` | 0.12.2d |

## Installation

1. Unzip the release so that the `Worldsmith Commands` folder sits inside `Starsector/mods/`.
2. Enable **Worldsmith Commands** in the launcher (or in TriOS).
3. In the campaign, open the console (default key: `Ctrl+Backspace`) and type `help worldsmith`.

## How the commands work

- **Target:** if you give a name, that body is used. If you don't, the planet or station whose
  dialog is open is used. `removeplanet` is the exception: it always needs a name.
- **Names** are the names shown on the map. Case does not matter, spaces are allowed and
  underscores count as spaces (`Corvus IV` = `corvus_iv`). Planet, station and market IDs work
  too. A command only acts on an exact match. If several bodies have that name, they are listed
  (name, system, owner, ID) and nothing happens. If nothing matches, up to 5 similar names are
  suggested.
- **Factions** are the last word (before an optional number): an ID like `tritachyon` or a name
  like `Tri-Tachyon`. Use underscores for spaces (`luddic_church`). Small typos are tolerated, and
  the faction that was used is always printed. `list factions` shows all faction IDs.
- **Tab completion** suggests planet and system names (with underscores), faction IDs, planet type
  IDs and keywords like `off` and `keep`.
- **Confirmations:** some commands ask first. A Yes/Cancel popup appears when you close the console
  (leave any open planet dialog first). Or confirm right away by typing the same command again with
  `yes` at the end. Cancel does nothing.
- Every command prints one line saying what changed, on which planet, in which system.

## Commands

### showsystem [system]
Lists every planet, moon and station of a star system: the system you are in (or the one whose
dialog is open), or the one you name. System names are fuzzy-matched like the `jump` command.

The header shows the star type(s), the number of stable locations and whether there is a gate.
Each line shows: name | type | hazard | owner and size or "unclaimed" | summary | ID.

The summary lists "habitable", then ruins, then the two most valuable resources (ranked with
Nexerelin's `nex_colonyConditionValues`), then the worst hazard if the hazard rating is 200% or
more. Unsurveyed bodies show "unsurveyed" (the `survey` command reveals them). The ID at the end
helps with commands like Nexerelin's `setmarketowner` for names with spaces.

```
showsystem
showsystem Corvus
```

### visit <planet|station|star>
Opens that body's dialog when you close the console, from anywhere, including uncolonized planets
(the built-in `openmarket` only finds colonies).

```
visit Corvus_IV
visit jangala
```

### npccolony [planet] <faction> [size 1-10, default 3]
Founds a colony on exactly that uncolonized planet for the faction, using the same function that
runs when a Nexerelin colony expedition lands. Nexerelin may give planets with generic names (like
"Corvus IV") a new name; the output then shows the old and the new name.

```
npccolony Corvus_IV hegemony
npccolony Corvus_IV luddic_church 5
npccolony persean 4            (with the planet's dialog open)
```

### treatasnpccolony [colony] [off]
Marks a colony the way Nexerelin marks colonies founded by its expeditions (market memory
`$nex_npcColony`). Nexerelin then treats it like an expedition colony:

- when it grows, the AI builds military and industry there;
- Nexerelin's size limit for these colonies applies (`maxNPCNewColonySize` in Nexerelin's settings,
  default 6);
- saturation-bombing it causes less outrage.

`off` removes the mark. If the colony is already bigger than the limit, you are asked to confirm
first: colonies like this normally stop growing at that size, so it keeps its current size but
won't grow any more. A cap set with `popcap` takes priority over this limit (then no confirmation
is needed). Nexerelin only applies the growth limit and AI building to NPC-owned colonies. For
your own colonies, use `popcap`.

```
treatasnpccolony Jangala
treatasnpccolony Jangala off
```

### popcap [colony] <size|off>
Caps a colony's size so it stops growing there. Works for player and NPC colonies alike. It never
shrinks a colony that is already bigger. `off` removes the cap. The resulting max size is printed.

Technically, it sets market memory `$nex_colony_growth_limit` (read by Nexerelin's NPC growth) and
adds a flat modifier with source ID `worldsmith_commands` to the colony's max size, so the game's
max size for that colony equals the cap.

```
popcap Jangala 5
popcap 4                  (with the colony's dialog open)
popcap Jangala off
```

### planettypes [filter]
Lists every planet type (type ID and name) grouped into habitable, hostile, gas giants and other,
including types added by mods such as Unknown Skies. Stars, black holes and nebula centers are
left out.

```
planettypes
planettypes ice
```

### terraform [planet] <type> [keep]
Changes a planet's type. The type can be a type ID or a name (see `planettypes`).

- Without `keep`, it only works on uncolonized planets. All conditions are cleared and new ones are
  rolled for the new type, the same way vanilla does for its Sentinel planet. A surveyed planet
  stays surveyed. Special conditions that the generator never makes (for example from quests or
  other mods) are removed too, and are listed in the output.
- With `keep`, only the type changes and every condition stays. This also works on colonies.

The planet keeps its size, and gas giant jump points in hyperspace are not added or removed.

```
terraform Corvus_IV terran
terraform Corvus_IV water keep
terraform jungle          (with the planet's dialog open)
```

### rename <old name> > <new name>
Renames a planet, moon, station or colony (the planet and its market). Without an old name
(`rename > New Hope`) it renames the planet whose dialog is open.

Renaming a star system renames the system (keeping custom full names), its star, its jump points
and their hyperspace ends, and generic names that start with the old system name ("Corvus IV"
becomes "Nova IV") plus their markets. Everything that was renamed is printed.

```
rename Corvus IV > New Hope
rename Corvus > Nova
```

### addplanet [type|random] [name]
Adds a planet orbiting the star of the system you are in, at your fleet's distance from the star
and at your fleet's angle.

- No type, or `random`: a random type, weighted the way the sector generator weights planet types.
- No name: the next free "<system> <roman numeral>", like "Corvus VI".
- Radius, color and orbit speed are generated like vanilla planets. Conditions are rolled for the
  system's age, and the planet is surveyed.
- It refuses if the new orbit would overlap the orbit of anything else (planets with their moons,
  stations, rings, asteroid belts, jump points...) and says what is in the way.

```
addplanet
addplanet jungle
addplanet random Eden
addplanet gas_giant Big Blue
```

### removeplanet <planet>
Deletes an uninhabited planet or moon. It never picks a planet by itself. It always asks for
confirmation, naming the planet and its system.

- Stars and colonies are refused. To remove a colony, first open its dialog (`visit <name>`), type
  `destroycolony`, then run `removeplanet` again.
- It refuses planets that moons, stations or other objects orbit, and lists those things. Rings and
  magnetic fields around the planet are removed with it.
- Jump points that orbit the planet are removed together with their hyperspace end. If both ends
  can't be found, it refuses. Gas giant gravity wells in hyperspace that lead to the planet are
  removed too.
- If the planet is marked as important for an active mission, the confirmation warns you.

```
removeplanet Corvus VI
removeplanet Corvus VI yes
```

### npcexpedition [planet] <faction>
Sends a real Nexerelin colony expedition to an uncolonized planet. The fleet starts from the
faction's colony nearest to the planet, preferring colonies with a working spaceport. Fleet
strength and organize time are computed exactly like Nexerelin's own random expeditions. The fleet
organizes, travels and can be fought like any expedition. If it arrives, Nexerelin founds the
colony.

```
npcexpedition Corvus_IV tritachyon
```

## Save notes (please read)

- The mod adds **nothing of its own** to your save: no scripts, listeners, intel or custom classes.
  It only changes plain data and vanilla objects: memory keys, stat modifiers with the source ID
  `worldsmith_commands`, and vanilla planets. That's why you can add or remove it mid-save
  (`"utility": true`).
- **Changes made with the commands stay in the save after you remove the mod.** That includes new
  or removed planets, new names, new planet types and conditions, founded colonies, NPC colony
  marks and population caps. To undo a population cap, run `popcap <colony> off` before removing
  the mod.
- **Planets turned into a modded type** (for example an Unknown Skies type) need that mod to stay
  installed, just like planets that mod generated itself.
- Colonies and expeditions created by `npccolony` and `npcexpedition` are made by Nexerelin's own
  code. They belong to Nexerelin, which this mod requires anyway.
- Test big changes on a copy of your save first.

## Building from source

The Java sources are in `src/`. Build them with JDK 17 or newer, for example:

```
javac --release 17 -cp "starfarer.api.jar;json.jar;log4j-1.2.9.jar;lwjgl.jar;lwjgl_util.jar;lw_Console.jar;LazyLib.jar;ExerelinCore.jar" -d classes <all .java files in src>
jar --create --file jars/WorldsmithCommands.jar -C classes .
```

## Credits

- **Console Commands** by LazyWizard (reworked console and tab completion by Lukas04). This mod
  adds commands to it and reuses its name matching.
- **LazyLib** by LazyWizard.
- **Nexerelin** by Histidine and contributors. `npccolony`, `treatasnpccolony`, `popcap` and
  `npcexpedition` use Nexerelin's own colony code and settings.
- **Starsector** by Fractal Softworks.
- Worldsmith Commands by Jan.

## License

MIT. See `LICENSE`.
