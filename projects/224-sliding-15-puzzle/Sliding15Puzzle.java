package com.randomjava.projects.sliding15puzzle;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Sliding 15 Puzzle - fifteen tiles and a gap, slid into order.
 *
 * <p><b>Half of all arrangements cannot be solved, and shuffling the tiles
 * produces them half the time.</b> This is the thing that catches people out:
 * scramble by permuting the tiles at random and roughly every other puzzle you
 * hand out is impossible, with no way to tell by looking.
 *
 * <p>The invariant is parity. Every legal slide swaps the blank with one tile,
 * which changes the permutation's parity by one - and it also moves the blank
 * one row, changing the blank's row parity by one. So
 * {@code inversions + blankRow} has a parity that no move can alter. The solved
 * state has it even, therefore any reachable state has it even too, and any
 * arrangement where it is odd is unreachable.
 *
 * <p>Two ways to avoid handing out an impossible puzzle. Generating random
 * arrangements and rejecting the odd ones works and is what
 * {@link #solvable} exists for. Shuffling by making legal moves backwards from
 * the solved state cannot produce one at all, since every state it reaches is
 * by construction reachable - that is what {@link #shuffle} does.
 */
public final class Sliding15Puzzle implements Project {

    public static final Meta META = new Meta(224, "sliding-15-puzzle", "Sliding 15 Puzzle", "Logic and Puzzle Games", Kind.GRID,
            Difficulty.BEGINNER, "Slide numbered tiles into order through the one empty square.",
            "", true);

    private int side = 4;
    private int[] tiles;
    private int blank;
    private int moves;
    private final Random random;

    public Sliding15Puzzle() { this(4, new Random()); }

    public Sliding15Puzzle(int side, Random random) {
        this.random = random;
        reset(side);
    }

    @Override public Meta meta() { return META; }

    public void reset(int newSide) {
        if (newSide < 2 || newSide > 8) {
            throw new IllegalArgumentException("The board must be between 2 and 8 a side.");
        }
        side = newSide;
        tiles = new int[side * side];
        for (int i = 0; i < tiles.length - 1; i++) { tiles[i] = i + 1; }
        tiles[tiles.length - 1] = 0;
        blank = tiles.length - 1;
        moves = 0;
    }

    public int side() { return side; }
    public int moves() { return moves; }
    public int[] tiles() { return tiles.clone(); }
    public boolean solved() {
        for (int i = 0; i < tiles.length - 1; i++) {
            if (tiles[i] != i + 1) { return false; }
        }
        return tiles[tiles.length - 1] == 0;
    }

    // ------------------------------------------------------------------
    // Parity
    // ------------------------------------------------------------------

    /** Pairs of tiles that are in the wrong order relative to each other. */
    public static int inversions(int[] board) {
        int count = 0;
        for (int i = 0; i < board.length; i++) {
            if (board[i] == 0) { continue; }
            for (int j = i + 1; j < board.length; j++) {
                if (board[j] != 0 && board[i] > board[j]) { count++; }
            }
        }
        return count;
    }

    /**
     * Whether an arrangement can be reached from the solved state.
     *
     * <p>On an odd-width board the blank's row does not affect the count, so
     * solvability is just even inversions. On an even-width board it does, and
     * the rule is the combined parity - which is why the 4x4 case is the one
     * everybody gets wrong.
     */
    public static boolean solvable(int[] board, int side) {
        int inversions = inversions(board);
        if (side % 2 == 1) { return inversions % 2 == 0; }
        int blankIndex = 0;
        for (int i = 0; i < board.length; i++) { if (board[i] == 0) { blankIndex = i; } }
        int blankRowFromBottom = side - (blankIndex / side);
        return (inversions + blankRowFromBottom) % 2 == 1;
    }

    public boolean solvable() { return solvable(tiles, side); }

    // ------------------------------------------------------------------
    // Moving
    // ------------------------------------------------------------------

    /** Indices the blank could swap with. */
    public List<Integer> movable() {
        List<Integer> out = new ArrayList<>();
        int row = blank / side;
        int col = blank % side;
        if (row > 0) { out.add(blank - side); }
        if (row < side - 1) { out.add(blank + side); }
        if (col > 0) { out.add(blank - 1); }
        if (col < side - 1) { out.add(blank + 1); }
        return out;
    }

    /** Slides the tile at {@code index} into the gap, if they are neighbours. */
    public boolean slide(int index) {
        if (index < 0 || index >= tiles.length) {
            throw new IllegalArgumentException("There is no square " + index + ".");
        }
        if (!movable().contains(index)) { return false; }
        tiles[blank] = tiles[index];
        tiles[index] = 0;
        blank = index;
        moves++;
        return true;
    }

    /** Slides by tile number rather than position, which is how people think. */
    public boolean slideTile(int tile) {
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == tile) { return slide(i); }
        }
        throw new IllegalArgumentException("There is no tile " + tile + ".");
    }

    /**
     * Shuffles by walking the blank around at random.
     *
     * <p>Every state reached this way is reachable by construction, so this
     * cannot produce an impossible puzzle - no parity check needed afterwards.
     */
    public void shuffle(int steps) {
        for (int i = 0; i < steps; i++) {
            List<Integer> options = movable();
            slide(options.get(random.nextInt(options.size())));
        }
        moves = 0;
    }

    /** Sets an arbitrary arrangement, for tests. Refuses an impossible one. */
    public void setTiles(int[] board) {
        if (board.length != side * side) {
            throw new IllegalArgumentException("That is not a " + side + "x" + side + " board.");
        }
        boolean[] seen = new boolean[board.length];
        for (int value : board) {
            if (value < 0 || value >= board.length || seen[value]) {
                throw new IllegalArgumentException("Tiles must be 0 to " + (board.length - 1)
                        + ", each exactly once.");
            }
            seen[value] = true;
        }
        if (!solvable(board, side)) {
            throw new IllegalArgumentException(
                    "That arrangement cannot be reached from the solved state. Its parity is "
                    + "wrong, so no sequence of slides will ever order it.");
        }
        tiles = board.clone();
        for (int i = 0; i < tiles.length; i++) { if (tiles[i] == 0) { blank = i; } }
        moves = 0;
    }

    /** How many tiles are already home. A simple progress measure. */
    public int inPlace() {
        int count = 0;
        for (int i = 0; i < tiles.length - 1; i++) {
            if (tiles[i] == i + 1) { count++; }
        }
        return count;
    }

    /** Sum of each tile's distance from home: never overestimates the moves left. */
    public int manhattan() {
        int total = 0;
        for (int i = 0; i < tiles.length; i++) {
            int tile = tiles[i];
            if (tile == 0) { continue; }
            int home = tile - 1;
            total += Math.abs(i / side - home / side) + Math.abs(i % side - home % side);
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public char[][] cells() {
        // Two characters per tile would need a wider grid, so tiles above 9 are
        // drawn as letters - readable, and keeps every cell one character.
        char[][] grid = new char[side][side];
        for (int i = 0; i < tiles.length; i++) {
            int tile = tiles[i];
            grid[i / side][i % side] = tile == 0 ? '.'
                    : tile < 10 ? (char) ('0' + tile) : (char) ('a' + tile - 10);
        }
        return grid;
    }

    private String detail() {
        if (solved()) { return "Solved in " + moves + " moves."; }
        return String.format("%d moves, %d of %d tiles home, %d moves away at best",
                moves, inPlace(), tiles.length - 1, manhattan());
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("Type a tile number to slide it. Tiles above 9 show as letters.");
        shuffle(200);
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            if (solved()) { return; }
            try {
                int tile = io.askInt("slide tile:", 1, side * side - 1, 1);
                if (!slideTile(tile)) { io.error("That tile is not next to the gap."); }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    reset(Math.min(Math.max(Json.integer(body, "size", side), 2), 8));
                    shuffle(Math.max(50, side * side * 8));
                    return board("Shuffled by making legal moves, so it is always solvable.");
                }
                case "step", "slide" -> {
                    int index = Json.integer(body, "index", -1);
                    if (index < 0) {
                        index = Json.integer(body, "row", 0) * side + Json.integer(body, "col", 0);
                    }
                    return board(slide(index) ? "" : "That tile is not next to the gap.");
                }
                case "solve" -> {
                    reset(side);
                    return board("Put straight back in order. Solving it properly needs A* with "
                            + "the Manhattan heuristic, which is not written here.");
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }

    private Map<String, Object> board(String note) {
        return Json.ok("board", Json.grid(cells()),
                "detail", note.isEmpty() ? detail() : note + "\n  " + detail());
    }
}
