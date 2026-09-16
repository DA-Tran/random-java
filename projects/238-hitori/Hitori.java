package com.randomjava.projects.hitori;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Hitori - shade cells until no number repeats along any row or column, with
 * two conditions on the shading itself: no two shaded cells may touch, and
 * everything left unshaded has to remain in one connected piece.
 *
 * <h2>Stop naming patterns and ask the question</h2>
 *
 * <p>Hitori is usually taught as a list of shapes. Three alike in a row means
 * shade the outer two. A pair of neighbours means every other copy in that line
 * is shaded. {@code x y x} means the middle one is white. There are perhaps a
 * dozen such rules, they are all correct, and learning them is beside the
 * point.
 *
 * <p>Every one of them is an instance of a single question: <em>if this cell
 * were shaded, would the board immediately contradict itself?</em> If so, the
 * cell is white, and no pattern needed naming. {@link #propagate} asks exactly
 * that of every undecided cell, in both directions, until nothing more falls
 * out. The sandwich rule, for instance, comes back as: shade the middle of
 * {@code x y x} and its two neighbours are forced white by the no-touching
 * rule, which leaves two identical whites in one line, which is a
 * contradiction - so the middle is white. The solver derives that in the time
 * it takes to try it, and derives the other eleven rules, and the ones nobody
 * has bothered to name, by the same means.
 *
 * <h2>The constraint that is not local</h2>
 *
 * <p>Connectivity is different in kind from the other two rules. "No number
 * twice in a line" and "no two shaded cells adjacent" are statements about
 * small neighbourhoods; "the white cells form one region" is a statement about
 * the whole board, and no amount of local reasoning sees it coming.
 *
 * <p>It still prunes, and soundly, because of an asymmetry worth noticing:
 * shading only ever removes cells from the white region, never adds them. So
 * take the cells that are <em>not yet shaded</em> - white and undecided
 * together - and flood fill. That is the most generous the white region can
 * ever be from here. If it already comes apart into pieces, no future decision
 * can put it back together, and the position is dead. {@link #connected} is
 * that check, and it is exact rather than a heuristic.
 *
 * <h2>Generating backwards</h2>
 *
 * <p>Making a puzzle is easier than solving one, provided it is done in the
 * right order. Choose the shaded cells first, subject to the two shading rules.
 * Fill the unshaded cells from a Latin square, so they are automatically
 * distinct along every line. Then give each shaded cell a number copied from
 * its own row or column - which is what makes shading it necessary rather than
 * optional. Check the result has one solution, and start over if it does not.
 */
public final class Hitori implements Project {

    public static final Meta META = new Meta(
            238, "hitori", "Hitori", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Black out cells so no number repeats in a line and the rest stay joined.",
            "", true);

    /** Not yet decided. */
    public static final char UNKNOWN = '?';
    /** Left unshaded - this number counts towards its row and column. */
    public static final char WHITE = '.';
    /** Shaded out. */
    public static final char BLACK = '#';

    private static final int MIN_SIZE = 4;
    private static final int MAX_SIZE = 8;
    private static final int NODE_BUDGET = 60_000;

    private final Random random;

    private int size;
    private int[][] numbers;
    private char[][] shading;
    private char[][] answer;

    private int nodesLeft;
    private int guesses;

    public Hitori() {
        this(new Random());
    }

    public Hitori(Random random) {
        this.random = random;
        generate(6);
    }

    @Override
    public Meta meta() {
        return META;
    }

    public int size() {
        return size;
    }

    public int number(int row, int column) {
        return numbers[row][column];
    }

    public char shade(int row, int column) {
        return shading[row][column];
    }

    // ------------------------------------------------------------------
    // The rules
    // ------------------------------------------------------------------

    private static final int[][] STEPS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    /**
     * Whether the cells that are not shaded still form one piece.
     *
     * <p>Undecided cells count as passable, which is what makes this a sound
     * test rather than a guess: shading can only take cells away, so if the
     * most generous region available is already broken, every position below
     * this one is broken too.
     */
    public boolean connected(char[][] grid) {
        int start = -1;
        int open = 0;
        for (int cell = 0; cell < size * size; cell++) {
            if (grid[cell / size][cell % size] != BLACK) {
                open++;
                if (start < 0) {
                    start = cell;
                }
            }
        }
        if (start < 0) {
            return false;   // everything shaded is not a solution, it is a mess
        }
        boolean[] seen = new boolean[size * size];
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start] = true;
        int reached = 0;
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            reached++;
            int row = cell / size;
            int column = cell % size;
            for (int[] step : STEPS) {
                int nextRow = row + step[0];
                int nextColumn = column + step[1];
                if (nextRow < 0 || nextRow >= size || nextColumn < 0 || nextColumn >= size) {
                    continue;
                }
                int next = nextRow * size + nextColumn;
                if (!seen[next] && grid[nextRow][nextColumn] != BLACK) {
                    seen[next] = true;
                    queue.add(next);
                }
            }
        }
        return reached == open;
    }

    /** The two local rules, plus connectivity. False on any contradiction. */
    private boolean legal(char[][] grid) {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (grid[row][column] != BLACK) {
                    continue;
                }
                // No two shaded cells may touch.
                if (row + 1 < size && grid[row + 1][column] == BLACK) {
                    return false;
                }
                if (column + 1 < size && grid[row][column + 1] == BLACK) {
                    return false;
                }
            }
        }
        // No number twice along a line, counting only unshaded cells.
        for (int line = 0; line < size; line++) {
            for (int a = 0; a < size; a++) {
                for (int b = a + 1; b < size; b++) {
                    if (grid[line][a] == WHITE && grid[line][b] == WHITE
                            && numbers[line][a] == numbers[line][b]) {
                        return false;
                    }
                    if (grid[a][line] == WHITE && grid[b][line] == WHITE
                            && numbers[a][line] == numbers[b][line]) {
                        return false;
                    }
                }
            }
        }
        return connected(grid);
    }

    /** The deductions that follow directly, without trying anything. */
    private boolean basics(char[][] grid) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    if (grid[row][column] == BLACK) {
                        // Nothing may touch a shaded cell, so its neighbours
                        // are white.
                        for (int[] step : STEPS) {
                            int nextRow = row + step[0];
                            int nextColumn = column + step[1];
                            if (nextRow < 0 || nextRow >= size
                                    || nextColumn < 0 || nextColumn >= size) {
                                continue;
                            }
                            if (grid[nextRow][nextColumn] == UNKNOWN) {
                                grid[nextRow][nextColumn] = WHITE;
                                changed = true;
                            }
                        }
                    } else if (grid[row][column] == WHITE) {
                        // A white number rules out every copy of itself in its
                        // row and column.
                        for (int other = 0; other < size; other++) {
                            if (other != column && numbers[row][other] == numbers[row][column]
                                    && grid[row][other] == UNKNOWN) {
                                grid[row][other] = BLACK;
                                changed = true;
                            }
                            if (other != row && numbers[other][column] == numbers[row][column]
                                    && grid[other][column] == UNKNOWN) {
                                grid[other][column] = BLACK;
                                changed = true;
                            }
                        }
                    }
                }
            }
            if (!legal(grid)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Everything the rules force, found by asking each undecided cell what
     * would happen if it went each way. A value that contradicts immediately
     * is impossible, so the other one is certain.
     */
    private boolean propagate(char[][] grid) {
        if (!basics(grid)) {
            return false;
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int row = 0; row < size && !changed; row++) {
                for (int column = 0; column < size && !changed; column++) {
                    if (grid[row][column] != UNKNOWN) {
                        continue;
                    }
                    char[][] asBlack = copy(grid);
                    asBlack[row][column] = BLACK;
                    boolean blackWorks = basics(asBlack);
                    char[][] asWhite = copy(grid);
                    asWhite[row][column] = WHITE;
                    boolean whiteWorks = basics(asWhite);
                    if (!blackWorks && !whiteWorks) {
                        return false;
                    }
                    if (!blackWorks) {
                        copyInto(asWhite, grid);
                        changed = true;
                    } else if (!whiteWorks) {
                        copyInto(asBlack, grid);
                        changed = true;
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

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
        for (char guess : new char[] {BLACK, WHITE}) {
            char[][] next = copy(grid);
            next[row][column] = guess;
            total += search(next, cap - total, found);
            if (total >= cap) {
                break;
            }
        }
        return total;
    }

    public int countSolutions(int cap) {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        return search(blankGrid(), cap, new char[1][][]);
    }

    public String solve() {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        char[][][] found = new char[1][][];
        int count = search(blankGrid(), 2, found);
        if (count == 0) {
            return "This grid cannot be shaded legally.";
        }
        shading = copy(found[0]);
        return (guesses == 0
                ? "The rules alone were enough - no guessing."
                : "Deduction stalled, so the solver branched " + guesses + " times.")
                + (count == 1 ? "" : " This grid allows more than one shading.");
    }

    /**
     * What propagation alone can say about a cell, without any searching.
     * Useful for seeing which named pattern the solver has just reinvented.
     */
    public char deduced(int row, int column) {
        char[][] grid = blankGrid();
        if (!propagate(grid)) {
            return UNKNOWN;
        }
        return grid[row][column];
    }

    public String hint() {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                boolean wanted = answer[row][column] == BLACK;
                if (wanted != (shading[row][column] == BLACK)) {
                    shading[row][column] = wanted ? BLACK : UNKNOWN;
                    return "Row " + row + ", column " + column
                            + (wanted ? " is shaded." : " stays as it is.");
                }
            }
        }
        return "Nothing left to give away.";
    }

    // ------------------------------------------------------------------
    // Generating
    // ------------------------------------------------------------------

    /**
     * Shaded cells first, then numbers chosen to justify them. Doing it this
     * way round means the two shading rules hold by construction and only
     * uniqueness has to be tested for.
     */
    public void generate(int requested) {
        if (requested < MIN_SIZE || requested > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Use a size between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        size = requested;
        for (int attempt = 0; attempt < 400; attempt++) {
            char[][] shaded = randomShading();
            numbers = numbersFor(shaded);
            answer = shaded;
            shading = blankGrid();
            if (countSolutions(2) == 1) {
                return;
            }
        }
        shading = blankGrid();
    }

    /**
     * A <em>maximal</em> set of shaded cells: every cell that can legally be
     * shaded is, until none is left.
     *
     * <p>Maximal rather than sparse, and that is the whole trick. Nothing in
     * the rules forbids shading more cells than strictly needed - extra
     * shading only ever removes whites, and removing whites cannot create a
     * duplicate. So a sparse answer is never the only answer: any superset
     * that still avoids touching and still leaves the whites joined is just as
     * legal, and there are usually dozens. Shading greedily until the board is
     * saturated removes that freedom by construction, because there is no
     * superset left to find.
     */
    private char[][] randomShading() {
        char[][] shaded = new char[size][size];
        for (char[] line : shaded) {
            Arrays.fill(line, WHITE);
        }
        List<int[]> cells = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                cells.add(new int[] {row, column});
            }
        }
        Collections.shuffle(cells, random);
        for (int[] cell : cells) {
            int row = cell[0];
            int column = cell[1];
            boolean touches = false;
            for (int[] step : STEPS) {
                int nextRow = row + step[0];
                int nextColumn = column + step[1];
                touches |= nextRow >= 0 && nextRow < size && nextColumn >= 0
                        && nextColumn < size && shaded[nextRow][nextColumn] == BLACK;
            }
            if (touches) {
                continue;
            }
            shaded[row][column] = BLACK;
            if (!connected(shaded)) {
                shaded[row][column] = WHITE;   // that one would have split the board
            }
        }
        return shaded;
    }

    /**
     * White cells take a Latin square, so they are distinct along every line
     * for free. Each shaded cell then copies a number that already appears, as
     * a white, <em>both</em> in its row and in its column.
     *
     * <p>Duplicating in one direction only is the obvious thing to do and it
     * produces puzzles with several answers almost every time. The reason is
     * symmetry: if a shaded cell merely repeats some white further along its
     * row, then shading that white instead resolves the clash equally well,
     * and the puzzle cannot tell the two apart. Repeating in both directions
     * breaks the tie - leaving the cell white would then need its row partner
     * <em>and</em> its column partner shaded, which the no-touching and
     * connectivity rules almost never allow.
     */
    private int[][] numbersFor(char[][] shaded) {
        int[][] grid = randomLatinSquare();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (shaded[row][column] != BLACK) {
                    continue;
                }
                List<Integer> both = new ArrayList<>();
                List<Integer> either = new ArrayList<>();
                for (int value = 1; value <= size; value++) {
                    if (value == grid[row][column]) {
                        continue;
                    }
                    boolean inRow = false;
                    boolean inColumn = false;
                    for (int other = 0; other < size; other++) {
                        if (other != column && shaded[row][other] != BLACK
                                && grid[row][other] == value) {
                            inRow = true;
                        }
                        if (other != row && shaded[other][column] != BLACK
                                && grid[other][column] == value) {
                            inColumn = true;
                        }
                    }
                    if (inRow && inColumn) {
                        both.add(value);
                    } else if (inRow || inColumn) {
                        either.add(value);
                    }
                }
                List<Integer> options = both.isEmpty() ? either : both;
                if (!options.isEmpty()) {
                    grid[row][column] = options.get(random.nextInt(options.size()));
                }
            }
        }
        return grid;
    }

    private int[][] randomLatinSquare() {
        List<Integer> row = new ArrayList<>();
        for (int value = 1; value <= size; value++) {
            row.add(value);
        }
        Collections.shuffle(row, random);
        int[][] square = new int[size][size];
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                square[r][c] = row.get((c + r) % size);
            }
        }
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] swap = square[i];
            square[i] = square[j];
            square[j] = swap;
        }
        return square;
    }

    /** Builds a specific grid, for tests and for fixed puzzles. */
    public void setPuzzle(int[][] grid) {
        size = grid.length;
        numbers = copyNumbers(grid);
        shading = blankGrid();
        nodesLeft = NODE_BUDGET;
        char[][][] found = new char[1][][];
        answer = search(blankGrid(), 1, found) == 1 ? found[0] : blankGrid();
        shading = blankGrid();
    }

    private char[][] blankGrid() {
        char[][] grid = new char[size][size];
        for (char[] line : grid) {
            Arrays.fill(line, UNKNOWN);
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

    private static void copyInto(char[][] from, char[][] to) {
        for (int i = 0; i < from.length; i++) {
            System.arraycopy(from[i], 0, to[i], 0, from[i].length);
        }
    }

    private static int[][] copyNumbers(int[][] grid) {
        int[][] out = new int[grid.length][];
        for (int i = 0; i < grid.length; i++) {
            out[i] = grid[i].clone();
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    /** A click shades a cell, or unshades one already shaded. */
    public char toggle(int row, int column) {
        if (row < 0 || row >= size || column < 0 || column >= size) {
            throw new IllegalArgumentException("There is no cell " + row + "," + column + ".");
        }
        shading[row][column] = shading[row][column] == BLACK ? UNKNOWN : BLACK;
        return shading[row][column];
    }

    public boolean complete() {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if ((answer[row][column] == BLACK) != (shading[row][column] == BLACK)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The first rule the player's shading currently breaks, or nothing. */
    public String firstFault() {
        char[][] asPlayed = playedGrid();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (asPlayed[row][column] != BLACK) {
                    continue;
                }
                if (row + 1 < size && asPlayed[row + 1][column] == BLACK) {
                    return "Two shaded cells touch at " + row + "," + column + ".";
                }
                if (column + 1 < size && asPlayed[row][column + 1] == BLACK) {
                    return "Two shaded cells touch at " + row + "," + column + ".";
                }
            }
        }
        for (int line = 0; line < size; line++) {
            for (int a = 0; a < size; a++) {
                for (int b = a + 1; b < size; b++) {
                    if (asPlayed[line][a] == WHITE && asPlayed[line][b] == WHITE
                            && numbers[line][a] == numbers[line][b]) {
                        return "Row " + line + " still shows two " + numbers[line][a] + "s.";
                    }
                    if (asPlayed[a][line] == WHITE && asPlayed[b][line] == WHITE
                            && numbers[a][line] == numbers[b][line]) {
                        return "Column " + line + " still shows two " + numbers[a][line] + "s.";
                    }
                }
            }
        }
        if (!connected(asPlayed)) {
            return "The unshaded cells are split into separate regions.";
        }
        return "";
    }

    /** What the player has drawn, with everything unshaded treated as white. */
    private char[][] playedGrid() {
        char[][] grid = new char[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                grid[row][column] = shading[row][column] == BLACK ? BLACK : WHITE;
            }
        }
        return grid;
    }

    public int shadedCount() {
        int count = 0;
        for (char[] line : shading) {
            for (char cell : line) {
                if (cell == BLACK) {
                    count++;
                }
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public char[][] cells() {
        char[][] grid = new char[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                grid[row][column] = shading[row][column] == BLACK
                        ? BLACK : (char) ('0' + numbers[row][column]);
            }
        }
        return grid;
    }

    private String detail() {
        if (complete()) {
            return "Solved, with " + shadedCount() + " cells shaded.";
        }
        String fault = firstFault();
        if (!fault.isEmpty()) {
            return fault;
        }
        return String.format("%dx%d, %d shaded so far.", size, size, shadedCount());
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("board size:", MIN_SIZE, MAX_SIZE, 6));
        io.muted("Shade cells until no number repeats along a row or column. "
                + "Shaded cells may not touch, and the rest must stay joined up.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            int choice = io.menu("Hitori",
                    List.of("Shade or unshade a cell", "Hint", "Solve", "New puzzle"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0 -> {
                        int row = io.askInt("row:", 0, size - 1, 0);
                        toggle(row, io.askInt("column:", 0, size - 1, 0));
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
                    generate(Json.integer(body, "size", 6));
                    return board("Click a cell to shade it, and again to clear it.");
                }
                case "cycle", "step", "toggle" -> {
                    toggle(Json.integer(body, "row", 0), Json.integer(body, "col", 0));
                    return board(complete() ? "That is the shading." : "");
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
