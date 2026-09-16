package com.randomjava.projects.boxofficepredictor;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Box Office Predictor - estimates an opening weekend, and says how much to
 * trust it.
 *
 * <p>The model is multiplicative rather than additive, because the drivers
 * scale each other: a wide release doubles the take of a good film and of a bad
 * one alike. So a budget-derived baseline is multiplied by screen count, genre,
 * release window and review score, instead of adding fixed amounts that would
 * be nonsense at either end of the budget range.
 *
 * <p>A prediction interval comes with it. Opening weekends are genuinely hard
 * to call, and a single number implies a precision this does not have; the
 * range is what an honest forecast looks like.
 */
public final class BoxOfficePredictor implements Project {

    public static final Meta META = new Meta(83, "box-office-predictor", "Box Office Predictor", "Data Science", Kind.TOOL,
            Difficulty.INTERMEDIATE, "Predict opening weekend from budget, genre and screen count.",
            "", true);

    /** How each genre tends to front-load its run. */
    private static final Map<String, Double> GENRE = new LinkedHashMap<>();
    static {
        GENRE.put("horror", 1.45);      // opens huge, drops fast
        GENRE.put("superhero", 1.40);
        GENRE.put("action", 1.20);
        GENRE.put("animation", 0.95);   // plays long, opens softer
        GENRE.put("comedy", 1.00);
        GENRE.put("thriller", 1.05);
        GENRE.put("scifi", 1.10);
        GENRE.put("drama", 0.70);       // builds by word of mouth
        GENRE.put("documentary", 0.35);
        GENRE.put("musical", 0.85);
    }

    private static final Map<String, Double> WINDOW = Map.of(
            "summer", 1.25, "holiday", 1.30, "spring", 1.00,
            "autumn", 0.90, "fall", 0.90, "winter", 0.85, "january", 0.70);

    public record Prediction(double estimate, double low, double high,
                             double perScreen, List<String> drivers) { }

    @Override public Meta meta() { return META; }

    /**
     * @param budgetMillions production budget
     * @param screens        opening screen count
     * @param genre          one of the known genres
     * @param window         release window
     * @param reviewScore    0 to 100, or negative when unknown
     */
    public Prediction predict(double budgetMillions, int screens, String genre,
            String window, double reviewScore) {
        if (budgetMillions <= 0) { throw new IllegalArgumentException("Budget must be positive."); }
        if (screens <= 0) { throw new IllegalArgumentException("Screen count must be positive."); }

        List<String> drivers = new ArrayList<>();

        // Baseline: opening weekends scale sub-linearly with budget.
        double base = 3.2 * Math.pow(budgetMillions, 0.78);
        drivers.add(String.format("Budget %.0fM gives a baseline of %.1fM "
                + "(sub-linear: doubling spend does not double the opening)", budgetMillions, base));

        double screenFactor = Math.pow(screens / 3000.0, 0.55);
        drivers.add(String.format("%,d screens against a 3,000 norm: x%.2f", screens, screenFactor));

        double genreFactor = GENRE.getOrDefault(
                genre == null ? "" : genre.trim().toLowerCase(Locale.ROOT), 1.0);
        drivers.add(String.format("Genre %s: x%.2f", genre, genreFactor));

        double windowFactor = WINDOW.getOrDefault(
                window == null ? "" : window.trim().toLowerCase(Locale.ROOT), 1.0);
        drivers.add(String.format("Window %s: x%.2f", window, windowFactor));

        double reviewFactor = 1.0;
        if (reviewScore >= 0) {
            reviewFactor = 0.75 + reviewScore / 100.0 * 0.5;
            drivers.add(String.format("Reviews %.0f: x%.2f (they matter less on opening "
                    + "weekend than on the second)", reviewScore, reviewFactor));
        }

        double estimate = base * screenFactor * genreFactor * windowFactor * reviewFactor;
        // Wide interval, because opening weekends really are this uncertain.
        return new Prediction(estimate, estimate * 0.6, estimate * 1.6,
                estimate * 1_000_000 / screens, drivers);
    }

    public Set<String> genres() { return GENRE.keySet(); }

    private String detail(Prediction p) {
        StringBuilder sb = new StringBuilder("How it was built up:");
        for (String driver : p.drivers()) { sb.append("\n  ").append(driver); }
        sb.append(String.format("%n%nEstimate: %.1fM%nLikely range: %.1fM to %.1fM%n"
                + "Per screen: %,.0f", p.estimate(), p.low(), p.high(), p.perScreen()));
        sb.append("\n\nThe range is wide on purpose. Opening weekends swing on word of mouth "
                + "and what else released that week, neither of which is in this model, so a "
                + "single number would imply a precision it does not have.");
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Genres: " + String.join(", ", genres()));
        while (true) {
            double budget = io.askDouble("budget in millions (0 to stop):", 0);
            if (budget <= 0) { return; }
            try {
                Prediction p = predict(budget,
                        io.askInt("opening screens:", 1, 10000, 3000),
                        io.ask("genre:", "action"),
                        io.ask("window (summer/holiday/january/...):", "summer"),
                        io.askDouble("review score 0-100 (-1 if unknown):", -1));
                io.result(String.format("%.1fM", p.estimate()),
                        String.format("likely %.1fM to %.1fM", p.low(), p.high()));
                io.println("  " + detail(p).replace("\n", "\n  "));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            String input = Json.str(body, "input", "");
            String[] bits = input.trim().split("[\\s,]+");
            if (bits.length < 2) {
                return Json.error("Give at least a budget and screen count, as in:  "
                        + "200 4000 superhero summer 78");
            }
            Prediction p = predict(Double.parseDouble(bits[0]), Integer.parseInt(bits[1]),
                    bits.length > 2 ? bits[2] : "action",
                    bits.length > 3 ? bits[3] : "spring",
                    bits.length > 4 ? Double.parseDouble(bits[4]) : -1);
            return Json.ok("result", String.format("%.1fM opening weekend, likely %.1f to %.1f",
                            p.estimate(), p.low(), p.high()),
                    "detail", detail(p));
        } catch (NumberFormatException e) {
            return Json.error("Budget, screens and review score must be numbers.");
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
