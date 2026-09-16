package com.randomjava.projects.futoshiki;

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
 * Futoshiki - a Latin square with greater-than signs between some neighbours.
 *
 * <p>Half the puzzle is ordinary Latin square work: each digit once per row and
 * once per column, tracked as a bitmask of what each cell could still be. The
 * other half is the inequalities, and they are worth more than they look.
 *
 * <h2>What an inequality is actually worth</h2>
 *
 * <p>The obvious reading of {@code a < b} is local: a cannot be the largest
 * value, b cannot be the smallest. Narrow both ends and move on. That is arc
 * consistency, it is cheap, and it is what {@link #narrowInequalities} does
 * first.
 *
 * <p>But inequalities compose. If {@code a < b} and {@code b < c} then three
 * distinct values sit in increasing order, so {@code c} is at least 3 and
 * {@code a} is at most {@code n - 2}. Follow the signs as a directed graph and
 * the rule generalises: <b>a cell with a strictly increasing chain of length k
 * running into it is at least k, and a chain of length k running out of it puts
 * it at most n - k + 1.</b> Both bounds come from one longest-path computation
 * over the constraint graph, which {@link #chainBounds} does in
 * {@code O(cells + signs)}.
 *
 * <p>This is not a refinement of the local rule, it is strictly stronger, and
 * on a board with a long run of signs it can settle cells outright that pairwise
 * reasoning leaves wide open. A chain of four rising signs across a 5x5 fixes
 * all five cells with no other information at all.
 *
 * <h2>A free consistency check</h2>
 *
 * <p>The same graph answers a question the local rule cannot even ask. If the
 * signs contain a cycle - {@code a < b < c < a} - then a value is strictly less
 * than itself, and no amount of searching will help. The longest-path pass
 * detects that while it runs, so an impossible board is reported as impossible
 * rather than searched to exhaustion.
 *
 * <h2>And then the search</h2>
 *
 * <p>Propagation is still not complete, so when it stalls {@link #search} takes
 * the cell with the fewest candidates left and tries each in turn. Choosing the
 * most constrained cell rather than the first one matters here: it keeps the
 * branching factor near two and makes the generator's repeated uniqueness
 * checks affordable.
 */
public final class Futoshiki implements Project {

    public static final Meta META = new Meta(
            233, "futoshiki", "Futoshiki", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "A Latin square with greater-than signs constraining neighbouring cells.",
            "", true);

    private static final int MIN_SIZE = 4;
    private static final int MAX_SIZE = 7;
    private static final int NODE_BUDGET = 300_000;

    private final Random random;

    private int size;
    /** {@code less[a][b]} - the puzzle says cell a holds less than cell b. */
    private boolean[][] less;
    /**
     * The same signs as a plain edge list. Both the narrowing and the
     * longest-path pass run once per sign rather than once per pair of cells,
     * which on a 7x7 is 84 edges instead of 2401 pairs - and the generator
     * calls them thousands of times while carving the puzzle down.
     */
    private int[][] edges;
    private int[][] digits;
    private int[][] answer;
    private boolean[][] given;

    private int nodesLeft;
    private int guesses;

    public Futoshiki() {
        this(new Random());
    }

    public Futoshiki(Random random) {
        this.random = random;
        generate(5);
    }

    @Override
    public Meta meta() {
        return META;
    }

    public int size() {
        return size;
    }

    public int digit(int row, int column) {
        return digits[row][column];
    }

    public boolean isGiven(int row, int column) {
        return given[row][column];
    }

    private int index(int row, int column) {
        return row * size + column;
    }

    /** True when the puzzle says the first cell is less than the second. */
    public boolean lessThan(int rowA, int columnA, int rowB, int columnB) {
        return less[index(rowA, columnA)][index(rowB, columnB)];
    }

    // ------------------------------------------------------------------
    // Candidate propagation
    // ------------------------------------------------------------------

    private int fullMask() {
        return (1 << size) - 1;      // bit d-1 means "this cell could be d"
    }

    private int[] masksFrom(int[][] grid) {
        int[] masks = new int[size * size];
        Arrays.fill(masks, fullMask());
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (grid[row][column] != 0) {
                    masks[index(row, column)] = 1 << (grid[row][column] - 1);
                }
            }
        }
        return masks;
    }

    private static int lowest(int mask) {
        return Integer.numberOfTrailingZeros(mask) + 1;
    }

    private static int highest(int mask) {
        return 32 - Integer.numberOfLeadingZeros(mask);
    }

    /**
     * Pairwise narrowing: nothing may be as large as the largest thing above
     * it, nor as small as the smallest thing below it.
     */
    private boolean narrowInequalities(int[] masks) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int[] edge : edges) {
                {
                    int a = edge[0];
                    int b = edge[1];
                    int trimmedA = masks[a] & ((1 << (highest(masks[b]) - 1)) - 1);
                    int trimmedB = masks[b] & ~((1 << lowest(masks[a])) - 1);
                    if (trimmedA == 0 || trimmedB == 0) {
                        return false;
                    }
                    if (trimmedA != masks[a] || trimmedB != masks[b]) {
                        masks[a] = trimmedA;
                        masks[b] = trimmedB;
                        changed = true;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Longest-chain bounds, and a cycle check for free.
     *
     * <p>{@code rising[c]} is the length of the longest strictly increasing run
     * of signs ending at c, so c is at least {@code rising[c] + 1}. {@code
     * falling[c]} does the same going the other way and caps c from above. A
     * cell that never finishes being relaxed sits on a cycle, which means the
     * signs contradict each other.
     */
    private boolean chainBounds(int[] masks) {
        int cells = size * size;
        int[] rising = longestPaths(cells, false);
        int[] falling = longestPaths(cells, true);
        if (rising == null || falling == null) {
            return false;   // the signs contain a cycle
        }
        for (int cell = 0; cell < cells; cell++) {
            int atLeast = rising[cell] + 1;
            int atMost = size - falling[cell];
            if (atLeast > atMost) {
                return false;
            }
            int window = ((1 << atMost) - 1) & ~((1 << (atLeast - 1)) - 1);
            masks[cell] &= window;
            if (masks[cell] == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Longest path to (or from) every cell along the sign graph, by relaxation.
     * Returns null when a length exceeds what an acyclic graph could produce,
     * which is exactly the condition for a cycle.
     */
    private int[] longestPaths(int cells, boolean reversed) {
        int[] best = new int[cells];
        for (int round = 0; round <= cells; round++) {
            boolean changed = false;
            for (int[] edge : edges) {
                int from = reversed ? edge[1] : edge[0];
                int to = reversed ? edge[0] : edge[1];
                if (best[to] < best[from] + 1) {
                    best[to] = best[from] + 1;
                    changed = true;
                }
            }
            if (!changed) {
                return best;
            }
        }
        return null;   // still relaxing after n rounds: there is a cycle
    }

    /** Rebuilds the edge list. Called whenever a sign is added or removed. */
    private void rebuildEdges() {
        List<int[]> found = new ArrayList<>();
        for (int a = 0; a < less.length; a++) {
            for (int b = 0; b < less.length; b++) {
                if (less[a][b]) {
                    found.add(new int[] {a, b});
                }
            }
        }
        edges = found.toArray(new int[0][]);
    }

    /** One digit per row and column, plus the two inequality rules, to fixpoint. */
    private boolean propagate(int[] masks) {
        boolean changed = true;
        while (changed) {
            changed = false;
            if (!narrowInequalities(masks) || !chainBounds(masks)) {
                return false;
            }
            // A settled cell takes its digit away from its row and column.
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    int cell = index(row, column);
                    if (Integer.bitCount(masks[cell]) != 1) {
                        continue;
                    }
                    for (int other = 0; other < size; other++) {
                        changed |= strike(masks, index(row, other), cell);
                        changed |= strike(masks, index(other, column), cell);
                    }
                }
            }
            // And a digit with only one home in a line belongs there.
            for (int line = 0; line < size; line++) {
                for (int digit = 0; digit < size; digit++) {
                    changed |= placeOnly(masks, line, digit, true);
                    changed |= placeOnly(masks, line, digit, false);
                }
            }
            for (int mask : masks) {
                if (mask == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean strike(int[] masks, int target, int source) {
        if (target == source || (masks[target] & masks[source]) == 0) {
            return false;
        }
        masks[target] &= ~masks[source];
        return true;
    }

    private boolean placeOnly(int[] masks, int line, int digit, boolean byRow) {
        int bit = 1 << digit;
        int home = -1;
        int count = 0;
        for (int other = 0; other < size; other++) {
            int cell = byRow ? index(line, other) : index(other, line);
            if ((masks[cell] & bit) != 0) {
                count++;
                home = cell;
            }
        }
        if (count == 1 && masks[home] != bit) {
            masks[home] = bit;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    private int search(int[] masks, int cap, int[][][] found) {
        if (cap <= 0 || nodesLeft-- <= 0) {
            return 0;
        }
        if (!propagate(masks)) {
            return 0;
        }
        // The most constrained cell, which keeps the branching factor low.
        int pick = -1;
        int fewest = Integer.MAX_VALUE;
        for (int cell = 0; cell < masks.length; cell++) {
            int options = Integer.bitCount(masks[cell]);
            if (options > 1 && options < fewest) {
                fewest = options;
                pick = cell;
            }
        }
        if (pick < 0) {
            if (found[0] == null) {
                found[0] = gridOf(masks);
            }
            return 1;
        }
        guesses++;
        int total = 0;
        for (int digit = 0; digit < size; digit++) {
            if ((masks[pick] >> digit & 1) == 0) {
                continue;
            }
            int[] next = masks.clone();
            next[pick] = 1 << digit;
            total += search(next, cap - total, found);
            if (total >= cap) {
                break;
            }
        }
        return total;
    }

    private int[][] gridOf(int[] masks) {
        int[][] grid = new int[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                grid[row][column] = lowest(masks[index(row, column)]);
            }
        }
        return grid;
    }

    public int countSolutions(int cap) {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        return search(masksFrom(givenGrid()), cap, new int[1][][]);
    }

    public String solve() {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        int[][][] found = new int[1][][];
        int count = search(masksFrom(givenGrid()), 2, found);
        if (count == 0) {
            return "This board cannot be completed.";
        }
        digits = copy(found[0]);
        return (guesses == 0
                ? "Propagation alone was enough - no guessing."
                : "Propagation stalled, so the solver branched " + guesses + " times.")
                + (count == 1 ? "" : " These clues allow more than one grid.");
    }

    public String hint() {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (digits[row][column] != answer[row][column]) {
                    digits[row][column] = answer[row][column];
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
     * A random Latin square, a scattering of signs read off it, then givens
     * and signs taken away for as long as the answer stays unique.
     */
    public void generate(int requested) {
        if (requested < MIN_SIZE || requested > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Use a size between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        size = requested;
        answer = randomLatinSquare();
        less = new boolean[size * size][size * size];
        edges = new int[0][];
        given = new boolean[size][size];

        // Every adjacent pair is a candidate sign; about half of them start on.
        List<int[]> signs = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (column + 1 < size) {
                    signs.add(new int[] {row, column, row, column + 1});
                }
                if (row + 1 < size) {
                    signs.add(new int[] {row, column, row + 1, column});
                }
            }
        }
        Collections.shuffle(signs, random);
        for (int[] pair : signs) {
            setSign(pair, true);
        }
        for (int row = 0; row < size; row++) {
            Arrays.fill(given[row], true);
        }

        // Givens go first, while every sign is still there to carry the
        // puzzle. Doing it the other way round is a trap: with all the digits
        // still showing, the answer is already pinned and every sign looks
        // redundant, so they all get discarded and what is left is a plain
        // Latin square with no inequalities in it at all.
        List<int[]> cells = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                cells.add(new int[] {row, column});
            }
        }
        Collections.shuffle(cells, random);
        for (int[] cell : cells) {
            given[cell[0]][cell[1]] = false;
            if (countSolutions(2) != 1) {
                given[cell[0]][cell[1]] = true;
            }
        }
        // Only now are the surviving signs load-bearing, so thinning them
        // leaves a board that genuinely needs its inequalities.
        for (int[] pair : signs) {
            setSign(pair, false);
            if (countSolutions(2) != 1) {
                setSign(pair, true);
            }
        }
        digits = givenGrid();
    }

    /**
     * Builds a specific board rather than a random one, for tests and for
     * fixed puzzles. Each entry of {@code signs} is
     * {@code {rowA, columnA, rowB, columnB}} and means A holds less than B.
     */
    public void setPuzzle(int requested, int[][] givens, int[][] signs) {
        if (requested < MIN_SIZE || requested > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Use a size between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        size = requested;
        less = new boolean[size * size][size * size];
        this.given = new boolean[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                this.given[row][column] = givens[row][column] != 0;
            }
        }
        for (int[] sign : signs) {
            less[index(sign[0], sign[1])][index(sign[2], sign[3])] = true;
        }
        rebuildEdges();
        answer = copy(givens);
        digits = copy(givens);
        nodesLeft = NODE_BUDGET;
        int[][][] found = new int[1][][];
        if (search(masksFrom(givens), 1, found) == 1) {
            answer = found[0];
        }
        digits = givenGrid();
    }

    /**
     * The digits still possible in a cell once propagation has run, smallest
     * first. Empty means the board is already contradictory.
     */
    public List<Integer> candidates(int row, int column) {
        int[] masks = masksFrom(givenGrid());
        List<Integer> out = new ArrayList<>();
        if (!propagate(masks)) {
            return out;
        }
        int mask = masks[index(row, column)];
        for (int digit = 1; digit <= size; digit++) {
            if ((mask >> (digit - 1) & 1) == 1) {
                out.add(digit);
            }
        }
        return out;
    }

    private void setSign(int[] pair, boolean on) {
        int a = index(pair[0], pair[1]);
        int b = index(pair[2], pair[3]);
        boolean aIsLess = answer[pair[0]][pair[1]] < answer[pair[2]][pair[3]];
        less[aIsLess ? a : b][aIsLess ? b : a] = on;
        rebuildEdges();
    }

    /** A Latin square by shifting a shuffled first row, then shuffling lines. */
    private int[][] randomLatinSquare() {
        List<Integer> row = new ArrayList<>();
        for (int digit = 1; digit <= size; digit++) {
            row.add(digit);
        }
        Collections.shuffle(row, random);
        int[][] square = new int[size][size];
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                square[r][c] = row.get((c + r) % size);
            }
        }
        // Permuting whole rows, or whole columns, keeps it a Latin square.
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] swap = square[i];
            square[i] = square[j];
            square[j] = swap;
        }
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            for (int[] line : square) {
                int swap = line[i];
                line[i] = line[j];
                line[j] = swap;
            }
        }
        return square;
    }

    private int[][] givenGrid() {
        int[][] grid = new int[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                grid[row][column] = given[row][column] ? answer[row][column] : 0;
            }
        }
        return grid;
    }

    private static int[][] copy(int[][] grid) {
        int[][] out = new int[grid.length][];
        for (int i = 0; i < grid.length; i++) {
            out[i] = grid[i].clone();
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    public void place(int row, int column, int digit) {
        if (row < 0 || row >= size || column < 0 || column >= size) {
            throw new IllegalArgumentException("There is no cell " + row + "," + column + ".");
        }
        if (given[row][column]) {
            throw new IllegalArgumentException("That one was given.");
        }
        if (digit < 0 || digit > size) {
            throw new IllegalArgumentException("Use 1 to " + size + ", or 0 to clear.");
        }
        digits[row][column] = digit;
    }

    public boolean complete() {
        for (int row = 0; row < size; row++) {
            if (!Arrays.equals(digits[row], answer[row])) {
                return false;
            }
        }
        return true;
    }

    /** The first rule the board currently breaks, or an empty string. */
    public String firstFault() {
        for (int line = 0; line < size; line++) {
            for (int a = 0; a < size; a++) {
                for (int b = a + 1; b < size; b++) {
                    if (digits[line][a] != 0 && digits[line][a] == digits[line][b]) {
                        return "Row " + line + " has two " + digits[line][a] + "s.";
                    }
                    if (digits[a][line] != 0 && digits[a][line] == digits[b][line]) {
                        return "Column " + line + " has two " + digits[a][line] + "s.";
                    }
                }
            }
        }
        for (int a = 0; a < size * size; a++) {
            for (int b = 0; b < size * size; b++) {
                if (!less[a][b]) {
                    continue;
                }
                int low = digits[a / size][a % size];
                int high = digits[b / size][b % size];
                if (low != 0 && high != 0 && low > high) {
                    return "The sign between " + (a / size) + "," + (a % size) + " and "
                            + (b / size) + "," + (b % size) + " is the wrong way round.";
                }
            }
        }
        return "";
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * The board interleaved with its signs: cells land on even coordinates and
     * the gaps between them carry the sign, or a space when there is none.
     */
    public char[][] cells() {
        int span = size * 2 - 1;
        char[][] grid = new char[span][span];
        for (char[] line : grid) {
            Arrays.fill(line, ' ');
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int value = digits[row][column];
                grid[row * 2][column * 2] = value == 0 ? '.' : (char) ('0' + value);
                if (column + 1 < size) {
                    grid[row * 2][column * 2 + 1] = horizontalSign(row, column);
                }
                if (row + 1 < size) {
                    grid[row * 2 + 1][column * 2] = verticalSign(row, column);
                }
            }
        }
        return grid;
    }

    private char horizontalSign(int row, int column) {
        if (lessThan(row, column, row, column + 1)) {
            return '<';
        }
        return lessThan(row, column + 1, row, column) ? '>' : ' ';
    }

    private char verticalSign(int row, int column) {
        if (lessThan(row, column, row + 1, column)) {
            return '^';
        }
        return lessThan(row + 1, column, row, column) ? 'v' : ' ';
    }

    public int signCount() {
        int count = 0;
        for (boolean[] line : less) {
            for (boolean sign : line) {
                if (sign) {
                    count++;
                }
            }
        }
        return count;
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
                if (digits[row][column] == 0) {
                    blank++;
                }
                if (given[row][column]) {
                    clues++;
                }
            }
        }
        return String.format("%dx%d, %d givens and %d signs, %d cells still blank.",
                size, size, clues, signCount(), blank);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("board size:", MIN_SIZE, MAX_SIZE, 5));
        io.muted("Each digit once per row and column. < and > point at the smaller "
                + "cell; ^ means the cell above is smaller.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            int choice = io.menu("Futoshiki",
                    List.of("Place a digit", "Hint", "Solve", "New puzzle"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0 -> {
                        int row = io.askInt("row:", 0, size - 1, 0);
                        int column = io.askInt("column:", 0, size - 1, 0);
                        place(row, column, io.askInt("digit (0 clears):", 0, size, 0));
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

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    generate(Json.integer(body, "size", 5));
                    return board("Click a cell to drop in the digit chosen above. "
                            + "Click it again to clear it.");
                }
                case "cycle", "step", "place" -> {
                    // Display coordinates: cells sit on the even ones, the odd
                    // ones are the signs between them and are not clickable.
                    int displayRow = Json.integer(body, "row", -1);
                    int displayColumn = Json.integer(body, "col", -1);
                    if (displayRow < 0 || displayColumn < 0
                            || displayRow % 2 == 1 || displayColumn % 2 == 1) {
                        return board("");
                    }
                    int row = displayRow / 2;
                    int column = displayColumn / 2;
                    int digit = Json.integer(body, "value", 1);
                    place(row, column, digits[row][column] == digit ? 0 : digit);
                    return board(complete() ? "That is the square." : "");
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
