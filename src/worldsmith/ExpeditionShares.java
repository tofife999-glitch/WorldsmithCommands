package worldsmith;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-save control of Nexerelin's colony expeditions: the share of all expeditions a faction sends, and how often
 * expeditions happen at all. Nexerelin picks the faction of each expedition by weight. A share set here becomes a
 * weight computed so that the faction gets exactly that share, while the other factions keep their usual weights and
 * share the rest in their usual proportions. The factions with colonies change over time, so the weights are worked
 * out again when a save loads, when something changes, and every few days. Everything is plain data in the save;
 * Nexerelin's settings are shared by all saves, so they are put back when a save without changes loads.
 */
public final class ExpeditionShares {
    /** Faction memory: the share set with the expeditions command, in percent (0-100). */
    public static final String MEM_SHARE = "$worldsmith_expedition_share";
    /** Sector memory: how often expeditions happen, in percent of Nexerelin's normal rate. */
    public static final String MEM_FREQUENCY = "$worldsmith_expedition_frequency";

    /** Nexerelin's own weights of factions changed this session (spawnfaction slots have their own normal). */
    private static final Map<String, Float> ORIGINAL = new HashMap<>();
    /** Nexerelin's own expedition interval, and the one last set here (to notice when LunaLib changed it since). */
    private static float originalInterval;
    private static Float appliedInterval;

    private ExpeditionShares() {
    }

    // ------------------------------------------------------------------ weights

    /** The weight Nexerelin gives the faction without shares set here. */
    public static float normalWeight(FactionAPI f) {
        if (FactionSlots.isSlot(f)) {
            // factionpersonality's expansionist / isolationist
            MemoryAPI mem = f.getMemoryWithoutUpdate();
            return mem.contains(FactionCharacter.MEM_EXPANSION) ? mem.getFloat(FactionCharacter.MEM_EXPANSION) : 1f;
        }
        Float orig = ORIGINAL.get(f.getId());
        return orig != null ? orig : NexConfig.getFactionConfig(f.getId()).colonyExpeditionChance;
    }

    /**
     * False for factions without Nexerelin settings of their own: they share Nexerelin's default settings, which
     * must not be changed for one of them.
     */
    public static boolean canChange(FactionAPI f) {
        return !f.isPlayerFaction() && NexConfig.getFactionConfig(f.getId(), false) != null;
    }

    private static void setWeight(FactionAPI f, float weight) {
        if (!canChange(f)) return;
        NexFactionConfig conf = NexConfig.getFactionConfig(f.getId(), false);
        if (!FactionSlots.isSlot(f) && !ORIGINAL.containsKey(f.getId()))
            ORIGINAL.put(f.getId(), conf.colonyExpeditionChance);
        conf.colonyExpeditionChance = weight;
    }

    /** Nexerelin favours factions you sold survey data to, until their next expedition. */
    private static float surveyBonus(String factionId) {
        FactionAPI f = Global.getSector().getFaction(factionId);
        if (f == null) return 1f;
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        float mult = ColonyManager.EXPEDITION_BASE_CHANCE_BEFORE_DATA;
        if (mem.contains(ColonyManager.MEMORY_KEY_FACTION_SURVEY_BONUS))
            mult += mem.getFloat(ColonyManager.MEMORY_KEY_FACTION_SURVEY_BONUS);
        return Math.max(1f, mult / ColonyManager.EXPEDITION_BASE_CHANCE_BEFORE_DATA);
    }

    /** The chance (0-1) that Nexerelin picks this faction for the next expedition, as Nexerelin itself works it out. */
    public static float actualShare(FactionAPI f) {
        float total = 0f, mine = 0f;
        for (String id : SectorManager.getLiveFactionIdsCopy()) {
            float w = Math.max(0f, NexConfig.getFactionConfig(id).colonyExpeditionChance) * surveyBonus(id);
            total += w;
            if (id.equals(f.getId())) mine = w;
        }
        return total <= 0f ? 0f : mine / total;
    }

    public static boolean isSet(FactionAPI f) {
        return f.getMemoryWithoutUpdate().contains(MEM_SHARE);
    }

    /** The share set for the faction, in percent, or -1 if none is set. */
    public static float sharePercent(FactionAPI f) {
        return isSet(f) ? f.getMemoryWithoutUpdate().getFloat(MEM_SHARE) : -1f;
    }

    /** Sets the faction's share in percent (0-100); null removes it. */
    public static void setShare(FactionAPI f, Float percent) {
        if (percent == null) f.getMemoryWithoutUpdate().unset(MEM_SHARE);
        else f.getMemoryWithoutUpdate().set(MEM_SHARE, percent);
        apply();
        if (percent != null) ensureScript();
    }

    /** The shares set for factions that have colonies, added up, in percent. */
    public static float setTotal() {
        float sum = 0f;
        for (String id : SectorManager.getLiveFactionIdsCopy()) {
            FactionAPI f = Global.getSector().getFaction(id);
            if (f != null && isSet(f) && canChange(f)) sum += sharePercent(f);
        }
        return sum;
    }

    /** Turns the set shares into Nexerelin weights for the factions that have colonies now. */
    public static void apply() {
        // Back to the usual weights first, for factions changed earlier.
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (FactionSlots.isSlot(f) || ORIGINAL.containsKey(f.getId())) setWeight(f, normalWeight(f));
        }
        List<FactionAPI> set = new ArrayList<>();
        List<FactionAPI> others = new ArrayList<>();
        float setSum = 0f, othersWeight = 0f;
        for (String id : SectorManager.getLiveFactionIdsCopy()) {
            FactionAPI f = Global.getSector().getFaction(id);
            if (f == null) continue;
            if (isSet(f) && canChange(f)) {
                set.add(f);
                setSum += sharePercent(f) / 100f;
            } else {
                others.add(f);
                othersWeight += Math.max(0f, NexConfig.getFactionConfig(id).colonyExpeditionChance) * surveyBonus(id);
            }
        }
        if (set.isEmpty()) return;
        if (setSum >= 1f || othersWeight <= 0f) {
            // The set shares take all expeditions: the others send none, and the set shares are scaled to fill 100%.
            for (FactionAPI f : others) setWeight(f, 0f);
            for (FactionAPI f : set) setWeight(f, sharePercent(f) / 100f / surveyBonus(f.getId()));
        } else {
            // weight / (others + all set weights) = share, for every set faction at once
            float total = othersWeight / (1f - setSum);
            for (FactionAPI f : set) setWeight(f, sharePercent(f) / 100f * total / surveyBonus(f.getId()));
        }
    }

    /** Sets every faction back to its usual share. */
    public static void clearShares() {
        for (FactionAPI f : Global.getSector().getAllFactions()) f.getMemoryWithoutUpdate().unset(MEM_SHARE);
        apply();
    }

    // ------------------------------------------------------------------ frequency

    /** How often expeditions happen, in percent of Nexerelin's normal rate. */
    public static float frequency() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        return mem.contains(MEM_FREQUENCY) ? mem.getFloat(MEM_FREQUENCY) : 100f;
    }

    /** Null or 100 is Nexerelin's normal rate. */
    public static void setFrequency(Float percent) {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (percent == null || percent == 100f) mem.unset(MEM_FREQUENCY);
        else mem.set(MEM_FREQUENCY, percent);
        applyFrequency();
    }

    private static void applyFrequency() {
        float current = NexConfig.colonyExpeditionInterval;
        // Set by Nexerelin or LunaLib's settings since the last change here: that is the normal interval now.
        if (appliedInterval == null || current != appliedInterval) originalInterval = current;
        float percent = frequency();
        if (percent == 100f) {
            NexConfig.colonyExpeditionInterval = originalInterval;
            appliedInterval = null;
        } else {
            appliedInterval = originalInterval * 100f / percent;
            NexConfig.colonyExpeditionInterval = appliedInterval;
        }
    }

    // ------------------------------------------------------------------ keeping it up to date

    private static void ensureScript() {
        if (!Global.getSector().hasTransientScript(Refresh.class)) Global.getSector().addTransientScript(new Refresh());
    }

    /** Works the weights out again every few days, as factions gain or lose their colonies. */
    public static class Refresh implements EveryFrameScript {
        private final IntervalUtil interval = new IntervalUtil(3f, 3f);

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public void advance(float amount) {
            interval.advance(Global.getSector().getClock().convertToDays(amount));
            if (!interval.intervalElapsed()) return;
            try {
                apply();
            } catch (Exception ex) {
                Global.getLogger(ExpeditionShares.class).error("Worldsmith: could not update expedition shares", ex);
            }
        }
    }

    public static void onGameLoad() {
        applyFrequency();
        apply();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (isSet(f)) {
                ensureScript();
                break;
            }
        }
    }
}
