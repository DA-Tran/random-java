package com.randomjava.projects.stockpricepredictor;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Stock Price Predictor - fits a trend line to a price history and extrapolates
 * it, then spends most of its effort telling you how little that is worth.
 *
 * <p>Drawing a line through past prices and continuing it is easy, and on its
 * own it is close to dishonest: it produces a single confident number with
 * nothing to say how wrong it might be. Three things here exist to stop that.
 *
 * <ul>
 *   <li><b>A prediction interval, not a point.</b> The width grows with the
 *       horizon, by the standard formula
 *       {@code s * sqrt(1 + 1/n + (x - xbar)^2 / Sxx)}. The last term is the
 *       cost of extrapolating: predicting far from the data you fitted on is
 *       less certain than predicting near it, and the arithmetic says so
 *       without being asked.</li>
 *   <li><b>R squared</b>, so a line fitted through noise is visibly a line
 *       fitted through noise rather than a forecast.</li>
 *   <li><b>A backtest against the naive baseline.</b> The last few points are
 *       held out, the trend is fitted without them and asked to predict them,
 *       and its error is compared against simply guessing that tomorrow equals
 *       today. On anything resembling a real price series the naive baseline
 *       usually wins, and a predictor that cannot report losing to it is not
 *       measuring anything.</li>
 * </ul>
 *
 * <p>None of this makes the output a forecast. Real prices are close to a
 * random walk, where the best estimate of tomorrow genuinely is today and a
 * fitted slope is mostly an artefact of the window chosen. The tool says so in
 * its own output rather than only in this comment.
 */
public final class StockPricePredictor implements Project {

    public static final Meta META = new Meta(57, "stock-price-predictor", "Stock Price Predictor", "AI and Machine Learning", Kind.TOOL,
            Difficulty.ADVANCED, "Project a price from history with moving averages and regression.",
            "", true);

    /** A straight line fitted to the history, with how well it fits. */
    public record Fit(double slope, double intercept, double rSquared,
                      double residualError, int points, double meanX, double sxx) {

        public double at(double x) { return intercept + slope * x; }

        /**
         * Half-width of the prediction interval at x.
         *
         * <p>The {@code (x - meanX)^2 / sxx} term is what makes this widen as
         * the horizon grows. Leaving it out gives a band of constant width,
         * which would claim a guess ten steps out is as good as one step out.
         */
        public double interval(double x, double tValue) {
            if (points <= 2 || sxx == 0) { return Double.NaN; }
            double leverage = 1.0 + 1.0 / points + Math.pow(x - meanX, 2) / sxx;
            return tValue * residualError * Math.sqrt(leverage);
        }
    }

    /** One projected step. */
    public record Projection(int step, double value, double low, double high) { }

    /** How the trend did against "tomorrow equals today" on held-out data. */
    public record Backtest(int heldOut, double trendError, double naiveError, boolean trendWon) { }

    public record Outlook(Fit fit, List<Projection> projections, List<Double> movingAverage,
                          Backtest backtest, String caveat) { }

    /**
     * Two-sided 95% t values by degrees of freedom, tailing off to the normal
     * approximation. A hard-coded 1.96 is badly wrong on the short histories
     * people actually paste in: at n = 5 the right multiplier is 3.18, so the
     * interval would be a third of its proper width.
     */
    private static final double[] T95 = {
            12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
            2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086};

    public static double tValue(int degreesOfFreedom) {
        if (degreesOfFreedom < 1) { return Double.NaN; }
        if (degreesOfFreedom <= T95.length) { return T95[degreesOfFreedom - 1]; }
        return 1.96;
    }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /** Reads prices out of free text: commas, spaces or newlines all work. */
    public static List<Double> prices(String text) {
        List<Double> out = new ArrayList<>();
        if (text == null) { return out; }
        for (String piece : text.split("[,;\\s]+")) {
            String clean = piece.replace("$", "").replace("_", "").trim();
            if (clean.isEmpty()) { continue; }
            try {
                double value = Double.parseDouble(clean);
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("\"" + piece + "\" is not a finite price.");
                }
                out.add(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("\"" + piece + "\" is not a number.");
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Fitting
    // ------------------------------------------------------------------

    /** Ordinary least squares against the index, 0 for the oldest price. */
    public static Fit fit(List<Double> series) {
        int n = series.size();
        if (n < 3) {
            throw new IllegalArgumentException(
                    "At least three prices are needed. With two you get a line through both "
                            + "points, no residuals, and therefore no idea how wrong it is.");
        }
        double meanX = (n - 1) / 2.0;
        double meanY = 0;
        for (double value : series) { meanY += value; }
        meanY /= n;

        double sxx = 0;
        double sxy = 0;
        for (int i = 0; i < n; i++) {
            sxx += (i - meanX) * (i - meanX);
            sxy += (i - meanX) * (series.get(i) - meanY);
        }
        double slope = sxx == 0 ? 0 : sxy / sxx;
        double intercept = meanY - slope * meanX;

        double sse = 0;
        double sst = 0;
        for (int i = 0; i < n; i++) {
            double predicted = intercept + slope * i;
            sse += Math.pow(series.get(i) - predicted, 2);
            sst += Math.pow(series.get(i) - meanY, 2);
        }
        // A flat series has no variance to explain. Reporting R squared as 1
        // there ("explained it all") or 0 ("explained none") are both lies, so
        // it is reported as NaN and rendered as "not meaningful".
        double rSquared = sst == 0 ? Double.NaN : 1 - sse / sst;
        double residual = Math.sqrt(sse / (n - 2));
        return new Fit(slope, intercept, rSquared, residual, n, meanX, sxx);
    }

    /** Trailing mean over the last {@code window} points, one value per step. */
    public static List<Double> movingAverage(List<Double> series, int window) {
        if (window < 1) { throw new IllegalArgumentException("The window must be at least 1."); }
        List<Double> out = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < series.size(); i++) {
            sum += series.get(i);
            if (i >= window) { sum -= series.get(i - window); }
            out.add(sum / Math.min(i + 1, window));
        }
        return out;
    }

    /**
     * Holds out the tail, fits on the rest, and compares the trend against
     * guessing that every held-out price equals the last one seen.
     *
     * <p>This is the only part of the tool that can embarrass it, which is what
     * makes it the useful part.
     */
    public static Backtest backtest(List<Double> series, int holdOut) {
        int n = series.size();
        if (holdOut < 1 || n - holdOut < 3) { return null; }
        List<Double> train = series.subList(0, n - holdOut);
        List<Double> test = series.subList(n - holdOut, n);
        Fit trained = fit(train);
        double lastSeen = train.get(train.size() - 1);

        double trendError = 0;
        double naiveError = 0;
        for (int i = 0; i < test.size(); i++) {
            double actual = test.get(i);
            trendError += Math.abs(actual - trained.at(train.size() + i));
            naiveError += Math.abs(actual - lastSeen);
        }
        trendError /= test.size();
        naiveError /= test.size();
        return new Backtest(holdOut, trendError, naiveError, trendError < naiveError);
    }

    public Outlook project(List<Double> series, int horizon, int window) {
        if (horizon < 1) { throw new IllegalArgumentException("Project at least one step."); }
        Fit fitted = fit(series);
        double t = tValue(series.size() - 2);

        List<Projection> projections = new ArrayList<>();
        for (int step = 1; step <= horizon; step++) {
            double x = series.size() - 1 + step;
            double value = fitted.at(x);
            double half = fitted.interval(x, t);
            projections.add(new Projection(step, value, value - half, value + half));
        }

        Backtest tested = backtest(series, Math.min(Math.max(1, series.size() / 4), 10));
        return new Outlook(fitted, projections, movingAverage(series, window), tested,
                caveat(fitted, tested));
    }

    /** The part people skip. Says plainly when the line means nothing. */
    private static String caveat(Fit fitted, Backtest tested) {
        StringBuilder sb = new StringBuilder();
        if (Double.isNaN(fitted.rSquared())) {
            sb.append("The series never moves, so there is no variation for a trend to explain. ");
        } else if (fitted.rSquared() < 0.3) {
            sb.append(String.format(
                    "The line explains %.0f%% of the movement, which is to say almost none of it. "
                    + "These projections are the shape of the noise, not of the price. ",
                    fitted.rSquared() * 100));
        } else if (fitted.rSquared() > 0.95) {
            sb.append(String.format(
                    "The line fits %.1f%% of the movement. Real prices do not do this, so treat a "
                    + "fit this clean as a sign the history is synthetic or very short. ",
                    fitted.rSquared() * 100));
        }
        if (tested != null) {
            sb.append(tested.trendWon()
                    ? String.format("Backtested on the last %d points the trend beat the naive "
                            + "\"tomorrow equals today\" guess, %.2f average error against %.2f. ",
                            tested.heldOut(), tested.trendError(), tested.naiveError())
                    : String.format("Backtested on the last %d points the trend was beaten by "
                            + "simply guessing that tomorrow equals today, %.2f average error "
                            + "against %.2f. On this history the trend line adds nothing. ",
                            tested.heldOut(), tested.trendError(), tested.naiveError()));
        }
        sb.append("Prices are close to a random walk. This extrapolates a straight line and "
                + "reports how uncertain that is; it does not forecast, and nothing here is "
                + "advice about money.");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    private static String describe(Outlook outlook) {
        Fit f = outlook.fit();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Trend: %+.4f per step, starting from %.2f.%n", f.slope(), f.intercept()));
        sb.append(String.format("Fit:   R2 %s, typical miss %.2f over %d points.%n%n",
                Double.isNaN(f.rSquared()) ? "not meaningful" : String.format("%.3f", f.rSquared()),
                f.residualError(), f.points()));
        sb.append("  step        value        95% interval        width\n");
        for (Projection p : outlook.projections()) {
            sb.append(String.format("  %4d   %10.2f   %8.2f to %8.2f   %8.2f%n",
                    p.step(), p.value(), p.low(), p.high(), p.high() - p.low()));
        }
        sb.append('\n').append(outlook.caveat());
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("Paste a price history. Commas, spaces or newlines all work.");
        while (true) {
            io.println();
            String raw = io.ask("prices (blank to quit):");
            if (raw.isEmpty()) { return; }
            try {
                List<Double> series = prices(raw);
                int horizon = io.askInt("steps ahead:", 1, 60, 5);
                Outlook outlook = project(series, horizon, Math.max(2, series.size() / 4));
                io.println();
                io.println(describe(outlook));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!List.of("compute", "project", "input").contains(action)) {
            return Json.error("Unknown action: " + action);
        }
        try {
            String raw = Json.str(body, "prices", "");
            if (raw.isBlank()) { raw = Json.str(body, "input", ""); }
            List<Double> series = prices(raw);
            if (series.isEmpty()) {
                return Json.error("Paste a price history first.");
            }
            int horizon = Math.min(Math.max(1, Json.integer(body, "horizon", 5)), 60);
            Outlook outlook = project(series, horizon, Math.max(2, series.size() / 4));

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Projection p : outlook.projections()) {
                rows.add(Json.map("step", p.step(),
                        "value", round(p.value()),
                        "low", round(p.low()),
                        "high", round(p.high()),
                        "width", round(p.high() - p.low())));
            }
            Projection last = outlook.projections().get(outlook.projections().size() - 1);
            return Json.ok("result", String.format("%.2f in %d step%s (%.2f to %.2f)",
                            last.value(), last.step(), last.step() == 1 ? "" : "s",
                            last.low(), last.high()),
                    "detail", describe(outlook),
                    "rows", rows);
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }

    private static double round(double value) {
        return Double.isFinite(value) ? Math.round(value * 100.0) / 100.0 : 0.0;
    }
}
