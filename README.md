# Worldsmith Commands

Console commands for shaping the sector. Starsector 0.98a-RC8; needs Console Commands, LazyLib and
Nexerelin. [Download](https://github.com/tofife999-glitch/WorldsmithCommands/releases/latest)

Type `help worldsmith` in the console. Without a name, commands use the open planet dialog.

- `showsystem [system]`: list a system's planets with type, hazard, owner and resources
- `visit <planet>`: open any planet's dialog from anywhere
- `npccolony [planet] <faction> [size]`: found a colony for a faction
- `treatasnpccolony [colony] [off]`: make Nexerelin treat a colony like its own expedition colony
- `popcap [colony] <size|off>`: cap a colony's size
- `planettypes [filter]`: list all planet types
- `terraform [planet] <type> [keep]`: change a planet's type (`keep` keeps its conditions)
- `rename <current name> <new name>`: rename a planet, station or star system
- `addplanet [type] [name]`: add a planet at your fleet's position
- `removeplanet <planet>`: delete an uninhabited planet (asks first)
- `npcexpedition [planet] <faction>`: send a Nexerelin colony expedition

**Note:** changes stay in your save even after removing the mod, and planets turned into a modded
type need that mod installed.

MIT license. Credits: LazyWizard (Console Commands, LazyLib), Histidine (Nexerelin).
