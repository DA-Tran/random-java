package com.randomjava.projects.hashiwokakero;

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
 * Hashiwokakero - join the numbered islands with bridges, at most two between
 * any pair, running only horizontally or vertically, never crossing, until
 * every island has exactly as many bridge ends as its number and the whole
 * archipelago is connected.
 *
 * <h2>The state does not live on the grid</h2>
 *
 * <p>Every other grid puzzle in this suite puts a variable on each cell. This
 * one should not. The empty water between islands carries no information, and
 * a bridge is not a property of a cell but of a <em>pair</em> of islands. So
 * the board is really a graph: the islands are vertices, two islands facing
 * each other along a clear row or column are joined by an edge, and the
 * unknown is how many bridges each edge carries - nought, one or two.
 *
 * <p>That collapses the problem enormously. A 9x9 board might hold 60 empty
 * cells and a dozen islands; the edge model has perhaps 20 variables of three
 * values each, and the grid never has to be searched at all. {@link #edges}
 * builds that list once, and everything afterwards works on it.
 *
 * <h2>Crossing becomes a pairwise conflict</h2>
 *
 * <p>Bridges may not cross, which on a grid is a statement about cells being
 * occupied twice. On the graph it is simply that certain pairs of edges are
 * incompatible - a horizontal edge and a vertical edge whose spans intersect
 * cannot both carry bridges. {@link #crossing} precomputes those pairs, so the
 * rule costs one lookup rather than a walk along the water.
 *
 * <h2>Two bounds and one global check</h2>
 *
 * <p>Counting gives the deductions. For each island, the bridges already
 * placed on its edges may not exceed its number, and its number may not exceed
 * what is placed plus twice the edges still undecided - otherwise it can never
 * be satisfied. Those two bounds, applied to fixpoint, solve most boards
 * outright, and they are what {@link #propagate} does.
 *
 * <p>Connectivity is the part no local rule sees. It is checked the way the
 * other puzzles here check theirs: generously, treating undecided edges as
 * usable, so a failure means no future choice could join the islands and the
 * branch is dead rather than merely unfinished.
 */
public final class Hashiwokakero implements Project {

    public static final Meta META = new Meta(
            236, "hashiwokakero", "Hashiwokakero", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Join islands with bridges so every island has its count and all connect.",
            "", true);

    private static final int MIN_SIZE = 5;
    private static final int MAX_SIZE = 9;
    private static final int NODE_BUDGET = 60_000;

    private final Random random;

    private int size;
    /** Island index per cell, or -1 for water. */
    private int[][] islandAt;
    /** Each island as {row, column}. */
    private List<int[]> islands;
    /** How many bridge ends each island wants. */
    private int[] wanted;
    /** Each edge as {islandA, islandB}, only between islands that face each other. */
    private List<int[]> edges;
    /** {@code crossing[e]} lists edges that cannot carry bridges at the same time. */
    private List<List<Integer>> crossing;
    /** Bridges currently drawn on each edge, and the answer's. */
    private int[] bridges;
    private int[] answer;

    private int nodesLeft;
    private int guesses;

    public Hashiwokakero() {
        this(new Random());
    }

    public Hashiwokakero(Random random) {
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

    public int islandCount() {
        return islands.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    public int wantedAt(int island) {
        return wanted[island];
    }

    public int[] islandAt(int island) {
        return islands.get(island).clone();
    }

    public int bridgesOn(int edge) {
        return bridges[edge];
    }

    public int[] edge(int index) {
        return edges.get(index).clone();
    }

    // ------------------------------------------------------------------
    // Building the graph
    // ------------------------------------------------------------------

    /**
     * Two islands share an edge when they face each other along a row or
     * column with nothing but water between them.
     */
    private void buildGraph() {
        edges = new ArrayList<>();
        for (int a = 0; a < islands.size(); a++) {
            int[] from = islands.get(a);
            for (int b = a + 1; b < islands.size(); b++) {
                int[] to = islands.get(b);
                if (from[0] == to[0]) {
                    boolean clear = true;
                    for (int column = Math.min(from[1], to[1]) + 1;
                            column < Math.max(from[1], to[1]); column++) {
                        clear &= islandAt[from[0]][column] < 0;
                    }
                    if (clear) {
                        edges.add(new int[] {a, b});
                    }
                } else if (from[1] == to[1]) {
                    boolean clear = true;
                    for (int row = Math.min(from[0], to[0]) + 1;
                            row < Math.max(from[0], to[0]); row++) {
                        clear &= islandAt[row][from[1]] < 0;
                    }
                    if (clear) {
                        edges.add(new int[] {a, b});
                    }
                }
            }
        }
        crossing = new ArrayList<>();
        for (int i = 0; i < edges.size(); i++) {
            crossing.add(new ArrayList<>());
        }
        for (int i = 0; i < edges.size(); i++) {
            for (int j = i + 1; j < edges.size(); j++) {
                if (cross(edges.get(i), edges.get(j))) {
                    crossing.get(i).add(j);
                    crossing.get(j).add(i);
                }
            }
        }
    }

    private boolean horizontal(int[] edge) {
        return islands.get(edge[0])[0] == islands.get(edge[1])[0];
    }

    /** Whether a horizontal span and a vertical span actually intersect. */
    private boolean cross(int[] first, int[] second) {
        if (horizontal(first) == horizontal(second)) {
            return false;   // parallel spans never overlap: islands block them
        }
        int[] across = horizontal(first) ? first : second;
        int[] down = horizontal(first) ? second : first;
        int row = islands.get(across[0])[0];
        int lowColumn = Math.min(islands.get(across[0])[1], islands.get(across[1])[1]);
        int highColumn = Math.max(islands.get(across[0])[1], islands.get(across[1])[1]);
        int column = islands.get(down[0])[1];
        int lowRow = Math.min(islands.get(down[0])[0], islands.get(down[1])[0]);
        int highRow = Math.max(islands.get(down[0])[0], islands.get(down[1])[0]);
        return column > lowColumn && column < highColumn
                && row > lowRow && row < highRow;
    }

    // ------------------------------------------------------------------
    // Solving
    // ------------------------------------------------------------------

    private static final int UNDECIDED = -1;

    /** Bridge ends already committed at an island, and edges still open to it. */
    private int[] tally(int[] state, int island) {
        int placed = 0;
        int open = 0;
        for (int e = 0; e < edges.size(); e++) {
            int[] edge = edges.get(e);
            if (edge[0] != island && edge[1] != island) {
                continue;
            }
            if (state[e] == UNDECIDED) {
                open++;
            } else {
                placed += state[e];
            }
        }
        return new int[] {placed, open};
    }

    /**
     * The two counting bounds, plus the crossing rule, to fixpoint. False the
     * moment an island cannot be satisfied.
     */
    private boolean propagate(int[] state) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int island = 0; island < islands.size(); island++) {
                int[] counts = tally(state, island);
                int placed = counts[0];
                int open = counts[1];
                if (placed > wanted[island] || placed + 2 * open < wanted[island]) {
                    return false;
                }
                if (open == 0) {
                    continue;
                }
                // Every open edge must take the most it can, or the least.
                if (placed + 2 * open == wanted[island]) {
                    changed |= setAllOpen(state, island, 2);
                } else if (placed == wanted[island]) {
                    changed |= setAllOpen(state, island, 0);
                }
            }
            // A bridge forbids everything it crosses.
            for (int e = 0; e < edges.size(); e++) {
                if (state[e] <= 0) {
                    continue;
                }
                for (int other : crossing.get(e)) {
                    if (state[other] == UNDECIDED) {
                        state[other] = 0;
                        changed = true;
                    } else if (state[other] > 0) {
                        return false;
                    }
                }
            }
        }
        return connectedEnough(state);
    }

    private boolean setAllOpen(int[] state, int island, int value) {
        boolean changed = false;
        for (int e = 0; e < edges.size(); e++) {
            int[] edge = edges.get(e);
            if ((edge[0] == island || edge[1] == island) && state[e] == UNDECIDED) {
                state[e] = value;
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Whether the islands can still all be joined, counting undecided edges as
     * usable. Generous, so a failure is final.
     */
    private boolean connectedEnough(int[] state) {
        boolean[] seen = new boolean[islands.size()];
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        seen[0] = true;
        int reached = 0;
        while (!queue.isEmpty()) {
            int island = queue.poll();
            reached++;
            for (int e = 0; e < edges.size(); e++) {
                if (state[e] == 0) {
                    continue;
                }
                int[] edge = edges.get(e);
                int other = edge[0] == island ? edge[1] : edge[1] == island ? edge[0] : -1;
                if (other >= 0 && !seen[other]) {
                    seen[other] = true;
                    queue.add(other);
                }
            }
        }
        return reached == islands.size();
    }

    /** A finished board: every island exact, nothing crossing, all joined. */
    public boolean solved(int[] state) {
        for (int value : state) {
            if (value == UNDECIDED) {
                return false;
            }
        }
        for (int island = 0; island < islands.size(); island++) {
            if (tally(state, island)[0] != wanted[island]) {
                return false;
            }
        }
        for (int e = 0; e < edges.size(); e++) {
            if (state[e] > 0) {
                for (int other : crossing.get(e)) {
                    if (state[other] > 0) {
                        return false;
                    }
                }
            }
        }
        return connectedEnough(state);
    }

    private int search(int[] state, int cap, int[][] found) {
        if (cap <= 0 || nodesLeft-- <= 0) {
            return 0;
        }
        int[] working = state.clone();
        if (!propagate(working)) {
            return 0;
        }
        int pick = -1;
        for (int e = 0; e < edges.size(); e++) {
            if (working[e] == UNDECIDED) {
                pick = e;
                break;
            }
        }
        if (pick < 0) {
            if (!solved(working)) {
                return 0;
            }
            if (found[0] == null) {
                found[0] = working.clone();
            }
            return 1;
        }
        guesses++;
        int total = 0;
        for (int value : new int[] {2, 1, 0}) {
            int[] next = working.clone();
            next[pick] = value;
            total += search(next, cap - total, found);
            if (total >= cap) {
                break;
            }
        }
        return total;
    }

    private int[] blankState() {
        int[] state = new int[edges.size()];
        Arrays.fill(state, UNDECIDED);
        return state;
    }

    public int countSolutions(int cap) {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        return search(blankState(), cap, new int[1][]);
    }

    public String solve() {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        int[][] found = new int[1][];
        int count = search(blankState(), 2, found);
        if (count == 0) {
            return "These islands cannot all be joined.";
        }
        bridges = found[0].clone();
        return (guesses == 0
                ? "The counts alone were enough - no guessing."
                : "Counting stalled, so the solver branched " + guesses + " times.")
                + (count == 1 ? "" : " These islands allow more than one bridging.");
    }

    public String hint() {
        for (int e = 0; e < edges.size(); e++) {
            if (bridges[e] != answer[e]) {
                bridges[e] = answer[e];
                int[] edge = edges.get(e);
                return "The link between island " + edge[0] + " and " + edge[1]
                        + " carries " + answer[e] + ".";
            }
        }
        return "Nothing left to give away.";
    }

    // ------------------------------------------------------------------
    // Generating
    // ------------------------------------------------------------------

    /**
     * Grow a connected set of islands by laying bridges from one already
     * placed, so the archipelago is joined by construction. The counts are
     * then read off, and the puzzle is kept only if they force that bridging.
     */
    public void generate(int requested) {
        if (requested < MIN_SIZE || requested > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Use a size between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        size = requested;
        for (int attempt = 0; attempt < 600; attempt++) {
            if (!layout()) {
                continue;
            }
            if (countSolutions(2) == 1) {
                bridges = blankState();
                return;
            }
        }
        bridges = blankState();
    }

    private boolean layout() {
        islandAt = new int[size][size];
        for (int[] line : islandAt) {
            Arrays.fill(line, -1);
        }
        islands = new ArrayList<>();
        List<int[]> planned = new ArrayList<>();

        int row = random.nextInt(size);
        int column = random.nextInt(size);
        islandAt[row][column] = 0;
        islands.add(new int[] {row, column});

        int target = Math.max(4, size * size / 6);
        for (int guard = 0; guard < 400 && islands.size() < target; guard++) {
            int from = random.nextInt(islands.size());
            int[] at = islands.get(from);
            int[][] steps = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            int[] step = steps[random.nextInt(4)];
            int distance = 2 + random.nextInt(3);
            int toRow = at[0] + step[0] * distance;
            int toColumn = at[1] + step[1] * distance;
            if (toRow < 0 || toRow >= size || toColumn < 0 || toColumn >= size) {
                continue;
            }
            if (islandAt[toRow][toColumn] >= 0) {
                continue;
            }
            // The water between must be empty, and must not already be spanned.
            boolean clear = true;
            for (int i = 1; i < distance; i++) {
                int midRow = at[0] + step[0] * i;
                int midColumn = at[1] + step[1] * i;
                clear &= islandAt[midRow][midColumn] < 0 && !spanned(planned, midRow, midColumn);
            }
            if (!clear || spanned(planned, toRow, toColumn)) {
                continue;
            }
            int index = islands.size();
            islandAt[toRow][toColumn] = index;
            islands.add(new int[] {toRow, toColumn});
            planned.add(new int[] {at[0], at[1], toRow, toColumn, 1 + random.nextInt(2)});
        }
        if (islands.size() < 4) {
            return false;
        }

        buildGraph();
        // Turn the planned bridges into an answer over the edge list.
        answer = new int[edges.size()];
        for (int[] plan : planned) {
            int a = islandAt[plan[0]][plan[1]];
            int b = islandAt[plan[2]][plan[3]];
            for (int e = 0; e < edges.size(); e++) {
                int[] edge = edges.get(e);
                if ((edge[0] == a && edge[1] == b) || (edge[0] == b && edge[1] == a)) {
                    answer[e] = plan[4];
                }
            }
        }
        wanted = new int[islands.size()];
        for (int e = 0; e < edges.size(); e++) {
            int[] edge = edges.get(e);
            wanted[edge[0]] += answer[e];
            wanted[edge[1]] += answer[e];
        }
        for (int value : wanted) {
            if (value == 0) {
                return false;   // an island nothing reaches
            }
        }
        return solved(answer);
    }

    /** Whether a cell lies strictly between the ends of a planned bridge. */
    private boolean spanned(List<int[]> planned, int row, int column) {
        for (int[] plan : planned) {
            if (plan[0] == plan[2] && plan[0] == row) {
                if (column > Math.min(plan[1], plan[3]) && column < Math.max(plan[1], plan[3])) {
                    return true;
                }
            } else if (plan[1] == plan[3] && plan[1] == column) {
                if (row > Math.min(plan[0], plan[2]) && row < Math.max(plan[0], plan[2])) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    /** Cycles an edge through none, one, two bridges. */
    public int cycle(int edgeIndex) {
        if (edgeIndex < 0 || edgeIndex >= edges.size()) {
            throw new IllegalArgumentException("There is no link " + edgeIndex + ".");
        }
        int current = bridges[edgeIndex] == UNDECIDED ? 0 : bridges[edgeIndex];
        bridges[edgeIndex] = (current + 1) % 3;
        return bridges[edgeIndex];
    }

    /** The edge joining the two islands nearest a clicked cell, or -1. */
    public int edgeAt(int row, int column) {
        for (int e = 0; e < edges.size(); e++) {
            int[] edge = edges.get(e);
            int[] from = islands.get(edge[0]);
            int[] to = islands.get(edge[1]);
            if (from[0] == to[0] && from[0] == row
                    && column > Math.min(from[1], to[1]) && column < Math.max(from[1], to[1])) {
                return e;
            }
            if (from[1] == to[1] && from[1] == column
                    && row > Math.min(from[0], to[0]) && row < Math.max(from[0], to[0])) {
                return e;
            }
        }
        return -1;
    }

    public boolean complete() {
        for (int e = 0; e < edges.size(); e++) {
            int drawn = bridges[e] == UNDECIDED ? 0 : bridges[e];
            if (drawn != answer[e]) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Islands show their number; the water between two linked islands shows
     * the bridge, single or double, in the direction it runs.
     */
    public char[][] cells() {
        char[][] grid = new char[size][size];
        for (char[] line : grid) {
            Arrays.fill(line, '.');
        }
        for (int e = 0; e < edges.size(); e++) {
            int drawn = bridges[e] == UNDECIDED ? 0 : bridges[e];
            if (drawn == 0) {
                continue;
            }
            int[] edge = edges.get(e);
            int[] from = islands.get(edge[0]);
            int[] to = islands.get(edge[1]);
            if (from[0] == to[0]) {
                for (int column = Math.min(from[1], to[1]) + 1;
                        column < Math.max(from[1], to[1]); column++) {
                    grid[from[0]][column] = drawn == 1 ? '-' : '=';
                }
            } else {
                for (int row = Math.min(from[0], to[0]) + 1;
                        row < Math.max(from[0], to[0]); row++) {
                    grid[row][from[1]] = drawn == 1 ? '|' : '"';
                }
            }
        }
        for (int island = 0; island < islands.size(); island++) {
            int[] at = islands.get(island);
            grid[at[0]][at[1]] = (char) ('0' + Math.min(9, wanted[island]));
        }
        return grid;
    }

    private String detail() {
        if (complete()) {
            return "Solved - every island satisfied and all of them joined.";
        }
        int drawn = 0;
        for (int e = 0; e < edges.size(); e++) {
            drawn += bridges[e] == UNDECIDED ? 0 : bridges[e];
        }
        return String.format("%dx%d, %d islands and %d possible links, %d bridges drawn.",
                size, size, islands.size(), edges.size(), drawn);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("board size:", MIN_SIZE, MAX_SIZE, 7));
        io.muted("Each number is how many bridge ends that island needs. Bridges run "
                + "straight, never cross, and at most two join any pair.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            int choice = io.menu("Hashiwokakero",
                    List.of("Toggle a link", "Hint", "Solve", "New puzzle"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0 -> {
                        int row = io.askInt("row of water:", 0, size - 1, 0);
                        int column = io.askInt("column of water:", 0, size - 1, 0);
                        int edge = edgeAt(row, column);
                        if (edge < 0) {
                            io.warn("No link runs through there.");
                        } else {
                            cycle(edge);
                        }
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
                    generate(Json.integer(body, "size", 7));
                    return board("Click the water between two islands to lay a bridge, "
                            + "again for a double, again to remove it.");
                }
                case "cycle", "step", "toggle" -> {
                    int edge = edgeAt(Json.integer(body, "row", -1),
                            Json.integer(body, "col", -1));
                    if (edge < 0) {
                        return board("");
                    }
                    cycle(edge);
                    return board(complete() ? "That is the bridging." : "");
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
