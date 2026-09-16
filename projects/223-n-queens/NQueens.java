package com.randomjava.projects.nqueens;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Place N queens on an NxN board so that none attacks another.
 *
 * <p>The search is backtracking, one queen per row, which is the point of the
 * puzzle: rather than trying all N^N arrangements, it places a queen, checks
 * only against the queens already placed, and abandons a branch the moment it
 * becomes impossible. For N=8 that is a few thousand steps instead of sixteen
 * million.
 *
 * <p>The three attack checks are kept as boolean arrays rather than loops: a
 * column, and the two diagonals, which are constant for a given row minus
 * column and row plus column. That makes each placement test O(1).
 */
public final class NQueens implements Project {

    public static final Meta META = new Meta(223, "n-queens", "N Queens", "Logic and Puzzle Games", Kind.GRID,
            Difficulty.INTERMEDIATE, "Place N queens on a board so that no two of them attack each other.",
            "", true);

    /** Counting every solution past this takes a noticeable while. */
    private static final int MAX_COUNT_SIZE = 13;
    private static final int MAX_SIZE = 24;

    private int size = 8;
    private int[] solution = new int[0];
    private long steps;

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // Core
    // ------------------------------------------------------------------

    /** Finds one arrangement, or null when none exists. Rows 2 and 3 have none. */
    public int[] solve(int boardSize) {
        size = Math.max(1, Math.min(MAX_SIZE, boardSize));
        steps = 0;
        int[] queens = new int[size];
        boolean[] column = new boolean[size];
        boolean[] rising = new boolean[2 * size];
        boolean[] falling = new boolean[2 * size];
        solution = place(0, queens, column, rising, falling) ? queens.clone() : null;
        return solution;
    }

    private boolean place(int row, int[] queens, boolean[] column,
            boolean[] rising, boolean[] falling) {
        if (row == size) {
            return true;
        }
        for (int col = 0; col < size; col++) {
            steps++;
            if (column[col] || rising[row + col] || falling[row - col + size]) {
                continue;
            }
            queens[row] = col;
            column[col] = rising[row + col] = falling[row - col + size] = true;
            if (place(row + 1, queens, column, rising, falling)) {
                return true;
            }
            column[col] = rising[row + col] = falling[row - col + size] = false;
        }
        return false;
    }

    /** Counts every distinct arrangement, which grows very fast with N. */
    public long countAll(int boardSize) {
        int n = Math.max(1, Math.min(MAX_COUNT_SIZE, boardSize));
        return count(0, n, new boolean[n], new boolean[2 * n], new boolean[2 * n]);
    }

    private long count(int row, int n, boolean[] column, boolean[] rising, boolean[] falling) {
        if (row == n) {
            return 1;
        }
        long total = 0;
        for (int col = 0; col < n; col++) {
            if (column[col] || rising[row + col] || falling[row - col + n]) {
                continue;
            }
            column[col] = rising[row + col] = falling[row - col + n] = true;
            total += count(row + 1, n, column, rising, falling);
            column[col] = rising[row + col] = falling[row - col + n] = false;
        }
        return total;
    }

    /** True when no two queens in the arrangement attack each other. */
    public static boolean isValid(int[] queens) {
        if (queens == null) {
            return false;
        }
        for (int a = 0; a < queens.length; a++) {
            for (int b = a + 1; b < queens.length; b++) {
                if (queens[a] == queens[b] || Math.abs(queens[a] - queens[b]) == b - a) {
                    return false;
                }
            }
        }
        return true;
    }

    public char[][] render() {
        char[][] grid = new char[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                grid[row][col] = solution != null && solution[row] == col ? 'Q' : '.';
            }
        }
        return grid;
    }

    private String detail() {
        if (solution == null) {
            return "No arrangement exists for " + size + " queens. Boards of 2 and 3 are the "
                    + "only sizes with no solution at all.";
        }
        return size + " queens placed in " + steps + " search steps.\n"
                + "Trying every arrangement blindly would be " + size + "^" + size + " boards; "
                + "abandoning a branch as soon as it fails is what makes this quick.";
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        while (true) {
            int n = io.askInt("board size:", 1, MAX_SIZE, 8);
            solve(n);
            io.println();
            if (solution == null) {
                io.warn("No arrangement exists for " + n + " queens.");
            } else {
                io.grid(render());
                io.muted(detail());
            }
            if (n <= MAX_COUNT_SIZE && io.askYesNo("Count every solution too?", false)) {
                io.result("Distinct arrangements for " + n, String.valueOf(countAll(n)));
            }
            if (!io.askYesNo("Try another size?", true)) {
                return;
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate", "solve" -> {
                    solve(Json.integer(body, "size", 8));
                    return Json.ok("board", Json.grid(render()),
                            "result", solution == null ? "No solution exists"
                                    : size + " queens placed",
                            "detail", detail());
                }
                case "count" -> {
                    int n = Json.integer(body, "size", 8);
                    if (n > MAX_COUNT_SIZE) {
                        return Json.error("Counting every solution is only offered up to "
                                + MAX_COUNT_SIZE + ", since the total grows explosively.");
                    }
                    long total = countAll(n);
                    return Json.ok("result", total + " distinct arrangements for " + n + " queens",
                            "detail", "For 8 queens the answer is 92, a number worth "
                                    + "checking any implementation against.");
                }
                case "step" -> {
                    return Json.ok("board", Json.grid(render()), "detail", detail());
                }
                default -> {
                    return Json.error("Unknown action: " + action);
                }
            }
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }

    /** The arrangement as column indices per row, for tests. */
    public List<Integer> queens() {
        List<Integer> out = new ArrayList<>();
        if (solution != null) {
            for (int col : solution) {
                out.add(col);
            }
        }
        return out;
    }
}
