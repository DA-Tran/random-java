package com.randomjava.projects.lightsout;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Lights Out - press cells to toggle them and their neighbours, until the board
 * is dark.
 *
 * <p>It looks like a search problem and is not one. Two observations collapse
 * it into linear algebra:
 *
 * <ul>
 *   <li><b>Pressing a cell twice is the same as not pressing it.</b> Toggles
 *       cancel, so every button is pressed either once or not at all.</li>
 *   <li><b>Order does not matter.</b> Toggling is commutative, so a solution is
 *       a <em>set</em> of buttons, not a sequence.</li>
 * </ul>
 *
 * <p>So a solution is a vector over GF(2) - the field with two elements, where
 * addition is XOR - and the puzzle is the system {@code Ax = b}: A is which
 * cells each button toggles, b is the lights currently on, x is the set to
 * press. Gaussian elimination solves it in O(n^3) on n = rows x columns cells,
 * instantly, and for boards where breadth-first search over 2^25 states is
 * hopeless.
 *
 * <p><b>Not every board is solvable, and that falls out of the same maths.</b>
 * When A is singular its null space is non-trivial: there are non-empty sets of
 * buttons whose combined effect is nothing at all - "quiet patterns". Their
 * existence means A does not reach every configuration, so some boards cannot
 * be turned off. On the classic 5x5 the null space has dimension 2, so only one
 * configuration in four is solvable. A solver that always returns an answer is
 * wrong on three boards out of four.
 *
 * <p>Randomising by pressing buttons therefore guarantees solvability, exactly
 * as shuffling a sliding puzzle by legal moves does.
 */
public final class LightsOut implements Project {

    public static final Meta META = new Meta(229, "lights-out", "Lights Out", "Logic and Puzzle Games", Kind.GRID,
            Difficulty.INTERMEDIATE, "Turn every light off, where pressing one flips it and its neighbours.",
            "", true);

    private int rows;
    private int columns;
    private boolean[] lights;
    private int presses;
    private final Random random;

    public LightsOut() { this(5, 5, new Random()); }

    public LightsOut(int rows, int columns, Random random) {
        this.random = random;
        reset(rows, columns);
    }

    @Override public Meta meta() { return META; }

    public void reset(int newRows, int newColumns) {
        if (newRows < 2 || newRows > 10 || newColumns < 2 || newColumns > 10) {
            throw new IllegalArgumentException("Use 2 to 10 rows and columns.");
        }
        rows = newRows;
        columns = newColumns;
        lights = new boolean[rows * columns];
        presses = 0;
    }

    public int rows() { return rows; }
    public int columns() { return columns; }
    public int presses() { return presses; }
    public boolean[] lights() { return lights.clone(); }
    public boolean dark() {
        for (boolean on : lights) { if (on) { return false; } }
        return true;
    }
    public int lit() {
        int count = 0;
        for (boolean on : lights) { if (on) { count++; } }
        return count;
    }

    public void setLights(boolean[] state) {
        if (state.length != rows * columns) {
            throw new IllegalArgumentException("That is not a " + rows + "x" + columns + " board.");
        }
        lights = state.clone();
        presses = 0;
    }

    /** Which cells a press at {@code index} toggles: itself and its edge neighbours. */
    public List<Integer> affects(int index) {
        int row = index / columns;
        int col = index % columns;
        List<Integer> out = new ArrayList<>();
        out.add(index);
        if (row > 0) { out.add(index - columns); }
        if (row < rows - 1) { out.add(index + columns); }
        if (col > 0) { out.add(index - 1); }
        if (col < columns - 1) { out.add(index + 1); }
        return out;
    }

    public void press(int index) {
        if (index < 0 || index >= lights.length) {
            throw new IllegalArgumentException("There is no cell " + index + ".");
        }
        for (int cell : affects(index)) { lights[cell] = !lights[cell]; }
        presses++;
    }

    /** Randomises by pressing buttons, so the result is always solvable. */
    public void scramble(int steps) {
        for (int i = 0; i < steps; i++) { press(random.nextInt(lights.length)); }
        presses = 0;
    }

    // ------------------------------------------------------------------
    // Solving over GF(2)
    // ------------------------------------------------------------------

    /**
     * Solves {@code Ax = b} by Gaussian elimination over GF(2), returning the
     * set of buttons to press, or null when the board cannot be turned off.
     *
     * <p>Rows are bitsets held in longs, so an elimination step is one XOR
     * rather than a loop over columns. That caps the board at 63 cells, which
     * comfortably covers anything playable.
     */
    public int[] solve() {
        int n = rows * columns;
        if (n > 62) { throw new IllegalStateException("Board too large for the bitset solver."); }

        // Augmented matrix: bit i is the coefficient, bit n is the target.
        long[] matrix = new long[n];
        for (int cell = 0; cell < n; cell++) {
            for (int button : affects(cell)) { matrix[cell] |= 1L << button; }
            if (lights[cell]) { matrix[cell] |= 1L << n; }
        }

        int[] pivotOf = new int[n];
        Arrays.fill(pivotOf, -1);
        int row = 0;
        for (int col = 0; col < n && row < n; col++) {
            int pivot = -1;
            for (int r = row; r < n; r++) {
                if ((matrix[r] >> col & 1) == 1) { pivot = r; break; }
            }
            if (pivot < 0) { continue; }   // free column: a quiet pattern lives here
            long swap = matrix[row];
            matrix[row] = matrix[pivot];
            matrix[pivot] = swap;
            for (int r = 0; r < n; r++) {
                if (r != row && (matrix[r] >> col & 1) == 1) { matrix[r] ^= matrix[row]; }
            }
            pivotOf[col] = row;
            row++;
        }

        // A row of all-zero coefficients with a 1 on the right is 0 = 1:
        // the board is unreachable, which is what a quiet pattern costs.
        for (int r = row; r < n; r++) {
            if ((matrix[r] & ((1L << n) - 1)) == 0 && (matrix[r] >> n & 1) == 1) {
                return null;
            }
        }

        int[] solution = new int[n];
        for (int col = 0; col < n; col++) {
            if (pivotOf[col] >= 0) {
                solution[col] = (int) (matrix[pivotOf[col]] >> n & 1);
            }
        }
        return solution;
    }

    public boolean solvable() { return solve() != null; }

    /** The dimension of the null space: how many quiet patterns the board has. */
    public int quietPatterns() {
        int n = rows * columns;
        long[] matrix = new long[n];
        for (int cell = 0; cell < n; cell++) {
            for (int button : affects(cell)) { matrix[cell] |= 1L << button; }
        }
        int rank = 0;
        for (int col = 0; col < n && rank < n; col++) {
            int pivot = -1;
            for (int r = rank; r < n; r++) {
                if ((matrix[r] >> col & 1) == 1) { pivot = r; break; }
            }
            if (pivot < 0) { continue; }
            long swap = matrix[rank];
            matrix[rank] = matrix[pivot];
            matrix[pivot] = swap;
            for (int r = 0; r < n; r++) {
                if (r != rank && (matrix[r] >> col & 1) == 1) { matrix[r] ^= matrix[rank]; }
            }
            rank++;
        }
        return n - rank;
    }

    /** Applies a solution. Returns how many presses it took. */
    public int applySolution() {
        int[] solution = solve();
        if (solution == null) {
            throw new IllegalStateException(
                    "This board cannot be turned off. The toggle matrix is singular, so it does "
                    + "not reach every configuration - and this one is outside its range.");
        }
        int count = 0;
        for (int i = 0; i < solution.length; i++) {
            if (solution[i] == 1) { press(i); count++; }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public char[][] cells() {
        char[][] grid = new char[rows][columns];
        for (int i = 0; i < lights.length; i++) {
            grid[i / columns][i % columns] = lights[i] ? '#' : '.';
        }
        return grid;
    }

    private String detail() {
        if (dark()) { return "All off, in " + presses + " presses."; }
        int[] solution = solve();
        String note = solution == null
                ? "This board cannot be turned off at all."
                : "Solvable in " + Arrays.stream(solution).sum() + " presses.";
        return String.format("%d lights on, %d presses so far. %s", lit(), presses, note);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        scramble(rows * columns);
        io.muted("Pressing a cell toggles it and its four neighbours.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            if (dark()) { return; }
            try {
                int row = io.askInt("row:", 0, rows - 1, 0);
                int column = io.askInt("column:", 0, columns - 1, 0);
                press(row * columns + column);
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    int side = Math.min(Math.max(Json.integer(body, "size", 5), 2), 7);
                    reset(side, side);
                    scramble(side * side);
                    return board("Scrambled by pressing buttons, so it is always solvable. "
                            + "This board has " + quietPatterns() + " quiet patterns.");
                }
                case "step", "press" -> {
                    press(Json.integer(body, "row", 0) * columns + Json.integer(body, "col", 0));
                    return board("");
                }
                case "solve" -> {
                    int count = applySolution();
                    return board("Solved in " + count + " presses, by Gaussian elimination over "
                            + "GF(2) rather than by searching.");
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }

    private Map<String, Object> board(String note) {
        return Json.ok("board", Json.grid(cells()),
                "detail", note.isEmpty() ? detail() : note + "\n  " + detail());
    }
}
