package com.randomjava.projects.communitygardenplanner;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Community Garden Planner - Lay out plots and plan what goes in each bed by season.
 *
 * <p>Scaffold. Both front ends already work and talk to this class; what is
 * missing is the real logic. Fill in the marked method and the terminal and
 * the browser both start behaving properly, with nothing written twice.
 */
public final class CommunityGardenPlanner implements Project {

    public static final Meta META = new Meta(
            120, "community-garden-planner", "Community Garden Planner", "Community and Social Good",
            Kind.GRID, Difficulty.INTERMEDIATE, "Lay out plots and plan what goes in each bed by season.",
            "", false);

    @Override
    public Meta meta() {
        return META;
    }

    private char[][] cells = new char[0][0];
    private int size = 12;
    private int steps;

    // ------------------------------------------------------------------
    // The methods to implement
    // ------------------------------------------------------------------

    /** Builds the starting board. */
    private void generate(int requestedSize) {
        // TODO: Lay out plots and plan what goes in each bed by season.
        size = Math.max(4, Math.min(40, requestedSize));
        steps = 0;
        cells = new char[size][size];
        Random random = new Random();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                boolean edge = row == 0 || column == 0 || row == size - 1 || column == size - 1;
                cells[row][column] = edge || random.nextInt(5) == 0 ? '#' : '.';
            }
        }
        cells[1][1] = '*';
    }

    /** Advances the board one tick. */
    private void step() {
        // TODO: advance the simulation by one step
        if (cells.length == 0) {
            generate(size);
            return;
        }
        steps++;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (cells[row][column] == '*') {
                    cells[row][column] = '.';
                    int nextColumn = column + 1 < size - 1 ? column + 1 : 1;
                    int nextRow = nextColumn == 1 ? (row + 1 < size - 1 ? row + 1 : 1) : row;
                    cells[nextRow][nextColumn] = '*';
                    return;
                }
            }
        }
    }

    /** Runs to completion, if that means anything for this project. */
    private String solve() {
        // TODO: solve or run to completion
        for (int i = 0; i < size * 2; i++) {
            step();
        }
        return "Ran " + (size * 2) + " steps. Implement solve() for the real thing.";
    }

    private String status() {
        return size + "x" + size + " board, " + steps + " steps taken";
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("board size:", 4, 40, 12));
        io.println();
        io.grid(cells);
        io.muted(status());
        while (true) {
            int choice = io.menu("Board", List.of("Step", "Run to completion", "Regenerate"));
            if (choice < 0) {
                return;
            }
            switch (choice) {
                case 0 -> step();
                case 1 -> io.info(solve());
                default -> generate(size);
            }
            io.println();
            io.grid(cells);
            io.muted(status());
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        switch (action) {
            case "generate" -> {
                generate(Json.integer(body, "size", 12));
                return Json.ok("board", Json.grid(cells), "detail", status());
            }
            case "step" -> {
                step();
                return Json.ok("board", Json.grid(cells), "detail", status());
            }
            case "solve" -> {
                String message = solve();
                return Json.ok("board", Json.grid(cells), "result", message, "detail", status());
            }
            default -> {
                return Json.error("Unknown action: " + action);
            }
        }
    }
}
