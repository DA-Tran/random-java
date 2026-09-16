package com.randomjava.projects.nurikabe;

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
 * Nurikabe - shade cells into a single wall, leaving numbered islands whose
 * sizes are exactly the numbers written on them.
 *
 * <h2>Two connectivity rules pulling opposite ways</h2>
 *
 * <p>Most grid puzzles have at most one global constraint. Nurikabe has two,
 * and they pull against each other:
 *
 * <ul>
 *   <li>Every shaded cell must belong to one connected wall. Shading too
 *       cautiously leaves the wall in pieces.</li>
 *   <li>Every island must be exactly as big as its number and hold exactly one
 *       number. Shading too freely strangles an island before it can grow.</li>
 * </ul>
 *
 * <p>Neither is visible to local reasoning, and they cannot be checked the same
 * way. The wall test has to be generous - count undecided cells as potentially
 * wall, because they still might be - while the island test has to be strict in
 * one direction and generous in the other: an island's <em>settled</em> cells
 * may not already exceed its number, and the space it could still reach may not
 * be smaller than it. {@link #legal} is those three tests, and the asymmetry
 * between them is the whole reason they prune rather than merely detect a
 * finished board.
 *
 * <h2>The count that comes for free</h2>
 *
 * <p>Island cells total the sum of the numbers, because every island is exactly
 * its number and every island has exactly one. So the wall is always
 * {@code cells - sum} squares, known before a single deduction is made. A board
 * whose shaded count has already passed that total is finished being wrong, and
 * so is one that cannot reach it.
 *
 * <h2>Why the 2x2 rule exists</h2>
 *
 * <p>Without it the wall could be a fat blob and the puzzle would usually have
 * many answers. Forbidding a fully shaded 2x2 forces the wall to stay thin,
 * which is what makes the answer pin down - and it is a local rule, so unlike
 * the other two it costs nothing to check.
 *
 * <h2>Deriving the patterns instead of listing them</h2>
 *
 * <p>Nurikabe is taught with named shapes - a cell between two numbers is wall,
 * an island of 1 is surrounded immediately, a diagonal neighbour of a number
 * cannot be reached. As in {@link com.randomjava.projects.hitori.Hitori}, all
 * of them are the same question: shade the cell, see whether the board
 * contradicts itself, and if it does the cell is island. {@link #propagate}
 * asks that of every undecided cell rather than carrying a rule table.
 */
public final class Nurikabe implements Project {

    public static final Meta META = new Meta(
            237, "nurikabe", "Nurikabe", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Divide a grid into numbered islands separated by one connected wall.",
            "", true);

    /** Not yet decided. */
    public static final char UNKNOWN = '?';
    /** Dry land - part of some numbered island. */
    public static final char ISLAND = '.';
    /** Shaded - part of the wall. */
    public static final char WALL = '#';

    private static final int MIN_SIZE = 5;
    private static final int MAX_SIZE = 7;
    private static final int NODE_BUDGET = 40_000;
    private static final int[][] STEPS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private final Random random;

    private int size;
    private int[][] numbers;
    private char[][] shading;
    private char[][] answer;

    private int nodesLeft;
    private int guesses;

    public Nurikabe() {
        this(new Random());
    }

    public Nurikabe(Random random) {
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

    /** How many cells the wall must end up covering. Known in advance. */
    public int wallTotal() {
        int islands = 0;
        for (int[] line : numbers) {
            for (int value : line) {
                islands += value;
            }
        }
        return size * size - islands;
    }

    // ------------------------------------------------------------------
    // Legality
    // ------------------------------------------------------------------

    /** Cells reachable from a start, moving only through cells that pass. */
    private List<Integer> flood(char[][] grid, int start, char blocked) {
        boolean[] seen = new boolean[size * size];
        Deque<Integer> queue = new ArrayDeque<>();
        List<Integer> found = new ArrayList<>();
        queue.add(start);
        seen[start] = true;
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            found.add(cell);
            int row = cell / size;
            int column = cell % size;
            for (int[] step : STEPS) {
                int nextRow = row + step[0];
                int nextColumn = column + step[1];
                if (nextRow < 0 || nextRow >= size || nextColumn < 0 || nextColumn >= size) {
                    continue;
                }
                int next = nextRow * size + nextColumn;
                if (!seen[next] && grid[nextRow][nextColumn] != blocked) {
                    seen[next] = true;
                    queue.add(next);
                }
            }
        }
        return found;
    }

    /**
     * The three tests. Each is generous about undecided cells in exactly the
     * direction that keeps it a sound prune rather than a guess.
     */
    public boolean legal(char[][] grid) {
        // A fully shaded 2x2 is never allowed - local, and cheap.
        for (int row = 0; row + 1 < size; row++) {
            for (int column = 0; column + 1 < size; column++) {
                if (grid[row][column] == WALL && grid[row + 1][column] == WALL
                        && grid[row][column + 1] == WALL
                        && grid[row + 1][column + 1] == WALL) {
                    return false;
                }
            }
        }

        int shaded = 0;
        int firstWall = -1;
        for (int cell = 0; cell < size * size; cell++) {
            if (grid[cell / size][cell % size] == WALL) {
                shaded++;
                if (firstWall < 0) {
                    firstWall = cell;
                }
            }
        }
        if (shaded > wallTotal()) {
            return false;
        }
        // The wall must still be able to join up. Undecided cells count as
        // passable, because they may yet be shaded - so failing this means no
        // future decision could repair it.
        if (firstWall >= 0) {
            List<Integer> reach = flood(grid, firstWall, ISLAND);
            for (int cell = 0; cell < size * size; cell++) {
                if (grid[cell / size][cell % size] == WALL && !reach.contains(cell)) {
                    return false;
                }
            }
        }

        // Islands: settled size may not exceed the number, reachable space may
        // not fall short of it, and two numbers may not share an island.
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (numbers[row][column] == 0) {
                    continue;
                }
                int start = row * size + column;
                if (grid[row][column] == WALL) {
                    return false;   // a number is island by definition
                }
                // Strict direction: the cells already settled as island around
                // this number may not have overshot it, and may not have
                // swallowed a second number.
                List<Integer> solid = islandOf(grid, start);
                int numbersInside = 0;
                for (int cell : solid) {
                    if (numbers[cell / size][cell % size] != 0) {
                        numbersInside++;
                    }
                }
                if (numbersInside > 1 || solid.size() > numbers[row][column]) {
                    return false;
                }
                // Generous direction: counting undecided cells as still
                // available, is there even room left to reach the number?
                if (flood(grid, start, WALL).size() < numbers[row][column]) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The settled island a cell belongs to: only cells already marked island. */
    private List<Integer> islandOf(char[][] grid, int start) {
        boolean[] seen = new boolean[size * size];
        Deque<Integer> queue = new ArrayDeque<>();
        List<Integer> found = new ArrayList<>();
        queue.add(start);
        seen[start] = true;
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            found.add(cell);
            int row = cell / size;
            int column = cell % size;
            for (int[] step : STEPS) {
                int nextRow = row + step[0];
                int nextColumn = column + step[1];
                if (nextRow < 0 || nextRow >= size || nextColumn < 0 || nextColumn >= size) {
                    continue;
                }
                int next = nextRow * size + nextColumn;
                if (!seen[next] && grid[nextRow][nextColumn] == ISLAND) {
                    seen[next] = true;
                    queue.add(next);
                }
            }
        }
        return found;
    }

    /** True when every rule holds and nothing is left undecided. */
    public boolean solved(char[][] grid) {
        for (char[] line : grid) {
            for (char cell : line) {
                if (cell == UNKNOWN) {
                    return false;
                }
            }
        }
        if (!legal(grid)) {
            return false;
        }
        int shaded = 0;
        for (char[] line : grid) {
            for (char cell : line) {
                if (cell == WALL) {
                    shaded++;
                }
            }
        }
        if (shaded != wallTotal()) {
            return false;
        }
        // Every island must be exactly its number, and every island needs one.
        boolean[] counted = new boolean[size * size];
        for (int cell = 0; cell < size * size; cell++) {
            int row = cell / size;
            int column = cell % size;
            if (grid[row][column] != ISLAND || counted[cell]) {
                continue;
            }
            List<Integer> island = islandOf(grid, cell);
            int wanted = 0;
            int found = 0;
            for (int member : island) {
                counted[member] = true;
                if (numbers[member / size][member % size] != 0) {
                    found++;
                    wanted = numbers[member / size][member % size];
                }
            }
            if (found != 1 || island.size() != wanted) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Propagation and search
    // ------------------------------------------------------------------

    private boolean basics(char[][] grid) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    if (numbers[row][column] != 0 && grid[row][column] == UNKNOWN) {
                        grid[row][column] = ISLAND;
                        changed = true;
                    }
                    if (grid[row][column] != UNKNOWN) {
                        continue;
                    }
                    // A cell touching two different numbered islands is wall.
                    int touching = 0;
                    for (int[] step : STEPS) {
                        int nextRow = row + step[0];
                        int nextColumn = column + step[1];
                        if (nextRow < 0 || nextRow >= size
                                || nextColumn < 0 || nextColumn >= size) {
                            continue;
                        }
                        if (grid[nextRow][nextColumn] == ISLAND) {
                            List<Integer> island = islandOf(grid, nextRow * size + nextColumn);
                            for (int member : island) {
                                if (numbers[member / size][member % size] != 0) {
                                    touching++;
                                    break;
                                }
                            }
                        }
                    }
                    if (touching >= 2) {
                        grid[row][column] = WALL;
                        changed = true;
                    }
                }
            }
            if (!legal(grid)) {
                return false;
            }
        }
        return true;
    }

    /** Ask each undecided cell what would happen if it went each way. */
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
                    char[][] asWall = copy(grid);
                    asWall[row][column] = WALL;
                    boolean wallWorks = basics(asWall);
                    char[][] asIsland = copy(grid);
                    asIsland[row][column] = ISLAND;
                    boolean islandWorks = basics(asIsland);
                    if (!wallWorks && !islandWorks) {
                        return false;
                    }
                    if (!wallWorks) {
                        copyInto(asIsland, grid);
                        changed = true;
                    } else if (!islandWorks) {
                        copyInto(asWall, grid);
                        changed = true;
                    }
                }
            }
        }
        return true;
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
        for (char guess : new char[] {WALL, ISLAND}) {
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
            return "This grid cannot be divided legally.";
        }
        shading = copy(found[0]);
        return (guesses == 0
                ? "The rules alone were enough - no guessing."
                : "Deduction stalled, so the solver branched " + guesses + " times.")
                + (count == 1 ? "" : " This grid allows more than one wall.");
    }

    public String hint() {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                boolean wanted = answer[row][column] == WALL;
                if (wanted != (shading[row][column] == WALL)) {
                    shading[row][column] = wanted ? WALL : UNKNOWN;
                    return "Row " + row + ", column " + column
                            + (wanted ? " is wall." : " is island.");
                }
            }
        }
        return "Nothing left to give away.";
    }

    // ------------------------------------------------------------------
    // Generating
    // ------------------------------------------------------------------

    /**
     * Grow islands first, call everything left the wall, and keep the result
     * only if the wall is connected, has no fat 2x2, and the numbers admit no
     * other division.
     */
    public void generate(int requested) {
        if (requested < MIN_SIZE || requested > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Use a size between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        size = requested;
        // Per-attempt success is low - the wall has to stay connected and free
        // of solid 2x2 at once - but an attempt costs well under a millisecond,
        // so the budget is large rather than the layout being clever.
        for (int attempt = 0; attempt < 6000; attempt++) {
            if (!layout()) {
                continue;
            }
            shading = blankGrid();
            if (countSolutions(2) == 1) {
                return;
            }
        }
        shading = blankGrid();
    }

    private boolean layout() {
        char[][] grid = new char[size][size];
        for (char[] line : grid) {
            Arrays.fill(line, WALL);
        }
        numbers = new int[size][size];
        List<Integer> starts = new ArrayList<>();
        for (int cell = 0; cell < size * size; cell++) {
            starts.add(cell);
        }
        Collections.shuffle(starts, random);

        int placed = 0;
        int budget = size * size * 11 / 20;
        for (int start : starts) {
            if (placed >= budget) {
                break;
            }
            int row = start / size;
            int column = start % size;
            if (grid[row][column] != WALL || touchesIsland(grid, row, column)) {
                continue;
            }
            int wanted = 1 + random.nextInt(Math.min(3, budget - placed));
            List<Integer> island = new ArrayList<>();
            island.add(start);
            grid[row][column] = ISLAND;
            while (island.size() < wanted) {
                List<Integer> frontier = new ArrayList<>();
                for (int cell : island) {
                    for (int[] step : STEPS) {
                        int nextRow = cell / size + step[0];
                        int nextColumn = cell % size + step[1];
                        if (nextRow < 0 || nextRow >= size
                                || nextColumn < 0 || nextColumn >= size) {
                            continue;
                        }
                        if (grid[nextRow][nextColumn] == WALL
                                && !touchesOtherIsland(grid, nextRow, nextColumn, island)) {
                            frontier.add(nextRow * size + nextColumn);
                        }
                    }
                }
                if (frontier.isEmpty()) {
                    break;
                }
                Collections.shuffle(frontier, random);
                int pick = -1;
                for (int candidate : frontier) {
                    grid[candidate / size][candidate % size] = ISLAND;
                    if (wallConnected(grid)) {
                        pick = candidate;
                        break;
                    }
                    grid[candidate / size][candidate % size] = WALL;
                }
                if (pick < 0) {
                    break;   // every way to grow would strand part of the wall
                }
                island.add(pick);
            }
            numbers[row][column] = island.size();
            placed += island.size();
        }
        if (placed == 0) {
            return false;
        }
        if (!thinTheWall(grid, numbers)) {
            return false;
        }
        answer = grid;
        return legal(grid) && solved(grid);
    }

    /**
     * Removes every solid 2x2 from the wall by turning one of its cells into
     * island.
     *
     * <p>This is what makes generation work at all past a 5x5. A wall covering
     * roughly half the board almost always contains a solid 2x2 somewhere, so
     * drawing layouts at random and throwing away the illegal ones threw away
     * essentially all of them - at 7x7 and 8x8, every single attempt. Repairing
     * is cheap and it only ever shrinks the wall, so it terminates.
     *
     * <p>Which cell to convert matters. A cell touching no island becomes a new
     * island of one. A cell touching exactly one island joins it, and that
     * island's number goes up. A cell touching two islands is skipped, because
     * joining them would put two numbers on one island.
     */
    private boolean thinTheWall(char[][] grid, int[][] islandNumbers) {
        for (int guard = 0; guard < size * size; guard++) {
            int[] block = findSolidSquare(grid);
            if (block == null) {
                return true;
            }
            boolean fixed = false;
            for (int corner = 0; corner < 4 && !fixed; corner++) {
                int row = block[0] + corner / 2;
                int column = block[1] + corner % 2;
                List<Integer> heads = adjacentIslands(grid, islandNumbers, row, column);
                if (heads.size() > 1) {
                    continue;   // would merge two islands into one
                }
                grid[row][column] = ISLAND;
                if (!wallConnected(grid)) {
                    grid[row][column] = WALL;
                    continue;
                }
                if (heads.isEmpty()) {
                    islandNumbers[row][column] = 1;
                } else {
                    int head = heads.get(0);
                    islandNumbers[head / size][head % size]++;
                }
                fixed = true;
            }
            if (!fixed) {
                return false;
            }
        }
        return findSolidSquare(grid) == null;
    }

    private int[] findSolidSquare(char[][] grid) {
        for (int row = 0; row + 1 < size; row++) {
            for (int column = 0; column + 1 < size; column++) {
                if (grid[row][column] == WALL && grid[row + 1][column] == WALL
                        && grid[row][column + 1] == WALL
                        && grid[row + 1][column + 1] == WALL) {
                    return new int[] {row, column};
                }
            }
        }
        return null;
    }

    /** The numbered cell of each distinct island touching this cell. */
    private List<Integer> adjacentIslands(char[][] grid, int[][] islandNumbers,
                                          int row, int column) {
        List<Integer> heads = new ArrayList<>();
        for (int[] step : STEPS) {
            int nextRow = row + step[0];
            int nextColumn = column + step[1];
            if (nextRow < 0 || nextRow >= size || nextColumn < 0 || nextColumn >= size) {
                continue;
            }
            if (grid[nextRow][nextColumn] != ISLAND) {
                continue;
            }
            for (int member : islandOf(grid, nextRow * size + nextColumn)) {
                if (islandNumbers[member / size][member % size] != 0 
                        && !heads.contains(member)) {
                    heads.add(member);
                }
            }
        }
        return heads;
    }

    /** Whether every shaded cell still reaches every other through shade. */
    private boolean wallConnected(char[][] grid) {
        int first = -1;
        int shaded = 0;
        for (int cell = 0; cell < size * size; cell++) {
            if (grid[cell / size][cell % size] == WALL) {
                shaded++;
                if (first < 0) {
                    first = cell;
                }
            }
        }
        if (first < 0) {
            return true;
        }
        return flood(grid, first, ISLAND).size() == shaded;
    }

    private boolean touchesIsland(char[][] grid, int row, int column) {
        for (int[] step : STEPS) {
            int nextRow = row + step[0];
            int nextColumn = column + step[1];
            if (nextRow >= 0 && nextRow < size && nextColumn >= 0 && nextColumn < size
                    && grid[nextRow][nextColumn] == ISLAND) {
                return true;
            }
        }
        return false;
    }

    private boolean touchesOtherIsland(char[][] grid, int row, int column,
                                       List<Integer> mine) {
        for (int[] step : STEPS) {
            int nextRow = row + step[0];
            int nextColumn = column + step[1];
            if (nextRow < 0 || nextRow >= size || nextColumn < 0 || nextColumn >= size) {
                continue;
            }
            int next = nextRow * size + nextColumn;
            if (grid[nextRow][nextColumn] == ISLAND && !mine.contains(next)) {
                return true;
            }
        }
        return false;
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

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    public char toggle(int row, int column) {
        if (row < 0 || row >= size || column < 0 || column >= size) {
            throw new IllegalArgumentException("There is no cell " + row + "," + column + ".");
        }
        if (numbers[row][column] != 0) {
            throw new IllegalArgumentException("A numbered cell is always island.");
        }
        shading[row][column] = shading[row][column] == WALL ? UNKNOWN : WALL;
        return shading[row][column];
    }

    public boolean complete() {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if ((answer[row][column] == WALL) != (shading[row][column] == WALL)) {
                    return false;
                }
            }
        }
        return true;
    }

    public int shadedCount() {
        int count = 0;
        for (char[] line : shading) {
            for (char cell : line) {
                if (cell == WALL) {
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
                if (shading[row][column] == WALL) {
                    grid[row][column] = WALL;
                } else if (numbers[row][column] != 0) {
                    grid[row][column] = (char) ('0' + numbers[row][column]);
                } else {
                    grid[row][column] = '.';
                }
            }
        }
        return grid;
    }

    private String detail() {
        if (complete()) {
            return "Solved, with a wall of " + shadedCount() + ".";
        }
        return String.format("%dx%d, the wall must cover %d cells, %d shaded so far.",
                size, size, wallTotal(), shadedCount());
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("board size:", MIN_SIZE, MAX_SIZE, 6));
        io.muted("Shade cells into one connected wall with no solid 2x2, leaving each "
                + "number on an island of exactly that many cells.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            int choice = io.menu("Nurikabe",
                    List.of("Shade or clear a cell", "Hint", "Solve", "New puzzle"));
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
                    return board("Click a cell to shade it. The wall must cover "
                            + wallTotal() + " cells here.");
                }
                case "toggle", "cycle", "step" -> {
                    toggle(Json.integer(body, "row", 0), Json.integer(body, "col", 0));
                    return board(complete() ? "That is the wall." : "");
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
