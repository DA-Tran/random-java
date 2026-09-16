package com.randomjava.projects.minesweeper;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Minesweeper - clear the board without uncovering a mine.
 *
 * <p>Two rules separate a playable Minesweeper from a frustrating one, and both
 * are about the <em>first</em> click.
 *
 * <p><b>The first click must never be a mine.</b> Placing mines before the game
 * starts means one click in five or so ends it immediately, which is not a
 * puzzle, it is a coin toss. Mines are placed after the first click and the
 * clicked square is excluded.
 *
 * <p><b>The first click should open a region, not a single number.</b> Modern
 * implementations also keep the eight neighbours clear, so the first click
 * always cascades and gives something to reason from. Without it a game can
 * open with a lone "4" and no deducible move anywhere.
 *
 * <p>The flood fill is the other place this goes wrong. Revealing a zero has to
 * cascade to its neighbours, and a recursive version overflows the stack on a
 * large board with a big empty region - which is exactly the board where the
 * cascade matters. This uses an explicit queue.
 */
public final class Minesweeper implements Project {

    public static final Meta META = new Meta(225, "minesweeper", "Minesweeper", "Logic and Puzzle Games", Kind.GRID,
            Difficulty.INTERMEDIATE, "Clear a grid without detonating a mine, using the counts as clues.",
            "", true);

    public enum State { PLAYING, WON, LOST }

    private int rows;
    private int columns;
    private int mineCount;
    private boolean[][] mine;
    private boolean[][] revealed;
    private boolean[][] flagged;
    private boolean placed;
    private State state = State.PLAYING;
    private final Random random;

    public Minesweeper() { this(9, 9, 10, new Random()); }

    public Minesweeper(int rows, int columns, int mines, Random random) {
        this.random = random;
        start(rows, columns, mines);
    }

    @Override public Meta meta() { return META; }

    public void start(int newRows, int newColumns, int mines) {
        if (newRows < 3 || newRows > 24 || newColumns < 3 || newColumns > 30) {
            throw new IllegalArgumentException("Use 3 to 24 rows and 3 to 30 columns.");
        }
        // The first click and its neighbours are kept clear, so at most
        // rows*cols - 9 squares can hold a mine.
        int maximum = Math.max(1, newRows * newColumns - 9);
        if (mines < 1 || mines > maximum) {
            throw new IllegalArgumentException(
                    "That board holds between 1 and " + maximum + " mines, because the first "
                    + "click and its eight neighbours are always left clear.");
        }
        rows = newRows;
        columns = newColumns;
        mineCount = mines;
        mine = new boolean[rows][columns];
        revealed = new boolean[rows][columns];
        flagged = new boolean[rows][columns];
        placed = false;
        state = State.PLAYING;
    }

    public int rows() { return rows; }
    public int columns() { return columns; }
    public int mines() { return mineCount; }
    public State state() { return state; }
    public boolean placed() { return placed; }
    public boolean isMine(int r, int c) { return mine[r][c]; }
    public boolean isRevealed(int r, int c) { return revealed[r][c]; }
    public boolean isFlagged(int r, int c) { return flagged[r][c]; }

    /** Mines are laid only once the first square is known, and never on it. */
    private void placeMines(int safeRow, int safeColumn) {
        List<int[]> spots = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (Math.abs(r - safeRow) <= 1 && Math.abs(c - safeColumn) <= 1) { continue; }
                spots.add(new int[]{r, c});
            }
        }
        Collections.shuffle(spots, random);
        for (int i = 0; i < mineCount && i < spots.size(); i++) {
            mine[spots.get(i)[0]][spots.get(i)[1]] = true;
        }
        placed = true;
    }

    public int neighbouringMines(int row, int column) {
        int count = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) { continue; }
                int r = row + dr;
                int c = column + dc;
                if (r >= 0 && r < rows && c >= 0 && c < columns && mine[r][c]) { count++; }
            }
        }
        return count;
    }

    /**
     * Uncovers a square, cascading through any zeros.
     *
     * <p>Iterative on purpose: a recursive flood fill overflows the stack on a
     * large board with a big open region, which is the case that matters.
     */
    public boolean reveal(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            throw new IllegalArgumentException("That square is off the board.");
        }
        if (state != State.PLAYING || revealed[row][column] || flagged[row][column]) {
            return false;
        }
        if (!placed) { placeMines(row, column); }

        if (mine[row][column]) {
            revealed[row][column] = true;
            state = State.LOST;
            return true;
        }

        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{row, column});
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            int r = at[0];
            int c = at[1];
            if (r < 0 || r >= rows || c < 0 || c >= columns) { continue; }
            if (revealed[r][c] || flagged[r][c] || mine[r][c]) { continue; }
            revealed[r][c] = true;
            if (neighbouringMines(r, c) != 0) { continue; }
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr != 0 || dc != 0) { queue.add(new int[]{r + dr, c + dc}); }
                }
            }
        }
        checkWon();
        return true;
    }

    public boolean flag(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            throw new IllegalArgumentException("That square is off the board.");
        }
        if (state != State.PLAYING || revealed[row][column]) { return false; }
        flagged[row][column] = !flagged[row][column];
        return true;
    }

    /** Won when every square that is not a mine has been uncovered. */
    private void checkWon() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (!mine[r][c] && !revealed[r][c]) { return; }
            }
        }
        state = State.WON;
    }

    public int flagsPlaced() {
        int count = 0;
        for (boolean[] row : flagged) {
            for (boolean f : row) { if (f) { count++; } }
        }
        return count;
    }

    public int revealedCount() {
        int count = 0;
        for (boolean[] row : revealed) {
            for (boolean r : row) { if (r) { count++; } }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public char[][] cells() {
        char[][] grid = new char[rows][columns];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (flagged[r][c] && !revealed[r][c]) { grid[r][c] = '*'; }
                else if (!revealed[r][c]) {
                    // A lost game shows where the mines were.
                    grid[r][c] = state == State.LOST && mine[r][c] ? 'X' : '#';
                } else if (mine[r][c]) { grid[r][c] = 'X'; }
                else {
                    int count = neighbouringMines(r, c);
                    grid[r][c] = count == 0 ? '.' : (char) ('0' + count);
                }
            }
        }
        return grid;
    }

    private String detail() {
        return switch (state) {
            case WON -> "Cleared, with " + mineCount + " mines never touched.";
            case LOST -> "That was a mine. " + revealedCount() + " squares had been cleared.";
            case PLAYING -> String.format("%d mines, %d flags placed, %d of %d squares cleared.%s",
                    mineCount, flagsPlaced(), revealedCount(), rows * columns - mineCount,
                    placed ? "" : " Mines are laid after your first click, never under it.");
        };
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("# hidden, * flagged, . empty, digits count neighbouring mines.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            if (state != State.PLAYING) { return; }
            try {
                int row = io.askInt("row:", 0, rows - 1, 0);
                int column = io.askInt("column:", 0, columns - 1, 0);
                if (io.askYesNo("flag it instead of clearing?", false)) { flag(row, column); }
                else { reveal(row, column); }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    int side = Math.min(Math.max(Json.integer(body, "size", 9), 3), 24);
                    start(side, side, Math.max(1, side * side / 8));
                    return board("Click anywhere. The first square is never a mine.");
                }
                case "step", "reveal" -> {
                    reveal(Json.integer(body, "row", 0), Json.integer(body, "col", 0));
                    return board("");
                }
                case "flag" -> {
                    flag(Json.integer(body, "row", 0), Json.integer(body, "col", 0));
                    return board("");
                }
                case "solve" -> {
                    if (!placed) { reveal(rows / 2, columns / 2); }
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < columns; c++) {
                            if (!mine[r][c]) { revealed[r][c] = true; }
                        }
                    }
                    checkWon();
                    return board("Revealed every safe square. Playing it properly means "
                            + "constraint solving on the counts, which is not written here.");
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
