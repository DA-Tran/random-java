package com.randomjava.projects.skyscrapers;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Skyscrapers - a Latin square of building heights, where each clue around the
 * edge says how many buildings are visible looking in from that side. A
 * building is visible when nothing taller stands in front of it.
 *
 * <h2>Lines are permutations, so enumerate them</h2>
 *
 * <p>Every row and column holds each height exactly once, so a line is a
 * permutation of {@code 1..n} and there are only {@code n!} of them - 720 at
 * size six. {@link #permutations} builds that list once per size and tags each
 * with how many buildings are visible from the left and from the right. After
 * that, solving a line is filtering: keep the permutations whose two counts
 * match the clues and whose digits match the cells already known, then look at
 * what the survivors agree on. Same move as every other line puzzle here, and
 * as usual the intersection is by definition every deduction a single line can
 * support.
 *
 * <h2>Clues are wildly uneven, and that is the whole game</h2>
 *
 * <p>A building is visible exactly when it is taller than everything to its
 * left - a <em>left-to-right maximum</em>, or record. The number of
 * permutations of {@code n} with exactly {@code k} records is the unsigned
 * Stirling number of the first kind, and those numbers are not remotely flat.
 * At {@code n = 5} the five clue values occur in 24, 50, 35, 10 and 1 of the
 * 120 permutations.
 *
 * <p>Read the ends of that row. A clue of {@code n} happens in exactly one
 * permutation, the strictly increasing one, so it settles an entire line on
 * sight and needs no search at all. A clue of {@code 1} says the tallest
 * building comes first, which fixes one cell immediately. The middle values
 * are the ones that carry almost no information - a clue of 2 on a line of
 * five leaves 50 possibilities standing. That skew is why generated puzzles
 * keep their extreme clues and shed their middling ones: {@link #generate}
 * does not know any of this, but carving clues away while the answer stays
 * unique arrives at the same place.
 *
 * <h2>And then the search</h2>
 *
 * <p>Line filtering plus the Latin square rules is still not complete, so when
 * it stalls the solver branches on the most constrained cell. By then there is
 * usually very little left to branch on.
 */
public final class Skyscrapers implements Project {

    public static final Meta META = new Meta(
            240, "skyscrapers", "Skyscrapers", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Place building heights so each edge clue counts the skyline visible from it.",
            "", true);

    private static final int MIN_SIZE = 4;
    private static final int MAX_SIZE = 6;
    private static final int NODE_BUDGET = 200_000;

    /** Permutations by size, with their visibility counts. Built once. */
    private static final Map<Integer, int[][]> PERMUTATION_CACHE = new HashMap<>();

    private final Random random;

    private int size;
    private int[][] heights;
    private int[][] answer;
    /** Clues read clockwise from the top-left: top, right, bottom, left. */
    private int[] top;
    private int[] bottom;
    private int[] leftSide;
    private int[] rightSide;

    private int nodesLeft;
    private int guesses;

    public Skyscrapers() {
        this(new Random());
    }

    public Skyscrapers(Random random) {
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

    public int height(int row, int column) {
        return heights[row][column];
    }

    public int topClue(int column) {
        return top[column];
    }

    public int bottomClue(int column) {
        return bottom[column];
    }

    public int leftClue(int row) {
        return leftSide[row];
    }

    public int rightClue(int row) {
        return rightSide[row];
    }

    public int clueCount() {
        int count = 0;
        for (int[] side : new int[][] {top, bottom, leftSide, rightSide}) {
            for (int clue : side) {
                if (clue != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Permutations and visibility
    // ------------------------------------------------------------------

    /** How many buildings are visible looking along this line from the start. */
    public static int visible(int[] line) {
        int count = 0;
        int tallest = 0;
        for (int height : line) {
            if (height > tallest) {
                tallest = height;
                count++;
            }
        }
        return count;
    }

    /**
     * Every permutation of {@code 1..n}, each followed by how many buildings
     * it shows from the left and from the right.
     */
    public static int[][] permutations(int n) {
        return PERMUTATION_CACHE.computeIfAbsent(n, size -> {
            List<int[]> out = new ArrayList<>();
            int[] line = new int[size];
            for (int i = 0; i < size; i++) {
                line[i] = i + 1;
            }
            permute(line, 0, out);
            int[][] table = new int[out.size()][];
            for (int i = 0; i < table.length; i++) {
                int[] order = out.get(i);
                int[] reversed = new int[size];
                for (int j = 0; j < size; j++) {
                    reversed[j] = order[size - 1 - j];
                }
                int[] row = Arrays.copyOf(order, size + 2);
                row[size] = visible(order);
                row[size + 1] = visible(reversed);
                table[i] = row;
            }
            return table;
        });
    }

    private static void permute(int[] line, int at, List<int[]> out) {
        if (at == line.length) {
            out.add(line.clone());
            return;
        }
        for (int i = at; i < line.length; i++) {
            swap(line, at, i);
            permute(line, at + 1, out);
            swap(line, at, i);
        }
    }

    private static void swap(int[] line, int a, int b) {
        int held = line[a];
        line[a] = line[b];
        line[b] = held;
    }

    /**
     * Narrows one line to what the two clues and the known cells allow, or
     * returns null when no permutation survives.
     *
     * <p>{@code masks[i]} has bit {@code d-1} set while height {@code d} is
     * still possible at position {@code i}. A clue of zero means no clue.
     */
    public static int[] refineLine(int[] masks, int fromStart, int fromEnd) {
        int n = masks.length;
        int[] agreed = new int[n];
        boolean any = false;
        for (int[] candidate : permutations(n)) {
            if (fromStart != 0 && candidate[n] != fromStart) {
                continue;
            }
            if (fromEnd != 0 && candidate[n + 1] != fromEnd) {
                continue;
            }
            boolean fits = true;
            for (int i = 0; i < n && fits; i++) {
                fits = (masks[i] >> (candidate[i] - 1) & 1) == 1;
            }
            if (!fits) {
                continue;
            }
            any = true;
            for (int i = 0; i < n; i++) {
                agreed[i] |= 1 << (candidate[i] - 1);
            }
        }
        return any ? agreed : null;
    }

    // ------------------------------------------------------------------
    // Propagation and search
    // ------------------------------------------------------------------

    private int[][] masksFrom(int[][] grid) {
        int full = (1 << size) - 1;
        int[][] masks = new int[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                masks[row][column] = grid[row][column] == 0
                        ? full : 1 << (grid[row][column] - 1);
            }
        }
        return masks;
    }

    private boolean propagate(int[][] masks) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int row = 0; row < size; row++) {
                int[] refined = refineLine(masks[row], leftSide[row], rightSide[row]);
                if (refined == null) {
                    return false;
                }
                if (!Arrays.equals(masks[row], refined)) {
                    masks[row] = refined;
                    changed = true;
                }
            }
            for (int column = 0; column < size; column++) {
                int[] line = new int[size];
                for (int row = 0; row < size; row++) {
                    line[row] = masks[row][column];
                }
                int[] refined = refineLine(line, top[column], bottom[column]);
                if (refined == null) {
                    return false;
                }
                for (int row = 0; row < size; row++) {
                    if (masks[row][column] != refined[row]) {
                        masks[row][column] = refined[row];
                        changed = true;
                    }
                }
            }
        }
        return true;
    }

    private int search(int[][] masks, int cap, int[][][] found) {
        if (cap <= 0 || nodesLeft-- <= 0) {
            return 0;
        }
        if (!propagate(masks)) {
            return 0;
        }
        int pickRow = -1;
        int pickColumn = -1;
        int fewest = Integer.MAX_VALUE;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int options = Integer.bitCount(masks[row][column]);
                if (options > 1 && options < fewest) {
                    fewest = options;
                    pickRow = row;
                    pickColumn = column;
                }
            }
        }
        if (pickRow < 0) {
            if (found[0] == null) {
                found[0] = gridOf(masks);
            }
            return 1;
        }
        guesses++;
        int total = 0;
        for (int digit = 0; digit < size; digit++) {
            if ((masks[pickRow][pickColumn] >> digit & 1) == 0) {
                continue;
            }
            int[][] next = copy(masks);
            next[pickRow][pickColumn] = 1 << digit;
            total += search(next, cap - total, found);
            if (total >= cap) {
                break;
            }
        }
        return total;
    }

    private int[][] gridOf(int[][] masks) {
        int[][] grid = new int[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                grid[row][column] = Integer.numberOfTrailingZeros(masks[row][column]) + 1;
            }
        }
        return grid;
    }

    public int countSolutions(int cap) {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        return search(masksFrom(new int[size][size]), cap, new int[1][][]);
    }

    public String solve() {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        int[][][] found = new int[1][][];
        int count = search(masksFrom(new int[size][size]), 2, found);
        if (count == 0) {
            return "These clues cannot be satisfied.";
        }
        heights = copy(found[0]);
        return (guesses == 0
                ? "The clues alone were enough - no guessing."
                : "Propagation stalled, so the solver branched " + guesses + " times.")
                + (count == 1 ? "" : " These clues allow more than one skyline.");
    }

    public String hint() {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (heights[row][column] != answer[row][column]) {
                    heights[row][column] = answer[row][column];
                    return "Row " + row + ", column " + column + " is "
                            + answer[row][column] + " storeys.";
                }
            }
        }
        return "Nothing left to give away.";
    }

    // ------------------------------------------------------------------
    // Generating
    // ------------------------------------------------------------------

    /**
     * A random Latin square, all four sides of clues read off it, then clues
     * taken away for as long as the skyline stays the only one that fits.
     */
    public void generate(int requested) {
        if (requested < MIN_SIZE || requested > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Use a size between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        size = requested;

        // A full set of clues does not always pin the city down. At size six
        // there are Latin squares whose four sides of clues are satisfied by
        // two or three different skylines - the clues simply do not carry
        // enough information to separate them. Carving from such a square
        // yields a puzzle with no single answer and, worse, one that keeps
        // every clue, since removing any of them cannot make a count of three
        // into a count of one. So the square itself has to be checked before
        // anything is taken away.
        for (int attempt = 0; attempt < 500; attempt++) {
            answer = randomLatinSquare();
            readClues();
            if (countSolutions(2) == 1) {
                break;
            }
        }
        carveClues();
        heights = new int[size][size];
    }

    private void readClues() {
        top = new int[size];
        bottom = new int[size];
        leftSide = new int[size];
        rightSide = new int[size];
        for (int row = 0; row < size; row++) {
            int[] line = answer[row].clone();
            leftSide[row] = visible(line);
            rightSide[row] = visible(reverse(line));
        }
        for (int column = 0; column < size; column++) {
            int[] line = new int[size];
            for (int row = 0; row < size; row++) {
                line[row] = answer[row][column];
            }
            top[column] = visible(line);
            bottom[column] = visible(reverse(line));
        }
    }

    private void carveClues() {
        List<int[]> clues = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            clues.add(new int[] {0, index});
            clues.add(new int[] {1, index});
            clues.add(new int[] {2, index});
            clues.add(new int[] {3, index});
        }
        Collections.shuffle(clues, random);
        for (int[] clue : clues) {
            int[] side = sideOf(clue[0]);
            int held = side[clue[1]];
            side[clue[1]] = 0;
            if (countSolutions(2) != 1) {
                side[clue[1]] = held;
            }
        }
    }

    private int[] sideOf(int which) {
        return switch (which) {
            case 0 -> top;
            case 1 -> rightSide;
            case 2 -> bottom;
            default -> leftSide;
        };
    }

    private static int[] reverse(int[] line) {
        int[] out = new int[line.length];
        for (int i = 0; i < line.length; i++) {
            out[i] = line[line.length - 1 - i];
        }
        return out;
    }

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
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] swap = square[i];
            square[i] = square[j];
            square[j] = swap;
        }
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            for (int[] line : square) {
                int held = line[i];
                line[i] = line[j];
                line[j] = held;
            }
        }
        return square;
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

    public void place(int row, int column, int storeys) {
        if (row < 0 || row >= size || column < 0 || column >= size) {
            throw new IllegalArgumentException("There is no plot at " + row + "," + column + ".");
        }
        if (storeys < 0 || storeys > size) {
            throw new IllegalArgumentException("Use 1 to " + size + ", or 0 to clear.");
        }
        heights[row][column] = storeys;
    }

    public boolean complete() {
        for (int row = 0; row < size; row++) {
            if (!Arrays.equals(heights[row], answer[row])) {
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
                    if (heights[line][a] != 0 && heights[line][a] == heights[line][b]) {
                        return "Row " + line + " has two towers of " + heights[line][a] + ".";
                    }
                    if (heights[a][line] != 0 && heights[a][line] == heights[b][line]) {
                        return "Column " + line + " has two towers of " + heights[a][line] + ".";
                    }
                }
            }
        }
        for (int row = 0; row < size; row++) {
            String fault = clueFault(heights[row], leftSide[row], rightSide[row], "Row " + row);
            if (!fault.isEmpty()) {
                return fault;
            }
        }
        for (int column = 0; column < size; column++) {
            int[] line = new int[size];
            for (int row = 0; row < size; row++) {
                line[row] = heights[row][column];
            }
            String fault = clueFault(line, top[column], bottom[column], "Column " + column);
            if (!fault.isEmpty()) {
                return fault;
            }
        }
        return "";
    }

    /** Clues are only checked once the line is full - a gap could still fix it. */
    private String clueFault(int[] line, int fromStart, int fromEnd, String label) {
        for (int height : line) {
            if (height == 0) {
                return "";
            }
        }
        if (fromStart != 0 && visible(line) != fromStart) {
            return label + " shows " + visible(line) + " towers, not " + fromStart + ".";
        }
        if (fromEnd != 0 && visible(reverse(line)) != fromEnd) {
            return label + " shows " + visible(reverse(line)) + " from the far end, not "
                    + fromEnd + ".";
        }
        return "";
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** The city with its clues around the edge, corners left blank. */
    public char[][] cells() {
        int span = size + 2;
        char[][] grid = new char[span][span];
        for (char[] line : grid) {
            Arrays.fill(line, ' ');
        }
        for (int index = 0; index < size; index++) {
            grid[0][index + 1] = digit(top[index]);
            grid[span - 1][index + 1] = digit(bottom[index]);
            grid[index + 1][0] = digit(leftSide[index]);
            grid[index + 1][span - 1] = digit(rightSide[index]);
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                grid[row + 1][column + 1] = digit(heights[row][column]);
            }
        }
        return grid;
    }

    private static char digit(int value) {
        return value == 0 ? '.' : (char) ('0' + value);
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
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (heights[row][column] == 0) {
                    blank++;
                }
            }
        }
        return String.format("%dx%d, %d of %d clues kept, %d plots still empty.",
                size, size, clueCount(), size * 4, blank);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("city size:", MIN_SIZE, MAX_SIZE, 5));
        io.muted("Each height once per row and column. A clue counts the towers "
                + "visible from that edge; a taller tower hides everything behind it.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            int choice = io.menu("Skyscrapers",
                    List.of("Build", "Hint", "Solve", "New city"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0 -> {
                        int row = io.askInt("row:", 0, size - 1, 0);
                        int column = io.askInt("column:", 0, size - 1, 0);
                        place(row, column, io.askInt("storeys (0 clears):", 0, size, 0));
                    }
                    case 1 -> io.info(hint());
                    case 2 -> io.info(solve());
                    default -> generate(io.askInt("city size:", MIN_SIZE, MAX_SIZE, size));
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
                    return board("Pick a height above, then click a plot to build. "
                            + "Clicking the same height again clears it.");
                }
                case "cycle", "step", "place" -> {
                    // The clues ring the city, so the playable plots are the
                    // inside of the displayed grid.
                    int row = Json.integer(body, "row", -1) - 1;
                    int column = Json.integer(body, "col", -1) - 1;
                    if (row < 0 || row >= size || column < 0 || column >= size) {
                        return board("");
                    }
                    int storeys = Json.integer(body, "value", 1);
                    place(row, column, heights[row][column] == storeys ? 0 : storeys);
                    return board(complete() ? "That is the skyline." : "");
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
