package com.randomjava.projects.tangram;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tangram - fit the seven pieces together so they cover a silhouette exactly.
 *
 * <h2>The pieces do not sit on a square grid</h2>
 *
 * <p>This is the whole difficulty, and it is a modelling problem rather than a
 * search problem. Every other grid puzzle here has cells; tangram has right
 * isosceles triangles whose legs run along diagonals, so a piece does not align
 * with a square lattice at all. Model it as polyominoes and the pieces are no
 * longer tangram pieces.
 *
 * <p>What saves it: in a square of side four, every vertex of every piece can
 * be placed on an integer point, and every edge is horizontal, vertical or at
 * exactly 45 degrees. So cut each unit cell along <em>both</em> diagonals into
 * four micro-triangles, and each piece becomes an exact set of them - 64 in the
 * square, and the seven pieces account for precisely 64:
 *
 * <pre>
 *   two large triangles   16 each
 *   medium triangle        8
 *   square                 8
 *   parallelogram          8
 *   two small triangles    4 each
 * </pre>
 *
 * <p>Once that holds, tangram is exact cover - the same problem as
 * {@link com.randomjava.projects.shikaku.Shikaku} - and the same search solves
 * it: repeatedly take the lowest uncovered triangle and try every placement
 * that could cover it, so a hole nothing can fill is found at once rather than
 * after the rest of the board is filled in around it.
 *
 * <h2>Deriving the pieces instead of listing them</h2>
 *
 * <p>The micro-triangles belonging to a piece are not written out by hand -
 * that is where the errors would be. Each piece is given as a polygon, and
 * {@link #trianglesOf} asks which micro-triangle centres fall inside it. The
 * eight orientations come from transforming the polygon's vertices, not from
 * eight separate transcriptions, so a piece can only be wrong in one place
 * rather than eight.
 *
 * <p>That also gives a free correctness check. Tangram <em>is</em> a dissection
 * of the square, so a solver with correct pieces must be able to tile a plain
 * four-by-four square. If it cannot, the pieces are wrong - and a solver with
 * subtly wrong pieces would otherwise happily tile things and look convincing.
 */
public final class Tangram implements Project {

    public static final Meta META = new Meta(
            250, "tangram", "Tangram", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Fit seven flat shapes together to match a silhouette exactly.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    /** The square is four units on a side, so sixteen cells and 64 triangles. */
    public static final int SIDE = 4;

    public static final String[] PIECE_NAMES = {
        "large triangle", "large triangle", "medium triangle",
        "square", "parallelogram", "small triangle", "small triangle"};

    /**
     * Each piece as a polygon on integer points. Every edge is horizontal,
     * vertical or 45 degrees, which is what lets them decompose exactly.
     */
    private static final int[][][] PIECES = {
        {{0, 0}, {4, 0}, {2, 2}},                    // large: legs 2root2, area 4
        {{0, 0}, {4, 0}, {2, 2}},                    // the second large triangle
        {{0, 0}, {2, 0}, {0, 2}},                    // medium: legs 2, area 2
        {{1, 0}, {2, 1}, {1, 2}, {0, 1}},            // square: side root2, area 2
        {{0, 0}, {2, 0}, {3, 1}, {1, 1}},            // parallelogram, area 2
        {{0, 0}, {2, 0}, {1, 1}},                    // small: legs root2, area 1
        {{0, 0}, {2, 0}, {1, 1}}};                   // the second small triangle

    /** Micro-triangle centres: bottom, right, top, left of cell (x, y). */
    private static double[] centre(int x, int y, int part) {
        return switch (part) {
            case 0 -> new double[] {x + 0.5, y + 1.0 / 6};
            case 1 -> new double[] {x + 5.0 / 6, y + 0.5};
            case 2 -> new double[] {x + 0.5, y + 5.0 / 6};
            default -> new double[] {x + 1.0 / 6, y + 0.5};
        };
    }

    public static int triangleId(int x, int y, int part) {
        return (y * SIDE + x) * 4 + part;
    }

    private static boolean inside(int[][] polygon, double px, double py) {
        boolean in = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            double xi = polygon[i][0];
            double yi = polygon[i][1];
            double xj = polygon[j][0];
            double yj = polygon[j][1];
            if ((yi > py) != (yj > py)
                    && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    /** Which micro-triangles of the board a polygon covers, or null if it spills. */
    public static Set<Integer> trianglesOf(int[][] polygon) {
        Set<Integer> covered = new LinkedHashSet<>();
        for (int[] point : polygon) {
            if (point[0] < 0 || point[0] > SIDE || point[1] < 0 || point[1] > SIDE) {
                return null;
            }
        }
        for (int y = 0; y < SIDE; y++) {
            for (int x = 0; x < SIDE; x++) {
                for (int part = 0; part < 4; part++) {
                    double[] at = centre(x, y, part);
                    if (inside(polygon, at[0], at[1])) {
                        covered.add(triangleId(x, y, part));
                    }
                }
            }
        }
        return covered;
    }

    // ------------------------------------------------------------------
    // Orientations and placements
    // ------------------------------------------------------------------

    private static int[][] turn(int[][] polygon) {
        int[][] out = new int[polygon.length][2];
        for (int i = 0; i < polygon.length; i++) {
            out[i][0] = -polygon[i][1];
            out[i][1] = polygon[i][0];
        }
        return out;
    }

    private static int[][] flip(int[][] polygon) {
        int[][] out = new int[polygon.length][2];
        for (int i = 0; i < polygon.length; i++) {
            out[i][0] = -polygon[i][0];
            out[i][1] = polygon[i][1];
        }
        return out;
    }

    private static int[][] shift(int[][] polygon, int dx, int dy) {
        int[][] out = new int[polygon.length][2];
        for (int i = 0; i < polygon.length; i++) {
            out[i][0] = polygon[i][0] + dx;
            out[i][1] = polygon[i][1] + dy;
        }
        return out;
    }

    /** Every placement of a piece: eight orientations at every offset that fits. */
    public static List<Set<Integer>> placements(int piece) {
        List<Set<Integer>> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int[][] shape = PIECES[piece];
        for (int reflection = 0; reflection < 2; reflection++) {
            int[][] oriented = reflection == 0 ? shape : flip(shape);
            for (int rotation = 0; rotation < 4; rotation++) {
                for (int dy = -SIDE; dy <= SIDE; dy++) {
                    for (int dx = -SIDE; dx <= SIDE; dx++) {
                        Set<Integer> covered = trianglesOf(shift(oriented, dx, dy));
                        if (covered == null || covered.isEmpty()) {
                            continue;
                        }
                        if (seen.add(covered.toString())) {
                            out.add(covered);
                        }
                    }
                }
                oriented = turn(oriented);
            }
        }
        return out;
    }

    /** How many micro-triangles a piece covers, whatever its orientation. */
    public static int areaOf(int piece) {
        List<Set<Integer>> all = placements(piece);
        return all.isEmpty() ? 0 : all.get(0).size();
    }

    // ------------------------------------------------------------------
    // Exact cover
    // ------------------------------------------------------------------

    private int[] owner = new int[SIDE * SIDE * 4];
    private int nodes;

    public int nodes() {
        return nodes;
    }

    public int ownerOf(int triangle) {
        return owner[triangle];
    }

    /**
     * Tiles the square with all seven pieces.
     *
     * <p>Branches on the lowest uncovered triangle rather than on the pieces in
     * order, so a triangle nothing can reach ends the branch immediately.
     */
    public boolean solve() {
        Arrays.fill(owner, -1);
        nodes = 0;
        List<List<Set<Integer>>> options = new ArrayList<>();
        for (int piece = 0; piece < PIECES.length; piece++) {
            options.add(placements(piece));
        }
        return cover(options, new boolean[PIECES.length]);
    }

    private boolean cover(List<List<Set<Integer>>> options, boolean[] used) {
        nodes++;
        int target = -1;
        for (int triangle = 0; triangle < owner.length; triangle++) {
            if (owner[triangle] < 0) {
                target = triangle;
                break;
            }
        }
        if (target < 0) {
            return true;
        }
        for (int piece = 0; piece < PIECES.length; piece++) {
            if (used[piece]) {
                continue;
            }
            for (Set<Integer> spot : options.get(piece)) {
                if (!spot.contains(target)) {
                    continue;
                }
                boolean clear = true;
                for (int triangle : spot) {
                    if (owner[triangle] >= 0) {
                        clear = false;
                        break;
                    }
                }
                if (!clear) {
                    continue;
                }
                for (int triangle : spot) {
                    owner[triangle] = piece;
                }
                used[piece] = true;
                if (cover(options, used)) {
                    return true;
                }
                used[piece] = false;
                for (int triangle : spot) {
                    owner[triangle] = -1;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Two rows of characters per cell, so each cell's four triangles show as a
     * little diamond of letters and the piece boundaries are visible.
     */
    public char[][] cells() {
        char[][] grid = new char[SIDE * 2][SIDE * 2];
        for (char[] line : grid) {
            Arrays.fill(line, '.');
        }
        for (int y = 0; y < SIDE; y++) {
            for (int x = 0; x < SIDE; x++) {
                int row = (SIDE - 1 - y) * 2;
                int column = x * 2;
                grid[row][column] = mark(triangleId(x, y, 3));       // left
                grid[row][column + 1] = mark(triangleId(x, y, 2));   // top
                grid[row + 1][column] = mark(triangleId(x, y, 0));   // bottom
                grid[row + 1][column + 1] = mark(triangleId(x, y, 1));
            }
        }
        return grid;
    }

    private char mark(int triangle) {
        int piece = owner[triangle];
        return piece < 0 ? '.' : (char) ('a' + piece);
    }

    private String detail() {
        int covered = 0;
        for (int piece : owner) {
            if (piece >= 0) {
                covered++;
            }
        }
        return String.format("%d of %d micro-triangles covered by %d pieces. %s",
                covered, owner.length, PIECES.length,
                nodes > 0 ? "Searched " + nodes + " placements." : "");
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        Arrays.fill(owner, -1);
        io.muted("Each cell is drawn as four triangles, so a letter block is one piece. "
                + "The seven pieces tile the square exactly.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            int choice = io.menu("Tangram", List.of("Fit the pieces", "Clear"));
            if (choice < 0) {
                return;
            }
            if (choice == 0) {
                io.info(solve() ? "All seven pieces fitted." : "No arrangement found.");
            } else {
                Arrays.fill(owner, -1);
                nodes = 0;
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate", "step" -> {
                    Arrays.fill(owner, -1);
                    nodes = 0;
                    return board("Press Solve to fit all seven pieces into the square.");
                }
                case "solve" -> {
                    boolean done = solve();
                    return board(done ? "All seven pieces fitted." : "No arrangement found.");
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
