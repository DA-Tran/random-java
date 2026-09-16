package com.randomjava.cube;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Json;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Shared behaviour for every twisty puzzle in the suite.
 *
 * <p>All of them want the same things: scramble, turn by hand, solve, step
 * through the solution, ask for one hint, or type in the cube sitting on your
 * desk and have that solved instead. Putting it here means each puzzle is only
 * its size and its solver, and that the ones without a solver yet still give a
 * working puzzle you can scramble and turn.
 */
public abstract class CubeProject implements Project {

    private final Random random = new Random();
    private FaceletCube cube;
    private String scramble = "";
    private Solution solution;
    private int stepIndex;

    /** How many layers this puzzle has. */
    protected abstract int size();

    /** True once a real solver is wired up. */
    protected boolean hasSolver() {
        return false;
    }

    /** The solving methods on offer, most useful first. */
    protected List<String> methods() {
        return List.of();
    }

    /**
     * Solves the puzzle.
     *
     * @throws UnsupportedOperationException when this puzzle has no solver yet
     */
    protected Solution solvePuzzle(FaceletCube state, String scrambleText, String method) {
        throw new UnsupportedOperationException(
                "The solver for " + meta().name() + " is not written yet. "
                        + "Scrambling and turning work, and the move engine is shared with the "
                        + "puzzles that do solve.");
    }

    /** Anything worth knowing about this particular puzzle. */
    protected String note() {
        return "";
    }

    protected FaceletCube cube() {
        if (cube == null) {
            cube = new FaceletCube(size());
        }
        return cube;
    }

    // ------------------------------------------------------------------
    // Browser
    // ------------------------------------------------------------------

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "state" -> {
                    return snapshot("");
                }
                case "scramble" -> {
                    cube = new FaceletCube(size());
                    List<Move> moves = cube.scramble(random);
                    scramble = Move.format(moves);
                    solution = null;
                    stepIndex = 0;
                    return snapshot("Scrambled with " + moves.size() + " turns.");
                }
                case "reset" -> {
                    cube = new FaceletCube(size());
                    scramble = "";
                    solution = null;
                    stepIndex = 0;
                    return snapshot("Back to solved.");
                }
                case "move" -> {
                    String notation = Json.str(body, "moves", "").trim();
                    if (notation.isEmpty()) {
                        return Json.error("Type some turns first, for example R U R' U'.");
                    }
                    List<Move> moves = Move.parse(notation, size());
                    cube().apply(moves);
                    solution = null;
                    return snapshot("Applied " + Move.format(moves) + ".");
                }
                case "load" -> {
                    String facelets = Json.str(body, "facelets", "");
                    FaceletCube loaded = FaceletCube.fromFacelets(facelets, size());
                    CubeValidator.Result check = CubeValidator.check(loaded);
                    if (!check.valid()) {
                        return Json.error(check.summary());
                    }
                    cube = loaded;
                    scramble = "";
                    solution = null;
                    stepIndex = 0;
                    return snapshot("Loaded your cube. It is a valid state, so it can be solved.");
                }
                case "solve" -> {
                    if (!hasSolver()) {
                        return Json.error("The solver for " + meta().name()
                                + " is not written yet. You can still scramble and turn it.");
                    }
                    if (cube().isSolved()) {
                        return Json.error("It is already solved.");
                    }
                    String method = Json.str(body, "method",
                            methods().isEmpty() ? "" : methods().get(0));
                    solution = solvePuzzle(cube(), scramble, method);
                    stepIndex = 0;
                    return snapshot("Solved in " + solution.moveCount() + " moves across "
                            + solution.steps().size() + " stages.");
                }
                case "hint" -> {
                    if (!hasSolver()) {
                        return Json.error("No solver for this puzzle yet, so no hints either.");
                    }
                    if (cube().isSolved()) {
                        return Json.error("Nothing to hint at, it is already solved.");
                    }
                    Solution hint = solvePuzzle(cube(), scramble, methodOrDefault(body));
                    Solution.Step first = hint.steps().isEmpty() ? null : hint.steps().get(0);
                    if (first == null) {
                        return Json.error("No hint available.");
                    }
                    Map<String, Object> result = snapshot("");
                    result.put("hint", Json.map(
                            "stage", first.stage(),
                            "explanation", first.explanation(),
                            "moves", first.moves(),
                            "algorithm", first.algorithm()));
                    return result;
                }
                case "apply" -> {
                    // Play the moves of one step, so the net follows the solution.
                    String notation = Json.str(body, "moves", "");
                    if (!notation.isBlank()) {
                        cube().apply(Move.parse(notation, size()));
                    }
                    return snapshot("");
                }
                default -> {
                    return Json.error("Unknown action: " + action);
                }
            }
        } catch (UnsupportedOperationException e) {
            return Json.error(e.getMessage());
        } catch (RuntimeException e) {
            return Json.error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private String methodOrDefault(Map<String, Object> body) {
        return Json.str(body, "method", methods().isEmpty() ? "" : methods().get(0));
    }

    private Map<String, Object> snapshot(String message) {
        Map<String, Object> result = Json.ok(
                "size", size(),
                "faces", cube().faceGrids(),
                "solved", cube().isSolved(),
                "scramble", scramble,
                "facelets", cube().toFacelets(),
                "hasSolver", hasSolver(),
                "methods", methods(),
                "note", note(),
                "correct", cube().correctStickers(),
                "total", 6 * size() * size());
        if (!message.isEmpty()) {
            result.put("message", message);
        }
        if (solution != null) {
            List<Map<String, Object>> steps = new ArrayList<>();
            for (Solution.Step step : solution.steps()) {
                steps.add(Json.map(
                        "stage", step.stage(),
                        "explanation", step.explanation(),
                        "moves", step.moves(),
                        "count", step.moveCount(),
                        "algorithm", step.algorithm(),
                        "facelets", step.facelets()));
            }
            result.put("solution", Json.map(
                    "method", solution.method(),
                    "moves", solution.moves(),
                    "count", solution.moveCount(),
                    "steps", steps));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Terminal
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        cube = new FaceletCube(size());
        if (!note().isBlank()) {
            io.muted(note());
        }
        while (true) {
            io.println();
            for (String row : cube.net()) {
                io.println("  " + row);
            }
            io.muted(cube.isSolved() ? "Solved."
                    : cube.correctStickers() + " of " + (6 * size() * size())
                            + " stickers in place.");

            List<String> options = new ArrayList<>(List.of(
                    "Scramble", "Turn it by hand", "Reset to solved", "Type in my own cube"));
            if (hasSolver()) {
                options.add("Solve it and explain");
                options.add("Just one hint");
            }
            int choice = io.menu(meta().name(), options);
            if (choice < 0) {
                return;
            }
            switch (choice) {
                case 0 -> {
                    cube = new FaceletCube(size());
                    List<Move> moves = cube.scramble(random);
                    scramble = Move.format(moves);
                    io.ok("Scramble: " + scramble);
                }
                case 1 -> {
                    String notation = io.ask("turns (e.g. R U R' U'):");
                    if (!notation.isBlank()) {
                        try {
                            cube.apply(Move.parse(notation, size()));
                        } catch (RuntimeException e) {
                            io.error(e.getMessage());
                        }
                    }
                }
                case 2 -> {
                    cube = new FaceletCube(size());
                    scramble = "";
                }
                case 3 -> typeInCube(io);
                case 4 -> explainSolve(io);
                default -> showHint(io);
            }
        }
    }

    private void typeInCube(ConsoleUI io) {
        io.muted("Enter " + (6 * size() * size()) + " letters from U R F D L B, reading each");
        io.muted("face left to right and top to bottom, in the order U R F D L B.");
        String facelets = io.ask("stickers:");
        if (facelets.isBlank()) {
            return;
        }
        try {
            FaceletCube loaded = FaceletCube.fromFacelets(facelets, size());
            CubeValidator.Result check = CubeValidator.check(loaded);
            if (!check.valid()) {
                io.error(check.summary());
                return;
            }
            cube = loaded;
            scramble = "";
            io.ok("That is a valid cube, so it can be solved.");
        } catch (RuntimeException e) {
            io.error(e.getMessage());
        }
    }

    private void explainSolve(ConsoleUI io) {
        if (cube.isSolved()) {
            io.warn("It is already solved.");
            return;
        }
        try {
            Solution found = solvePuzzle(cube, scramble, methods().isEmpty() ? "" : methods().get(0));
            io.println();
            io.info(found.method() + ", " + found.moveCount() + " moves in "
                    + found.steps().size() + " stages");
            for (Solution.Step step : found.steps()) {
                io.println();
                io.println("  " + step.stage() + "  (" + step.moveCount() + " moves)");
                io.muted(step.explanation());
                if (!step.algorithm().isEmpty()) {
                    io.muted("Algorithm: " + step.algorithm());
                }
                io.println("    " + step.moves());
            }
            cube.apply(Move.parse(found.moves(), size()));
            io.println();
            io.ok(cube.isSolved() ? "Solved." : "Something went wrong, it is not solved.");
        } catch (RuntimeException e) {
            io.error(e.getMessage());
        }
    }

    private void showHint(ConsoleUI io) {
        if (cube.isSolved()) {
            io.warn("Already solved, so there is nothing to hint at.");
            return;
        }
        try {
            Solution found = solvePuzzle(cube, scramble, methods().isEmpty() ? "" : methods().get(0));
            if (found.steps().isEmpty()) {
                io.warn("No hint available.");
                return;
            }
            Solution.Step next = found.steps().get(0);
            io.println();
            io.info("Next: " + next.stage());
            io.muted(next.explanation());
            io.result("Try", next.moves());
            if (io.askYesNo("Play those moves for me?", true)) {
                cube.apply(Move.parse(next.moves(), size()));
            }
        } catch (RuntimeException e) {
            io.error(e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // The page
    // ------------------------------------------------------------------

    @Override
    public String uiFragment() {
        return CubeUi.page(this, size(), hasSolver(), methods(), note());
    }
}
