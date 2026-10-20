package worldsmith;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.util.Random;

/**
 * War fleets with plain vanilla orders: they arrive at one of the system's jump points, attack the target,
 * raid the system, then withdraw and vanish. Nothing of this mod is attached to the fleet.
 */
public final class Armadas {
    private Armadas() {
    }

    /** The biggest visible colony in the system, or null. */
    public static MarketAPI biggestColony(StarSystemAPI sys) {
        MarketAPI best = null;
        for (MarketAPI m : Misc.getMarketsInLocation(sys)) {
            if (m.isPlanetConditionMarketOnly() || m.isHidden() || m.getPrimaryEntity() == null) continue;
            if (best == null || m.getSize() > best.getSize()) best = m;
        }
        return best;
    }

    /**
     * Spawns the fleet; null if the faction can't build one (no ships it knows). {@code attackDays} is split
     * between attacking the target and raiding the rest of the system.
     */
    public static CampaignFleetAPI launch(FactionAPI faction, StarSystemAPI sys, MarketAPI target, float fp,
                                         String fleetType, String name, float attackDays, Random random) {
        FleetParamsV3 params = new FleetParamsV3(null, sys.getLocation(), faction.getId(), null, fleetType,
                fp, 0f, 0f, 0f, 0f, 0f, 0f);
        params.random = random;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;
        fleet.setName(name);
        sys.addEntity(fleet);
        List<SectorEntityToken> jumpPoints = sys.getJumpPoints();
        SectorEntityToken entry = jumpPoints.isEmpty() ? null : jumpPoints.get(random.nextInt(jumpPoints.size()));
        Vector2f at = entry != null ? entry.getLocation() : Misc.getPointAtRadius(sys.getCenter().getLocation(), 4000f, random);
        fleet.setLocation(at.x, at.y);
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        if (Factions.REMNANTS.equals(faction.getId())) {
            RemnantSeededFleetManager.initRemnantFleetProperties(random, fleet, false);
        } else {
            mem.set(MemFlags.MEMORY_KEY_NO_JUMP, true);
            mem.set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, true);
            mem.set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);
        }
        mem.set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        if (target != null) {
            fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, target.getPrimaryEntity(), attackDays / 2f,
                    "attacking " + target.getName());
        }
        fleet.addAssignment(FleetAssignment.RAID_SYSTEM, sys.getCenter(), target != null ? attackDays / 2f : attackDays,
                "raiding " + sys.getBaseName());
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, entry != null ? entry : sys.getCenter(), 60f,
                "withdrawing");
        return fleet;
    }
}
