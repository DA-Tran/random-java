package com.randomjava.projects.game2048;

import com.randomjava.lib.*;
import java.util.*;

/**
 * 2048 - slide tiles together until two of them make 2048.
 *
 * <p>The merge rule is the whole game, and it has one clause everybody forgets:
 * <b>a tile that has already merged this move cannot merge again.</b> Without
 * it, a row of {@code 2 2 4 .} slid left gives {@code 8 . . .} - the twos make
 * a four, which immediately eats the four beside it. The correct answer is
 * {@code 4 4 . .}. It is a small difference that compounds: the naive version
 * collapses whole rows in a single move and the game becomes trivial.
 *
 * <p>The second rule: <b>a move that changes nothing is not a move</b>, and
 * must not spawn a new tile. Spawning on a no-op lets a player fill the board
 * by pressing a direction that does nothing, which is how a lost game becomes
 * unloseable.
 *
 * <p>All four directions are implemented by rotating the board so that only
 * "slide left" has to be correct. Four separate near-identical implementations
 * is four places for that merge clause to be subtly wrong in.
 */
public final class Game2048 implements Project {

    public static final Meta META = new Meta(226, "game-2048", "Game 2048", "Logic and Puzzle Games", Kind.GRID,
            Difficulty.BEGINNER, "Slide and merge matching tiles, doubling each time, to reach 2048.",
            "", true);

    public static final int TARGET = 2048;

    private final int side;
    private int[][] grid;
    private int score;
    private int moves;
    private boolean won;
    private final Random random;

    public Game2048() { this(4, new Random()); }

    public Game2048(int side, Random random) {
        if (side < 2 || side > 10) {
            throw new IllegalArgumentException("The board must be between 2 and 10 a side.");
        }
        this.side = side;
        this.random = random;
        restart();
    }

    @Override public Meta meta() { return META; }

    public void restart() {
        grid = new int[side][side];
        score = 0;
        moves = 0;
        won = false;
        spawn();
        spawn();
    }

    public int side() { return side; }
    public int score() { return score; }
    public int moves() { return moves; }
    public boolean won() { return won; }
    public int[][] grid() {
        int[][] copy = new int[side][];
        for (int r = 0; r < side; r++) { copy[r] = grid[r].clone(); }
        return copy;
    }

    public void setGrid(int[][] board) {
        for (int r = 0; r < side; r++) { grid[r] = board[r].clone(); }
    }

    public int highest() {
        int best = 0;
        for (int[] row : grid) {
            for (int value : row) { best = Math.max(best, value); }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // The merge
    // ------------------------------------------------------------------

    /**
     * Slides one row left and merges it, returning the new row.
     *
     * <p>{@code merged} is the clause that matters: once a tile has been formed
     * by a merge this move, it is finished and cannot absorb the next one.
     */
    public static int[] slideRow(int[] row, int[] scoreGained) {
        int size = row.length;
        int[] packed = new int[size];
        int next = 0;
        for (int value : row) {
            if (value != 0) { packed[next++] = value; }
        }
        int[] out = new int[size];
        int write = 0;
        int read = 0;
        while (read < next) {
            if (read + 1 < next && packed[read] == packed[read + 1]) {
                int merged = packed[read] * 2;
                out[write++] = merged;
                if (scoreGained != null) { scoreGained[0] += merged; }
                // Skip both tiles: the one just made is done for this move.
                read += 2;
            } else {
                out[write++] = packed[read++];
            }
        }
        return out;
    }

    private static int[][] rotate(int[][] board) {
        int n = board.length;
        int[][] out = new int[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) { out[c][n - 1 - r] = board[r][c]; }
        }
        return out;
    }

    /**
     * Slides the whole board. Direction 0 left, 1 up, 2 right, 3 down.
     *
     * <p>Rotated into "slide left" and back, so there is exactly one merge
     * implementation to get right rather than four.
     */
    public boolean slide(int direction) {
        int[][] working = grid;
        for (int i = 0; i < direction; i++) { working = rotate(working); }

        int[] gained = {0};
        int[][] slid = new int[side][];
        for (int r = 0; r < side; r++) { slid[r] = slideRow(working[r], gained); }

        for (int i = direction; i < 4; i++) { slid = rotate(slid); }

        if (Arrays.deepEquals(grid, slid)) {
            // Nothing moved, so nothing happened. Spawning here would let a
            // player fill the board with a key that does nothing.
            return false;
        }
        grid = slid;
        score += gained[0];
        moves++;
        if (highest() >= TARGET) { won = true; }
        spawn();
        return true;
    }

    private void spawn() {
        List<int[]> empty = new ArrayList<>();
        for (int r = 0; r < side; r++) {
            for (int c = 0; c < side; c++) {
                if (grid[r][c] == 0) { empty.add(new int[]{r, c}); }
            }
        }
        if (empty.isEmpty()) { return; }
        int[] at = empty.get(random.nextInt(empty.size()));
        grid[at[0]][at[1]] = random.nextInt(10) == 0 ? 4 : 2;
    }

    /** Over when the board is full and no neighbours match. */
    public boolean stuck() {
        for (int r = 0; r < side; r++) {
            for (int c = 0; c < side; c++) {
                if (grid[r][c] == 0) { return false; }
                if (c + 1 < side && grid[r][c] == grid[r][c + 1]) { return false; }
                if (r + 1 < side && grid[r][c] == grid[r + 1][c]) { return false; }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public char[][] cells() {
        // One character per cell, so powers of two are drawn by their exponent:
        // 2 is "1", 4 is "2", 1024 is "a". Compact and unambiguous.
        char[][] out = new char[side][side];
        for (int r = 0; r < side; r++) {
            for (int c = 0; c < side; c++) {
                int value = grid[r][c];
                if (value == 0) { out[r][c] = '.'; continue; }
                int exponent = Integer.numberOfTrailingZeros(value);
                out[r][c] = exponent < 10 ? (char) ('0' + exponent)
                        : (char) ('a' + exponent - 10);
            }
        }
        return out;
    }

    private String detail() {
        StringBuilder sb = new StringBuilder(String.format(
                "score %d, %d moves, highest tile %d", score, moves, highest()));
        if (won) { sb.append(" - reached ").append(TARGET).append('!'); }
        if (stuck()) { sb.append(" - no moves left."); }
        sb.append("\n  Cells show the exponent: 1 is 2, 2 is 4, b is 2048.");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("w a s d to slide, q to quit.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            if (stuck()) { return; }
            String key = io.ask("slide:").trim().toLowerCase(Locale.ROOT);
            if (key.equals("q")) { return; }
            int direction = switch (key) {
                case "a" -> 0;
                case "w" -> 1;
                case "d" -> 2;
                case "s" -> 3;
                default -> -1;
            };
            if (direction < 0 || !slide(direction)) { io.muted("Nothing moved."); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> { restart(); return board("New game."); }
                case "left" -> { return board(slide(0) ? "" : "Nothing moved."); }
                case "up" -> { return board(slide(1) ? "" : "Nothing moved."); }
                case "right" -> { return board(slide(2) ? "" : "Nothing moved."); }
                case "down" -> { return board(slide(3) ? "" : "Nothing moved."); }
                case "step" -> {
                    // One greedy move: whichever direction scores most now.
                    int best = -1;
                    int bestGain = -1;
                    for (int d = 0; d < 4; d++) {
                        Game2048 trial = new Game2048(side, new Random(0));
                        trial.setGrid(grid());
                        int before = trial.score();
                        if (trial.slide(d) && trial.score() - before > bestGain) {
                            bestGain = trial.score() - before;
                            best = d;
                        }
                    }
                    if (best < 0) { return board("No move changes anything."); }
                    slide(best);
                    return board("Played the move that scores most right now - which is a "
                            + "poor strategy, but it is a move.");
                }
                case "solve" -> {
                    int played = 0;
                    while (!stuck() && played < 500) {
                        boolean moved = false;
                        for (int d = 0; d < 4 && !moved; d++) { moved = slide(d); }
                        if (!moved) { break; }
                        played++;
                    }
                    return board("Played " + played + " greedy moves.");
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
