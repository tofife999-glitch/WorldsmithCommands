package worldsmith.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.NascentGravityWellAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RingBandAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.StarTerrain;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Deletes an uninhabited planet or moon, or the second or third star of a system. Never picks a target by
 * itself and always asks for confirmation.
 */
public class RemovePlanet implements BaseCommandWithSuggestion {
    private static final String USAGE = "removeplanet <planet>   (always asks for confirmation)";

    /** Everything that has to happen (or blocks) when removing one planet. */
    private static final class Plan {
        PlanetAPI planet;
        StarSystemAPI system;
        final List<String> blockers = new ArrayList<>();
        final List<SectorEntityToken> terrain = new ArrayList<>();
        /** In-system jump points orbiting the planet: removed together with their hyperspace partner. */
        final List<SectorEntityToken> systemJumpPoints = new ArrayList<>();
        final Set<JumpPointAPI> hyperRemove = new LinkedHashSet<>();
        /** Hyperspace jump points that also lead elsewhere: only the destination to this planet is removed. */
        final List<JumpPointAPI> hyperTrim = new ArrayList<>();
        /** Jump points that only use the planet as "related planet" or as their picture: kept, link cleared. */
        final List<JumpPointAPI> unlinkRelated = new ArrayList<>();
        final List<JumpPointAPI> resetVisual = new ArrayList<>();
        final List<NascentGravityWellAPI> wells = new ArrayList<>();
        final List<CampaignFleetAPI> fleets = new ArrayList<>();
        /** Vanilla decorative "Habitat" debris (tag orbital_junk); the game deletes and regenerates it anyway. */
        final List<SectorEntityToken> junk = new ArrayList<>();
        boolean missionImportant;
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        boolean yes = WsUtil.endsWithYes(parts);
        if (yes) parts.remove(parts.size() - 1);
        final String name = WsUtil.join(parts, 0, parts.size());
        if (name.isEmpty()) {
            // Deliberately never uses the open dialog or the nearest planet.
            WsUtil.print("Usage: " + USAGE);
            return CommandResult.BAD_SYNTAX;
        }
        List<SectorEntityToken> companions = WsUtil.exactMatches(name, companionStars());
        SectorEntityToken e = companions.size() == 1 ? companions.get(0)
                : WsUtil.findBody(name, WsUtil.Kind.PLANET, "planet or moon");
        if (e == null) return CommandResult.ERROR;
        final PlanetAPI planet = (PlanetAPI) e;

        Plan plan = analyze(planet);
        if (!plan.blockers.isEmpty()) {
            WsUtil.print("Can't remove " + planet.getName() + " (" + WsUtil.systemName(planet) + "):");
            for (String b : plan.blockers) WsUtil.printRaw("  - " + b);
            return CommandResult.ERROR;
        }
        final String key = "removeplanet:" + planet.getId();
        if (yes) {
            WsUtil.cancelPending(key);
            if (isDialogOpenWith(planet)) {
                WsUtil.print("Leave " + planet.getName() + "'s dialog first, then run the command again.");
                return CommandResult.ERROR;
            }
            WsUtil.print(execute(plan));
            return CommandResult.SUCCESS;
        }

        String question = "Permanently remove " + planet.getName() + " (" + planet.getSpec().getName() + ") from "
                + plan.system.getName() + "?" + extras(plan);
        WsUtil.print(question);
        WsUtil.confirm(key, question, "removeplanet " + name + " yes", new Script() {
            @Override
            public void run() {
                WsUtil.runSafely("removeplanet", new Runnable() {
                    @Override
                    public void run() {
                        // Things may have changed while the popup was waiting: check again.
                        Plan again = analyze(planet);
                        if (!again.blockers.isEmpty()) {
                            WsUtil.report("Did not remove " + planet.getName() + ": " + String.join("; ", again.blockers));
                            return;
                        }
                        WsUtil.report(execute(again));
                    }
                });
            }
        });
        return CommandResult.SUCCESS;
    }

    /** Second and third stars of binary and trinary systems; unlike main stars, these can be removed. */
    private static List<SectorEntityToken> companionStars() {
        List<SectorEntityToken> out = new ArrayList<>();
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (s.getSecondary() != null) out.add(s.getSecondary());
            if (s.getTertiary() != null) out.add(s.getTertiary());
        }
        return out;
    }

    private static boolean isDialogOpenWith(SectorEntityToken e) {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        InteractionDialogAPI d = ui == null ? null : ui.getCurrentInteractionDialog();
        return d != null && d.getInteractionTarget() == e;
    }

    private static String extras(Plan p) {
        List<String> also = new ArrayList<>();
        for (SectorEntityToken t : p.terrain) also.add(describeThing(t));
        for (SectorEntityToken j : p.systemJumpPoints) also.add("jump point " + j.getName());
        for (JumpPointAPI j : p.hyperRemove) also.add("hyperspace jump point " + j.getName());
        if (!p.wells.isEmpty()) also.add(p.wells.size() + " hyperspace gravity well(s)");
        if (!p.junk.isEmpty()) also.add(p.junk.size() + " pieces of decorative orbital debris");
        String s = also.isEmpty() ? "" : " This also removes: " + String.join(", ", also) + ".";
        if (p.missionImportant)
            s += " WARNING: it is marked as important for an active mission; removing it may break that mission.";
        return s;
    }

    private static String describeThing(SectorEntityToken t) {
        if (t instanceof CampaignTerrainAPI) {
            CampaignTerrainAPI ct = (CampaignTerrainAPI) t;
            String n = ct.getPlugin() == null ? null : ct.getPlugin().getTerrainName();
            return (n == null || n.isEmpty()) ? "terrain (" + ct.getType() + ")" : n;
        }
        if (t instanceof RingBandAPI) return "ring band";
        String n = t.getName();
        return (n == null || n.isEmpty() ? t.getId() : n);
    }

    private static SectorEntityToken ringParent(SectorEntityToken e) {
        if (e instanceof CampaignTerrainAPI && ((CampaignTerrainAPI) e).getPlugin() instanceof BaseRingTerrain) {
            BaseRingTerrain.RingParams rp = ((BaseRingTerrain) ((CampaignTerrainAPI) e).getPlugin()).getRingParams();
            return rp == null ? null : rp.relatedEntity;
        }
        if (e instanceof RingBandAPI) return ((RingBandAPI) e).getFocus();
        return null;
    }

    private static List<JumpPointAPI.JumpDestination> destinations(JumpPointAPI jp) {
        List<JumpPointAPI.JumpDestination> d = jp.getDestinations();
        return d == null ? new ArrayList<JumpPointAPI.JumpDestination>() : d;
    }

    private static boolean leadsTo(JumpPointAPI jp, SectorEntityToken target) {
        for (JumpPointAPI.JumpDestination d : destinations(jp)) {
            if (d.getDestination() == target) return true;
        }
        return false;
    }

    private static Plan analyze(PlanetAPI planet) {
        Plan p = new Plan();
        p.planet = planet;
        LocationAPI loc = planet.getContainingLocation();
        if (planet.isStar() && !companionStars().contains(planet)) {
            p.blockers.add("it is the main star of its system; change its type with changestar instead.");
            return p;
        }
        if (!(loc instanceof StarSystemAPI) || !loc.getAllEntities().contains(planet)) {
            p.blockers.add("it is not in a star system (or was already removed).");
            return p;
        }
        p.system = (StarSystemAPI) loc;
        if (WsUtil.isColony(planet.getMarket())) {
            p.blockers.add("it is a colony (" + WsUtil.owner(planet) + "). Destroy the colony first: open its dialog"
                    + " (visit " + planet.getName() + "), type destroycolony, then run removeplanet again.");
        }
        p.missionImportant = planet.getMemoryWithoutUpdate().getBoolean(MemFlags.ENTITY_MISSION_IMPORTANT);

        // In-system jump points that belong to (orbit) this planet, each with its hyperspace partner(s).
        // Jump points that merely name the planet as "related planet" (often a system entrance orbiting the
        // star) are kept; only that link is cleared.
        LocationAPI hyper = Global.getSector().getHyperspace();
        Set<SectorEntityToken> ownJumpPoints = new LinkedHashSet<>();
        for (SectorEntityToken t : p.system.getJumpPoints()) {
            if (!(t instanceof JumpPointAPI)) continue;
            JumpPointAPI jp = (JumpPointAPI) t;
            if (jp.getOrbitFocus() != planet) {
                if (jp.getRelatedPlanet() == planet) p.unlinkRelated.add(jp);
                continue;
            }
            ownJumpPoints.add(jp);
            List<JumpPointAPI> partners = new ArrayList<>();
            for (SectorEntityToken h : hyper.getJumpPoints()) {
                if (h instanceof JumpPointAPI && leadsTo((JumpPointAPI) h, jp)) partners.add((JumpPointAPI) h);
            }
            for (JumpPointAPI.JumpDestination d : destinations(jp)) {
                SectorEntityToken dest = d.getDestination();
                if (dest instanceof JumpPointAPI && dest.getContainingLocation() == hyper && !partners.contains(dest))
                    partners.add((JumpPointAPI) dest);
            }
            if (partners.isEmpty()) {
                p.blockers.add("its jump point " + jp.getName() + " has no hyperspace partner I can find, so I can't"
                        + " remove both ends safely.");
                continue;
            }
            p.systemJumpPoints.add(jp);
            for (JumpPointAPI h : partners) addHyper(p, h, jp);
        }
        // Hyperspace jump points that lead straight to the planet (gas giant gravity wells), or only show it.
        for (SectorEntityToken t : hyper.getJumpPoints()) {
            if (!(t instanceof JumpPointAPI)) continue;
            JumpPointAPI h = (JumpPointAPI) t;
            if (leadsTo(h, planet)) addHyper(p, h, planet);
            else if (h.getDestinationVisualEntity() == planet && !p.hyperRemove.contains(h)) p.resetVisual.add(h);
        }
        if (hyper.getGravityWells() != null) {
            for (NascentGravityWellAPI w : hyper.getGravityWells()) {
                if (w.getTarget() == planet) p.wells.add(w);
            }
        }

        // Everything orbiting the planet: attached terrain and decorative debris go with it, anything else blocks.
        Map<String, List<String>> orbiting = new LinkedHashMap<>();
        for (SectorEntityToken t : p.system.getAllEntities()) {
            if (t == planet || ownJumpPoints.contains(t)) continue;
            if (t instanceof CampaignFleetAPI) {
                if (t.getOrbitFocus() == planet) p.fleets.add((CampaignFleetAPI) t);
                continue;
            }
            boolean attachedRing = ringParent(t) == planet;
            if (!attachedRing && t.getOrbitFocus() != planet) continue;
            if (t.hasTag(Tags.ORBITAL_JUNK)) {
                p.junk.add(t);
            } else if (attachedRing || t instanceof CampaignTerrainAPI) {
                p.terrain.add(t);
            } else {
                String kind = t instanceof PlanetAPI ? "moon" : (WsUtil.isStation(t) ? "station" : "object");
                String label = kind + " '" + describeThing(t) + "'";
                if (!orbiting.containsKey(label)) orbiting.put(label, new ArrayList<String>());
                orbiting.get(label).add(t.getId());
            }
        }
        if (!orbiting.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, List<String>> en : orbiting.entrySet()) {
                List<String> ids = en.getValue();
                parts.add(ids.size() == 1 ? en.getKey() + " (ID " + ids.get(0) + ")" : ids.size() + "x " + en.getKey());
            }
            p.blockers.add("other things orbit it: " + String.join(", ", parts) + ".");
        }
        return p;
    }

    private static void addHyper(Plan p, JumpPointAPI h, SectorEntityToken removedTarget) {
        if (p.hyperRemove.contains(h) || p.hyperTrim.contains(h)) return;
        int others = 0;
        for (JumpPointAPI.JumpDestination d : destinations(h)) {
            SectorEntityToken dest = d.getDestination();
            if (dest != removedTarget && dest != p.planet && !p.systemJumpPoints.contains(dest)) others++;
        }
        if (others > 0) p.hyperTrim.add(h);
        else p.hyperRemove.add(h);
    }

    private static String execute(Plan p) {
        PlanetAPI planet = p.planet;
        StarSystemAPI sys = p.system;
        LocationAPI hyper = Global.getSector().getHyperspace();
        List<String> also = new ArrayList<>();

        for (JumpPointAPI h : p.hyperTrim) {
            h.removeDestination(planet);
            for (SectorEntityToken j : p.systemJumpPoints) h.removeDestination(j);
        }
        List<JumpPointAPI> autoJumpPoints = sys.getAutogeneratedJumpPointsInHyper(); // may be null
        List<NascentGravityWellAPI> autoWells = sys.getAutogeneratedNascentWellsInHyper(); // may be null
        for (JumpPointAPI h : p.hyperRemove) {
            also.add("hyperspace jump point " + h.getName());
            hyper.removeEntity(h);
            if (autoJumpPoints != null) autoJumpPoints.remove(h);
        }
        for (NascentGravityWellAPI w : p.wells) {
            hyper.removeEntity(w);
            if (autoWells != null) autoWells.remove(w);
        }
        if (!p.wells.isEmpty()) also.add(p.wells.size() + " gravity well(s)");
        for (SectorEntityToken j : p.systemJumpPoints) {
            also.add("jump point " + j.getName());
            sys.removeEntity(j);
        }
        for (SectorEntityToken t : p.terrain) {
            also.add(describeThing(t));
            sys.removeEntity(t);
        }
        for (SectorEntityToken t : p.junk) sys.removeEntity(t);
        if (!p.junk.isEmpty()) also.add(p.junk.size() + " pieces of orbital debris");
        for (JumpPointAPI j : p.unlinkRelated) j.setRelatedPlanet(null);
        for (JumpPointAPI h : p.resetVisual) {
            if (sys.getStar() != null) h.setStandardWormholeToStarOrPlanetVisual(sys.getStar());
            else h.setStandardWormholeToNothingVisual();
        }
        for (CampaignFleetAPI f : p.fleets) f.setOrbit(null);
        sys.removeEntity(planet);
        if (planet == sys.getSecondary()) {
            sys.setSecondary(sys.getTertiary());
            sys.setTertiary(null);
        } else if (planet == sys.getTertiary()) {
            sys.setTertiary(null);
        }
        if (planet.isStar()) {
            sys.setType(sys.getSecondary() == null ? StarSystemGenerator.StarSystemType.SINGLE
                    : StarSystemGenerator.StarSystemType.BINARY_FAR);
            sys.setLightColor(StarTerrain.systemLight(sys, new Random()));
        }
        WsUtil.invalidateCache();
        return "Removed " + planet.getName() + " (" + planet.getSpec().getName() + ") from " + sys.getName() + "."
                + (also.isEmpty() ? "" : " Also removed: " + String.join(", ", also) + ".");
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) {
            List<String> l = WsUtil.bodySuggestions(WsUtil.Kind.UNCOLONIZED);
            for (SectorEntityToken s : companionStars()) l.add(WsUtil.token(s.getName()));
            return l;
        }
        return WsUtil.list("yes");
    }
}
