package com.randomjava.projects.shikaku;

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
 * Shikaku - cut the grid into rectangles so that each one contains exactly one
 * number, and that number is its area.
 *
 * <h2>It is an exact cover problem, and saying so is most of the work</h2>
 *
 * <p>Strip the presentation away and the puzzle is: given a collection of
 * candidate rectangles, choose a subcollection that covers every cell exactly
 * once. That is <em>exact cover</em> - the same problem as tiling a board with
 * pentominoes, or as Sudoku once it is rephrased. Recognising it matters
 * because exact cover comes with a known way to search it that is much better
 * than the obvious one.
 *
 * <p>The candidate list is small and easy to build. For a clue of {@code v} at
 * some cell, enumerate every rectangle of area exactly {@code v} that contains
 * that cell, fits on the board, and contains no <em>other</em> clue - a
 * rectangle holding two numbers is illegal by definition, so it never needs
 * considering again. {@link #candidatesFor} does this once per puzzle, and the
 * "no other clue" filter alone usually removes most of what naive enumeration
 * would produce.
 *
 * <h2>Branch on cells, not on clues</h2>
 *
 * <p>The obvious search takes the clues in order and tries each of their
 * rectangles. It works and it is slow, because it spends its time deep in a
 * subtree that some far-away cell has already made impossible.
 *
 * <p>The standard move for exact cover is to branch on the <em>element to be
 * covered</em> rather than the set doing the covering, and to pick the element
 * with the fewest options left. So {@link #search} looks for the uncovered cell
 * that the fewest surviving rectangles can still reach, and tries only those.
 * Two things fall out of that for free:
 *
 * <ul>
 *   <li>A cell no rectangle can cover ends the branch at once, rather than
 *       after the rest of the board has been filled in around it.</li>
 *   <li>A cell exactly one rectangle can cover is not a guess at all, so the
 *       forced moves get played first and the branching factor stays near the
 *       smallest it can be.</li>
 * </ul>
 *
 * <h2>Generating backwards</h2>
 *
 * <p>Cutting a grid into rectangles is easy; recovering the cut is the hard
 * part. So {@link #generate} does the easy direction - grow random rectangles
 * until the board is used up, then write each one's area into one of its cells -
 * and then checks that the clues it just wrote admit only the cut it started
 * from. A partition always exists, because a leftover cell is a perfectly legal
 * rectangle of area one.
 */
public final class Shikaku implements Project {

    public static final Meta META = new Meta(
            241, "shikaku", "Shikaku", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Cut a grid into rectangles, each containing one number equal to its area.",
            "", true);

    private static final int MIN_SIZE = 4;
    private static final int MAX_SIZE = 10;
    private static final int NODE_BUDGET = 200_000;

    /** Glyphs for neighbouring rectangles, so the cuts between them show. */
    private static final char[] SHADES = {'#', '*', 'o', '+'};

    private final Random random;

    private int size;
    /** Clue value per cell, zero where there is none. */
    private int[][] clues;
    /** Which rectangle covers each cell in the answer, by index. */
    private int[][] answerRegion;
    /** The answer, as {@code {top, left, height, width}} per clue. */
    private List<int[]> answer;
    /** What the player has drawn, or null while nothing is shown. */
    private int[][] shownRegion;

    private int nodesLeft;
    private int guesses;

    public Shikaku() {
        this(new Random());
    }

    public Shikaku(Random random) {
        this.random = random;
        generate(7);
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

    public int clueCount() {
        int count = 0;
        for (int[] line : clues) {
            for (int value : line) {
                if (value != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /** The rectangles of the answer, as {@code {top, left, height, width}}. */
    public List<int[]> answer() {
        List<int[]> out = new ArrayList<>();
        for (int[] rectangle : answer) {
            out.add(rectangle.clone());
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Candidate rectangles
    // ------------------------------------------------------------------

    private List<int[]> clueCells() {
        List<int[]> cells = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (clues[row][column] != 0) {
                    cells.add(new int[] {row, column});
                }
            }
        }
        return cells;
    }

    /**
     * Every rectangle that could belong to the clue at {@code (row, column)}:
     * the right area, on the board, covering its own clue, and covering no
     * other clue.
     */
    public List<int[]> candidatesFor(int row, int column) {
        int area = clues[row][column];
        List<int[]> out = new ArrayList<>();
        if (area <= 0) {
            return out;
        }
        for (int height = 1; height <= area; height++) {
            if (area % height != 0) {
                continue;
            }
            int width = area / height;
            if (height > size || width > size) {
                continue;
            }
            for (int top = Math.max(0, row - height + 1); top <= row; top++) {
                for (int left = Math.max(0, column - width + 1); left <= column; left++) {
                    if (top + height > size || left + width > size) {
                        continue;
                    }
                    if (holdsOneClue(top, left, height, width, row, column)) {
                        out.add(new int[] {top, left, height, width});
                    }
                }
            }
        }
        return out;
    }

    private boolean holdsOneClue(int top, int left, int height, int width,
                                 int clueRow, int clueColumn) {
        for (int row = top; row < top + height; row++) {
            for (int column = left; column < left + width; column++) {
                if (clues[row][column] != 0 && (row != clueRow || column != clueColumn)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean covers(int[] rectangle, int row, int column) {
        return row >= rectangle[0] && row < rectangle[0] + rectangle[2]
                && column >= rectangle[1] && column < rectangle[1] + rectangle[3];
    }

    private static boolean fitsIn(int[] rectangle, boolean[][] covered) {
        for (int row = rectangle[0]; row < rectangle[0] + rectangle[2]; row++) {
            for (int column = rectangle[1]; column < rectangle[1] + rectangle[3]; column++) {
                if (covered[row][column]) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Exact cover search
    // ------------------------------------------------------------------

    /**
     * Fills the board, branching each time on whichever uncovered cell has the
     * fewest rectangles still able to reach it.
     */
    private int search(boolean[][] covered, boolean[] used, List<int[]>[] options,
                       int[] chosen, int cap, List<int[]>[] found) {
        if (cap <= 0 || nodesLeft-- <= 0) {
            return 0;
        }
        // Each move is {clue index, rectangle index within that clue}, so the
        // owner never has to be looked up again by searching for the shape.
        List<int[]> bestMoves = null;
        search:
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (covered[row][column]) {
                    continue;
                }
                List<int[]> moves = new ArrayList<>();
                for (int clue = 0; clue < options.length; clue++) {
                    if (used[clue]) {
                        continue;
                    }
                    List<int[]> shapes = options[clue];
                    for (int index = 0; index < shapes.size(); index++) {
                        int[] rectangle = shapes.get(index);
                        if (covers(rectangle, row, column) && fitsIn(rectangle, covered)) {
                            moves.add(new int[] {clue, index});
                        }
                    }
                }
                if (moves.isEmpty()) {
                    return 0;   // nothing can ever cover this cell
                }
                if (bestMoves == null || moves.size() < bestMoves.size()) {
                    bestMoves = moves;
                    if (moves.size() == 1) {
                        break search;   // forced, so no cell can be better
                    }
                }
            }
        }
        if (bestMoves == null) {
            if (found[0] == null) {
                found[0] = new ArrayList<>();
                for (int clue = 0; clue < chosen.length; clue++) {
                    found[0].add(options[clue].get(chosen[clue]).clone());
                }
            }
            return 1;
        }
        if (bestMoves.size() > 1) {
            guesses++;
        }

        int total = 0;
        for (int[] move : bestMoves) {
            int owner = move[0];
            int[] rectangle = options[owner].get(move[1]);
            paint(covered, rectangle, true);
            used[owner] = true;
            int previous = chosen[owner];
            chosen[owner] = move[1];
            total += search(covered, used, options, chosen, cap - total, found);
            chosen[owner] = previous;
            used[owner] = false;
            paint(covered, rectangle, false);
            if (total >= cap) {
                break;
            }
        }
        return total;
    }

    private static void paint(boolean[][] covered, int[] rectangle, boolean value) {
        for (int row = rectangle[0]; row < rectangle[0] + rectangle[2]; row++) {
            for (int column = rectangle[1]; column < rectangle[1] + rectangle[3]; column++) {
                covered[row][column] = value;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<int[]>[] allOptions() {
        List<int[]> cells = clueCells();
        List<int[]>[] options = new List[cells.size()];
        for (int index = 0; index < cells.size(); index++) {
            options[index] = candidatesFor(cells.get(index)[0], cells.get(index)[1]);
        }
        return options;
    }

    /** How many ways the grid can be cut, counted no further than {@code cap}. */
    public int countSolutions(int cap) {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        List<int[]>[] options = allOptions();
        @SuppressWarnings("unchecked")
        List<int[]>[] found = new List[1];
        return search(new boolean[size][size], new boolean[options.length], options,
                new int[options.length], cap, found);
    }

    public String solve() {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        List<int[]>[] options = allOptions();
        @SuppressWarnings("unchecked")
        List<int[]>[] found = new List[1];
        int count = search(new boolean[size][size], new boolean[options.length], options,
                new int[options.length], 2, found);
        if (count == 0) {
            return "These numbers cannot be cut into rectangles.";
        }
        shownRegion = regionOf(found[0]);
        return (guesses == 0
                ? "Every cell was forced - no guessing needed."
                : "The most constrained cell left a choice " + guesses + " times.")
                + (count == 1 ? "" : " These numbers allow more than one cut.");
    }

    public String hint() {
        if (shownRegion == null) {
            shownRegion = new int[size][size];
            for (int[] line : shownRegion) {
                Arrays.fill(line, -1);
            }
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (shownRegion[row][column] != answerRegion[row][column]) {
                    int target = answerRegion[row][column];
                    int[] rectangle = answer.get(target);
                    for (int r = rectangle[0]; r < rectangle[0] + rectangle[2]; r++) {
                        for (int c = rectangle[1]; c < rectangle[1] + rectangle[3]; c++) {
                            shownRegion[r][c] = target;
                        }
                    }
                    return "One rectangle is " + rectangle[2] + " by " + rectangle[3]
                            + " with its corner at " + rectangle[0] + "," + rectangle[1] + ".";
                }
            }
        }
        return "Nothing left to give away.";
    }

    // ------------------------------------------------------------------
    // Generating
    // ------------------------------------------------------------------

    /**
     * Grow random rectangles until the board is used up, write each area into
     * one of its cells, then keep the result only if those numbers admit no
     * other cut.
     */
    public void generate(int requested) {
        if (requested < MIN_SIZE || requested > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Use a size between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        size = requested;
        for (int attempt = 0; attempt < 300; attempt++) {
            List<int[]> pieces = randomPartition();
            clues = new int[size][size];
            answerRegion = new int[size][size];
            for (int index = 0; index < pieces.size(); index++) {
                int[] rectangle = pieces.get(index);
                for (int row = rectangle[0]; row < rectangle[0] + rectangle[2]; row++) {
                    for (int column = rectangle[1]; column < rectangle[1] + rectangle[3];
                            column++) {
                        answerRegion[row][column] = index;
                    }
                }
                int row = rectangle[0] + random.nextInt(rectangle[2]);
                int column = rectangle[1] + random.nextInt(rectangle[3]);
                clues[row][column] = rectangle[2] * rectangle[3];
            }
            answer = pieces;
            shownRegion = null;
            if (countSolutions(2) == 1) {
                return;
            }
        }
        shownRegion = null;
    }

    /** A partition into rectangles, grown greedily from whatever is still free. */
    private List<int[]> randomPartition() {
        boolean[][] taken = new boolean[size][size];
        List<int[]> pieces = new ArrayList<>();
        List<int[]> starts = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                starts.add(new int[] {row, column});
            }
        }
        for (int[] start : starts) {
            int row = start[0];
            int column = start[1];
            if (taken[row][column]) {
                continue;
            }
            // How far the rectangle could stretch from here, before choosing.
            int maxHeight = 0;
            while (row + maxHeight < size && !taken[row + maxHeight][column]) {
                maxHeight++;
            }
            int maxWidth = 0;
            while (column + maxWidth < size && !taken[row][column + maxWidth]) {
                maxWidth++;
            }
            int height = 1 + random.nextInt(Math.min(maxHeight, 4));
            int width = 1 + random.nextInt(Math.min(maxWidth, 4));
            // Shrink until the whole block really is free.
            while (height > 1 && !blockFree(taken, row, column, height, width)) {
                height--;
            }
            while (width > 1 && !blockFree(taken, row, column, height, width)) {
                width--;
            }
            for (int r = row; r < row + height; r++) {
                for (int c = column; c < column + width; c++) {
                    taken[r][c] = true;
                }
            }
            pieces.add(new int[] {row, column, height, width});
        }
        Collections.shuffle(pieces, random);
        return pieces;
    }

    private boolean blockFree(boolean[][] taken, int top, int left, int height, int width) {
        if (top + height > size || left + width > size) {
            return false;
        }
        for (int row = top; row < top + height; row++) {
            for (int column = left; column < left + width; column++) {
                if (taken[row][column]) {
                    return false;
                }
            }
        }
        return true;
    }

    private int[][] regionOf(List<int[]> pieces) {
        int[][] region = new int[size][size];
        for (int[] line : region) {
            Arrays.fill(line, -1);
        }
        for (int index = 0; index < pieces.size(); index++) {
            int[] rectangle = pieces.get(index);
            for (int row = rectangle[0]; row < rectangle[0] + rectangle[2]; row++) {
                for (int column = rectangle[1]; column < rectangle[1] + rectangle[3]; column++) {
                    region[row][column] = index;
                }
            }
        }
        return region;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Clues stay as digits; the rest of a rectangle is filled with a glyph
     * chosen so that touching rectangles never share one, which is what makes
     * the cuts visible at all in a plain character grid.
     */
    public char[][] cells() {
        char[][] grid = new char[size][size];
        for (char[] line : grid) {
            Arrays.fill(line, '.');
        }
        int[][] region = shownRegion;
        char[] glyphs = region == null ? null : glyphsFor(region);
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (clues[row][column] != 0) {
                    int value = clues[row][column];
                    grid[row][column] = value < 10
                            ? (char) ('0' + value) : (char) ('A' + value - 10);
                } else if (region != null && region[row][column] >= 0) {
                    grid[row][column] = glyphs[region[row][column]];
                }
            }
        }
        return grid;
    }

    /** Greedy four-colouring of the rectangles, by which ones touch. */
    private char[] glyphsFor(int[][] region) {
        int count = 0;
        for (int[] line : region) {
            for (int index : line) {
                count = Math.max(count, index + 1);
            }
        }
        char[] glyphs = new char[Math.max(count, 1)];
        for (int index = 0; index < glyphs.length; index++) {
            boolean[] blocked = new boolean[SHADES.length];
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    if (region[row][column] != index) {
                        continue;
                    }
                    for (int[] step : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                        int nextRow = row + step[0];
                        int nextColumn = column + step[1];
                        if (nextRow < 0 || nextRow >= size
                                || nextColumn < 0 || nextColumn >= size) {
                            continue;
                        }
                        int other = region[nextRow][nextColumn];
                        if (other >= 0 && other < index) {
                            for (int shade = 0; shade < SHADES.length; shade++) {
                                if (glyphs[other] == SHADES[shade]) {
                                    blocked[shade] = true;
                                }
                            }
                        }
                    }
                }
            }
            glyphs[index] = SHADES[0];
            for (int shade = 0; shade < SHADES.length; shade++) {
                if (!blocked[shade]) {
                    glyphs[index] = SHADES[shade];
                    break;
                }
            }
        }
        return glyphs;
    }

    public boolean showingAnswer() {
        return shownRegion != null;
    }

    private String detail() {
        if (shownRegion == null) {
            return String.format("%dx%d, %d numbers. Every cell belongs to exactly one "
                    + "rectangle.", size, size, clueCount());
        }
        int shown = 0;
        for (int[] line : shownRegion) {
            for (int index : line) {
                if (index >= 0) {
                    shown++;
                }
            }
        }
        return String.format("%dx%d, %d numbers, %d of %d cells assigned.",
                size, size, clueCount(), shown, size * size);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("board size:", MIN_SIZE, MAX_SIZE, 7));
        io.muted("Each number is the area of the rectangle it sits in, and every cell "
                + "belongs to exactly one rectangle.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            int choice = io.menu("Shikaku",
                    List.of("Reveal one rectangle", "Solve", "New puzzle"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0 -> io.info(hint());
                    case 1 -> io.info(solve());
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
                    generate(Json.integer(body, "size", 7));
                    return board("Each number is the area of the rectangle around it.");
                }
                case "hint", "step", "cycle" -> {
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
