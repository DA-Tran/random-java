package com.randomjava.projects.trafficanalysis;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Traffic Analysis - flow, density and speed, and the relation between them.
 *
 * <p>The fundamental equation of traffic flow is <b>flow = density x speed</b>:
 * vehicles per hour equals vehicles per kilometre times kilometres per hour.
 * Give any two and the third follows, which is why this takes whichever two you
 * have rather than insisting on a fixed pair.
 *
 * <p>The counter-intuitive part, and the reason congestion exists at all, is
 * that flow is not monotonic in density. An empty road carries nothing because
 * there are no vehicles; a jammed road carries nothing because nobody moves.
 * Peak throughput sits in the middle, and pushing past it makes things worse.
 */
public final class TrafficAnalysis implements Project {

    public static final Meta META = new Meta(85, "traffic-analysis", "Traffic Analysis", "Data Science", Kind.TOOL,
            Difficulty.INTERMEDIATE, "Aggregate sensor counts into flow, density and congestion.",
            "", true);

    /** Free-flow speed and jam density for a typical motorway lane. */
    private static final double FREE_FLOW_SPEED = 110.0;
    private static final double JAM_DENSITY = 140.0;

    public record State(double flow, double density, double speed) { }

    @Override public Meta meta() { return META; }

    /** Completes the third value from the other two. */
    public State complete(Double flow, Double density, Double speed) {
        int given = (flow != null ? 1 : 0) + (density != null ? 1 : 0) + (speed != null ? 1 : 0);
        if (given < 2) {
            throw new IllegalArgumentException(
                    "Give at least two of flow, density and speed; the third follows.");
        }
        if (flow == null) { flow = density * speed; }
        else if (density == null) {
            if (speed == 0) { throw new IllegalArgumentException("Speed cannot be zero when solving for density."); }
            density = flow / speed;
        } else if (speed == null) {
            if (density == 0) { throw new IllegalArgumentException("Density cannot be zero when solving for speed."); }
            speed = flow / density;
        }
        for (double value : new double[]{flow, density, speed}) {
            if (value < 0) { throw new IllegalArgumentException("None of these can be negative."); }
        }
        return new State(flow, density, speed);
    }

    /**
     * Greenshields' linear model: speed falls linearly as density rises, so
     * flow is a parabola peaking at half the jam density.
     */
    public static double modelledFlow(double density) {
        double speed = FREE_FLOW_SPEED * (1 - density / JAM_DENSITY);
        return Math.max(0, density * speed);
    }

    public static double capacityDensity() { return JAM_DENSITY / 2; }
    public static double capacity() { return modelledFlow(capacityDensity()); }

    /** Highway Capacity Manual style bands, by how full the road is. */
    public static String levelOfService(double density) {
        if (density <= 11) { return "A - free flow"; }
        if (density <= 18) { return "B - reasonably free"; }
        if (density <= 26) { return "C - stable but restricted"; }
        if (density <= 35) { return "D - approaching unstable"; }
        if (density <= 45) { return "E - at capacity"; }
        return "F - breakdown, stop and go";
    }

    private String detail(State state) {
        double utilisation = 100 * state.flow() / capacity();
        StringBuilder sb = new StringBuilder(String.format(
                "Flow %.0f veh/h = density %.1f veh/km x speed %.1f km/h%n"
                + "Level of service: %s%n"
                + "Modelled capacity is about %.0f veh/h at %.0f veh/km, so this is %.0f%% of it.",
                state.flow(), state.density(), state.speed(), levelOfService(state.density()),
                capacity(), capacityDensity(), utilisation));
        if (state.density() > capacityDensity()) {
            sb.append("\n\nDensity is past the peak, which is the congested side of the curve: "
                    + "adding vehicles here lowers throughput rather than raising it.");
        } else if (utilisation > 85) {
            sb.append("\n\nClose to capacity. Small disturbances at this point tend to "
                    + "propagate backwards as stop-and-go waves.");
        }
        return sb.toString();
    }

    private static Double number(String text) {
        if (text == null || text.isBlank() || text.equals("?") || text.equals("-")) { return null; }
        try { return Double.parseDouble(text.trim()); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a number.");
        }
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Enter flow (veh/h), density (veh/km) and speed (km/h). Leave one blank.");
        while (true) {
            String flow = io.ask("flow:");
            if (flow.isEmpty() && io.askYesNo("Finish?", false)) { return; }
            try {
                State state = complete(number(flow), number(io.ask("density:")), number(io.ask("speed:")));
                io.result(String.format("%.0f veh/h", state.flow()), levelOfService(state.density()));
                io.println("  " + detail(state).replace("\n", "\n  "));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            String input = Json.str(body, "input", "");
            String[] bits = input.trim().split("[\\s,;]+");
            if (bits.length < 3) {
                return Json.error("Give flow, density and speed with one left as ? , "
                        + "for example:  ? 25 90");
            }
            State state = complete(number(bits[0]), number(bits[1]), number(bits[2]));
            List<Double> curve = new ArrayList<>();
            for (int d = 0; d <= 140; d += 5) { curve.add(modelledFlow(d)); }
            return Json.ok("result", String.format("%.0f veh/h   %s",
                            state.flow(), levelOfService(state.density())),
                    "detail", detail(state), "bars", curve);
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
