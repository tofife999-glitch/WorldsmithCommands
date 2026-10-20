# Worldsmith Commands

Console commands for shaping the sector. Starsector 0.98a-RC8; needs Console Commands, LazyLib and
Nexerelin. [Download](https://github.com/tofife999-glitch/WorldsmithCommands/releases/latest)

Type `help worldsmith` in the console for the command list, and `wshelp` for explanations.

## Commands

- `showsystem [system]`: list a system's planets with type, hazard, owner and resources
- `findplanet <filters>`: search the sector (see below)
- `planetinfo [planet]`: everything about one planet
- `planettypes [filter]` / `startypes [filter]`: list all planet / star types
- `visit <planet>`: open any planet's dialog from anywhere
- `npccolony [planet] <faction> [size]`: found a colony for a faction
- `npcexpedition [planet] <faction>`: send a Nexerelin colony expedition
- `treatasnpccolony [colony] [off]`: make Nexerelin treat a colony like its own expedition colony
- `popcap [colony] <size|off>`: cap a colony's size
- `terraform [planet] <type> [keep]`: change a planet's type (`keep` keeps its conditions)
- `rename [system|star] <current name> <new name>`: rename a planet, station, star or star system
- `addplanet [type] [name]` / `addmoon <planet> [type] [name]`: add a planet or moon
- `addobject <type>` / `addring [planet] <type>`: add an object or a ring (see below)
- `newsystem <name> [star type] [full|empty]`: create a star system in hyperspace
- `moveplanet <planet>` / `resizeplanet [planet] <size>`: move a planet to your orbit, or resize it
- `changestar [system] <star type> [reroll]`: change a system's star
- `removeplanet`, `removemoon`, `removering`, `removeobject`: remove things again (see below)
- `factionpower`, `strengthenfaction`, `weakenfaction`: faction strength (see below)
- `wshelp [topic]`: explanations in the game

## How things work

**Names.** Type names as shown on the map. Case doesn't matter, and underscores count as spaces
(`Corvus_IV` = `Corvus IV`). IDs work too. Without a name, most commands use the open planet dialog.

**findplanet filters** (combine any): a condition like `farmland_rich` or a group like `farmland`,
`ore` or `ruins` (`wshelp conditions` lists them); `-toxic_atmosphere` excludes one. Other filters:

- `habitable`, `uncolonized`, `colonized`, `type:jungle`
- `hazard<150` = hazard rating below 150%
- `within:20` = at most 20 light-years from your fleet (`within:40` searches twice as far)
- `owner:hegemony`
- `all` = also search planets you haven't surveyed

Example: `findplanet habitable farmland hazard<150 within:20`.

**addobject types:**

- `stable_location`: an empty stable orbit
- `comm_relay`: colony stability in the system
- `nav_buoy`: faster travel in the system
- `sensor_array`: sensor range in the system
- the three above as `_makeshift`, weaker versions
- `inactive_gate`

Objects go at your position and start unclaimed.

**addring types:** `belt` (asteroid belt), `dust` or `ice` (a ring), `magfield` (magnetic field).
Around the star they go at your distance from it; around a planet, at your distance or in the first
free orbit.

**Sizes.** A planet's size is its radius (`planetinfo` shows it).

- The same number changes nothing; twice the number, or `200%`, makes it twice as wide.
- Rocky planets are about 90-220, gas giants 250-400.
- `auto` picks a normal size for the planet's type.

**Renaming a system.**

- `rename system Glith Hakeball` renames the system, its star, and planets named after it
  (Glith I becomes Hakeball I).
- `rename star <star> <new name>` renames only the star.
- New systems name their star "<name> Prime", so the star and the system are never mixed up.

**Removing.**

- `removeplanet` and `removemoon` delete uninhabited planets or moons.
- `removering` removes the belt, ring or field nearest to you around the star, or all rings of a
  named planet.
- `removeobject` removes the nearest object (or one of a type, or a named one).
- Removing planets, moons and objects asks first.

**New systems.** In hyperspace, `newsystem <name>` creates a system at your position. A popup asks
whether to generate planets, moons, rings and stable locations, or keep it empty; `full` or `empty`
answers that in advance.

**Factions.**

- `factionpower <faction>` shows a faction's colonies, stability, unrest, disrupted industries,
  garrisons, Nexerelin invasion points and troop reserves. It also lists any Worldsmith effects and
  how long they still last.
- `weakenfaction <faction> [1-3]` and `strengthenfaction <faction> [1-3]` first show what will happen
  in each colony, then ask Yes/Cancel. Level 1 is a light nudge, level 2 lasts months, and level 3
  lasts up to 3 years.
- All effects wear off by themselves, and they stay correct across saving and loading.
- `factionpower <faction> clear` ends them early.

**Note:** changes stay in your save even after removing the mod, and planets turned into a modded
type need that mod installed.

MIT license. Credits: LazyWizard (Console Commands, LazyLib), Histidine (Nexerelin).
