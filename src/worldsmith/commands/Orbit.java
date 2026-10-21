package worldsmith.commands;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import worldsmith.OrbitCheck;
import worldsmith.WsUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Changes a planet's, moon's or companion star's orbit in plain terms: distance and speed in percent of the current
 * ones, the direction, or a tidal lock that keeps a moon behind its planet (on the far side from the star, in the
 * planet's shadow, where pulsar beams can't reach it). Things at the body's L4/L5 points and tidally locked moons
 * move along.
 */
public class Orbit implements BaseCommandWithSuggestion {
    private static final String USAGE = "orbit [planet] [distance <percent>] [speed <percent>] [reverse] [tidallock]";
    /** Set on a tidally locked moon, so it stays behind its planet when the planet's orbit changes. */
    public static final String MEM_LOCKED = "$worldsmith_behind";
    private static final float MIN_PERCENT = 10f;
    private static final float MAX_PERCENT = 1000f;
    private static final float MIN_DAYS = 1f;

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        List<String> parts = WsUtil.splitArgs(args);
        int firstOpt = parts.size();
        while (firstOpt > 0 && isOption(parts.get(firstOpt - 1))) firstOpt--;
        SectorEntityToken[] found = new SectorEntityToken[1];
        int k = WsUtil.leadingBody(parts, WsUtil.Kind.ANY, found);
        if (k < 0) return CommandResult.ERROR;
        SectorEntityToken target;
        List<String> opts;
        if (k > 0 && k >= firstOpt) {
            target = found[0];
            opts = parts.subList(k, parts.size());
        } else {
            target = WsUtil.resolveTarget(WsUtil.join(parts, 0, firstOpt), context, WsUtil.Kind.ANY,
                    "planet or moon", USAGE);
            opts = parts.subList(firstOpt, parts.size());
        }
        if (target == null) return CommandResult.ERROR;
        if (!(target instanceof PlanetAPI)) {
            WsUtil.print(target.getName() + " is a station. Only planets, moons and companion stars can be changed.");
            return CommandResult.ERROR;
        }
        PlanetAPI body = (PlanetAPI) target;
        SectorEntityToken focus = body.getOrbitFocus();
        if (focus == null || body.getOrbit() == null) {
            WsUtil.print(body.getName() + " doesn't orbit anything (" + (body.isStar() ? "it is the system's main star"
                    : "it has a fixed position") + "), so there is nothing to change.");
            return CommandResult.ERROR;
        }

        float oldR = Misc.getDistance(body.getLocation(), focus.getLocation());
        float oldAngle = Misc.getAngleInDegrees(focus.getLocation(), body.getLocation());
        float oldDays = body.getCircularOrbitPeriod();
        if (oldDays == 0f) oldDays = 365f;
        boolean wasLocked = body.getMemoryWithoutUpdate().getBoolean(MEM_LOCKED);
        if (opts.isEmpty()) {
            WsUtil.print(describe(body, focus, oldDays) + " Usage: " + USAGE);
            return CommandResult.SUCCESS;
        }

        // ---- read the options
        Float distPct = null, speedPct = null;
        boolean reverse = false, lock = false;
        for (int i = 0; i < opts.size(); i++) {
            String o = opts.get(i).toLowerCase(Locale.ROOT);
            if (o.equals("distance") || o.equals("speed") || o.startsWith("distance:") || o.startsWith("speed:")) {
                String key = o.startsWith("distance") ? "distance" : "speed";
                String value = o.contains(":") ? o.substring(o.indexOf(':') + 1)
                        : i + 1 < opts.size() ? opts.get(++i) : "";
                Float pct = percent(value);
                if (pct == null || pct < MIN_PERCENT || pct > MAX_PERCENT) {
                    return syntax(key + " needs a percentage between " + Math.round(MIN_PERCENT) + "% and "
                            + Math.round(MAX_PERCENT) + "%, like " + key + " 150% (100% = as now).");
                }
                if (key.equals("distance")) distPct = pct;
                else speedPct = pct;
            } else if (o.equals("reverse")) {
                reverse = true;
            } else if (o.equals("tidallock") || o.equals("behind")) {
                lock = true;
            } else {
                return syntax("'" + opts.get(i) + "' isn't an option.");
            }
        }

        // ---- work out the new orbit
        float newR = distPct != null ? oldR * distPct / 100f : oldR;
        float newDays;
        float newAngle;
        // A tidally locked moon stays locked when only its distance changes.
        if (wasLocked && speedPct == null && !reverse) lock = true;
        if (lock) {
            if (speedPct != null || reverse) {
                return syntax("A tidally locked moon takes its speed and direction from its planet, so 'speed' and"
                        + " 'reverse' can't go with 'tidallock'.");
            }
            SectorEntityToken star = focus.getOrbitFocus();
            if (body.isStar() || star == null || focus.getOrbit() == null || focus.getCircularOrbitPeriod() == 0f) {
                WsUtil.print("'tidallock' is for moons: it keeps a moon behind its planet, away from the star. "
                        + body.getName() + " orbits " + focus.getName() + ", which doesn't orbit anything.");
                return CommandResult.ERROR;
            }
            newDays = focus.getCircularOrbitPeriod();
            newAngle = Misc.getAngleInDegrees(star.getLocation(), focus.getLocation());
        } else {
            // It keeps the same travel speed unless 'speed' says otherwise, so a wider orbit takes longer.
            float abs = Math.abs(oldDays) * (newR / oldR) / (speedPct != null ? speedPct / 100f : 1f);
            abs = Math.max(MIN_DAYS, abs);
            boolean positive = (oldDays > 0f) != reverse;
            newDays = positive ? abs : -abs;
            newAngle = oldAngle;
        }

        LocationAPI loc = body.getContainingLocation();
        List<SectorEntityToken> all = OrbitCheck.relevant(loc);
        List<SectorEntityToken> companions = lagrangeCompanions(body, focus, oldR, oldDays, all);
        if (Math.abs(newR - oldR) > 1f) {
            Set<SectorEntityToken> exclude = new HashSet<>(OrbitCheck.satellites(body, all));
            exclude.add(body);
            exclude.addAll(companions);
            float reach = OrbitCheck.extent(body, all);
            String overlap = OrbitCheck.check(loc, focus, newR, reach, exclude);
            if (overlap == null && !WsUtil.isStar(focus) && focus instanceof PlanetAPI)
                overlap = OrbitCheck.checkParentFootprint(loc, focus, newR + reach + OrbitCheck.MARGIN, exclude);
            if (overlap != null) {
                WsUtil.print("Can't move " + body.getName() + " to " + Math.round(distPct) + "% of its distance: "
                        + overlap + " Nothing was changed.");
                return CommandResult.ERROR;
            }
        }

        // ---- apply
        body.setCircularOrbit(focus, newAngle, newR, newDays);
        if (lock) body.getMemoryWithoutUpdate().set(MEM_LOCKED, true);
        else body.getMemoryWithoutUpdate().unset(MEM_LOCKED);
        for (SectorEntityToken c : companions) {
            float offset = Misc.getAngleInDegrees(focus.getLocation(), c.getLocation()) - oldAngle;
            setOrbitLike(c, focus, newAngle + offset, newR, newDays);
        }
        int relocked = 0;
        for (SectorEntityToken e : all) {
            if (e.getOrbitFocus() != body || !e.getMemoryWithoutUpdate().getBoolean(MEM_LOCKED)) continue;
            float r = e.getCircularOrbitRadius() > 0f ? e.getCircularOrbitRadius()
                    : Misc.getDistance(e.getLocation(), body.getLocation());
            e.setCircularOrbit(body, newAngle, r, newDays);
            relocked++;
        }
        WsUtil.invalidateCache();

        StringBuilder msg = new StringBuilder(body.getName());
        int pct = Math.round(newR / oldR * 100f);
        if (pct > 100) msg.append(" is now ").append(pct - 100).append("% farther from ").append(focus.getName()).append(".");
        else if (pct < 100) msg.append(" is now ").append(100 - pct).append("% closer to ").append(focus.getName()).append(".");
        else msg.append(" keeps its distance.");
        msg.append(" ").append(describe(body, focus, newDays));
        if (!lock && Math.round(Math.abs(newDays)) != Math.round(Math.abs(oldDays)))
            msg.append(" (Before: ").append(Math.round(Math.abs(oldDays))).append(" days.)");
        if (wasLocked && !lock) msg.append(" It is no longer tidally locked.");
        if (!companions.isEmpty())
            msg.append(" ").append(companions.size()).append(" thing(s) at its L4/L5 points moved with it.");
        if (relocked > 0) msg.append(" Its tidally locked moon(s) stay behind it.");
        WsUtil.print(msg.toString());
        return CommandResult.SUCCESS;
    }

    private static String describe(PlanetAPI body, SectorEntityToken focus, float days) {
        // The game turns orbits with a positive number of days clockwise.
        String s = body.getName() + " circles " + focus.getName() + " " + (days > 0f ? "clockwise" : "counterclockwise")
                + "; one orbit takes " + Math.max(1, Math.round(Math.abs(days))) + " days.";
        SectorEntityToken star = focus.getOrbitFocus();
        if (body.getMemoryWithoutUpdate().getBoolean(MEM_LOCKED) && star != null) {
            s += " It is tidally locked: it always stays behind " + focus.getName() + ", away from " + star.getName()
                    + ", in its shadow where pulsar beams can't reach it.";
        }
        return s;
    }

    /** Things sharing the body's orbit exactly (relays and buoys at L4/L5, Trojan planets). */
    private static List<SectorEntityToken> lagrangeCompanions(PlanetAPI body, SectorEntityToken focus, float r,
                                                              float days, List<SectorEntityToken> all) {
        List<SectorEntityToken> out = new ArrayList<>();
        for (SectorEntityToken e : all) {
            if (e == body || e.getOrbitFocus() != focus || e.getOrbit() == null) continue;
            if (OrbitCheck.ring(e) != null) continue;
            if (Math.abs(Misc.getDistance(e.getLocation(), focus.getLocation()) - r) > 2f) continue;
            if (Math.abs(e.getCircularOrbitPeriod() - days) > 0.01f) continue;
            out.add(e);
        }
        return out;
    }

    /** Stations turn to face what they orbit; keep that. */
    private static void setOrbitLike(SectorEntityToken e, SectorEntityToken focus, float angle, float r, float days) {
        if (e.getOrbit().getClass().getSimpleName().contains("PointDown"))
            e.setCircularOrbitPointingDown(focus, angle, r, days);
        else e.setCircularOrbit(focus, angle, r, days);
    }

    private static boolean isOption(String token) {
        String o = token.toLowerCase(Locale.ROOT);
        return percent(o) != null || o.equals("distance") || o.equals("speed") || o.startsWith("distance:")
                || o.startsWith("speed:") || o.equals("reverse") || o.equals("tidallock") || o.equals("behind");
    }

    /** "150%" or "150" as 150; null if it isn't a number. */
    private static Float percent(String s) {
        String t = s.endsWith("%") ? s.substring(0, s.length() - 1) : s;
        try {
            float f = Float.parseFloat(t);
            return Float.isNaN(f) || Float.isInfinite(f) ? null : f;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static CommandResult syntax(String why) {
        WsUtil.print(why + " Usage: " + USAGE);
        return CommandResult.BAD_SYNTAX;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        if (parameter == 0) return WsUtil.bodySuggestions(WsUtil.Kind.PLANET);
        String last = previous.isEmpty() ? "" : previous.get(previous.size() - 1).toLowerCase(Locale.ROOT);
        if (last.equals("distance") || last.equals("speed")) return WsUtil.list("50%", "75%", "150%", "200%");
        return WsUtil.list("distance", "speed", "reverse", "tidallock");
    }
}
