package com.randomjava.projects.knightstour;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Knight's Tour - move a knight so it lands on every square exactly once.
 *
 * <h2>Why a greedy rule beats a search</h2>
 *
 * <p>Brute force is hopeless: the knight has up to eight moves from each of 64
 * squares, and blind backtracking on an 8x8 board explores an astronomical
 * number of dead ends. Warnsdorff's rule from 1823 solves it greedily -
 * <b>always move to the square with the fewest onward moves</b> - and on
 * ordinary boards it walks straight to a tour with no backtracking at all.
 *
 * <p>The reasoning is that a square with few exits is the one most likely to
 * become stranded, so visiting it early, while it is still reachable, avoids
 * the mistake that actually kills a tour: leaving an isolated square behind.
 * Corners and edges get used up first, and the knight works inward.
 *
 * <p>It is a heuristic, not a theorem - ties can be broken badly and it does
 * fail on some boards and some starts. So {@link #tour} keeps backtracking
 * behind it, with the moves at each step ordered by the same rule. The greedy
 * order is what makes that search finish quickly; the backtracking is what
 * makes it correct.
 *
 * <h2>Where the knight has to start, and why</h2>
 *
 * <p>A knight always moves from a light square to a dark one and back, so a
 * tour alternates colours. On a board with an odd number of squares - any odd
 * {@code n} - the colours cannot be balanced: there are
 * {@code (n*n+1)/2} of one and {@code (n*n-1)/2} of the other. An alternating
 * walk over all of them must therefore begin <em>and</em> end on the majority
 * colour, which on an odd board is the colour of the corners.
 *
 * <p>So on a 5x5, a tour starting on a dark square does not exist - not because
 * it is hard, but because counting forbids it. {@link #startCanWork} is that
 * argument, and it rejects half the starting squares of every odd board before
 * any search begins. {@link #tourExists} adds the other known impossibilities:
 * boards of side 1 aside, there is no tour at all for {@code n} of 2, 3 or 4.
 */
public final class KnightsTour implements Project {

    public static final Meta META = new Meta(
            248, "knights-tour", "Knights Tour", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Move a knight to every square on the board exactly once.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    private static final int MIN_SIZE = 5;
    private static final int MAX_SIZE = 8;
    private static final int[][] JUMPS = {
        {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};

    private int size = 6;
    private int startRow;
    private int startColumn;
    /** Move number per square, or 0 where the knight has not been. */
    private int[][] visitOrder = new int[0][0];

    public int size() {
        return size;
    }

    public int orderAt(int row, int column) {
        return visitOrder[row][column];
    }

    // ------------------------------------------------------------------
    // What is possible before any searching
    // ------------------------------------------------------------------

    /**
     * Whether a tour exists on this board at all. Sides of 2, 3 and 4 have
     * none - the 3x3 cannot reach its own centre, and the others strand
     * squares the same way.
     */
    public static boolean tourExists(int size) {
        return size == 1 || size >= 5;
    }

    /**
     * Whether a tour can start here. On an odd board the colours are unequal,
     * and an alternating walk over every square must start on the majority
     * colour - the colour of the corners.
     */
    public static boolean startCanWork(int size, int row, int column) {
        if (!tourExists(size)) {
            return false;
        }
        if (size % 2 == 0) {
            return true;   // equal numbers of each colour, so either will do
        }
        return (row + column) % 2 == 0;   // same colour as (0,0), the majority
    }

    private static int onwardMoves(int[][] board, int size, int row, int column) {
        int count = 0;
        for (int[] jump : JUMPS) {
            int nextRow = row + jump[0];
            int nextColumn = column + jump[1];
            if (nextRow >= 0 && nextRow < size && nextColumn >= 0 && nextColumn < size
                    && board[nextRow][nextColumn] == 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * A tour from this square, or null if there is none.
     *
     * <p>Moves are tried in Warnsdorff order - fewest onward moves first -
     * which usually walks straight to an answer. The backtracking behind it is
     * what makes the result trustworthy when the rule mis-steps.
     */
    public static int[][] tour(int size, int row, int column) {
        if (!startCanWork(size, row, column)) {
            return null;
        }
        int[][] board = new int[size][size];
        board[row][column] = 1;
        return walk(board, size, row, column, 1) ? board : null;
    }

    private static boolean walk(int[][] board, int size, int row, int column, int step) {
        if (step == size * size) {
            return true;
        }
        List<int[]> options = new ArrayList<>();
        for (int[] jump : JUMPS) {
            int nextRow = row + jump[0];
            int nextColumn = column + jump[1];
            if (nextRow < 0 || nextRow >= size || nextColumn < 0 || nextColumn >= size
                    || board[nextRow][nextColumn] != 0) {
                continue;
            }
            options.add(new int[] {
                onwardMoves(board, size, nextRow, nextColumn), nextRow, nextColumn});
        }
        options.sort((a, b) -> Integer.compare(a[0], b[0]));
        for (int[] option : options) {
            board[option[1]][option[2]] = step + 1;
            if (walk(board, size, option[1], option[2], step + 1)) {
                return true;
            }
            board[option[1]][option[2]] = 0;
        }
        return false;
    }

    /** Whether a filled board really is a legal tour: every square, once, by knight moves. */
    public static boolean isTour(int[][] board) {
        int size = board.length;
        int[] rowOf = new int[size * size + 1];
        int[] columnOf = new int[size * size + 1];
        Arrays.fill(rowOf, -1);
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int step = board[row][column];
                if (step < 1 || step > size * size || rowOf[step] >= 0) {
                    return false;   // missing, out of range, or used twice
                }
                rowOf[step] = row;
                columnOf[step] = column;
            }
        }
        for (int step = 1; step < size * size; step++) {
            int dRow = Math.abs(rowOf[step] - rowOf[step + 1]);
            int dColumn = Math.abs(columnOf[step] - columnOf[step + 1]);
            if (dRow * dColumn != 2) {
                return false;   // not a knight move
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Board state
    // ------------------------------------------------------------------

    public void reset(int requested, int row, int column) {
        if (requested < MIN_SIZE || requested > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Use a board between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        if (row < 0 || row >= requested || column < 0 || column >= requested) {
            throw new IllegalArgumentException("That square is not on the board.");
        }
        size = requested;
        startRow = row;
        startColumn = column;
        visitOrder = new int[size][size];
        visitOrder[row][column] = 1;
    }

    public String solve() {
        int[][] found = tour(size, startRow, startColumn);
        if (found == null) {
            return size % 2 == 1
                    ? "No tour starts there. On an odd board the knight must begin on a "
                      + "corner-coloured square, because the colours are unequal."
                    : "No tour starts there.";
        }
        visitOrder = found;
        return "Toured all " + size * size + " squares from " + startRow + ","
                + startColumn + ".";
    }

    public char[][] cells() {
        char[][] grid = new char[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int step = visitOrder[row][column];
                grid[row][column] = step == 0 ? '.'
                        : step < 10 ? (char) ('0' + step)
                        : (char) ('a' + (step - 10) % 26);
            }
        }
        return grid;
    }

    private String detail() {
        int visited = 0;
        for (int[] line : visitOrder) {
            for (int step : line) {
                if (step > 0) {
                    visited++;
                }
            }
        }
        String colour = size % 2 == 1
                ? " On an odd board only the corner colour can start a tour, so half the "
                  + "squares are ruled out before searching."
                : "";
        return String.format("%dx%d, started at %d,%d, %d of %d squares visited.%s",
                size, size, startRow, startColumn, visited, size * size, colour);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Squares are numbered in visiting order: 1-9 then a, b, c and on.");
        reset(6, 0, 0);
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            int choice = io.menu("Knight's Tour",
                    List.of("Choose a board and start", "Solve"));
            if (choice < 0) {
                return;
            }
            try {
                if (choice == 0) {
                    int side = io.askInt("board size:", MIN_SIZE, MAX_SIZE, size);
                    int row = io.askInt("start row:", 0, side - 1, 0);
                    reset(side, row, io.askInt("start column:", 0, side - 1, 0));
                } else {
                    io.info(solve());
                }
            } catch (RuntimeException e) {
                io.error(e.getMessage());
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    int side = Json.integer(body, "size", 6);
                    reset(side, Json.integer(body, "row", 0), Json.integer(body, "col", 0));
                    return board(startCanWork(side, startRow, startColumn)
                            ? "Click a square to start the knight there, then Solve."
                            : "No tour can start there on an odd board - wrong colour.");
                }
                case "cycle", "step", "place" -> {
                    int row = Json.integer(body, "row", -1);
                    int column = Json.integer(body, "col", -1);
                    if (row < 0 || column < 0 || row >= size || column >= size) {
                        return board("");
                    }
                    reset(size, row, column);
                    return board(startCanWork(size, row, column)
                            ? "" : "No tour can start on that colour.");
                }
                case "solve" -> {
                    return board(solve());
                }
                default -> {
                    return Json.error("Unknown action: " + action);
                }
            }
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }

    private Map<String, Object> board(String note) {
        Map<String, Object> out = Json.ok("board", Json.grid(cells()), "detail", detail());
        if (note != null && !note.isEmpty()) {
            out.put("result", note);
        }
        return out;
    }
}
