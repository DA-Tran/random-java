package com.randomjava.projects.coviddatadashboard;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Epidemic Dashboard - turns a daily case series into the figures that mean
 * something.
 *
 * <p>Raw daily counts are close to unreadable, because reporting is weekly in
 * shape: almost nowhere books cases at the weekend, so every series has a
 * seven-day sawtooth that has nothing to do with the disease. A <b>seven-day
 * rolling average</b> removes exactly one week of that artefact, which is why
 * the window is seven and not five or ten.
 *
 * <p>Growth is reported as a doubling time as well as a percentage, because
 * "8% a day" is hard to feel and "doubling every nine days" is not. When the
 * trend is falling the same arithmetic gives a halving time.
 */
public final class CovidDataDashboard implements Project {

    public static final Meta META = new Meta(81, "covid-data-dashboard", "Covid Data Dashboard", "Data Science", Kind.TOOL,
            Difficulty.INTERMEDIATE, "Summarise case and vaccination series by region and date.",
            "", true);

    private static final int WINDOW = 7;

    @Override public Meta meta() { return META; }

    public static List<Double> parse(String text) {
        List<Double> values = new ArrayList<>();
        for (String chunk : text.split("[,;\\s]+")) {
            if (chunk.isBlank()) { continue; }
            try {
                double value = Double.parseDouble(chunk.trim());
                if (value < 0) { throw new IllegalArgumentException("Case counts cannot be negative."); }
                values.add(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + chunk + "' is not a number.");
            }
        }
        if (values.size() < WINDOW * 2) {
            throw new IllegalArgumentException("Give at least " + (WINDOW * 2)
                    + " days so a rolling average has something to compare against. Got "
                    + values.size() + ".");
        }
        return values;
    }

    /** Trailing seven-day mean. The first six days have no full window. */
    public static List<Double> rollingAverage(List<Double> daily) {
        List<Double> out = new ArrayList<>();
        double running = 0;
        for (int i = 0; i < daily.size(); i++) {
            running += daily.get(i);
            if (i >= WINDOW) { running -= daily.get(i - WINDOW); }
            out.add(i >= WINDOW - 1 ? running / WINDOW : Double.NaN);
        }
        return out;
    }

    /** Daily growth rate between the last two complete weeks, as a fraction. */
    public static double dailyGrowth(List<Double> daily) {
        int n = daily.size();
        double thisWeek = 0;
        double lastWeek = 0;
        for (int i = 0; i < WINDOW; i++) {
            thisWeek += daily.get(n - 1 - i);
            lastWeek += daily.get(n - 1 - WINDOW - i);
        }
        if (lastWeek == 0) { return 0; }
        // Weekly ratio converted to a per-day rate.
        return Math.pow(thisWeek / lastWeek, 1.0 / WINDOW) - 1;
    }

    /** Days to double at the current rate, or to halve when it is falling. */
    public static double doublingTime(double dailyGrowth) {
        if (Math.abs(dailyGrowth) < 1e-9) { return Double.POSITIVE_INFINITY; }
        return Math.log(2) / Math.log(1 + dailyGrowth);
    }

    public static double per100k(double cases, double population) {
        if (population <= 0) { throw new IllegalArgumentException("Population must be positive."); }
        return cases / population * 100_000;
    }

    private String detail(List<Double> daily, double population) {
        List<Double> smooth = rollingAverage(daily);
        double latest = smooth.get(smooth.size() - 1);
        double growth = dailyGrowth(daily);
        double doubling = doublingTime(growth);

        double rawLast = daily.get(daily.size() - 1);
        StringBuilder sb = new StringBuilder(String.format(
                "%d days of data.%n"
                + "Yesterday's raw count: %.0f%n"
                + "Seven-day average:     %.1f   <- use this one%n"
                + "The raw figure is %.0f%% %s the smoothed one, which is reporting "
                + "rhythm rather than real change.",
                daily.size(), rawLast, latest,
                Math.abs(100 * (rawLast - latest) / Math.max(1, latest)),
                rawLast > latest ? "above" : "below"));

        sb.append(String.format("%n%nGrowth: %+.2f%% per day", growth * 100));
        if (Double.isInfinite(doubling)) {
            sb.append("\nFlat, so there is no doubling or halving time.");
        } else if (growth > 0) {
            sb.append(String.format("%nDoubling every %.1f days if this holds.", doubling));
        } else {
            sb.append(String.format("%nHalving every %.1f days if this holds.", Math.abs(doubling)));
        }

        if (population > 0) {
            double weekly = 0;
            for (int i = 0; i < WINDOW; i++) { weekly += daily.get(daily.size() - 1 - i); }
            sb.append(String.format("%n%nWeekly cases per 100,000: %.1f", per100k(weekly, population)));
            sb.append("\nPer-capita is the only way to compare places of different sizes; "
                    + "raw totals just rank them by population.");
        }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Daily case counts in order, at least 14. Blank line to finish.");
        while (true) {
            String input = io.ask("daily cases:");
            if (input.isEmpty()) { return; }
            try {
                List<Double> daily = parse(input);
                double population = io.askDouble("population (0 to skip):", 0);
                double growth = dailyGrowth(daily);
                io.result(String.format("%+.2f%% per day", growth * 100),
                        Double.isInfinite(doublingTime(growth)) ? "flat"
                                : String.format("%s every %.1f days",
                                        growth > 0 ? "doubling" : "halving",
                                        Math.abs(doublingTime(growth))));
                io.println("  " + detail(daily, population).replace("\n", "\n  "));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            List<Double> daily = parse(Json.str(body, "input", ""));
            double population = Json.num(body, "population", 0);
            List<Double> smooth = new ArrayList<>();
            for (double value : rollingAverage(daily)) {
                smooth.add(Double.isNaN(value) ? 0 : value);
            }
            double growth = dailyGrowth(daily);
            return Json.ok("result", String.format("%.1f a day on the seven-day average, %+.2f%% growth",
                            smooth.get(smooth.size() - 1), growth * 100),
                    "detail", detail(daily, population), "bars", smooth);
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
