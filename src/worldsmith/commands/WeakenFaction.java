package worldsmith.commands;

/** Gradually weakens a faction; everything wears off by itself. */
public class WeakenFaction extends FactionLevelCommand {
    @Override
    boolean weaken() {
        return true;
    }
}
