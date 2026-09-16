package com.randomjava.projects.energyconsumptiondashboard;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Energy Consumption - what each appliance costs, and what to do about it.
 *
 * <p>Energy is watts times hours, so a 2000 W kettle used six minutes a day
 * costs less than a 5 W router left on permanently. Ranking by wattage gets
 * that exactly backwards, which is why everything here is ranked by kWh over
 * the period rather than by how powerful the thing is.
 *
 * <p>Standby draw is worth calling out separately: a handful of watts is easy
 * to dismiss, but it runs 8,760 hours a year.
 */
public final class EnergyConsumptionDashboard implements Project {

    public static final Meta META = new Meta(90, "energy-consumption-dashboard", "Energy Consumption Dashboard", "Data Science", Kind.TOOL,
            Difficulty.INTERMEDIATE, "Break down usage by appliance and hour, and cost it.",
            "", true);

    public record Appliance(String name, double watts, double hoursPerDay) {
        /** Kilowatt hours over a number of days. */
        public double kwh(int days) { return watts * hoursPerDay * days / 1000.0; }
    }

    @Override public Meta meta() { return META; }

    /** Parses "fridge 150 24, kettle 2000 0.1" into appliances. */
    public List<Appliance> parse(String text) {
        List<Appliance> out = new ArrayList<>();
        for (String chunk : text.split("[,;\\n]+")) {
            String piece = chunk.trim();
            if (piece.isEmpty()) { continue; }
            String[] bits = piece.split("[\\s:=]+");
            if (bits.length < 3) {
                throw new IllegalArgumentException(
                        "Write each one as: name watts hoursPerDay   (e.g. fridge 150 24)");
            }
            try {
                double watts = Double.parseDouble(bits[bits.length - 2]);
                double hours = Double.parseDouble(bits[bits.length - 1]);
                if (watts < 0) { throw new IllegalArgumentException("Watts cannot be negative."); }
                if (hours < 0 || hours > 24) {
                    throw new IllegalArgumentException("Hours per day must be between 0 and 24.");
                }
                String name = String.join(" ", Arrays.copyOfRange(bits, 0, bits.length - 2));
                out.add(new Appliance(name, watts, hours));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + piece + "' needs a wattage and hours per day.");
            }
        }
        if (out.isEmpty()) { throw new IllegalArgumentException("No appliances given."); }
        return out;
    }

    /** Sorted by what they actually consume, not by how powerful they are. */
    public List<Appliance> ranked(List<Appliance> appliances, int days) {
        List<Appliance> copy = new ArrayList<>(appliances);
        copy.sort((a, b) -> Double.compare(b.kwh(days), a.kwh(days)));
        return copy;
    }

    public double totalKwh(List<Appliance> appliances, int days) {
        return appliances.stream().mapToDouble(a -> a.kwh(days)).sum();
    }

    private String detail(List<Appliance> appliances, int days, double tariff) {
        double total = totalKwh(appliances, days);
        StringBuilder sb = new StringBuilder(String.format(
                "Over %d days: %.1f kWh, costing %.2f at %.3f per kWh.%n"
                + "Ranked by consumption, not by wattage:", days, total, total * tariff, tariff));
        for (Appliance a : ranked(appliances, days)) {
            double kwh = a.kwh(days);
            sb.append(String.format("%n  %-18s %7.1f kWh  %7.2f  %4.1f%%  (%.0f W for %.1f h/day)",
                    a.name(), kwh, kwh * tariff, total == 0 ? 0 : 100 * kwh / total,
                    a.watts(), a.hoursPerDay()));
        }
        List<Appliance> order = ranked(appliances, days);
        if (!order.isEmpty() && total > 0) {
            Appliance worst = order.get(0);
            sb.append(String.format("%n%nMost of the bill is %s at %.0f%% of the total.",
                    worst.name(), 100 * worst.kwh(days) / total));
            Appliance loudest = appliances.stream()
                    .max(Comparator.comparingDouble(Appliance::watts)).orElse(worst);
            if (!loudest.name().equals(worst.name())) {
                sb.append(String.format("%n%s draws the most power (%.0f W) but only costs %.2f, "
                        + "because it runs %.1f hours a day.",
                        loudest.name(), loudest.watts(), loudest.kwh(days) * tariff,
                        loudest.hoursPerDay()));
            }
        }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Appliances as: name watts hoursPerDay, comma separated. Blank line to finish.");
        while (true) {
            String input = io.ask("appliances:");
            if (input.isEmpty()) { return; }
            try {
                List<Appliance> appliances = parse(input);
                int days = io.askInt("over how many days:", 1, 3650, 30);
                double tariff = io.askDouble("cost per kWh:", 0.28);
                io.result(String.format("%.2f", totalKwh(appliances, days) * tariff),
                        String.format("%.1f kWh over %d days", totalKwh(appliances, days), days));
                io.println("  " + detail(appliances, days, tariff).replace("\n", "\n  "));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            List<Appliance> appliances = parse(Json.str(body, "input", ""));
            int days = Math.max(1, Math.min(3650, Json.integer(body, "days", 30)));
            double tariff = Json.num(body, "tariff", 0.28);
            double total = totalKwh(appliances, days);
            List<Double> bars = new ArrayList<>();
            for (Appliance a : ranked(appliances, days)) { bars.add(a.kwh(days)); }
            return Json.ok("result", String.format("%.1f kWh, costing %.2f", total, total * tariff),
                    "detail", detail(appliances, days, tariff), "bars", bars);
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
