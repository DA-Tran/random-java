package com.randomjava.projects.salesforecasting;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Sales Forecasting - least squares trend, with a moving average for comparison.
 *
 * <p>Least squares fits the line minimising squared error, which has a closed
 * form: no iteration, no learning rate, one pass over the data. R squared then
 * says how much of the variation that line actually explains, which is the
 * number that tells you whether to trust the forecast at all. A confident
 * projection from an R squared of 0.1 is noise with a slope.
 */
public final class SalesForecasting implements Project {

    public static final Meta META = new Meta(82, "sales-forecasting", "Sales Forecasting", "Data Science", Kind.TOOL,
            Difficulty.INTERMEDIATE, "Forecast next period from trend and seasonality.",
            "", true);

    public record Fit(double slope, double intercept, double rSquared) {
        public double at(double x) { return slope * x + intercept; }
    }

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
        if (values.size() < 3) {
            throw new IllegalArgumentException("Give at least three periods of history.");
        }
        return values;
    }

    /** Closed-form least squares against period index 0, 1, 2 ... */
    public Fit fit(List<Double> values) {
        int n = values.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            sumX += i; sumY += values.get(i);
            sumXY += i * values.get(i); sumXX += (double) i * i;
        }
        double denominator = n * sumXX - sumX * sumX;
        double slope = denominator == 0 ? 0 : (n * sumXY - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;

        double mean = sumY / n, totalVariation = 0, unexplained = 0;
        for (int i = 0; i < n; i++) {
            totalVariation += Math.pow(values.get(i) - mean, 2);
            unexplained += Math.pow(values.get(i) - (slope * i + intercept), 2);
        }
        double rSquared = totalVariation == 0 ? 1 : 1 - unexplained / totalVariation;
        return new Fit(slope, intercept, rSquared);
    }

    public double movingAverage(List<Double> values, int window) {
        int size = Math.min(Math.max(1, window), values.size());
        double total = 0;
        for (int i = values.size() - size; i < values.size(); i++) { total += values.get(i); }
        return total / size;
    }

    public List<Double> forecast(List<Double> values, int periods) {
        Fit fit = fit(values);
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < periods; i++) { out.add(fit.at(values.size() + i)); }
        return out;
    }

    private String detail(List<Double> values, int periods) {
        Fit fit = fit(values);
        StringBuilder sb = new StringBuilder(String.format(
                "%d periods of history, mean %.2f%n"
                + "Trend: %+.3f per period, starting from %.2f%n"
                + "R squared %.3f - %s%n"
                + "Moving average of the last 3: %.2f",
                values.size(), values.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                fit.slope(), fit.intercept(), fit.rSquared(),
                fit.rSquared() > 0.8 ? "the trend explains most of the variation"
                        : fit.rSquared() > 0.5 ? "a real but noisy trend"
                        : "barely a trend at all, so treat the forecast with suspicion",
                movingAverage(values, 3)));
        sb.append("\nForecast:");
        List<Double> ahead = forecast(values, periods);
        for (int i = 0; i < ahead.size(); i++) {
            sb.append(String.format("%n  period %d: %.2f", values.size() + i + 1, ahead.get(i)));
        }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Enter past figures separated by spaces or commas. Blank line to finish.");
        while (true) {
            String input = io.ask("history:");
            if (input.isEmpty()) { return; }
            try {
                List<Double> values = parse(input);
                int periods = io.askInt("periods ahead:", 1, 24, 3);
                io.result(String.format("%.2f", forecast(values, periods).get(0)), "next period");
                io.println("  " + detail(values, periods).replace("\n", "\n  "));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            List<Double> values = parse(Json.str(body, "input", ""));
            int periods = Math.max(1, Math.min(24, Json.integer(body, "periods", 3)));
            return Json.ok("result", String.format("Next period: %.2f", forecast(values, periods).get(0)),
                    "detail", detail(values, periods), "bars", values);
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
