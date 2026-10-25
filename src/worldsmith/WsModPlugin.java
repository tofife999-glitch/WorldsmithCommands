package worldsmith;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

/**
 * Re-applies what the game does not keep by itself when a save loads: spawned factions' Nexerelin settings,
 * ships and doctrine, faction lore, colony expedition settings, war focuses
 * and changed faction laws. The plugin itself is never stored in the save.
 */
public class WsModPlugin extends BaseModPlugin {
    @Override
    public void onGameLoad(boolean newGame) {
        try {
            FactionSlots.onGameLoad();
        } catch (Exception ex) {
            Global.getLogger(WsModPlugin.class).error("Worldsmith: could not restore spawned factions", ex);
        }
        try {
            FactionIdentity.onGameLoad();
        } catch (Exception ex) {
            Global.getLogger(WsModPlugin.class).error("Worldsmith: could not restore faction lore and flags", ex);
        }
        try {
            WarPlans.onGameLoad();
        } catch (Exception ex) {
            Global.getLogger(WsModPlugin.class).error("Worldsmith: could not restore war focuses", ex);
        }
        try {
            IllegalGoods.onGameLoad();
        } catch (Exception ex) {
            Global.getLogger(WsModPlugin.class).error("Worldsmith: could not restore changed faction laws", ex);
        }
        try {
            ExpeditionShares.onGameLoad();
        } catch (Exception ex) {
            Global.getLogger(WsModPlugin.class).error("Worldsmith: could not restore colony expedition settings", ex);
        }
    }
}
