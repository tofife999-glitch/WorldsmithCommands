# Worldsmith Commands

Console commands for shaping the sector. Starsector 0.98a-RC8; needs Console Commands, LazyLib and
Nexerelin. [Download](https://github.com/tofife999-glitch/WorldsmithCommands/releases/latest)

Type `help worldsmith` in the console. Without a name, commands use the open planet dialog.

- `showsystem [system]`: list a system's planets with type, hazard, owner and resources
- `findplanet <filters>`: search the sector, e.g. `findplanet habitable farmland hazard<150 within:20`
- `planetinfo [planet]`: everything about one planet
- `planettypes [filter]` / `startypes [filter]`: list all planet / star types
- `visit <planet>`: open any planet's dialog from anywhere
- `npccolony [planet] <faction> [size]`: found a colony for a faction
- `npcexpedition [planet] <faction>`: send a Nexerelin colony expedition
- `treatasnpccolony [colony] [off]`: make Nexerelin treat a colony like its own expedition colony
- `popcap [colony] <size|off>`: cap a colony's size
- `terraform [planet] <type> [keep]`: change a planet's type (`keep` keeps its conditions)
- `rename <current name> <new name>`: rename a planet, station or star system
- `addplanet [type] [name]`: add a planet at your fleet's position
- `addmoon <planet> [type] [name]`: add a moon to a planet
- `addobject <type>`: add a stable location, relay, buoy, sensor array or inactive gate
- `addring [planet] <belt|dust|ice|magfield>`: add an asteroid belt, ring or magnetic field
- `newsystem <name> [star type]`: create an empty star system at your position in hyperspace
- `moveplanet <planet>`: move a planet (with its moons) to your fleet's orbit
- `resizeplanet [planet] <size|auto>`: change a planet's size
- `changestar [system] <star type> [reroll]`: change a system's star
- `removeplanet <planet>`: delete an uninhabited planet (asks first)
- `factionpower <faction> [clear]`: a faction's strength and active Worldsmith effects
- `strengthenfaction` / `weakenfaction <faction> [1-3]`: gradual effects; level 3 lasts up to 3 years

**Note:** changes stay in your save even after removing the mod, and planets turned into a modded
type need that mod installed. Faction effects wear off by themselves.

MIT license. Credits: LazyWizard (Console Commands, LazyLib), Histidine (Nexerelin).
