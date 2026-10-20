# Worldsmith Commands

Console commands for shaping the sector. Starsector 0.98a-RC8; needs Console Commands, LazyLib and
Nexerelin. [Download](https://github.com/tofife999-glitch/WorldsmithCommands/releases/latest)

Type `help worldsmith` in the console for the command list, and `wshelp` for explanations.

## Commands

**Planets and systems**

- `showsystem [system]`: list a system's planets with type, hazard, owner and resources
- `findplanet <filters>`: search the sector (see below)
- `planetinfo [planet]`: everything about one planet
- `planettypes [filter]` / `startypes [filter]`: list all planet / star types
- `visit <planet>`: open any planet's dialog from anywhere
- `terraform [planet] <type> [keep]`: change a planet's type (`keep` keeps its conditions)
- `rename [system|star|faction] <current name> <new name>`: rename a planet, station, star, system or faction
- `recolor [planet] <color>`: tint a planet, moon or star
- `addplanet [type] [name]` / `addmoon <planet> [type] [name]`: add a planet or moon
- `addring [planet] <type>`: add an asteroid belt, ring or magnetic field
- `addstar [type] [name]`: add a second or third star to a system
- `addfield <asteroids|debris> [small|large]`: add an asteroid field or a salvageable debris field
- `newsystem <name> [star type] [full|empty]`: create a star system in hyperspace
- `moveplanet <planet>` / `resizeplanet [planet] <size>`: move a planet to your orbit, or resize it
- `changestar [system] <star type> [reroll]`: change a system's star
- `hyperclouds <clear|add> [radius]`: clear or grow hyperspace clouds around you
- `removeplanet`, `removemoon`, `removering`, `removeobject`: remove things again

**Colonies and factions**

- `treatasnpccolony [colony] [off]`: make Nexerelin treat a colony like its own expedition colony
- `popcap [colony] <size|off>`: cap a colony's size
- `spawnfaction <name>`: found a new faction (see below)
- `factionflag <faction> [flag]` / `factionlore <faction> <text>`: change any faction's flag or lore
- `factionpower`, `strengthenfaction`, `weakenfaction`: faction strength (see below)
- `wars [faction]`: who is at war with whom
- `factionranking [order]`: rank the factions by size, industry, military, wars or invasion points
- `armada <faction> [system] [small|medium|large]`: send a faction's war fleet to attack a system
- `rebellion [colony] [faction]`: start a Nexerelin rebellion in one colony
- `alliance list|dissolve|rename`: list, dissolve or rename Nexerelin alliances
- `totalwar` / `totalpeace`: everyone at war, or everyone at peace
- `apocalypse <remnant|bombard|revolution>`: end the world (see below)
- `wshelp [topic]`: explanations in the game

For founding colonies, sending colony expeditions and adding objects like relays, use the Additional
Console Commands mod (`colonizeplanet`, `spawncolonyfleet`, `addentity`).

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

**addring types:** `belt` (asteroid belt), `dust` or `ice` (a ring), `magfield` (magnetic field).
Around the star they go at your distance from it; around a planet, at your distance or in the first
free orbit.

**Sizes.** A planet's size is its radius (`planetinfo` shows it).

- The same number changes nothing; twice the number, or `200%`, makes it twice as wide.
- Rocky planets are about 90-220, gas giants 250-400.
- `auto` picks a normal size for the planet's type.

**Stars.** `newsystem`, `changestar` and `addstar` take every star type, including black holes (with an
event horizon) and neutron stars (with a pulsar beam). `addstar` makes a binary or trinary system;
`removeplanet` removes the extra star again.

**Renaming a system.**

- `rename system Glith Hakeball` renames the system, its star, and planets named after it
  (Glith I becomes Hakeball I).
- `rename star <star> <new name>` renames only the star.
- New systems name their star "<name> Prime", so the star and the system are never mixed up.

**Removing.**

- `removeplanet` and `removemoon` delete uninhabited planets or moons.
- `removering` removes the belt, ring or field nearest to you around the star, or all rings of a
  named planet.
- `removeobject` removes the nearest object or field (or one of a type, or a named one).
- Removing planets, moons and objects asks first.

**New systems.** In hyperspace, `newsystem <name>` creates a system at your position. A popup asks
whether to generate planets, moons, rings and stable locations, or keep it empty; `full` or `empty`
answers that in advance.

**New factions.** `spawnfaction Hakeball Republic` opens a popup that asks where (your system or a
random empty place) and how big (1, 3 or 5 colonies), then shows the planets before anything happens.

- The faction gets a random player flag, empty lore, and the ships and tactics of an existing faction
  (`like hegemony`, otherwise a random one).
- Nexerelin runs it like any other faction: diplomacy, wars, invasions, alliances.
- There are 8 slots, one per color; `spawnfaction list` shows them. A slot frees up again once its
  faction has no colonies left.
- `factionlore Hakeball_Republic <text>` writes its lore (a `|` starts a new paragraph);
  `factionflag <faction> 7` or `random` changes any faction's flag.

**Factions.**

- `factionpower <faction>` shows a faction's colonies, stability, unrest, disrupted industries,
  garrisons, Nexerelin invasion points and troop reserves. It also lists any Worldsmith effects and
  how long they still last.
- `weakenfaction <faction> [1-3]` and `strengthenfaction <faction> [1-3]` first show what will happen
  in each colony, then ask Yes/Cancel. Level 1 is a light nudge, level 2 lasts months, and level 3
  lasts up to 3 years.
- All effects wear off by themselves, and they stay correct across saving and loading.
- `factionpower <faction> clear` ends them early.

**War and peace.** `totalwar` puts every faction with colonies at war with every other one and breaks
up all alliances; `totalpeace` makes peace (pirates only with `all`). You stay out of both unless you
add `withplayer`. Nexerelin's diplomacy carries on from there. `armada hegemony Askonia` sends a
war fleet that attacks for about two months and only fights its own enemies, so declare war first if
needed.

**Apocalypse** (asks first; your own colonies are spared unless you add `all`):

- `remnant`: a Remnant armada attacks every inhabited system for about two months, then leaves.
- `bombard`: every colony is saturation-bombed into ruins; story-critical worlds survive.
- `revolution`: Nexerelin rebellions break out in every colony of size 3 or more.

**Note:** you can add this mod to a running save, but don't remove it from a save afterwards: it adds 8
blank faction slots (for `spawnfaction`) to the save. All changes stay in the save, and planets turned
into a modded type need that mod installed.

MIT license. Credits: LazyWizard (Console Commands, LazyLib), Histidine (Nexerelin).
