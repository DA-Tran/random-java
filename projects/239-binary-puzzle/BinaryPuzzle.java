package com.randomjava.projects.binarypuzzle;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Binary Puzzle - fill a square grid with two symbols under three rules:
 * never three of a kind in a row, each line evenly split between the two, and
 * no two rows or two columns alike.
 *
 * <h2>Enumerate the lines, then intersect them</h2>
 *
 * <p>The first two rules are properties of a single line, and a line of length
 * {@code n} has only {@code 2^n} possible contents. That is a small number at
 * playable sizes, and the rules cut it down hard: of the 4096 ways to fill a
 * line of twelve, 208 are balanced with no run of three. Across the even
 * lengths the counts run 6, 14, 34, 84, 208 - growing, but nothing like the
 * {@code 2^n} they are drawn from. So rather than reason about the rules,
 * {@link #validLines} enumerates every legal line once and caches it, and
 * everything downstream is set arithmetic over that list.
 *
 * <p>Solving a line is then the same move as in {@link
 * com.randomjava.projects.nonogram.Nonogram}: keep the candidates that agree
 * with the cells already known, and look at what they all have in common. A
 * cell that is the same symbol in every surviving candidate is forced. A line
 * with no surviving candidate is a contradiction. Nothing else may be written
 * down - and, importantly, nothing else <em>needs</em> to be, because the
 * intersection of all the survivors is by definition every deduction the two
 * local rules can support. There is no cleverer local rule waiting to be
 * discovered; this is the whole of it.
 *
 * <p>That is worth stating plainly because the usual presentation is a bag of
 * patterns - {@code 11_} forces a nought, {@code 1_1} forces one in the middle,
 * a line with all its ones placed is full of noughts. Every one of those falls
 * out of the intersection, and so does every pattern nobody bothered to name.
 *
 * <h2>The rule that is not local</h2>
 *
 * <p>The third rule - all rows distinct, all columns distinct - is not a
 * property of any one line, so it cannot join the intersection. It is checked
 * separately, and it is genuinely used: {@link #search} rejects a grid the
 * moment two completed rows match, which prunes far earlier than waiting for a
 * full grid to compare.
 *
 * <p>Propagation plus that check still leaves puzzles it cannot finish, so the
 * solver guesses a cell and recurses when it stalls, the same shape of fallback
 * every constraint puzzle here ends up needing.
 *
 * <h2>Making a puzzle rather than checking one</h2>
 *
 * <p>Generation runs the machinery backwards. Fill a grid at random subject to
 * the rules, then take givens away one at a time, keeping a removal only while
 * the puzzle still has exactly one solution. What is left is minimal in the
 * useful sense: put any of the removed cells back and you have made the puzzle
 * easier, take any remaining one away and it stops having a single answer.
 */
public final class BinaryPuzzle implements Project {

    public static final Meta META = new Meta(
            239, "binary-puzzle", "Binary Puzzle", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Fill a grid with two symbols, never three in a row, balanced and all rows distinct.",
            "", true);

    /** Nothing written here yet. */
    public static final char UNKNOWN = '.';
    /** The first symbol. */
    public static final char ONE = '#';
    /** The second symbol. */
    public static final char ZERO = '*';

    private static final int MIN_SIZE = 4;
    private static final int MAX_SIZE = 12;
    private static final int NODE_BUDGET = 200_000;

    /** Legal lines by length. Computed once, then shared - they never change. */
    private static final Map<Integer, int[]> LINE_CACHE = new HashMap<>();

    private final Random random;

    private int size;
    private char[][] cells;
    private char[][] answer;
    private boolean[][] given;
    private int nodesLeft;
    private int guesses;

    public BinaryPuzzle() {
        this(new Random());
    }

    public BinaryPuzzle(Random random) {
        this.random = random;
        generate(8);
    }

    @Override
    public Meta meta() {
        return META;
    }

    public int size() {
        return size;
    }

    public boolean isGiven(int row, int column) {
        return given[row][column];
    }

    public char cell(int row, int column) {
        return cells[row][column];
    }

    // ------------------------------------------------------------------
    // Legal lines
    // ------------------------------------------------------------------

    /**
     * Every line of this length that is balanced and has no run of three, as
     * bitmasks with a set bit meaning {@link #ONE}.
     *
     * <p>Enumerating all {@code 2^n} and filtering is the honest way to do
     * this: it is obviously correct, it runs once per size, and at the sizes
     * anyone would play it is a few thousand cheap tests.
     */
    public static int[] validLines(int length) {
        return LINE_CACHE.computeIfAbsent(length, n -> {
            List<Integer> good = new ArrayList<>();
            for (int bits = 0; bits < (1 << n); bits++) {
                if (Integer.bitCount(bits) * 2 != n) {
                    continue;   // not evenly split
                }
                boolean runOfThree = false;
                for (int i = 0; i + 2 < n && !runOfThree; i++) {
                    int a = bits >> i & 1;
                    int b = bits >> (i + 1) & 1;
                    int c = bits >> (i + 2) & 1;
                    runOfThree = a == b && b == c;
                }
                if (!runOfThree) {
                    good.add(bits);
                }
            }
            int[] out = new int[good.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = good.get(i);
            }
            return out;
        });
    }

    /**
     * Writes into a line everything the two local rules force, or returns null
     * when no legal line agrees with what is already there.
     *
     * <p>The candidates that survive are exactly the legal lines matching the
     * known cells; a cell they all agree on is forced, and a cell they differ
     * on cannot be settled by any rule that looks at one line at a time.
     */
    public static char[] refineLine(char[] line) {
        int n = line.length;
        int onesEverywhere = -1;         // bits that are ONE in every survivor
        int zerosEverywhere = -1;        // bits that are ZERO in every survivor
        boolean any = false;
        for (int candidate : validLines(n)) {
            boolean agrees = true;
            for (int i = 0; i < n && agrees; i++) {
                if (line[i] == UNKNOWN) {
                    continue;
                }
                agrees = (candidate >> i & 1) == (line[i] == ONE ? 1 : 0);
            }
            if (!agrees) {
                continue;
            }
            any = true;
            onesEverywhere &= candidate;
            zerosEverywhere &= ~candidate;
        }
        if (!any) {
            return null;
        }
        char[] out = line.clone();
        for (int i = 0; i < n; i++) {
            if ((onesEverywhere >> i & 1) == 1) {
                out[i] = ONE;
            } else if ((zerosEverywhere >> i & 1) == 1) {
                out[i] = ZERO;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Propagation and search
    // ------------------------------------------------------------------

    private boolean propagate(char[][] grid) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int row = 0; row < size; row++) {
                char[] refined = refineLine(grid[row]);
                if (refined == null) {
                    return false;
                }
                if (!Arrays.equals(grid[row], refined)) {
                    grid[row] = refined;
                    changed = true;
                }
            }
            for (int column = 0; column < size; column++) {
                char[] line = new char[size];
                for (int row = 0; row < size; row++) {
                    line[row] = grid[row][column];
                }
                char[] refined = refineLine(line);
                if (refined == null) {
                    return false;
                }
                for (int row = 0; row < size; row++) {
                    if (grid[row][column] != refined[row]) {
                        grid[row][column] = refined[row];
                        changed = true;
                    }
                }
            }
        }
        return distinctSoFar(grid);
    }

    /**
     * The third rule, checked as early as it can be: two <em>finished</em>
     * lines that match are a contradiction, and waiting for the whole grid
     * before noticing costs an enormous amount of pointless search.
     */
    private boolean distinctSoFar(char[][] grid) {
        for (int a = 0; a < size; a++) {
            for (int b = a + 1; b < size; b++) {
                if (settled(grid[a]) && settled(grid[b]) && Arrays.equals(grid[a], grid[b])) {
                    return false;
                }
                char[] first = columnOf(grid, a);
                char[] second = columnOf(grid, b);
                if (settled(first) && settled(second) && Arrays.equals(first, second)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean settled(char[] line) {
        for (char cell : line) {
            if (cell == UNKNOWN) {
                return false;
            }
        }
        return true;
    }

    private char[] columnOf(char[][] grid, int column) {
        char[] line = new char[size];
        for (int row = 0; row < size; row++) {
            line[row] = grid[row][column];
        }
        return line;
    }

    private int search(char[][] grid, int cap, char[][][] found) {
        if (cap <= 0 || nodesLeft-- <= 0) {
            return 0;
        }
        if (!propagate(grid)) {
            return 0;
        }
        int row = -1;
        int column = -1;
        outer:
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (grid[r][c] == UNKNOWN) {
                    row = r;
                    column = c;
                    break outer;
                }
            }
        }
        if (row < 0) {
            if (found[0] == null) {
                found[0] = copy(grid);
            }
            return 1;
        }
        guesses++;
        int total = 0;
        for (char guess : new char[] {ONE, ZERO}) {
            char[][] next = copy(grid);
            next[row][column] = guess;
            total += search(next, cap - total, found);
            if (total >= cap) {
                break;
            }
        }
        return total;
    }

    /** How many grids finish this puzzle, counted no further than {@code cap}. */
    public int countSolutions(int cap) {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        return search(givenGrid(), cap, new char[1][][]);
    }

    public String solve() {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        char[][][] found = new char[1][][];
        int count = search(givenGrid(), 2, found);
        if (count == 0) {
            return "These givens cannot be completed.";
        }
        cells = copy(found[0]);
        return (guesses == 0
                ? "The three rules alone were enough - no guessing."
                : "Propagation stalled, so the solver branched " + guesses + " times.")
                + (count == 1 ? "" : " These givens allow more than one grid.");
    }

    public String hint() {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (cells[row][column] != answer[row][column]) {
                    cells[row][column] = answer[row][column];
                    return "Row " + row + ", column " + column + " is "
                            + answer[row][column] + ".";
                }
            }
        }
        return "Nothing left to give away.";
    }

    // ------------------------------------------------------------------
    // Generating
    // ------------------------------------------------------------------

    /**
     * A full legal grid, then givens removed for as long as exactly one
     * solution survives. The result is minimal: every remaining given is
     * load-bearing.
     */
    public void generate(int requested) {
        if (requested < MIN_SIZE || requested > MAX_SIZE || requested % 2 != 0) {
            throw new IllegalArgumentException(
                    "Use an even size between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        size = requested;
        answer = randomGrid();
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
        java.util.Collections.shuffle(order, random);
        for (int[] cell : order) {
            given[cell[0]][cell[1]] = false;
            if (countSolutions(2) != 1) {
                given[cell[0]][cell[1]] = true;   // that one was holding it up
            }
        }
        cells = givenGrid();
    }

    /**
     * A random legal grid, built by filling cells in a random order and
     * letting the same propagate-and-branch machinery keep it legal. Choosing
     * the symbol at random each time is what makes the grids differ; the
     * solver is what makes them legal.
     */
    private char[][] randomGrid() {
        while (true) {
            char[][] grid = new char[size][size];
            for (char[] line : grid) {
                Arrays.fill(line, UNKNOWN);
            }
            boolean ok = true;
            for (int attempt = 0; attempt < size * size && ok; attempt++) {
                nodesLeft = NODE_BUDGET;
                char[][][] found = new char[1][][];
                char[][] trial = copy(grid);
                int row = random.nextInt(size);
                int column = random.nextInt(size);
                if (grid[row][column] != UNKNOWN) {
                    continue;
                }
                trial[row][column] = random.nextBoolean() ? ONE : ZERO;
                if (search(copy(trial), 1, found) == 1) {
                    grid = trial;
                } else {
                    // That symbol was impossible here, so the other one is
                    // forced - and propagation will find it next pass.
                    trial[row][column] = trial[row][column] == ONE ? ZERO : ONE;
                    nodesLeft = NODE_BUDGET;
                    if (search(copy(trial), 1, new char[1][][]) == 1) {
                        grid = trial;
                    } else {
                        ok = false;
                    }
                }
            }
            if (!ok) {
                continue;
            }
            nodesLeft = NODE_BUDGET;
            char[][][] found = new char[1][][];
            if (search(copy(grid), 1, found) == 1) {
                return found[0];
            }
        }
    }

    private char[][] givenGrid() {
        char[][] grid = new char[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                grid[row][column] = given[row][column] ? answer[row][column] : UNKNOWN;
            }
        }
        return grid;
    }

    private static char[][] copy(char[][] grid) {
        char[][] out = new char[grid.length][];
        for (int i = 0; i < grid.length; i++) {
            out[i] = grid[i].clone();
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    /** A click: one symbol, the other, then blank again. Givens do not move. */
    public char cycle(int row, int column) {
        check(row, column);
        if (given[row][column]) {
            throw new IllegalArgumentException("That one was given.");
        }
        char next = switch (cells[row][column]) {
            case UNKNOWN -> ONE;
            case ONE -> ZERO;
            default -> UNKNOWN;
        };
        cells[row][column] = next;
        return next;
    }

    public void set(int row, int column, char symbol) {
        check(row, column);
        if (given[row][column]) {
            throw new IllegalArgumentException("That one was given.");
        }
        if (symbol != UNKNOWN && symbol != ONE && symbol != ZERO) {
            throw new IllegalArgumentException("A cell holds " + ONE + ", " + ZERO + " or nothing.");
        }
        cells[row][column] = symbol;
    }

    private void check(int row, int column) {
        if (row < 0 || row >= size || column < 0 || column >= size) {
            throw new IllegalArgumentException("There is no cell " + row + "," + column + ".");
        }
    }

    public boolean complete() {
        for (int row = 0; row < size; row++) {
            if (!Arrays.equals(cells[row], answer[row])) {
                return false;
            }
        }
        return true;
    }

    /** The first rule the board currently breaks, or an empty string. */
    public String firstFault() {
        for (int index = 0; index < size; index++) {
            String row = faultIn(cells[index], "Row " + index);
            if (!row.isEmpty()) {
                return row;
            }
            String column = faultIn(columnOf(cells, index), "Column " + index);
            if (!column.isEmpty()) {
                return column;
            }
        }
        for (int a = 0; a < size; a++) {
            for (int b = a + 1; b < size; b++) {
                if (settled(cells[a]) && Arrays.equals(cells[a], cells[b])) {
                    return "Rows " + a + " and " + b + " are the same.";
                }
                char[] first = columnOf(cells, a);
                if (settled(first) && Arrays.equals(first, columnOf(cells, b))) {
                    return "Columns " + a + " and " + b + " are the same.";
                }
            }
        }
        return "";
    }

    private String faultIn(char[] line, String label) {
        for (int i = 0; i + 2 < line.length; i++) {
            if (line[i] != UNKNOWN && line[i] == line[i + 1] && line[i] == line[i + 2]) {
                return label + " has three " + line[i] + " in a row.";
            }
        }
        int ones = 0;
        int zeros = 0;
        for (char cell : line) {
            if (cell == ONE) {
                ones++;
            } else if (cell == ZERO) {
                zeros++;
            }
        }
        if (ones > size / 2 || zeros > size / 2) {
            return label + " has more than " + size / 2 + " of one symbol.";
        }
        return "";
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public char[][] cells() {
        return copy(cells);
    }

    private String detail() {
        if (complete()) {
            return "Solved.";
        }
        String fault = firstFault();
        if (!fault.isEmpty()) {
            return fault;
        }
        int blank = 0;
        int clues = 0;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (cells[row][column] == UNKNOWN) {
                    blank++;
                }
                if (given[row][column]) {
                    clues++;
                }
            }
        }
        return String.format("%dx%d, %d givens, %d cells still blank.", size, size, clues, blank);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(evenSize(io.askInt("board size (even):", MIN_SIZE, MAX_SIZE, 8)));
        io.muted("Two symbols, " + ONE + " and " + ZERO + ". Never three alike in a line, "
                + "an even split in every line, and no two rows or columns the same.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            int choice = io.menu("Binary Puzzle",
                    List.of("Place " + ONE, "Place " + ZERO, "Clear a cell",
                            "Hint", "Solve", "New puzzle"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0, 1, 2 -> {
                        int row = io.askInt("row:", 0, size - 1, 0);
                        int column = io.askInt("column:", 0, size - 1, 0);
                        set(row, column, choice == 0 ? ONE : choice == 1 ? ZERO : UNKNOWN);
                    }
                    case 3 -> io.info(hint());
                    case 4 -> io.info(solve());
                    default -> generate(evenSize(
                            io.askInt("board size (even):", MIN_SIZE, MAX_SIZE, size)));
                }
            } catch (RuntimeException e) {
                io.error(e.getMessage());
            }
        }
    }

    private static int evenSize(int requested) {
        return requested % 2 == 0 ? requested : requested - 1;
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    generate(evenSize(Json.integer(body, "size", 8)));
                    return board("Click a cell to cycle it through " + ONE + ", "
                            + ZERO + " and blank.");
                }
                case "cycle", "step" -> {
                    cycle(Json.integer(body, "row", 0), Json.integer(body, "col", 0));
                    return board(complete() ? "That is the grid." : "");
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
        Map<String, Object> out = Json.ok("board", Json.grid(cells()), "detail", detail());
        if (note != null && !note.isEmpty()) {
            out.put("result", note);
        }
        return out;
    }
}
