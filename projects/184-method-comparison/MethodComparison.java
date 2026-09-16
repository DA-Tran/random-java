package com.randomjava.projects.methodcomparison;

import com.randomjava.cube.FaceletCube;
import com.randomjava.cube.Move;
import com.randomjava.cube.Solution;
import com.randomjava.cube.Solver3x3Beginner;
import com.randomjava.cube.Solver3x3Cfop;
import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Runs every method the suite has on the same scramble and puts the results
 * side by side.
 *
 * <p>Arguments about solving methods are usually about move count, and this
 * settles them with numbers rather than opinion: the same cube, the same
 * scramble, each method's own stages and totals. Averaging over many scrambles
 * shows the gap is consistent rather than a lucky case.
 */
public final class MethodComparison implements Project {

    public static final Meta META = new Meta(184, "method-comparison", "Method Comparison", "Cube Solving Methods", Kind.TOOL,
            Difficulty.ADVANCED, "Run layer-by-layer and CFOP on the same scramble and compare the move counts.",
            "", true);

    private final Random random = new Random();

    @Override
    public Meta meta() {
        return META;
    }

    /** One method's result on one scramble. */
    public record Run(String method, int moves, int stages, String solution, List<String> steps) {
    }

    // ------------------------------------------------------------------
    // Core
    // ------------------------------------------------------------------

    /** Solves the same scramble with every method and reports each. */
    public List<Run> compare(String scramble) {
        List<Run> runs = new ArrayList<>();
        runs.add(run("beginner", scramble));
        runs.add(run("cfop", scramble));
        return runs;
    }

    private Run run(String which, String scramble) {
        FaceletCube cube = new FaceletCube(3);
        cube.apply(Move.parse(scramble, 3));
        Solution solution = which.equals("cfop")
                ? new Solver3x3Cfop().solve(cube, scramble)
                : new Solver3x3Beginner().solve(cube, scramble);

        // Never trust a solver's own word for it: replay onto a fresh cube.
        FaceletCube check = new FaceletCube(3);
        check.apply(Move.parse(scramble, 3));
        check.apply(solution.moves());
        if (!check.isSolved()) {
            throw new IllegalStateException(solution.method() + " produced a solution that "
                    + "does not solve the cube.");
        }

        List<String> steps = new ArrayList<>();
        for (Solution.Step step : solution.steps()) {
            steps.add(step.stage() + "  " + step.moveCount() + " moves");
        }
        return new Run(solution.method(), solution.moveCount(), solution.steps().size(),
                solution.moves(), steps);
    }

    /** Averages each method over several random scrambles. */
    public Map<String, Object> average(int trials) {
        int count = Math.max(1, Math.min(25, trials));
        int beginnerTotal = 0;
        int cfopTotal = 0;
        for (int i = 0; i < count; i++) {
            FaceletCube cube = new FaceletCube(3);
            String scramble = Move.format(cube.scramble(random));
            beginnerTotal += run("beginner", scramble).moves();
            cfopTotal += run("cfop", scramble).moves();
        }
        double beginnerAverage = beginnerTotal / (double) count;
        double cfopAverage = cfopTotal / (double) count;
        return Json.map(
                "trials", count,
                "beginner", round(beginnerAverage),
                "cfop", round(cfopAverage),
                "saving", round(100.0 * (beginnerAverage - cfopAverage) / beginnerAverage));
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    public String randomScramble() {
        return Move.format(new FaceletCube(3).scramble(random));
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        while (true) {
            int choice = io.menu("Method comparison", List.of(
                    "Compare on a random scramble",
                    "Compare on a scramble I type in",
                    "Average both over 10 scrambles"));
            if (choice < 0) {
                return;
            }
            try {
                if (choice == 2) {
                    Map<String, Object> averages = average(10);
                    io.result("Average over " + averages.get("trials") + " scrambles",
                            "layer-by-layer " + averages.get("beginner")
                                    + " moves,  CFOP " + averages.get("cfop") + " moves");
                    io.muted("CFOP is " + averages.get("saving") + "% shorter on average.");
                    continue;
                }
                String scramble = choice == 0 ? randomScramble() : io.ask("scramble:");
                if (scramble.isBlank()) {
                    continue;
                }
                io.println();
                io.muted("Scramble: " + scramble);
                List<List<String>> rows = new ArrayList<>();
                for (Run result : compare(scramble)) {
                    rows.add(List.of(result.method(), String.valueOf(result.moves()),
                            String.valueOf(result.stages())));
                }
                io.table(List.of("method", "moves", "stages"), rows);
            } catch (RuntimeException e) {
                io.error(e.getMessage());
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "scramble" -> {
                    return Json.ok("scramble", randomScramble(),
                            "detail", "A fresh random scramble. Compare to solve it both ways.");
                }
                case "compute", "compare" -> {
                    String scramble = Json.str(body, "scramble", "").trim();
                    if (scramble.isEmpty()) {
                        scramble = randomScramble();
                    }
                    List<Map<String, Object>> runs = new ArrayList<>();
                    for (Run result : compare(scramble)) {
                        runs.add(Json.map("method", result.method(), "moves", result.moves(),
                                "stages", result.stages(), "steps", result.steps(),
                                "solution", result.solution()));
                    }
                    int beginner = (int) runs.get(0).get("moves");
                    int cfop = (int) runs.get(1).get("moves");
                    return Json.ok("runs", runs, "scramble", scramble,
                            "result", cfop + " moves with CFOP against " + beginner
                                    + " layer by layer",
                            "detail", "Both solutions were replayed onto a fresh cube to "
                                    + "confirm they really solve it.");
                }
                case "average" -> {
                    Map<String, Object> averages = average(Json.integer(body, "trials", 10));
                    return Json.ok("result", "CFOP is " + averages.get("saving")
                            + "% shorter over " + averages.get("trials") + " scrambles",
                            "detail", "Layer by layer averaged " + averages.get("beginner")
                                    + " moves, CFOP averaged " + averages.get("cfop") + ".");
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
