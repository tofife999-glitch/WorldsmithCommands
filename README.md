# Worldsmith Console Commands

Console commands for shaping the sector. Starsector 0.98a-RC8; needs Console Commands, LazyLib and
Nexerelin. [Download](https://github.com/tofife999-glitch/WorldsmithConsoleCommands/releases/latest)

Open the console with Ctrl+Backspace. `help worldsmith` lists the commands, `help <command>` explains one,
and `wshelp` has plain-language topics.

- Type names as shown on the map. Case doesn't matter, underscores count as spaces (`Corvus_IV` =
  `Corvus IV`), and IDs work too.
- Without a name, most commands use the planet whose dialog is open.
- `[brackets]` are optional; `a|b` means "a or b".

## Looking things up

- `showsystem [system]`: every planet, moon and station of a system, with type, hazard, owner and
  resources.
- `planetinfo [planet]`: everything about one planet: size, orbit, industries, its conditions and how
  much each adds to the hazard.
- `planettypes [filter]` / `startypes [filter]`: all planet or star types, modded ones included. The
  filter shows only types whose name contains it.
- `visit <planet|station|star>`: opens its dialog from anywhere.
- `findplanet <filters>`: searches the sector and lists the nearest 20 matches. Combine any of:
  - a condition such as `farmland_rich`, or a group such as `farmland`, `ore` or `ruins`
    (`wshelp conditions` lists them all)
  - `-toxic_atmosphere`: must not have it
  - `habitable`, `uncolonized`, `colonized`
  - `type:jungle`: that planet type
  - `hazard<150`: hazard below 150% (also `<=`, `>`, `>=`)
  - `within:20`: at most 20 light-years from you
  - `owner:hegemony`: colonies of that faction
  - `all`: also planets you haven't surveyed

  Example: `findplanet habitable farmland hazard<150 within:20`

## Planets and colonies

- `terraform [planet] <type> [keep]`: changes an uncolonized planet's type and rolls new conditions that
  fit it (`planettypes` lists the types).
  - `keep`: only the type changes and the conditions stay; this also works on colonies
- `rename <current name> <new name>`: renames a planet, station or colony.
  - put a `>` between the names if you're unsure where the current one ends
  - with a planet's dialog open, just `rename <new name>`
  - `rename system <system> <new name>`: the system, its star and the planets named after it
    (Glith I becomes Hakeball I). Stars always follow their system's name.
  - `rename faction <faction> <new name>`: a faction, everywhere; `reset` as the new name undoes it
- `recolor [planet] <color>`: tints a planet, moon or star. Only the look changes.
  - `red`, `orange`, `yellow`, `gold`, `green`, `teal`, `cyan`, `blue`, `purple`, `pink`, `white`,
    `grey`, `brown`, `black`
  - a hex color such as `#3080ff`
  - `random`
  - `reset`: the standard look of its type
- `addplanet [type|random] [name]`: a planet at your distance from the star, generated like vanilla's.
- `addmoon <planet> [type|random] [name]`: a moon at your distance from the planet, or in its first
  free orbit.
- `moveplanet <planet>`: moves it, with its moons, to your distance and angle.
- `resizeplanet [planet] <percent|auto>`: makes it wider or narrower.
  - a percentage: `150%` makes it 1.5 times as wide, `200%` twice as wide, `50%` half as wide
  - `auto`: a normal size for its type
- `orbit [planet] [options]`: changes the orbit of a planet, moon or companion star. Without options it shows
  which way it circles and how many days one orbit takes. Options can be combined:
  - `distance 150%`: 1.5 times as far out (`50%` = half as far). It keeps its speed, so a wider orbit takes
    longer.
  - `speed 200%`: twice as fast (`50%` = half as fast)
  - `reverse`: circles the other way
  - `tidallock`: for moons. The moon always stays behind its planet, away from the star, in the planet's
    shadow, where a neutron star's pulsar beam can't reach it. `speed` or `reverse` ends the lock.

  Moons, stations and rings move with it. Example: `orbit Corvus_V distance 150%`, then
  `orbit Corvus_VA tidallock`.
- `popcap [colony] <size|off>`: the colony stops growing at that size. It never shrinks a colony.
  - `off`: removes the cap
- `treatasnpccolony [colony] [off]`: Nexerelin treats it like its own expedition colonies: the AI
  builds it up as it grows, and Nexerelin's size limit for such colonies applies.
  - `off`: removes that mark

## Systems, stars and space

- `newsystem <name> [star type] [full|empty]`: in hyperspace, a new star system at your position, with
  an inner and a fringe jump point. A popup asks how to fill it, unless you say so in advance:
  - `full`: planets, moons, rings and stable locations, like a random system
  - `empty`: only the star

  Without a star type it picks one at random; black holes and neutron stars work too. The star is
  called "<name> Prime".
- `addstar [star type|random] [name]`: a second or third star at your position, orbiting the main star:
  a binary or trinary system.
- `addring [planet] <type>`: around the star at your distance from it, or around the named planet.
  - `belt`: an asteroid belt with asteroids
  - `dust`: a dusty ring that looks like debris
  - `ice`: an ice ring
  - `magfield`: a magnetic field
- `addobject <type>`: at your position, orbiting the star.
  - `stable_location`: an empty stable orbit, where a relay, buoy or array can be built
  - `comm_relay`: helps the stability of colonies in the system
  - `nav_buoy`: fleets travel faster in the system
  - `sensor_array`: better sensor range in the system
  - `comm_relay_makeshift`, `nav_buoy_makeshift`, `sensor_array_makeshift`: weaker versions
  - `inactive_gate`: a gate

  Relays, buoys and arrays start unclaimed, so you claim them like any other.
- `addfield <asteroids|debris> [small|large]`: at your position.
  - `asteroids`: an asteroid field
  - `debris`: a debris field you can salvage
  - `small` / `large`: its size (otherwise in between)
- `addwreck [size] [faction|ship]`: a derelict ship next to you, placed the way the sector generator does it:
  salvage it, or recover the ship if you're lucky.
  - `small`, `medium`, `large` (cruisers and capitals) or `civilian`: its size
  - a faction: one of its ships (otherwise a random faction's)
  - a ship name or hull ID, like `paragon`: that exact ship
- `addjumppoint [name]`: a two-way jump point where you are. In a system, it leads out to hyperspace;
  in hyperspace, it leads into the nearest system (at most 3 light-years away).
- `hyperclouds <clear|add> [radius]`: in hyperspace, around you.
  - `clear`: removes the clouds and the storms in them
  - `add`: grows new clouds (star systems keep their clear space)
  - radius: 0.5 to 5 light-years, 1 if you leave it out

## Sharing systems

- `exportsystem [system]`: writes the system you are in, or the one you name, to a JSON file in
  `Starsector/saves/common/worldsmith/`, named after the system (the game adds `.data` to the name). It lists
  the stars, planets and moons with their orbits and conditions, rings, belts, fields, objects and jump points,
  plus colonies and stations, so modders can rebuild the system in their own mod.
- `importsystem <file> [new name]`: in hyperspace, builds a saved system at your position (asks first): stars,
  planets and moons with their conditions, rings, belts, fields, relays, buoys, arrays, stable locations, gates
  and jump points. Planets come uncolonized and unsurveyed; colonies, stations and wrecks aren't rebuilt. Give a
  new name if a system with its name exists; planets named after it get the new name. To use a file from
  someone else, put it in that folder as `<name>.json.data`.

## Removing things

Everything here asks Yes/Cancel first.

- `removeplanet <planet>` / `removemoon <moon>`: uninhabited planets and moons, and the second or third
  star of a system. Rings and debris around them go with them.
- `removering [planet] [belt|ring|magfield]`: without a planet, the belt, ring or field around the star
  nearest to you. With a planet, all its rings, or only the kind you name.
- `removeobject [type|name]`: the nearest object where you are, the nearest of a type, or one by name.
  - `stable`, `relay`, `buoy`, `sensor`, `gate`: the objects `addobject` makes
  - `field`: an asteroid or debris field
  - `jumppoint`: a jump point made with `addjumppoint` (both ends)

## New factions

- `spawnfaction <name> [options]`: founds a new faction. A popup asks for anything you leave out and
  shows the planets before anything happens.
  - `here` / `random`: in your system, or at a random empty place ("Pick another place" rolls again)
  - `small` / `medium` / `large`: 1, 3 or 5 colonies
  - `red`, `orange`, `yellow`, `green`, `teal`, `blue`, `purple`, `pink`: its color on the map. There
    are 8 slots, one per color; `spawnfaction list` shows which are free.
  - `like <faction>`: it uses that faction's ships, weapons and way of fighting. Without it, a random
    faction's.

  It gets a random player flag and empty lore, and Nexerelin runs it like any other faction:
  diplomacy, wars, invasions, alliances. A slot frees up again when its faction has no colonies left.
- `factionflag <faction> [option]`: changes any faction's flag.
  - no option: a popup shows the flags as pictures; browse with Next/Previous or the arrow keys
  - `random`: a random player flag
  - a number: that player flag (`factionflag list` numbers them)
  - another faction: copies its flag
  - `reset`: its own flag again
- `factionlore <faction> [option]`: the text on the faction's page in the Intel screen. Works for every
  faction.
  - no option: shows the text
  - any text: replaces it (a `|` starts a new paragraph; don't use `;`, the console splits commands there)
  - `add <text>`: adds a paragraph
  - `reset`: brings back the original

- `factionillegal <faction> [option]`: what a faction considers illegal: its markets sell those goods only on
  the black market, and its patrols treat them as contraband. Works for every faction.
  - no option: lists what it bans
  - `<commodity> illegal` / `<commodity> legal`: changes one, e.g. `factionillegal hegemony drugs legal`
  - `reset`: undoes every change made with this command

These three shape a faction made with `spawnfaction`. Without an option, each one shows the current
setting, and every setting stays in the save.

- `factionships <faction> <style>`: which ships its new fleets use.
  - `lowtech`, `midline`, `hightech`: only ships of that tech level
  - `pirate`: pirate and low tech ships
  - `all`: every ship the sector's factions use
  - `like <faction>`: copies that faction completely: the same ships, the same weapons and the same
    way of fighting. For example, `like tritachyon` makes it fly Tri-Tachyon ships with Tri-Tachyon
    tactics.
- `factiondoctrine <faction> <preset>`: how its fleets are put together and how they fight. Presets add
  up (for example `aggressive`, then `carriers`, then `elite`); a new one replaces an earlier one that
  sets the same thing.
  - `aggressive`: its captains charge in and fight to the end
  - `cautious`: its captains keep their distance and pull back sooner
  - `carriers`: mostly carriers and fighters
  - `phase`: mostly phase ships
  - `warships`: mostly gun ships, few carriers or phase ships
  - `elite`: fewer ships, but the best quality and officers
  - `swarm`: many cheap, small ships with weak officers
  - `capitals`: mostly big ships
  - `reset`: back to the doctrine of the faction it copies
- `factionpersonality <faction> <type>`: how it behaves towards others.
  - `warlike`: declares war easily and tires of it slowly
  - `peaceful`: never starts wars itself and tires of them quickly
  - `pirate`: Nexerelin treats it as a pirate faction: hostile to everyone except pirates, free ports,
    nothing illegal
  - `lawful`: back to normal diplomacy after `pirate`
  - `expansionist`: sends colony expeditions often
  - `isolationist`: sends no colony expeditions
  - `normal`: undoes all of the above

## Faction strength

- `factionpower <faction> [clear]`: colonies, stability, unrest, disrupted industries, garrisons,
  Nexerelin invasion points and troop reserves, and any Worldsmith effects with how long they last.
  - `clear`: ends the Worldsmith effects early
- `weakenfaction <faction> [1-3]`: first shows what will happen in each colony, then asks. It all wears
  off by itself.
  - `1`: unrest for about 6 months
  - `2`: also a 6-month growth freeze, weaker garrisons, defenses disrupted for 4 months, fewer
    invasion points
  - `3`: lasts up to 3 years: 3-year growth freeze, stability -3 fading by 1 a year, military and ship
    industries disrupted for 1-2 years, drained troops and invasion points
- `strengthenfaction <faction> [1-3]`: the same in reverse.
  - `1`: clears unrest and disruptions, lifts Worldsmith growth freezes
  - `2`: also repairs garrisons, adds troops and invasion points, +2 stability for 6 months
  - `3`: also +3 stability fading by 1 a year, and many more invasion points
- `expeditions [option]`: Nexerelin's colony expeditions. Nexerelin sends one every few months and picks the
  faction by share.
  - no options: every faction's share and how often expeditions come
  - `<faction> 30%`: that faction sends 30% of all expeditions; the others share the rest in their usual
    proportions. `100%` = only that faction.
  - `<faction> off`: it sends none
  - `<faction> reset`: its usual share again
  - `frequency 200%`: expeditions come twice as often (`50%` = half as often)
  - `reset`: everything back to normal

## War and peace

- `wars [faction]`: who is at war with whom, the alliances, and how war-weary each faction is. With a
  faction: its relation (-100 to 100) with every other faction.
- `totalwar [withplayer]`: every faction with colonies goes to war with every other one, and all
  alliances break up. Asks first.
  - `withplayer`: you join in too (with a commission you share your employer's wars anyway)
- `totalpeace [all] [withplayer]`: every faction with colonies makes peace. Asks first.
  - `all`: pirates too
  - `withplayer`: you too
- `armada <faction> [system] [small|medium|large]`: that faction's war fleet jumps into the system (or
  the one you are in), attacks its biggest colony for about two months, then leaves. If the two factions
  aren't at war, only the armada turns hostile; no war is declared.
  - `small` / `medium` / `large`: its size (medium if you leave it out)
- `warfocus <faction> <enemy> [days]`: the faction aims all its Nexerelin invasions and raids at that
  enemy, for 180 days or the days you give (30-1825), or until they make peace. It spends the faction's own
  invasion points, so it doesn't add invasions; they must already be at war.
  - no options: lists the war focuses
  - only a faction: its focus and how close it is to the next invasion
  - `off`: it picks its targets by itself again
- `rebellion [colony] [faction]`: a Nexerelin rebellion in one colony. The rebels fight for the faction
  you name, or for one Nexerelin picks, or for independence. If they win, the colony changes hands.

Already in other mods:
- `setrelation`, `adjustrelation`, and `destroycolony` for one colony (Console Commands)
- `creatediplomacyevent <a> <b> declare_war|peace_treaty|ceasefire`, `createalliance`, `leavealliance`
  (Nexerelin)
- founding colonies and colony expeditions: `colonizeplanet`, `spawncolonyfleet` (Additional Console
  Commands)

## Apocalypse

`apocalypse <scenario> [all]` asks first. Your own colonies are spared unless you add `all`.

- `remnant`: an elite Remnant armada attacks every inhabited system for about two months, then leaves.
- `bombard`: every colony is saturation-bombed into ruins; story-critical worlds survive. This can't be
  undone.
- `revolution`: Nexerelin rebellions break out in every colony of size 3 or more.

**Note:** you can add this mod to a running save, but don't remove it from a save afterwards: it adds 8
blank faction slots (for `spawnfaction`) to the save. All changes stay in the save, and planets turned
into a modded type need that mod installed.

MIT license. Credits: LazyWizard and Lukas04 (Console Commands), LazyWizard (LazyLib), Histidine (Nexerelin).
