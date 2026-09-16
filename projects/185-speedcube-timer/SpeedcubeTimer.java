package com.randomjava.projects.speedcubetimer;

import com.randomjava.cube.FaceletCube;
import com.randomjava.cube.Move;
import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A speedcubing session: scrambles, times, and the rolling averages competitions
 * actually judge on.
 *
 * <p>An average of 5 is not the mean of five solves. The fastest and the slowest
 * are thrown away first and the middle three are averaged, so one lucky solve or
 * one disaster cannot carry a result. An average of 12 works the same way. That
 * trimming is the whole reason the statistic is worth reporting, and it is the
 * part a naive implementation gets wrong.
 *
 * <p>Penalties follow the same rules: a +2 adds two seconds to the recorded
 * time, and a DNF counts as infinitely slow, so it is always the one discarded
 * in an average of 5 unless there are two of them, in which case the average
 * itself is a DNF.
 */
public final class SpeedcubeTimer implements Project {

    public static final Meta META = new Meta(185, "speedcube-timer", "Speedcube Timer", "Cube Solving Methods", Kind.LIST,
            Difficulty.BEGINNER, "Inspection countdown, timing, and rolling averages of 5 and 12 over a session.",
            "", true);

    /** One recorded solve. */
    public record Solve(int id, double seconds, String penalty, String scramble) {

        /** The time as judged, with a +2 applied and a DNF treated as infinite. */
        public double effective() {
            if ("DNF".equals(penalty)) {
                return Double.POSITIVE_INFINITY;
            }
            return "+2".equals(penalty) ? seconds + 2 : seconds;
        }

        public String display() {
            if ("DNF".equals(penalty)) {
                return "DNF";
            }
            return format(effective()) + ("+2".equals(penalty) ? " (+2)" : "");
        }
    }

    private final List<Solve> solves = new ArrayList<>();
    private final Random random = new Random();
    private int nextId = 1;

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // Core
    // ------------------------------------------------------------------

    public Solve add(double seconds, String penalty, String scramble) {
        if (seconds < 0) {
            throw new IllegalArgumentException("A solve cannot take negative time.");
        }
        if (seconds > 3600) {
            throw new IllegalArgumentException("That is over an hour. Check the number.");
        }
        String clean = "+2".equals(penalty) || "DNF".equals(penalty) ? penalty : "";
        Solve solve = new Solve(nextId++, seconds, clean, scramble);
        solves.add(solve);
        return solve;
    }

    public boolean remove(int id) {
        return solves.removeIf(solve -> solve.id() == id);
    }

    public void clear() {
        solves.clear();
    }

    public String scramble() {
        return Move.format(new FaceletCube(3).scramble(random));
    }

    /**
     * The trimmed average of the last {@code count} solves, or NaN when there
     * are not enough yet.
     *
     * <p>Drops the best and worst, then means the rest. Two or more DNFs make
     * the whole average a DNF, which is returned as infinity.
     */
    public double average(int count) {
        if (solves.size() < count) {
            return Double.NaN;
        }
        List<Double> window = new ArrayList<>();
        for (Solve solve : solves.subList(solves.size() - count, solves.size())) {
            window.add(solve.effective());
        }
        long dnfs = window.stream().filter(v -> v.isInfinite()).count();
        if (dnfs >= 2) {
            return Double.POSITIVE_INFINITY;
        }
        Collections.sort(window);
        double total = 0;
        for (int i = 1; i < window.size() - 1; i++) {
            total += window.get(i);
        }
        return total / (window.size() - 2);
    }

    public double best() {
        return solves.stream().mapToDouble(Solve::effective).min().orElse(Double.NaN);
    }

    public double worst() {
        return solves.stream().mapToDouble(Solve::effective)
                .filter(v -> !Double.isInfinite(v)).max().orElse(Double.NaN);
    }

    /** The plain mean of every finished solve, which is not the same as an average of 5. */
    public double sessionMean() {
        List<Double> finished = new ArrayList<>();
        for (Solve solve : solves) {
            if (!Double.isInfinite(solve.effective())) {
                finished.add(solve.effective());
            }
        }
        if (finished.isEmpty()) {
            return Double.NaN;
        }
        return finished.stream().mapToDouble(Double::doubleValue).sum() / finished.size();
    }

    public static String format(double seconds) {
        if (Double.isNaN(seconds)) {
            return "-";
        }
        if (Double.isInfinite(seconds)) {
            return "DNF";
        }
        if (seconds < 60) {
            return String.format("%.2f", seconds);
        }
        int minutes = (int) (seconds / 60);
        return minutes + ":" + String.format("%05.2f", seconds - minutes * 60);
    }

    private String summary() {
        if (solves.isEmpty()) {
            return "No solves yet.";
        }
        return solves.size() + " solves  |  best " + format(best())
                + "  |  Ao5 " + format(average(5))
                + "  |  Ao12 " + format(average(12))
                + "  |  session mean " + format(sessionMean());
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = solves.size() - 1; i >= 0; i--) {
            Solve solve = solves.get(i);
            out.add(Json.map("id", solve.id(), "label", "#" + solve.id() + "   " + solve.display(),
                    "meta", solve.scramble(), "done", "DNF".equals(solve.penalty())));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Times are entered rather than measured here, so the terminal version is a");
        io.muted("session log. The browser page has a real timer with inspection.");
        while (true) {
            io.println();
            if (!solves.isEmpty()) {
                List<List<String>> rows = new ArrayList<>();
                for (Solve solve : solves) {
                    rows.add(List.of(String.valueOf(solve.id()), solve.display(),
                            solve.scramble().length() > 34
                                    ? solve.scramble().substring(0, 34) + "..." : solve.scramble()));
                }
                io.table(List.of("#", "time", "scramble"), rows);
            }
            io.muted(summary());

            int choice = io.menu("Timer", List.of(
                    "Record a solve", "New scramble", "Remove a solve", "Clear the session"));
            if (choice < 0) {
                return;
            }
            switch (choice) {
                case 0 -> {
                    String scramble = scramble();
                    io.info("Scramble: " + scramble);
                    double seconds = io.askDouble("time in seconds:", 0);
                    String penalty = io.ask("penalty (blank, +2 or DNF):");
                    try {
                        add(seconds, penalty, scramble);
                        io.ok("Recorded.");
                    } catch (RuntimeException e) {
                        io.error(e.getMessage());
                    }
                }
                case 1 -> io.result("Scramble", scramble());
                case 2 -> io.println(remove(io.askInt("id:", 1, Integer.MAX_VALUE, 1))
                        ? "  removed" : "  no solve with that id");
                default -> {
                    clear();
                    io.ok("Session cleared.");
                }
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "scramble" -> {
                    return Json.ok("scramble", scramble(), "items", snapshot(),
                            "detail", summary());
                }
                case "add" -> {
                    add(Json.num(body, "seconds", -1),
                            Json.str(body, "penalty", ""),
                            Json.str(body, "scramble", ""));
                    return Json.ok("items", snapshot(), "message", "Solve recorded",
                            "detail", summary(), "scramble", scramble());
                }
                case "remove" -> {
                    remove(Json.integer(body, "id", -1));
                    return Json.ok("items", snapshot(), "detail", summary());
                }
                case "clear" -> {
                    clear();
                    return Json.ok("items", snapshot(), "message", "Session cleared",
                            "detail", summary());
                }
                case "list" -> {
                    return Json.ok("items", snapshot(), "detail", summary());
                }
                default -> {
                    return Json.error("Unknown action: " + action);
                }
            }
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }
}
