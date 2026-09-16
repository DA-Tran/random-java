package com.randomjava.projects.slitherlink;

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
 * Slitherlink - draw one closed loop along the grid lines so that every number
 * has exactly that many of its four sides used.
 *
 * <h2>Stop thinking about edges</h2>
 *
 * <p>The obvious model is the one the puzzle is drawn in: a variable per edge,
 * with every lattice point needing degree zero or two, and the used edges
 * forming a single cycle. It works, and it is unpleasant - the degree rule is
 * fiddly, and "one cycle rather than several" is awkward to test on a partial
 * board.
 *
 * <p>There is a better model. A closed loop divides the plane into an inside
 * and an outside, so instead of asking which edges are used, ask <b>which cells
 * are inside it</b>. Then:
 *
 * <ul>
 *   <li>An edge is used exactly when it separates an inside cell from an
 *       outside one. The loop is the boundary of the inside region, and never
 *       has to be represented at all.</li>
 *   <li>A clue is satisfied when that many of the cell's four neighbours differ
 *       from it. Cells off the edge of the board count as outside, which is
 *       what makes border clues work with no special case.</li>
 *   <li>The loop is <em>single</em> and <em>closed</em> exactly when the inside
 *       region is connected and the outside region is connected. Two separate
 *       loops means two inside regions; a loop nested in another means the
 *       outside is split in two.</li>
 * </ul>
 *
 * <p>So the whole puzzle becomes: two-colour the cells, subject to per-cell
 * counting rules and two connectivity constraints. That is the same shape as
 * {@link com.randomjava.projects.nurikabe.Nurikabe}, and it reuses the same
 * machinery - propagate what is forced, check connectivity generously so it
 * prunes rather than merely detects, and guess when deduction stalls.
 *
 * <h2>What that buys</h2>
 *
 * <p>Generation becomes almost trivial. Under the edge model, drawing a random
 * valid loop is a real problem in itself. Under this one, <b>any connected
 * region whose complement is also connected already is a loop</b> - grow a
 * blob, count each cell's differing neighbours, and the clues are read off. No
 * loop ever has to be constructed or checked for self-intersection, because
 * nothing in the representation can express an invalid one.
 */
public final class Slitherlink implements Project {

    public static final Meta META = new Meta(
            235, "slitherlink", "Slitherlink", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Draw a single closed loop so each number has that many edges around it.",
            "", true);

    /** Not yet decided. */
    public static final char UNKNOWN = '?';
    /** Inside the loop. */
    public static final char INSIDE = '#';
    /** Outside the loop. */
    public static final char OUTSIDE = '.';

    private static final int MIN_SIZE = 4;
    private static final int MAX_SIZE = 7;
    private static final int NODE_BUDGET = 40_000;
    private static final int[][] STEPS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private final Random random;

    private int size;
    /** Clue per cell, or -1 for none. */
    private int[][] clues;
    private char[][] region;
    private char[][] answer;

    private int nodesLeft;
    private int guesses;

    public Slitherlink() {
        this(new Random());
    }

    public Slitherlink(Random random) {
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

    public int clue(int row, int column) {
        return clues[row][column];
    }

    public char side(int row, int column) {
        return region[row][column];
    }

    public int clueCount() {
        int count = 0;
        for (int[] line : clues) {
            for (int value : line) {
                if (value >= 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Anything off the board is outside the loop, which is why borders work. */
    private char at(char[][] grid, int row, int column) {
        if (row < 0 || row >= size || column < 0 || column >= size) {
            return OUTSIDE;
        }
        return grid[row][column];
    }

    /** How many of a cell's four sides lie on the loop. */
    public int edgesAround(char[][] grid, int row, int column) {
        int used = 0;
        char mine = at(grid, row, column);
        for (int[] step : STEPS) {
            if (at(grid, row + step[0], column + step[1]) != mine) {
                used++;
            }
        }
        return used;
    }

    // ------------------------------------------------------------------
    // Legality
    // ------------------------------------------------------------------

    /**
     * Whether a position is still viable. Clue counts are bounded from both
     * sides using what is undecided, so a cell that has already exceeded its
     * number fails immediately rather than at the end.
     */
    public boolean legal(char[][] grid) {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int clue = clues[row][column];
                if (clue < 0 || grid[row][column] == UNKNOWN) {
                    continue;
                }
                int differing = 0;
                int undecided = 0;
                char mine = grid[row][column];
                for (int[] step : STEPS) {
                    char other = at(grid, row + step[0], column + step[1]);
                    if (other == UNKNOWN) {
                        undecided++;
                    } else if (other != mine) {
                        differing++;
                    }
                }
                if (differing > clue || differing + undecided < clue) {
                    return false;
                }
            }
        }
        return connectedEnough(grid, INSIDE) && connectedEnough(grid, OUTSIDE);
    }

    /**
     * Whether every settled cell of one side can still reach every other,
     * travelling through its own side or through undecided cells. Generous
     * about the undecided, so failing it means no future choice could repair
     * the split.
     */
    private boolean connectedEnough(char[][] grid, char side) {
        int start = -1;
        int total = 0;
        for (int cell = 0; cell < size * size; cell++) {
            if (grid[cell / size][cell % size] == side) {
                total++;
                if (start < 0) {
                    start = cell;
                }
            }
        }
        if (start < 0) {
            return true;
        }
        boolean[] seen = new boolean[size * size];
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start] = true;
        int reached = 0;
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            if (grid[cell / size][cell % size] == side) {
                reached++;
            }
            int row = cell / size;
            int column = cell % size;
            for (int[] step : STEPS) {
                int nextRow = row + step[0];
                int nextColumn = column + step[1];
                if (nextRow < 0 || nextRow >= size || nextColumn < 0 || nextColumn >= size) {
                    continue;
                }
                int next = nextRow * size + nextColumn;
                char there = grid[nextRow][nextColumn];
                if (!seen[next] && (there == side || there == UNKNOWN)) {
                    seen[next] = true;
                    queue.add(next);
                }
            }
        }
        return reached == total;
    }

    /** A finished, correct board: clues exact, one inside region, one outside. */
    public boolean solved(char[][] grid) {
        for (char[] line : grid) {
            for (char cell : line) {
                if (cell == UNKNOWN) {
                    return false;
                }
            }
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (clues[row][column] >= 0
                        && edgesAround(grid, row, column) != clues[row][column]) {
                    return false;
                }
            }
        }
        // A loop needs something inside it, and both sides in one piece.
        return countOf(grid, INSIDE) > 0 && oneRegion(grid, INSIDE) && oneRegion(grid, OUTSIDE);
    }

    private int countOf(char[][] grid, char side) {
        int count = 0;
        for (char[] line : grid) {
            for (char cell : line) {
                if (cell == side) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Strictly one connected piece, travelling only through that side. */
    private boolean oneRegion(char[][] grid, char side) {
        int start = -1;
        int total = 0;
        for (int cell = 0; cell < size * size; cell++) {
            if (grid[cell / size][cell % size] == side) {
                total++;
                if (start < 0) {
                    start = cell;
                }
            }
        }
        if (start < 0) {
            return side == INSIDE ? false : true;
        }
        boolean[] seen = new boolean[size * size];
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start] = true;
        int reached = 0;
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            reached++;
            for (int[] step : STEPS) {
                int nextRow = cell / size + step[0];
                int nextColumn = cell % size + step[1];
                if (nextRow < 0 || nextRow >= size || nextColumn < 0 || nextColumn >= size) {
                    continue;
                }
                int next = nextRow * size + nextColumn;
                if (!seen[next] && grid[nextRow][nextColumn] == side) {
                    seen[next] = true;
                    queue.add(next);
                }
            }
        }
        return reached == total;
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    private int search(char[][] grid, int cap, char[][][] found) {
        if (cap <= 0 || nodesLeft-- <= 0) {
            return 0;
        }
        if (!legal(grid)) {
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
            if (!solved(grid)) {
                return 0;
            }
            if (found[0] == null) {
                found[0] = copy(grid);
            }
            return 1;
        }
        guesses++;
        int total = 0;
        for (char guess : new char[] {OUTSIDE, INSIDE}) {
            char[][] next = copy(grid);
            next[row][column] = guess;
            total += search(next, cap - total, found);
            if (total >= cap) {
                break;
            }
        }
        return total;
    }

    /**
     * Counts solutions. The board is fixed by its clues up to nothing at all -
     * swapping inside for outside is not a second answer, because the cells
     * beyond the border are outside by definition and pin the colouring down.
     */
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
            return "These numbers admit no loop at all.";
        }
        region = copy(found[0]);
        return (guesses == 0
                ? "Forced throughout - no guessing."
                : "Deduction stalled, so the solver branched " + guesses + " times.")
                + (count == 1 ? "" : " These numbers allow more than one loop.");
    }

    public String hint() {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (region[row][column] != answer[row][column]) {
                    region[row][column] = answer[row][column];
                    return "Row " + row + ", column " + column + " is "
                            + (answer[row][column] == INSIDE ? "inside" : "outside")
                            + " the loop.";
                }
            }
        }
        return "Nothing left to give away.";
    }

    // ------------------------------------------------------------------
    // Generating
    // ------------------------------------------------------------------

    /**
     * Grow a blob, read the clues off it, then drop clues while the loop stays
     * the only one. Nothing checks for a valid loop because nothing can build
     * an invalid one: the region is kept connected and its complement too, and
     * that is exactly what a single closed loop is.
     */
    public void generate(int requested) {
        if (requested < MIN_SIZE || requested > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Use a size between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        size = requested;
        for (int attempt = 0; attempt < 400; attempt++) {
            answer = randomRegion();
            if (answer == null) {
                continue;
            }
            clues = new int[size][size];
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    clues[row][column] = edgesAround(answer, row, column);
                }
            }
            if (countSolutions(2) != 1) {
                continue;
            }
            // Thin the clues while the loop stays pinned down.
            List<int[]> order = new ArrayList<>();
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    order.add(new int[] {row, column});
                }
            }
            Collections.shuffle(order, random);
            for (int[] cell : order) {
                int held = clues[cell[0]][cell[1]];
                clues[cell[0]][cell[1]] = -1;
                if (countSolutions(2) != 1) {
                    clues[cell[0]][cell[1]] = held;
                }
            }
            region = blankGrid();
            return;
        }
        region = blankGrid();
    }

    /** A connected blob whose complement is also connected, or null. */
    private char[][] randomRegion() {
        char[][] grid = new char[size][size];
        for (char[] line : grid) {
            Arrays.fill(line, OUTSIDE);
        }
        // Start away from the border so the outside always wraps right round.
        int row = 1 + random.nextInt(Math.max(1, size - 2));
        int column = 1 + random.nextInt(Math.max(1, size - 2));
        grid[row][column] = INSIDE;
        int wanted = Math.max(2, size * size / 3);
        for (int placed = 1; placed < wanted; placed++) {
            List<int[]> frontier = new ArrayList<>();
            for (int r = 1; r < size - 1; r++) {
                for (int c = 1; c < size - 1; c++) {
                    if (grid[r][c] != OUTSIDE) {
                        continue;
                    }
                    for (int[] step : STEPS) {
                        if (at(grid, r + step[0], c + step[1]) == INSIDE) {
                            frontier.add(new int[] {r, c});
                            break;
                        }
                    }
                }
            }
            Collections.shuffle(frontier, random);
            boolean grew = false;
            for (int[] cell : frontier) {
                grid[cell[0]][cell[1]] = INSIDE;
                if (oneRegion(grid, OUTSIDE)) {
                    grew = true;
                    break;
                }
                grid[cell[0]][cell[1]] = OUTSIDE;
            }
            if (!grew) {
                break;
            }
        }
        return oneRegion(grid, INSIDE) && oneRegion(grid, OUTSIDE)
                && countOf(grid, INSIDE) >= 2 ? grid : null;
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

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    public char cycle(int row, int column) {
        if (row < 0 || row >= size || column < 0 || column >= size) {
            throw new IllegalArgumentException("There is no cell " + row + "," + column + ".");
        }
        region[row][column] = switch (region[row][column]) {
            case UNKNOWN -> INSIDE;
            case INSIDE -> OUTSIDE;
            default -> UNKNOWN;
        };
        return region[row][column];
    }

    public boolean complete() {
        for (int row = 0; row < size; row++) {
            if (!Arrays.equals(region[row], answer[row])) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Clues stay visible; a cell marked inside the loop is shaded. */
    public String[][] display() {
        String[][] grid = new String[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                String clue = clues[row][column] >= 0
                        ? String.valueOf(clues[row][column]) : "";
                grid[row][column] = region[row][column] == INSIDE
                        ? (clue.isEmpty() ? "#" : "#" + clue)
                        : (clue.isEmpty() ? "." : clue);
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
            return "Solved - one closed loop.";
        }
        return String.format("%dx%d, %d numbers. Mark cells inside the loop; a number "
                + "counts how many of its sides the loop uses.", size, size, clueCount());
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("board size:", MIN_SIZE, MAX_SIZE, 6));
        io.muted("Mark each cell inside or outside the loop. A number says how many "
                + "of its four sides separate it from a cell on the other side.");
        while (true) {
            io.println();
            print(io);
            io.muted(detail());
            int choice = io.menu("Slitherlink",
                    List.of("Toggle a cell", "Hint", "Solve", "New puzzle"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0 -> {
                        int row = io.askInt("row:", 0, size - 1, 0);
                        cycle(row, io.askInt("column:", 0, size - 1, 0));
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

    private void print(ConsoleUI io) {
        for (String[] row : display()) {
            StringBuilder line = new StringBuilder("  ");
            for (String cell : row) {
                line.append(" ".repeat(Math.max(0, 2 - cell.length()))).append(cell).append(' ');
            }
            io.println(line.toString());
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    generate(Json.integer(body, "size", 6));
                    return board("Click a cell to mark it inside the loop.");
                }
                case "cycle", "step", "toggle" -> {
                    cycle(Json.integer(body, "row", 0), Json.integer(body, "col", 0));
                    return board(complete() ? "That is the loop." : "");
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
