package com.randomjava.projects.sokoban;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Sokoban - push crates onto the marked squares.
 *
 * <p>Pushing is the rule that has to be exactly right: you can only push a
 * crate if the square <em>beyond</em> it is clear. Walk into a crate with a
 * wall behind it and nothing happens; walk into two crates in a row and nothing
 * happens either, since a push moves one crate and not a train of them.
 *
 * <p><b>Deadlock detection is what makes it playable.</b> A crate pushed into a
 * corner can never be moved again - nothing can get behind it on either axis -
 * so if that corner is not a goal the level is already lost. Without this the
 * player keeps shuffling around a puzzle that ended ten moves ago, and Sokoban
 * levels are long enough for that to be genuinely cruel. {@link #deadlocked}
 * reports it as soon as it happens.
 *
 * <p>The check here is the simple corner case only. Real solvers also detect
 * crates frozen against walls in pairs, and whole dead regions; those are
 * harder and are not attempted, so this can miss a deadlock but never invents
 * one.
 */
public final class Sokoban implements Project {

    public static final Meta META = new Meta(227, "sokoban", "Sokoban", "Logic and Puzzle Games", Kind.GRID,
            Difficulty.INTERMEDIATE, "Push crates onto targets in a warehouse, never pulling and never squeezing past.",
            "", true);

    private static final String[] DEFAULT_LEVEL = {
        "########",
        "#      #",
        "#  $   #",
        "#  .@  #",
        "#   $  #",
        "#  .   #",
        "#      #",
        "########"};

    private char[][] walls;
    private boolean[][] goal;
    private boolean[][] crate;
    private int playerRow;
    private int playerCol;
    private int rows;
    private int columns;
    private int moves;
    private int pushes;

    public Sokoban() { this(DEFAULT_LEVEL); }

    public Sokoban(String[] level) { load(level); }

    @Override public Meta meta() { return META; }

    /** '#' wall, '$' crate, '.' goal, '@' player, '*' crate on goal, '+' player on goal. */
    public void load(String[] level) {
        if (level == null || level.length < 3) {
            throw new IllegalArgumentException("A level needs at least three rows.");
        }
        rows = level.length;
        columns = level[0].length();
        walls = new char[rows][columns];
        goal = new boolean[rows][columns];
        crate = new boolean[rows][columns];
        playerRow = -1;
        moves = 0;
        pushes = 0;

        for (int r = 0; r < rows; r++) {
            if (level[r].length() != columns) {
                throw new IllegalArgumentException("Row " + r + " is a different width.");
            }
            for (int c = 0; c < columns; c++) {
                char ch = level[r].charAt(c);
                walls[r][c] = ch == '#' ? '#' : ' ';
                if (ch == '.' || ch == '*' || ch == '+') { goal[r][c] = true; }
                if (ch == '$' || ch == '*') { crate[r][c] = true; }
                if (ch == '@' || ch == '+') { playerRow = r; playerCol = c; }
            }
        }
        if (playerRow < 0) { throw new IllegalArgumentException("The level has no player."); }
        int crates = 0;
        int goals = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (crate[r][c]) { crates++; }
                if (goal[r][c]) { goals++; }
            }
        }
        if (crates == 0) { throw new IllegalArgumentException("The level has no crates."); }
        if (crates != goals) {
            throw new IllegalArgumentException(
                    "The level has " + crates + " crates and " + goals + " goals. Unequal "
                    + "counts make it either impossible or trivially incomplete.");
        }
    }

    public int rows() { return rows; }
    public int columns() { return columns; }
    public int moves() { return moves; }
    public int pushes() { return pushes; }
    public int playerRow() { return playerRow; }
    public int playerCol() { return playerCol; }
    public boolean isWall(int r, int c) {
        return r < 0 || r >= rows || c < 0 || c >= columns || walls[r][c] == '#';
    }
    public boolean isCrate(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < columns && crate[r][c];
    }
    public boolean isGoal(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < columns && goal[r][c];
    }

    public boolean solved() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (crate[r][c] && !goal[r][c]) { return false; }
            }
        }
        return true;
    }

    /**
     * Moves the player, pushing one crate if there is room beyond it.
     *
     * @param dRow -1 up, 1 down; dCol -1 left, 1 right
     */
    public boolean move(int dRow, int dCol) {
        if (Math.abs(dRow) + Math.abs(dCol) != 1) {
            throw new IllegalArgumentException("Move one square, along one axis.");
        }
        int nextRow = playerRow + dRow;
        int nextCol = playerCol + dCol;
        if (isWall(nextRow, nextCol)) { return false; }

        if (isCrate(nextRow, nextCol)) {
            int beyondRow = nextRow + dRow;
            int beyondCol = nextCol + dCol;
            // A push needs the far side clear: not a wall, and not a second
            // crate - a push moves one crate, never a row of them.
            if (isWall(beyondRow, beyondCol) || isCrate(beyondRow, beyondCol)) { return false; }
            crate[nextRow][nextCol] = false;
            crate[beyondRow][beyondCol] = true;
            pushes++;
        }
        playerRow = nextRow;
        playerCol = nextCol;
        moves++;
        return true;
    }

    /**
     * A crate in a corner can never move again, so if that corner is not a goal
     * the level is lost. Reported immediately rather than letting the player
     * carry on with a puzzle that has already ended.
     */
    public boolean deadlocked() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (!crate[r][c] || goal[r][c]) { continue; }
                boolean verticallyStuck = isWall(r - 1, c) || isWall(r + 1, c);
                boolean horizontallyStuck = isWall(r, c - 1) || isWall(r, c + 1);
                if (verticallyStuck && horizontallyStuck) { return true; }
            }
        }
        return false;
    }

    public int cratesHome() {
        int count = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (crate[r][c] && goal[r][c]) { count++; }
            }
        }
        return count;
    }

    public int crateCount() {
        int count = 0;
        for (boolean[] row : crate) {
            for (boolean b : row) { if (b) { count++; } }
        }
        return count;
    }

    public char[][] cells() {
        char[][] grid = new char[rows][columns];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (walls[r][c] == '#') { grid[r][c] = '#'; }
                else if (crate[r][c]) { grid[r][c] = goal[r][c] ? '*' : '$'; }
                else if (r == playerRow && c == playerCol) { grid[r][c] = '@'; }
                else { grid[r][c] = goal[r][c] ? '.' : ' '; }
            }
        }
        return grid;
    }

    private String detail() {
        if (solved()) {
            return String.format("Solved in %d moves and %d pushes.", moves, pushes);
        }
        if (deadlocked()) {
            return "A crate is wedged in a corner it cannot leave. This level is already lost - "
                    + "press reset.";
        }
        return String.format("%d of %d crates home, %d moves, %d pushes",
                cratesHome(), crateCount(), moves, pushes);
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("w a s d to move. $ crate, . goal, * crate home, @ you.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            if (solved()) { return; }
            String key = io.ask("move:").trim().toLowerCase(Locale.ROOT);
            if (key.equals("q")) { return; }
            if (key.equals("r")) { load(DEFAULT_LEVEL); continue; }
            switch (key) {
                case "w" -> move(-1, 0);
                case "s" -> move(1, 0);
                case "a" -> move(0, -1);
                case "d" -> move(0, 1);
                default -> io.muted("w a s d, r to reset, q to quit.");
            }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> { load(DEFAULT_LEVEL); return board("New level."); }
                case "up" -> { return board(move(-1, 0) ? "" : "Blocked."); }
                case "down" -> { return board(move(1, 0) ? "" : "Blocked."); }
                case "left" -> { return board(move(0, -1) ? "" : "Blocked."); }
                case "right" -> { return board(move(0, 1) ? "" : "Blocked."); }
                case "step" -> { return board(""); }
                case "solve" -> {
                    load(DEFAULT_LEVEL);
                    return board("Reset. Solving Sokoban properly is PSPACE-complete, and a "
                            + "search is not written here.");
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
