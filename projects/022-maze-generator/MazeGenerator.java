package com.randomjava.projects.mazegenerator;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Maze Generator - carves a perfect maze with randomised depth-first search and
 * then solves it with breadth-first search.
 *
 * <p>"Perfect" means exactly one path between any two cells: no loops, no
 * unreachable pockets. The carve works on odd coordinates only, so walls always
 * sit between cells and the border stays intact.
 *
 * <p>BFS is the right solver here because every step costs the same, so the
 * first time it reaches the exit it has already found the shortest route.
 */
public final class MazeGenerator implements Project {

    public static final Meta META = new Meta(22, "maze-generator", "Maze Generator", "Algorithms and Data Structures", Kind.GRID,
            Difficulty.INTERMEDIATE, "Carve a perfect maze with randomised depth-first search, then solve it.",
            "", true);

    private static final char WALL = '#';
    private static final char OPEN = '.';
    private static final char PATH = '*';
    private static final char START = 'S';
    private static final char EXIT = 'E';

    private char[][] cells = new char[0][0];
    private int cellsAcross = 10;
    private boolean solved;
    private int pathLength;

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // Generating
    // ------------------------------------------------------------------

    /**
     * @param requested how many cells across; the rendered grid is twice this
     *                  plus one so there is a wall between every pair of cells
     */
    public void generate(int requested) {
        cellsAcross = Math.max(3, Math.min(30, requested));
        int span = cellsAcross * 2 + 1;
        cells = new char[span][span];
        for (char[] row : cells) {
            java.util.Arrays.fill(row, WALL);
        }

        Random random = new Random();
        boolean[][] visited = new boolean[cellsAcross][cellsAcross];
        Deque<int[]> stack = new ArrayDeque<>();

        int startRow = 0;
        int startColumn = 0;
        visited[startRow][startColumn] = true;
        cells[startRow * 2 + 1][startColumn * 2 + 1] = OPEN;
        stack.push(new int[]{startRow, startColumn});

        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        while (!stack.isEmpty()) {
            int[] current = stack.peek();
            List<int[]> options = new ArrayList<>();
            for (int[] direction : directions) {
                int nextRow = current[0] + direction[0];
                int nextColumn = current[1] + direction[1];
                if (nextRow >= 0 && nextRow < cellsAcross
                        && nextColumn >= 0 && nextColumn < cellsAcross
                        && !visited[nextRow][nextColumn]) {
                    options.add(new int[]{nextRow, nextColumn});
                }
            }
            if (options.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] chosen = options.get(random.nextInt(options.size()));
            visited[chosen[0]][chosen[1]] = true;
            // Knock out the wall sitting between the two cells.
            int wallRow = current[0] + chosen[0] + 1;
            int wallColumn = current[1] + chosen[1] + 1;
            cells[wallRow][wallColumn] = OPEN;
            cells[chosen[0] * 2 + 1][chosen[1] * 2 + 1] = OPEN;
            stack.push(chosen);
        }

        cells[1][1] = START;
        cells[span - 2][span - 2] = EXIT;
        solved = false;
        pathLength = 0;
    }

    // ------------------------------------------------------------------
    // Solving
    // ------------------------------------------------------------------

    /** Breadth-first search from S to E, marking the shortest route. */
    public String solve() {
        if (cells.length == 0) {
            generate(cellsAcross);
        }
        clearPath();
        int span = cells.length;
        int[][] cameFrom = new int[span * span][];
        boolean[][] seen = new boolean[span][span];
        Deque<int[]> queue = new ArrayDeque<>();

        queue.add(new int[]{1, 1});
        seen[1][1] = true;
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        boolean found = false;

        while (!queue.isEmpty() && !found) {
            int[] current = queue.poll();
            for (int[] direction : directions) {
                int row = current[0] + direction[0];
                int column = current[1] + direction[1];
                if (row < 0 || column < 0 || row >= span || column >= span) {
                    continue;
                }
                if (seen[row][column] || cells[row][column] == WALL) {
                    continue;
                }
                seen[row][column] = true;
                cameFrom[row * span + column] = current;
                if (cells[row][column] == EXIT) {
                    found = true;
                    break;
                }
                queue.add(new int[]{row, column});
            }
        }

        if (!found) {
            solved = false;
            return "No route found, which should be impossible for a perfect maze.";
        }

        List<int[]> route = new ArrayList<>();
        int[] step = cameFrom[(span - 2) * span + (span - 2)];
        while (step != null && !(step[0] == 1 && step[1] == 1)) {
            route.add(step);
            step = cameFrom[step[0] * span + step[1]];
        }
        Collections.reverse(route);
        for (int[] cell : route) {
            cells[cell[0]][cell[1]] = PATH;
        }
        solved = true;
        pathLength = route.size() + 1;
        return "Shortest route is " + pathLength + " steps.";
    }

    private void clearPath() {
        for (char[] row : cells) {
            for (int i = 0; i < row.length; i++) {
                if (row[i] == PATH) {
                    row[i] = OPEN;
                }
            }
        }
        solved = false;
        pathLength = 0;
    }

    public String status() {
        if (cells.length == 0) {
            return "No maze yet.";
        }
        return cellsAcross + "x" + cellsAcross + " cells, rendered at "
                + cells.length + "x" + cells.length
                + (solved ? ", solved in " + pathLength + " steps" : ", unsolved");
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("cells across:", 3, 30, 10));
        io.println();
        io.grid(cells);
        io.muted(status());
        while (true) {
            int choice = io.menu("Maze", List.of("Solve it", "New maze", "Clear the route"));
            if (choice < 0) {
                return;
            }
            switch (choice) {
                case 0 -> io.info(solve());
                case 1 -> generate(cellsAcross);
                default -> clearPath();
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
                generate(Json.integer(body, "size", 10));
                return Json.ok("board", Json.grid(cells), "detail", status());
            }
            case "solve" -> {
                String message = solve();
                return Json.ok("board", Json.grid(cells), "result", message, "detail", status());
            }
            case "step", "clear" -> {
                clearPath();
                return Json.ok("board", Json.grid(cells), "detail", status());
            }
            default -> {
                return Json.error("Unknown action: " + action);
            }
        }
    }
}
