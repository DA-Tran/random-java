package com.randomjava.projects.nonogram;

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
import java.util.Random;

/**
 * Nonogram - recover a picture from the run lengths written along each row and
 * column.
 *
 * <p>The naive reading is a search over 2^(rows x columns) grids, which is
 * hopeless past a toy board. The useful reading is that a nonogram is not one
 * big constraint but {@code rows + columns} small ones, each of which can be
 * reasoned about exactly, in isolation, and cheaply.
 *
 * <h2>Line solving</h2>
 *
 * <p>Take one line and its clue. A few cells are already known, most are not.
 * Rather than enumerate every placement of the runs - there are
 * {@code C(slack + k, k)} of those, still exponential - ask a smaller question
 * of each cell: <em>is there any valid placement that fills it, and is there
 * any that leaves it blank?</em> Three of the four answers decide something:
 *
 * <ul>
 *   <li>fillable but not blankable - every placement fills it, so it is
 *       filled, full stop;</li>
 *   <li>blankable but not fillable - blank, by the same argument;</li>
 *   <li>neither - the line admits no placement at all, so whatever guess led
 *       here was wrong;</li>
 *   <li>both - genuinely undetermined, and nothing may be written yet.</li>
 * </ul>
 *
 * <p>Both questions fall out of one dynamic program over
 * {@code (position, run index)}: {@link #fitTable} computes whether the tail of
 * the line from some position can hold the runs from some index onwards. That
 * is {@code O(n * k)} states, so a line is settled in time linear in its
 * length instead of exponential in it.
 *
 * <p>The overlap rule every player learns first is a consequence rather than a
 * special case. A row of ten with the single clue eight has three placements;
 * all three cover cells 2 to 7, so those six are forced while the four ends
 * stay open - and the DP finds that without knowing the rule exists.
 *
 * <h2>Why line solving is not the whole story</h2>
 *
 * <p>Sweeping every row and column until nothing changes solves most published
 * puzzles outright, because a person has to be able to follow them and that is
 * how they are built. It is not a decision procedure. Nonogram solving is
 * NP-complete, and propagation is only a fixpoint: it can stall with cells
 * still unknown. So when it stalls, {@link #search} guesses one cell and
 * recurses, with propagation doing the work at every node. Guessing is what
 * makes it complete; propagation is what makes it fast.
 *
 * <p><b>A clue need not describe exactly one picture.</b> The 2x2 board whose
 * four clues are all a single {@code 1} has two solutions, one per diagonal,
 * and no amount of deduction separates them - there is nothing there to
 * deduce. So generating a puzzle is not "draw a picture and read off the
 * clues": it is draw, read off, then <em>verify</em> that those clues admit
 * exactly one grid, by counting solutions up to two and throwing the drawing
 * away if the count gets there. A generator that skips the check ships puzzles
 * whose answer key is only one of several right answers.
 */
public final class Nonogram implements Project {

    public static final Meta META = new Meta(
            228, "nonogram", "Nonogram", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Fill a grid from the run lengths written along each row and column.",
            "", true);

    /** Nothing is known about this cell yet. */
    public static final char UNKNOWN = '.';
    /** The cell is part of a run. */
    public static final char FILLED = '#';
    /** The cell is known to be blank - crossed out, in pencil-and-paper terms. */
    public static final char EMPTY = '*';

    private static final int MIN_SIZE = 5;
    private static final int MAX_SIZE = 20;

    /**
     * How many search nodes a single solve may spend before giving up. Solving
     * is NP-complete, so an unlucky board could otherwise run for a very long
     * time inside a web request. Generation treats exhaustion as "this drawing
     * was a bad one" and moves on to the next.
     */
    private static final int NODE_BUDGET = 300_000;

    private final Random random;

    private int rows;
    private int columns;
    private int[][] rowClues;
    private int[][] columnClues;

    /** What the player has written down. */
    private char[][] cells;
    /** The one grid the clues describe. */
    private char[][] answer;

    private boolean propagationSuffices;
    private int guesses;
    private int nodesLeft;

    public Nonogram() {
        this(new Random());
    }

    public Nonogram(Random random) {
        this.random = random;
        generate(10);
    }

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // Clues
    // ------------------------------------------------------------------

    /** The run lengths of a line: {@code [1,1,0,1,1,1]} becomes {@code {2, 3}}. */
    public static int[] cluesFor(boolean[] line) {
        List<Integer> runs = new ArrayList<>();
        int run = 0;
        for (boolean filled : line) {
            if (filled) {
                run++;
            } else if (run > 0) {
                runs.add(run);
                run = 0;
            }
        }
        if (run > 0) {
            runs.add(run);
        }
        int[] out = new int[runs.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = runs.get(i);
        }
        return out;
    }

    /** How a clue is written in the margin. An empty line is shown as {@code 0}. */
    public static String[] clueText(int[] clue) {
        if (clue.length == 0) {
            return new String[] {"0"};
        }
        String[] out = new String[clue.length];
        for (int i = 0; i < clue.length; i++) {
            out[i] = String.valueOf(clue[i]);
        }
        return out;
    }

    public int rows() {
        return rows;
    }

    public int columns() {
        return columns;
    }

    public int[][] rowClues() {
        return deepCopy(rowClues);
    }

    public int[][] columnClues() {
        return deepCopy(columnClues);
    }

    /** True when the clues alone give the answer, with no guessing needed. */
    public boolean propagationSuffices() {
        return propagationSuffices;
    }

    // ------------------------------------------------------------------
    // The line solver
    // ------------------------------------------------------------------

    /**
     * Whether a run of {@code length} can start at {@code at}.
     *
     * <p>Three things have to hold: it has to fit inside the line, no cell it
     * would cover may be known blank, and the cell immediately after it may not
     * be known filled - otherwise the run would not end where the clue says.
     */
    private static boolean runFits(char[] line, int at, int length) {
        if (at + length > line.length) {
            return false;
        }
        for (int i = at; i < at + length; i++) {
            if (line[i] == EMPTY) {
                return false;
            }
        }
        return at + length == line.length || line[at + length] != FILLED;
    }

    /**
     * {@code fit[p][r]} - can {@code line[p..]} hold {@code runs[r..]}?
     *
     * <p>Filled in backwards so each entry only reads entries already computed.
     * Two ways forward from any state: leave cell p blank and stay on run r, or
     * start run r at p and jump past it plus the blank that has to separate it
     * from the next run.
     */
    private static boolean[][] fitTable(char[] line, int[] runs) {
        int n = line.length;
        int k = runs.length;
        boolean[][] fit = new boolean[n + 2][k + 1];

        // Past the end of the line, the only good state is "every run placed".
        for (int position = n; position <= n + 1; position++) {
            fit[position][k] = true;
        }

        for (int position = n - 1; position >= 0; position--) {
            for (int run = k; run >= 0; run--) {
                boolean possible = line[position] != FILLED && fit[position + 1][run];
                if (!possible && run < k && runFits(line, position, runs[run])) {
                    possible = fit[position + runs[run] + 1][run + 1];
                }
                fit[position][run] = possible;
            }
        }
        return fit;
    }

    /**
     * Writes into a line everything its clue forces, or returns null when the
     * clue cannot be satisfied at all.
     *
     * <p>The backwards table says which states can still reach a valid end. A
     * forward pass then walks only the states reachable from the start
     * <em>through</em> such an end, recording for every cell whether it is
     * filled in some surviving placement and whether it is blank in some
     * other. A cell with only one of the two is forced.
     */
    public static char[] refineLine(char[] line, int[] runs) {
        int n = line.length;
        int k = runs.length;
        boolean[][] fit = fitTable(line, runs);
        if (!fit[0][0]) {
            return null;
        }

        boolean[][] reachable = new boolean[n + 1][k + 1];
        reachable[0][0] = true;
        boolean[] canFill = new boolean[n];
        boolean[] canBlank = new boolean[n];

        for (int position = 0; position < n; position++) {
            for (int run = 0; run <= k; run++) {
                if (!reachable[position][run]) {
                    continue;
                }
                if (line[position] != FILLED && fit[position + 1][run]) {
                    canBlank[position] = true;
                    reachable[position + 1][run] = true;
                }
                if (run < k && runFits(line, position, runs[run])
                        && fit[position + runs[run] + 1][run + 1]) {
                    int after = position + runs[run];
                    for (int i = position; i < after; i++) {
                        canFill[i] = true;
                    }
                    // The cell closing the run is blank, and is consumed here -
                    // the next state starts beyond it.
                    if (after < n) {
                        canBlank[after] = true;
                        reachable[after + 1][run + 1] = true;
                    }
                }
            }
        }

        char[] out = new char[n];
        for (int i = 0; i < n; i++) {
            if (canFill[i] && !canBlank[i]) {
                out[i] = FILLED;
            } else if (canBlank[i] && !canFill[i]) {
                out[i] = EMPTY;
            } else if (!canBlank[i]) {
                return null;   // neither value survives: the line is impossible
            } else {
                out[i] = line[i];
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Propagation and search
    // ------------------------------------------------------------------

    /**
     * Sweeps every row and column until a pass changes nothing. Returns false
     * the moment some line turns out to be unsatisfiable.
     */
    private boolean propagate(char[][] grid) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int row = 0; row < rows; row++) {
                char[] refined = refineLine(grid[row], rowClues[row]);
                if (refined == null) {
                    return false;
                }
                if (!Arrays.equals(grid[row], refined)) {
                    grid[row] = refined;
                    changed = true;
                }
            }
            for (int column = 0; column < columns; column++) {
                char[] line = new char[rows];
                for (int row = 0; row < rows; row++) {
                    line[row] = grid[row][column];
                }
                char[] refined = refineLine(line, columnClues[column]);
                if (refined == null) {
                    return false;
                }
                for (int row = 0; row < rows; row++) {
                    if (grid[row][column] != refined[row]) {
                        grid[row][column] = refined[row];
                        changed = true;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Counts solutions, stopping at {@code cap}, and keeps the first one found.
     *
     * <p>Propagation runs first at every node, so the guessing only ever
     * happens where deduction has genuinely run out. When it leaves no unknown
     * cell the grid <em>is</em> a solution: every line was checked satisfiable
     * on the way, and a fully decided line has exactly one placement to be
     * satisfied by.
     */
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
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
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
        for (char guess : new char[] {FILLED, EMPTY}) {
            char[][] next = copy(grid);
            next[row][column] = guess;
            total += search(next, cap - total, found);
            if (total >= cap) {
                break;
            }
        }
        return total;
    }

    /** How many grids satisfy these clues, counted no further than {@code cap}. */
    public int countSolutions(int cap) {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        return search(blankGrid(), cap, new char[1][][]);
    }

    /** Solves from the clues alone and writes the answer into the board. */
    public String solve() {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        char[][][] found = new char[1][][];
        int count = search(blankGrid(), 2, found);
        if (count == 0) {
            return "These clues have no solution at all.";
        }
        cells = copy(found[0]);
        String how = guesses == 0
                ? "Line propagation alone was enough - no guessing."
                : "Propagation stalled, so the solver branched " + guesses
                  + (guesses == 1 ? " time." : " times.");
        String only = count == 1
                ? " These clues describe exactly one picture."
                : " And these clues describe more than one picture.";
        return how + only;
    }

    /** Fills in one cell the player has not got right yet. */
    public String hint() {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (cells[row][column] != answer[row][column]) {
                    cells[row][column] = answer[row][column];
                    return "Row " + row + ", column " + column + " is "
                           + (answer[row][column] == FILLED ? "filled." : "blank.");
                }
            }
        }
        return "Nothing left to give away.";
    }

    // ------------------------------------------------------------------
    // Generating a puzzle
    // ------------------------------------------------------------------

    /**
     * Draws a picture, reads off its clues and keeps it only if those clues
     * describe nothing else. Most random drawings pass; the ones that do not
     * are discarded rather than shipped with an answer key that is merely one
     * of several right answers.
     */
    public void generate(int size) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Use a size between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        for (int attempt = 0; attempt < 400; attempt++) {
            // Sparse drawings leave too much slack for the clues to pin down,
            // dense ones are trivial. Sweeping the middle finds a unique
            // picture quickly without always asking for the same texture.
            double density = 0.45 + (attempt % 5) * 0.05;
            adopt(randomPicture(size, density));
            nodesLeft = NODE_BUDGET;
            guesses = 0;
            char[][][] found = new char[1][][];
            if (search(blankGrid(), 2, found) == 1) {
                answer = found[0];
                propagationSuffices = guesses == 0;
                return;
            }
        }
        // Astronomically unlikely, and still has to leave a playable board.
        adopt(randomPicture(size, 0.55));
        nodesLeft = NODE_BUDGET;
        char[][][] found = new char[1][][];
        search(blankGrid(), 1, found);
        answer = found[0] != null ? found[0] : pictureGrid();
        propagationSuffices = false;
    }

    /** Builds a puzzle from a drawing, for tests and for fixed boards. */
    public void setPicture(boolean[][] picture) {
        adopt(picture);
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        char[][][] found = new char[1][][];
        search(blankGrid(), 2, found);
        answer = found[0] != null ? found[0] : pictureGrid();
        propagationSuffices = guesses == 0;
    }

    private boolean[][] drawing;

    private boolean[][] randomPicture(int size, double density) {
        boolean[][] picture = new boolean[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                picture[row][column] = random.nextDouble() < density;
            }
        }
        return picture;
    }

    private void adopt(boolean[][] picture) {
        drawing = picture;
        rows = picture.length;
        columns = picture[0].length;
        rowClues = new int[rows][];
        for (int row = 0; row < rows; row++) {
            rowClues[row] = cluesFor(picture[row]);
        }
        columnClues = new int[columns][];
        for (int column = 0; column < columns; column++) {
            boolean[] line = new boolean[rows];
            for (int row = 0; row < rows; row++) {
                line[row] = picture[row][column];
            }
            columnClues[column] = cluesFor(line);
        }
        cells = blankGrid();
    }

    private char[][] pictureGrid() {
        char[][] grid = new char[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                grid[row][column] = drawing[row][column] ? FILLED : EMPTY;
            }
        }
        return grid;
    }

    private char[][] blankGrid() {
        char[][] grid = new char[rows][columns];
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

    private static int[][] deepCopy(int[][] values) {
        int[][] out = new int[values.length][];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i].clone();
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    public char cell(int row, int column) {
        check(row, column);
        return cells[row][column];
    }

    public void mark(int row, int column, char state) {
        check(row, column);
        if (state != UNKNOWN && state != FILLED && state != EMPTY) {
            throw new IllegalArgumentException("A cell is filled, crossed or unknown.");
        }
        cells[row][column] = state;
    }

    /** Unknown to filled to crossed and back, which is what a click does. */
    public char cycle(int row, int column) {
        check(row, column);
        char next = switch (cells[row][column]) {
            case UNKNOWN -> FILLED;
            case FILLED -> EMPTY;
            default -> UNKNOWN;
        };
        cells[row][column] = next;
        return next;
    }

    private void check(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            throw new IllegalArgumentException("There is no cell " + row + "," + column + ".");
        }
    }

    /**
     * True when every filled cell of the answer is filled and no other cell is.
     * Crosses are the player's own bookkeeping and are not checked.
     */
    public boolean complete() {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                boolean wanted = answer[row][column] == FILLED;
                if (wanted != (cells[row][column] == FILLED)) {
                    return false;
                }
            }
        }
        return true;
    }

    private String detail() {
        if (complete()) {
            return "Solved. " + (propagationSuffices
                    ? "This one needed no guessing."
                    : "This one could not be finished by deduction alone.");
        }
        int filled = 0;
        int crossed = 0;
        for (char[] line : cells) {
            for (char cell : line) {
                if (cell == FILLED) {
                    filled++;
                } else if (cell == EMPTY) {
                    crossed++;
                }
            }
        }
        return String.format("%dx%d. %d filled, %d crossed, %d untouched.",
                rows, columns, filled, crossed, rows * columns - filled - crossed);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** How many columns of margin the row clues need. */
    public int gutterColumns() {
        int width = 1;
        for (int[] clue : rowClues) {
            width = Math.max(width, clue.length);
        }
        return width;
    }

    /** How many rows of margin the column clues need. */
    public int gutterRows() {
        int height = 1;
        for (int[] clue : columnClues) {
            height = Math.max(height, clue.length);
        }
        return height;
    }

    /**
     * The board with its clue margins, as text. One grid serves both front
     * ends, so the terminal and the browser cannot drift apart.
     */
    public String[][] display() {
        int marginX = gutterColumns();
        int marginY = gutterRows();
        String[][] out = new String[marginY + rows][marginX + columns];
        for (String[] line : out) {
            Arrays.fill(line, " ");
        }
        for (int column = 0; column < columns; column++) {
            String[] text = clueText(columnClues[column]);
            for (int i = 0; i < text.length; i++) {
                out[marginY - text.length + i][marginX + column] = text[i];
            }
        }
        for (int row = 0; row < rows; row++) {
            String[] text = clueText(rowClues[row]);
            for (int i = 0; i < text.length; i++) {
                out[marginY + row][marginX - text.length + i] = text[i];
            }
        }
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                out[marginY + row][marginX + column] = String.valueOf(cells[row][column]);
            }
        }
        return out;
    }

    private static List<List<String>> asLists(String[][] grid) {
        List<List<String>> out = new ArrayList<>(grid.length);
        for (String[] row : grid) {
            out.add(List.of(row));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("board size:", MIN_SIZE, MAX_SIZE, 10));
        io.muted("# is filled, * is crossed off, . is still unknown.");
        while (true) {
            io.println();
            print(io);
            io.muted(detail());
            int choice = io.menu("Nonogram",
                    List.of("Fill a cell", "Cross a cell", "Clear a cell",
                            "Hint", "Solve", "New puzzle"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0, 1, 2 -> {
                        int row = io.askInt("row:", 0, rows - 1, 0);
                        int column = io.askInt("column:", 0, columns - 1, 0);
                        mark(row, column, choice == 0 ? FILLED : choice == 1 ? EMPTY : UNKNOWN);
                    }
                    case 3 -> io.info(hint());
                    case 4 -> io.info(solve());
                    default -> generate(io.askInt("board size:", MIN_SIZE, MAX_SIZE, rows));
                }
            } catch (RuntimeException e) {
                io.error(e.getMessage());
            }
        }
    }

    /** Right-aligns every cell so two-digit clues keep the columns straight. */
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
                    generate(Json.integer(body, "size", 10));
                    return board(propagationSuffices
                            ? "Solvable by deduction alone."
                            : "This one needs a guess somewhere.");
                }
                case "cycle", "step" -> {
                    // The browser sends coordinates in the displayed grid,
                    // margins and all, so the clue cells get filtered out here
                    // rather than duplicating the layout in JavaScript.
                    int row = Json.integer(body, "row", 0) - gutterRows();
                    int column = Json.integer(body, "col", 0) - gutterColumns();
                    if (row < 0 || column < 0) {
                        return board("");
                    }
                    cycle(row, column);
                    return board(complete() ? "That is the picture." : "");
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
        if (!note.isEmpty()) {
            out.put("result", note);
        }
        return out;
    }
}
