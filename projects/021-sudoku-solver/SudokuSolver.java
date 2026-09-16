package com.randomjava.projects.sudokusolver;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.List;
import java.util.Map;

/**
 * Sudoku Solver - backtracking search with constraint checking.
 *
 * <p>The search always fills the empty cell with the fewest remaining
 * candidates first. That one change turns a puzzle that would otherwise take
 * millions of recursive calls into one that usually takes a few thousand,
 * because it fails fast on the branches that cannot work.
 */
public final class SudokuSolver implements Project {

    public static final Meta META = new Meta(21, "sudoku-solver", "Sudoku Solver", "Algorithms and Data Structures", Kind.GRID,
            Difficulty.ADVANCED, "Solve a 9x9 Sudoku with constraint-checked backtracking.",
            "", true);

    /** A well known puzzle, used as the default so there is always something to solve. */
    public static final String SAMPLE =
            "53..7...." + "6..195..." + ".98....6."
            + "8...6...3" + "4..8.3..1" + "7...2...6"
            + ".6....28." + "...419..5" + "....8..79";

    /** A deliberately hard one, to show the candidate ordering earning its keep. */
    public static final String HARD =
            "8........" + "..36....." + ".7..9.2.."
            + ".5...7..." + "....457.." + "...1...3."
            + "..1....68" + "..85...1." + ".9....4..";

    private int[][] board = parse(SAMPLE);
    private final boolean[][] given = new boolean[9][9];
    private long calls;
    private boolean solved;

    public SudokuSolver() {
        markGivens();
    }

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // Parsing and formatting
    // ------------------------------------------------------------------

    /** Accepts 81 characters where any of {@code . 0 _} means empty. */
    public static int[][] parse(String text) {
        String cleaned = text == null ? "" : text.replaceAll("[^0-9._]", "");
        if (cleaned.length() != 81) {
            throw new IllegalArgumentException(
                    "Expected 81 cells, got " + cleaned.length() + ". Use . or 0 for blanks.");
        }
        int[][] grid = new int[9][9];
        for (int i = 0; i < 81; i++) {
            char c = cleaned.charAt(i);
            grid[i / 9][i % 9] = (c == '.' || c == '0' || c == '_') ? 0 : c - '0';
        }
        return grid;
    }

    private void markGivens() {
        for (int row = 0; row < 9; row++) {
            for (int column = 0; column < 9; column++) {
                given[row][column] = board[row][column] != 0;
            }
        }
    }

    /** The board as characters, with {@code .} for blanks, ready for rendering. */
    public char[][] render() {
        char[][] cells = new char[9][9];
        for (int row = 0; row < 9; row++) {
            for (int column = 0; column < 9; column++) {
                cells[row][column] = board[row][column] == 0
                        ? '.' : (char) ('0' + board[row][column]);
            }
        }
        return cells;
    }

    public void load(String puzzle) {
        board = parse(puzzle);
        markGivens();
        calls = 0;
        solved = false;
        String problem = firstConflict();
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    // ------------------------------------------------------------------
    // Validity
    // ------------------------------------------------------------------

    private boolean canPlace(int row, int column, int value) {
        for (int i = 0; i < 9; i++) {
            if (board[row][i] == value || board[i][column] == value) {
                return false;
            }
        }
        int boxRow = row - row % 3;
        int boxColumn = column - column % 3;
        for (int r = boxRow; r < boxRow + 3; r++) {
            for (int c = boxColumn; c < boxColumn + 3; c++) {
                if (board[r][c] == value) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Returns a description of the first rule the starting grid breaks, or null. */
    private String firstConflict() {
        for (int row = 0; row < 9; row++) {
            for (int column = 0; column < 9; column++) {
                int value = board[row][column];
                if (value == 0) {
                    continue;
                }
                board[row][column] = 0;
                boolean legal = canPlace(row, column, value);
                board[row][column] = value;
                if (!legal) {
                    return "The starting grid is already invalid: " + value
                            + " at row " + (row + 1) + ", column " + (column + 1)
                            + " clashes with another cell.";
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Solving
    // ------------------------------------------------------------------

    public String solve() {
        calls = 0;
        String problem = firstConflict();
        if (problem != null) {
            solved = false;
            return problem;
        }
        long started = System.nanoTime();
        solved = search();
        double millis = (System.nanoTime() - started) / 1_000_000.0;
        if (!solved) {
            return "No solution exists for this grid.";
        }
        return String.format("Solved in %.1f ms after %,d recursive calls.", millis, calls);
    }

    private boolean search() {
        calls++;
        int bestRow = -1;
        int bestColumn = -1;
        int fewest = 10;

        // Pick the empty cell with the fewest legal candidates.
        for (int row = 0; row < 9; row++) {
            for (int column = 0; column < 9; column++) {
                if (board[row][column] != 0) {
                    continue;
                }
                int candidates = 0;
                for (int value = 1; value <= 9; value++) {
                    if (canPlace(row, column, value)) {
                        candidates++;
                    }
                }
                if (candidates == 0) {
                    return false;
                }
                if (candidates < fewest) {
                    fewest = candidates;
                    bestRow = row;
                    bestColumn = column;
                }
            }
        }

        if (bestRow == -1) {
            return true;
        }

        for (int value = 1; value <= 9; value++) {
            if (!canPlace(bestRow, bestColumn, value)) {
                continue;
            }
            board[bestRow][bestColumn] = value;
            if (search()) {
                return true;
            }
            board[bestRow][bestColumn] = 0;
        }
        return false;
    }

    public int blanks() {
        int count = 0;
        for (int[] row : board) {
            for (int value : row) {
                if (value == 0) {
                    count++;
                }
            }
        }
        return count;
    }

    public String status() {
        return solved ? "Solved. " + calls + " recursive calls."
                : blanks() + " cells still empty.";
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Paste 81 characters, or press Enter for the built-in puzzle.");
        String input = io.ask("puzzle:");
        try {
            load(input.isEmpty() ? SAMPLE : input);
        } catch (IllegalArgumentException e) {
            io.error(e.getMessage());
            return;
        }
        io.println();
        printBoard(io);
        while (true) {
            int choice = io.menu("Sudoku", List.of("Solve", "Load the sample", "Load a hard one"));
            if (choice < 0) {
                return;
            }
            switch (choice) {
                case 0 -> io.info(solve());
                case 1 -> load(SAMPLE);
                default -> load(HARD);
            }
            io.println();
            printBoard(io);
        }
    }

    /** Prints with box separators, which makes a 9x9 grid far easier to read. */
    private void printBoard(ConsoleUI io) {
        char[][] cells = render();
        for (int row = 0; row < 9; row++) {
            if (row % 3 == 0) {
                io.println("  +-------+-------+-------+");
            }
            StringBuilder line = new StringBuilder("  ");
            for (int column = 0; column < 9; column++) {
                if (column % 3 == 0) {
                    line.append("| ");
                }
                line.append(cells[row][column]).append(' ');
            }
            io.println(line.append('|').toString());
        }
        io.println("  +-------+-------+-------+");
        io.muted(status());
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate", "load" -> {
                    String puzzle = Json.str(body, "puzzle", "").trim();
                    load(puzzle.isEmpty() ? SAMPLE : puzzle);
                    return Json.ok("board", Json.grid(render()), "detail", status());
                }
                case "sample" -> {
                    load(SAMPLE);
                    return Json.ok("board", Json.grid(render()), "detail", status());
                }
                case "hard" -> {
                    load(HARD);
                    return Json.ok("board", Json.grid(render()), "detail", status());
                }
                case "solve" -> {
                    String message = solve();
                    return Json.ok("board", Json.grid(render()), "result", message, "detail", status());
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
