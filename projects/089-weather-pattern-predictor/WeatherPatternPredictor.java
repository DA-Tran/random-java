package com.randomjava.projects.weatherpatternpredictor;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Weather Patterns - separates the seasonal cycle from the underlying trend.
 *
 * <p>Raw monthly figures conflate two different things: where you are in the
 * year, and whether the climate is shifting. Decomposition splits them. Each
 * month gets a <b>seasonal index</b> - its average divided by the overall
 * average - so 1.20 means that month runs 20% above the yearly norm. Dividing
 * the observations by their index removes the cycle, and what is left is the
 * trend.
 *
 * <p>The indices necessarily sum to 12, one per month, which is a useful
 * internal check: if they do not, the decomposition is wrong.
 */
public final class WeatherPatternPredictor implements Project {

    public static final Meta META = new Meta(89, "weather-pattern-predictor", "Weather Pattern Predictor", "Data Science", Kind.TOOL,
            Difficulty.INTERMEDIATE, "Find seasonal patterns in historical weather and extrapolate.",
            "", true);

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    public record Decomposition(double[] seasonal, double overallMean,
                                double trendPerYear, List<Double> deseasonalised) { }

    @Override public Meta meta() { return META; }

    public static List<Double> parse(String text) {
        List<Double> values = new ArrayList<>();
        for (String chunk : text.split("[,;\\s]+")) {
            if (chunk.isBlank()) { continue; }
            try { values.add(Double.parseDouble(chunk.trim())); }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + chunk + "' is not a number.");
            }
        }
        if (values.size() < 24) {
            throw new IllegalArgumentException(
                    "Give at least two full years of monthly figures, so a cycle can be seen. "
                            + "Got " + values.size() + ".");
        }
        return values;
    }

    /** Splits the series into a repeating seasonal shape and a linear trend. */
    public Decomposition decompose(List<Double> monthly) {
        double overall = monthly.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (overall == 0) {
            throw new IllegalArgumentException("The average is zero, so no index can be formed.");
        }

        double[] sums = new double[12];
        int[] counts = new int[12];
        for (int i = 0; i < monthly.size(); i++) {
            sums[i % 12] += monthly.get(i);
            counts[i % 12]++;
        }
        double[] seasonal = new double[12];
        for (int m = 0; m < 12; m++) {
            seasonal[m] = counts[m] == 0 ? 1 : (sums[m] / counts[m]) / overall;
        }
        // Normalise so the twelve indices sum to exactly 12.
        double indexSum = Arrays.stream(seasonal).sum();
        for (int m = 0; m < 12; m++) { seasonal[m] = seasonal[m] * 12 / indexSum; }

        List<Double> flat = new ArrayList<>();
        for (int i = 0; i < monthly.size(); i++) {
            flat.add(monthly.get(i) / seasonal[i % 12]);
        }

        // Least squares on the de-seasonalised series gives the real trend.
        int n = flat.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX += i; sumY += flat.get(i);
            sumXY += i * flat.get(i); sumXX += (double) i * i;
        }
        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        return new Decomposition(seasonal, overall, slope * 12, flat);
    }

    /** Projects ahead by continuing the trend and re-applying the seasonal shape. */
    public List<Double> forecast(List<Double> monthly, int months) {
        Decomposition parts = decompose(monthly);
        double base = parts.deseasonalised().get(parts.deseasonalised().size() - 1);
        List<Double> out = new ArrayList<>();
        for (int i = 1; i <= months; i++) {
            double trended = base + parts.trendPerYear() / 12 * i;
            out.add(trended * parts.seasonal()[(monthly.size() + i - 1) % 12]);
        }
        return out;
    }

    private String detail(List<Double> monthly, int ahead) {
        Decomposition parts = decompose(monthly);
        StringBuilder sb = new StringBuilder(String.format(
                "%d months of history, overall mean %.2f%n"
                + "Underlying trend once the season is removed: %+.3f per year%n%n"
                + "Seasonal index by month (1.00 is the yearly norm):",
                monthly.size(), parts.overallMean(), parts.trendPerYear()));
        for (int m = 0; m < 12; m++) {
            sb.append(String.format("%n  %-4s %.3f  %s", MONTHS[m], parts.seasonal()[m],
                    parts.seasonal()[m] > 1 ? "above the norm" : "below the norm"));
        }
        int warmest = 0;
        int coolest = 0;
        for (int m = 1; m < 12; m++) {
            if (parts.seasonal()[m] > parts.seasonal()[warmest]) { warmest = m; }
            if (parts.seasonal()[m] < parts.seasonal()[coolest]) { coolest = m; }
        }
        sb.append(String.format("%n%nPeak is %s, trough is %s.", MONTHS[warmest], MONTHS[coolest]));
        sb.append(String.format("%nIndices sum to %.4f, which must be 12 if the split is sound.",
                Arrays.stream(parts.seasonal()).sum()));
        List<Double> forecast = forecast(monthly, ahead);
        sb.append("\n\nNext ").append(ahead).append(" months:");
        for (int i = 0; i < forecast.size(); i++) {
            sb.append(String.format("%n  %-4s %.2f", MONTHS[(monthly.size() + i) % 12], forecast.get(i)));
        }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Monthly figures in order, at least 24 of them. Blank line to finish.");
        while (true) {
            String input = io.ask("history:");
            if (input.isEmpty()) { return; }
            try {
                List<Double> monthly = parse(input);
                int ahead = io.askInt("months ahead:", 1, 24, 6);
                Decomposition parts = decompose(monthly);
                io.result(String.format("%+.3f per year", parts.trendPerYear()),
                        "trend with the seasonal cycle removed");
                io.println("  " + detail(monthly, ahead).replace("\n", "\n  "));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            List<Double> monthly = parse(Json.str(body, "input", ""));
            int ahead = Math.max(1, Math.min(24, Json.integer(body, "months", 6)));
            Decomposition parts = decompose(monthly);
            List<Double> bars = new ArrayList<>();
            for (double index : parts.seasonal()) { bars.add(index * 100); }
            return Json.ok("result", String.format("Trend %+.3f per year once the season is removed",
                            parts.trendPerYear()),
                    "detail", detail(monthly, ahead), "bars", bars);
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
