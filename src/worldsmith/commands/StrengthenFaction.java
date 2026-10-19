package worldsmith.commands;

/** Gradually strengthens a faction; everything wears off by itself. */
public class StrengthenFaction extends FactionLevelCommand {
    @Override
    boolean weaken() {
        return false;
    }
}
