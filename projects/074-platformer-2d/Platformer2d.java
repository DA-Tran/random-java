package com.randomjava.projects.platformer2d;

import com.randomjava.lib.*;
import java.util.*;

/**
 * 2D Platformer - run and jump across a tile map with gravity and collisions.
 *
 * <p>Collision is where every platformer is won or lost, and the failure is
 * always the same: <b>resolving both axes at once</b>. Move diagonally into a
 * corner, test the destination, find it blocked, and cancel the whole move -
 * and now running along a wall stops the player dead instead of sliding, and a
 * jump that clips a ceiling pixel cancels the horizontal movement too. The
 * axes are handled separately here: move horizontally and resolve, then move
 * vertically and resolve. Sliding along surfaces then falls out for free.
 *
 * <p>Second: <b>landing has to be detected on the resolve, not by looking
 * down.</b> Checking the tile below the player each frame gets it wrong at
 * speed, because a fast fall can move the player from above a platform to
 * below it in one step and the tile below is never solid on any frame that was
 * actually sampled. Grounding is set when a downward move is stopped, which
 * cannot be skipped over.
 *
 * <p>Fixed-step physics in integer subpixels, so behaviour does not depend on
 * frame rate and a test gets the same answer every run.
 */
public final class Platformer2d implements Project {

    public static final Meta META = new Meta(74, "platformer-2d", "Platformer 2D", "Game Development", Kind.GRID,
            Difficulty.ADVANCED, "Run and jump across a tile map with gravity and collisions.",
            "", true);

    public static final int GRAVITY = 1;
    public static final int TERMINAL_FALL = 4;
    public static final int JUMP_STRENGTH = -4;
    public static final int RUN_SPEED = 1;

    private final char[][] tiles;
    private final int rows;
    private final int columns;

    private int x;
    private int y;
    private int verticalSpeed;
    private boolean grounded;
    private int coinsTaken;
    private int coinsTotal;
    private boolean finished;
    private int steps;

    private static final String[] DEFAULT_MAP = {
        "............................",
        "............................",
        "..........ooo...............",
        ".........#####.......ooo....",
        "....................######..",
        "..ooo.......................",
        ".#####....####..............",
        "..........................G.",
        "############...#############"};

    public Platformer2d() { this(DEFAULT_MAP); }

    public Platformer2d(String[] map) {
        if (map == null || map.length < 3) {
            throw new IllegalArgumentException("A map needs at least three rows.");
        }
        rows = map.length;
        columns = map[0].length();
        tiles = new char[rows][];
        for (int r = 0; r < rows; r++) {
            if (map[r].length() != columns) {
                throw new IllegalArgumentException("Row " + r + " is a different width.");
            }
            tiles[r] = map[r].toCharArray();
            for (char c : tiles[r]) { if (c == 'o') { coinsTotal++; } }
        }
        spawn();
    }

    @Override public Meta meta() { return META; }

    private void spawn() {
        x = 1;
        y = 0;
        verticalSpeed = 0;
        grounded = false;
        finished = false;
        steps = 0;
        // Drop onto whatever is under the start.
        for (int r = 0; r < rows; r++) {
            if (solid(r, x)) { y = r - 1; break; }
        }
    }

    public int x() { return x; }
    public int y() { return y; }
    public boolean grounded() { return grounded; }
    public int coins() { return coinsTaken; }
    public int coinsTotal() { return coinsTotal; }
    public boolean finished() { return finished; }
    public int steps() { return steps; }
    public int verticalSpeed() { return verticalSpeed; }

    public boolean solid(int row, int col) {
        if (col < 0 || col >= columns) { return true; }   // walls at the edges
        if (row < 0) { return true; }                      // ceiling
        if (row >= rows) { return false; }                 // the pit is not solid
        return tiles[row][col] == '#';
    }

    public boolean fellOut() { return y >= rows; }

    // ------------------------------------------------------------------
    // Physics
    // ------------------------------------------------------------------

    /** Jumps only from the ground, so the player cannot climb the air. */
    public boolean jump() {
        if (!grounded || finished) { return false; }
        verticalSpeed = JUMP_STRENGTH;
        grounded = false;
        return true;
    }

    /**
     * One fixed step. Horizontal first, resolved; then vertical, resolved.
     *
     * @param direction -1 left, 0 still, 1 right
     */
    public void step(int direction) {
        if (finished || fellOut()) { return; }
        steps++;

        // --- horizontal, resolved on its own ---
        int wantedX = x + Integer.signum(direction) * RUN_SPEED;
        if (!solid(y, wantedX)) {
            x = wantedX;
        }

        // --- vertical, resolved on its own ---
        verticalSpeed = Math.min(TERMINAL_FALL, verticalSpeed + GRAVITY);
        int direction2 = Integer.signum(verticalSpeed);
        grounded = false;
        for (int moved = 0; moved < Math.abs(verticalSpeed); moved++) {
            int nextY = y + direction2;
            if (solid(nextY, x)) {
                // Stopped by something. Landing is recorded here, where it
                // cannot be stepped over, rather than by sampling the tile below.
                if (direction2 > 0) { grounded = true; }
                verticalSpeed = 0;
                break;
            }
            y = nextY;
        }

        collect();
    }

    private void collect() {
        if (y < 0 || y >= rows || x < 0 || x >= columns) { return; }
        if (tiles[y][x] == 'o') {
            tiles[y][x] = '.';
            coinsTaken++;
        } else if (tiles[y][x] == 'G') {
            finished = true;
        }
    }

    public void reset() {
        for (char[] row : tiles) {
            for (int c = 0; c < row.length; c++) { if (row[c] == '.') { continue; } }
        }
        spawn();
        coinsTaken = 0;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public char[][] cells() {
        char[][] grid = new char[rows][];
        for (int r = 0; r < rows; r++) { grid[r] = tiles[r].clone(); }
        if (y >= 0 && y < rows && x >= 0 && x < columns) { grid[y][x] = '@'; }
        return grid;
    }

    private String detail() {
        if (finished) {
            return String.format("Reached the goal in %d steps with %d of %d coins.",
                    steps, coinsTaken, coinsTotal);
        }
        if (fellOut()) { return "Fell off the map. Press reset."; }
        return String.format("at (%d,%d), %s, falling at %d, %d of %d coins",
                x, y, grounded ? "on the ground" : "in the air",
                verticalSpeed, coinsTaken, coinsTotal);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("a and d to run, w to jump, blank to stand still, q to quit.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            if (finished || fellOut()) { return; }
            String key = io.ask("move:").trim().toLowerCase(Locale.ROOT);
            if (key.equals("q")) { return; }
            int direction = key.equals("a") ? -1 : key.equals("d") ? 1 : 0;
            if (key.equals("w")) { jump(); }
            step(direction);
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> { reset(); return board("New run."); }
                case "left" -> { step(-1); return board(""); }
                case "right" -> { step(1); return board(""); }
                case "jump" -> {
                    boolean jumped = jump();
                    step(Json.integer(body, "direction", 0));
                    return board(jumped ? "" : "You can only jump from the ground.");
                }
                case "step" -> { step(0); return board(""); }
                case "solve" -> {
                    // Run right, jumping whenever a wall is in the way.
                    for (int i = 0; i < 200 && !finished && !fellOut(); i++) {
                        if (grounded && solid(y, x + 1)) { jump(); }
                        step(1);
                    }
                    return board("Ran right for 200 steps, jumping at walls.");
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
