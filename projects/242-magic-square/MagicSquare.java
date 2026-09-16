package com.randomjava.projects.magicsquare;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Magic Square - arrange {@code 1} to {@code n*n} so every row, every column
 * and both diagonals add to the same total.
 *
 * <h2>The total is not a choice</h2>
 *
 * <p>People often ask what the rows should add up to, as though it were a
 * parameter. It is forced. The numbers {@code 1..n*n} sum to
 * {@code n*n(n*n+1)/2}, and the rows partition all of them into {@code n}
 * equal shares, so each row sums to {@code n(n*n+1)/2} and nothing else is
 * possible. {@link #magicConstant} is that one line, and it rules out most
 * wrong answers before any arranging starts: 15 for a 3x3, 34 for a 4x4, 65
 * for a 5x5.
 *
 * <p>The same argument kills {@code n = 2} outright. Its constant would be 5,
 * so the cell holding 4 needs a partner summing to 5 in its row, its column
 * and its diagonal - three different cells, but a 2x2 gives it only two
 * neighbours and one diagonal partner, and 1 is the only number that works for
 * any of them. No 2x2 magic square exists, and no amount of searching for one
 * is time well spent.
 *
 * <h2>There is no single construction</h2>
 *
 * <p>The interesting part is that building one is solved, but solved three
 * separate times. Which method applies depends on {@code n mod 4}, and the
 * three have almost nothing to do with each other:
 *
 * <ul>
 *   <li><b>Odd</b> - the Siamese method. Put 1 in the middle of the top row
 *       and keep stepping up and to the right, wrapping around the edges;
 *       when that cell is taken, drop one square instead. That is the whole
 *       algorithm, and it produces a magic square for every odd n.</li>
 *   <li><b>Doubly even</b> ({@code n} divisible by 4) - fill the grid
 *       {@code 1, 2, 3, ...} in reading order, then replace {@code v} with
 *       {@code n*n+1-v} on a fixed lattice of cells. Nothing is searched; the
 *       pattern does all the work.</li>
 *   <li><b>Singly even</b> ({@code n} even but not divisible by 4, so 6, 10,
 *       14) - neither of the above works, and this is the awkward case. The
 *       LUX method builds an odd square of side {@code n/2}, inflates each of
 *       its cells into a 2x2 block of four consecutive numbers, and chooses
 *       between three arrangements of those four - L, U and X - by which band
 *       the block is in, with one deliberate swap near the middle.</li>
 * </ul>
 *
 * <p>So {@link #construct} is a three-way branch on arithmetic, not an
 * algorithm with cases bolted on. It is worth knowing that the smallest
 * awkward size, 6, resisted a general method for a long time - and that the
 * swap in the LUX layout, which looks arbitrary, is exactly what repairs the
 * diagonals that the plain banding would otherwise break.
 *
 * <h2>Playing it</h2>
 *
 * <p>A puzzle hides some of the cells and asks for them back. Completion is
 * judged by {@link #isMagic} rather than against the square it came from, so
 * any correct arrangement counts - which is the honest test, since large
 * squares have enormous numbers of solutions and insisting on one of them
 * would be arbitrary.
 */
public final class MagicSquare implements Project {

    public static final Meta META = new Meta(
            242, "magic-square", "Magic Square", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Arrange numbers so every row, column and diagonal adds to the same total.",
            "", true);

    private static final int MIN_SIZE = 3;
    private static final int MAX_SIZE = 10;

    private final Random random;

    private int size;
    private int[][] answer;
    private int[][] cells;
    private boolean[][] given;

    public MagicSquare() {
        this(new Random());
    }

    public MagicSquare(Random random) {
        this.random = random;
        generate(4);
    }

    @Override
    public Meta meta() {
        return META;
    }

    public int size() {
        return size;
    }

    public int cell(int row, int column) {
        return cells[row][column];
    }

    public boolean isGiven(int row, int column) {
        return given[row][column];
    }

    /** What every line has to add up to. Forced by the numbers, not chosen. */
    public static int magicConstant(int n) {
        return n * (n * n + 1) / 2;
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /** Which family {@code n} falls into: odd, doubly even, or singly even. */
    public static String methodFor(int n) {
        if (n % 2 == 1) {
            return "Siamese";
        }
        return n % 4 == 0 ? "complement lattice" : "LUX";
    }

    /** A magic square of side {@code n}, by whichever method {@code n} needs. */
    public static int[][] construct(int n) {
        if (n == 2) {
            throw new IllegalArgumentException(
                    "There is no 2x2 magic square - its constant would be 5, and 4 has "
                    + "no room for three different partners that each make 5.");
        }
        if (n < 1) {
            throw new IllegalArgumentException("A square needs a positive side.");
        }
        if (n % 2 == 1) {
            return siamese(n);
        }
        return n % 4 == 0 ? doublyEven(n) : singlyEven(n);
    }

    /**
     * Odd sides: start above the middle and walk up and to the right, wrapping
     * at the edges, dropping one square whenever the target is taken.
     */
    private static int[][] siamese(int n) {
        int[][] square = new int[n][n];
        int row = 0;
        int column = n / 2;
        for (int value = 1; value <= n * n; value++) {
            square[row][column] = value;
            int nextRow = (row - 1 + n) % n;
            int nextColumn = (column + 1) % n;
            if (square[nextRow][nextColumn] != 0) {
                nextRow = (row + 1) % n;
                nextColumn = column;
            }
            row = nextRow;
            column = nextColumn;
        }
        return square;
    }

    /**
     * Sides divisible by four: count up in reading order, then swap each
     * marked cell for its complement. A cell is marked when its row and its
     * column agree about being at the edge of their block of four - which is
     * what puts exactly half of each line on either side of the swap.
     */
    private static int[][] doublyEven(int n) {
        int[][] square = new int[n][n];
        int value = 1;
        for (int row = 0; row < n; row++) {
            for (int column = 0; column < n; column++) {
                boolean rowEdge = row % 4 == 0 || row % 4 == 3;
                boolean columnEdge = column % 4 == 0 || column % 4 == 3;
                square[row][column] = rowEdge == columnEdge ? n * n + 1 - value : value;
                value++;
            }
        }
        return square;
    }

    /**
     * Sides that are even but not divisible by four - the awkward family.
     *
     * <p>Build an odd square of side {@code n/2}, then blow every one of its
     * cells up into a 2x2 block holding the four consecutive numbers that cell
     * stands for. How those four are arranged inside the block is chosen by
     * band: L for the top bands, U for the one below them, X for the rest. The
     * single swap in the middle column, of the last L against the U beneath
     * it, is what repairs the diagonals.
     */
    private static int[][] singlyEven(int n) {
        int half = n / 2;
        int m = (n - 2) / 4;
        int[][] seed = siamese(half);
        char[][] pattern = new char[half][half];
        for (int row = 0; row < half; row++) {
            char shape = row <= m ? 'L' : row == m + 1 ? 'U' : 'X';
            Arrays.fill(pattern[row], shape);
        }
        // The swap: the L in the centre column of the last L band trades with
        // the U directly below it.
        pattern[m][half / 2] = 'U';
        pattern[m + 1][half / 2] = 'L';

        int[][] square = new int[n][n];
        for (int row = 0; row < half; row++) {
            for (int column = 0; column < half; column++) {
                int base = (seed[row][column] - 1) * 4;
                int[][] block = switch (pattern[row][column]) {
                    case 'L' -> new int[][] {{base + 4, base + 1}, {base + 2, base + 3}};
                    case 'U' -> new int[][] {{base + 1, base + 4}, {base + 2, base + 3}};
                    default -> new int[][] {{base + 1, base + 4}, {base + 3, base + 2}};
                };
                square[row * 2][column * 2] = block[0][0];
                square[row * 2][column * 2 + 1] = block[0][1];
                square[row * 2 + 1][column * 2] = block[1][0];
                square[row * 2 + 1][column * 2 + 1] = block[1][1];
            }
        }
        return square;
    }

    // ------------------------------------------------------------------
    // Checking
    // ------------------------------------------------------------------

    /** Whether a filled square really is magic, checked from first principles. */
    public static boolean isMagic(int[][] square) {
        int n = square.length;
        if (n == 0) {
            return false;
        }
        boolean[] seen = new boolean[n * n + 1];
        for (int[] line : square) {
            if (line.length != n) {
                return false;
            }
            for (int value : line) {
                if (value < 1 || value > n * n || seen[value]) {
                    return false;   // out of range, or used twice
                }
                seen[value] = true;
            }
        }
        int target = magicConstant(n);
        int downDiagonal = 0;
        int upDiagonal = 0;
        for (int index = 0; index < n; index++) {
            int row = 0;
            int column = 0;
            for (int other = 0; other < n; other++) {
                row += square[index][other];
                column += square[other][index];
            }
            if (row != target || column != target) {
                return false;
            }
            downDiagonal += square[index][index];
            upDiagonal += square[index][n - 1 - index];
        }
        return downDiagonal == target && upDiagonal == target;
    }

    /** The first line that does not add up yet, or an empty string. */
    public String firstFault() {
        int target = magicConstant(size);
        List<Integer> used = new ArrayList<>();
        for (int[] line : cells) {
            for (int value : line) {
                if (value != 0) {
                    if (used.contains(value)) {
                        return "The number " + value + " is used more than once.";
                    }
                    used.add(value);
                }
            }
        }
        for (int index = 0; index < size; index++) {
            String row = lineFault(cells[index], target, "Row " + index);
            if (!row.isEmpty()) {
                return row;
            }
            int[] column = new int[size];
            for (int other = 0; other < size; other++) {
                column[other] = cells[other][index];
            }
            String fault = lineFault(column, target, "Column " + index);
            if (!fault.isEmpty()) {
                return fault;
            }
        }
        return "";
    }

    /** A line only counts as wrong once it is full - a gap could still fix it. */
    private String lineFault(int[] line, int target, String label) {
        int sum = 0;
        for (int value : line) {
            if (value == 0) {
                return "";
            }
            sum += value;
        }
        return sum == target ? "" : label + " adds to " + sum + ", not " + target + ".";
    }

    // ------------------------------------------------------------------
    // Puzzle
    // ------------------------------------------------------------------

    /** Builds a square and hides some of it. */
    public void generate(int requested) {
        if (requested < MIN_SIZE || requested > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Use a size between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        size = requested;
        answer = construct(size);
        given = new boolean[size][size];
        for (boolean[] line : given) {
            Arrays.fill(line, true);
        }
        List<int[]> order = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                order.add(new int[] {row, column});
            }
        }
        Collections.shuffle(order, random);
        int hide = Math.max(2, size * size / 4);
        for (int index = 0; index < hide; index++) {
            int[] cell = order.get(index);
            given[cell[0]][cell[1]] = false;
        }
        cells = new int[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                cells[row][column] = given[row][column] ? answer[row][column] : 0;
            }
        }
    }

    /** The numbers that have not been placed yet. */
    public List<Integer> missing() {
        boolean[] placed = new boolean[size * size + 1];
        for (int[] line : cells) {
            for (int value : line) {
                if (value >= 1 && value <= size * size) {
                    placed[value] = true;
                }
            }
        }
        List<Integer> out = new ArrayList<>();
        for (int value = 1; value <= size * size; value++) {
            if (!placed[value]) {
                out.add(value);
            }
        }
        return out;
    }

    public void place(int row, int column, int value) {
        if (row < 0 || row >= size || column < 0 || column >= size) {
            throw new IllegalArgumentException("There is no cell " + row + "," + column + ".");
        }
        if (given[row][column]) {
            throw new IllegalArgumentException("That one was given.");
        }
        if (value < 0 || value > size * size) {
            throw new IllegalArgumentException("Use 1 to " + size * size + ", or 0 to clear.");
        }
        cells[row][column] = value;
    }

    /** Any arrangement that is magic counts, not only the one it came from. */
    public boolean complete() {
        return isMagic(cells);
    }

    public String solve() {
        cells = new int[size][size];
        for (int row = 0; row < size; row++) {
            cells[row] = answer[row].clone();
        }
        return "Filled in by the " + methodFor(size) + " method. Every line adds to "
                + magicConstant(size) + ".";
    }

    public String hint() {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (cells[row][column] != answer[row][column]) {
                    cells[row][column] = answer[row][column];
                    return "Row " + row + ", column " + column + " can hold "
                            + answer[row][column] + ".";
                }
            }
        }
        return "Nothing left to give away.";
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public String[][] display() {
        String[][] grid = new String[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                grid[row][column] = cells[row][column] == 0
                        ? "." : String.valueOf(cells[row][column]);
            }
        }
        return grid;
    }

    private static List<List<String>> asLists(String[][] grid) {
        List<List<String>> out = new ArrayList<>(grid.length);
        for (String[] row : grid) {
            out.add(List.of(row));
        }
        return out;
    }

    private String detail() {
        if (complete()) {
            return "Solved. Every line adds to " + magicConstant(size) + ".";
        }
        String fault = firstFault();
        if (!fault.isEmpty()) {
            return fault;
        }
        return String.format("%dx%d, every line must add to %d. %d numbers still to place: %s",
                size, size, magicConstant(size), missing().size(), missing());
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("board size:", MIN_SIZE, MAX_SIZE, 4));
        io.muted("Every row, column and both diagonals add to the same total, which the "
                + "numbers themselves decide.");
        while (true) {
            io.println();
            print(io);
            io.muted(detail());
            int choice = io.menu("Magic Square",
                    List.of("Place a number", "Hint", "Solve", "New puzzle"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0 -> {
                        int row = io.askInt("row:", 0, size - 1, 0);
                        int column = io.askInt("column:", 0, size - 1, 0);
                        place(row, column, io.askInt("number (0 clears):", 0, size * size, 0));
                    }
                    case 1 -> io.info(hint());
                    case 2 -> io.info(solve());
                    default -> generate(io.askInt("board size:", MIN_SIZE, MAX_SIZE, size));
                }
            } catch (RuntimeException e) {
                io.error(e.getMessage());
            }
        }
    }

    /** Right-aligns, so three-digit numbers keep the columns straight. */
    private void print(ConsoleUI io) {
        String[][] grid = display();
        int width = 1;
        for (String[] row : grid) {
            for (String cell : row) {
                width = Math.max(width, cell.length());
            }
        }
        for (String[] row : grid) {
            StringBuilder line = new StringBuilder("  ");
            for (String cell : row) {
                line.append(" ".repeat(width - cell.length())).append(cell).append(' ');
            }
            io.println(line.toString());
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    generate(Json.integer(body, "size", 4));
                    return board("Built by the " + methodFor(size) + " method, then partly "
                            + "hidden. Pick a number above and click a blank cell.");
                }
                case "cycle", "step", "place" -> {
                    int row = Json.integer(body, "row", -1);
                    int column = Json.integer(body, "col", -1);
                    if (row < 0 || column < 0 || row >= size || column >= size) {
                        return board("");
                    }
                    int value = Json.integer(body, "value", 1);
                    place(row, column, cells[row][column] == value ? 0 : value);
                    return board(complete() ? "That is a magic square." : "");
                }
                case "hint" -> {
                    return board(hint());
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
        Map<String, Object> out = Json.ok("board", asLists(display()), "detail", detail());
        if (note != null && !note.isEmpty()) {
            out.put("result", note);
        }
        return out;
    }
}
