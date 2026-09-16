package com.randomjava.projects.tetris;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Tetris - falling tetrominoes you rotate and drop to clear lines.
 *
 * <p>Rotation and line clearing are where this is normally got wrong, and both
 * failures survive casual play.
 *
 * <ul>
 *   <li><b>Rotating against a wall.</b> Rotating an I piece flat against the
 *       left edge puts two of its cells off the board. Refusing the rotation
 *       outright is playable but feels broken, because the piece plainly has
 *       room one column over. Real games "kick": try the rotation shifted a
 *       column or two, and take the first offset that fits. That is what
 *       {@link #KICKS} is.</li>
 *   <li><b>Clearing several rows at once.</b> The natural loop walks down the
 *       board removing full rows as it finds them, which shifts everything
 *       below into an index the loop has already passed. Four simultaneous
 *       lines - the whole point of the I piece - then clear as three. Rows are
 *       instead rebuilt into a fresh board, keeping only the incomplete ones,
 *       so the count cannot drift.</li>
 *   <li><b>Spawning into a full board.</b> If the new piece overlaps settled
 *       blocks the game is over at that moment. Letting it spawn anyway leaves
 *       a piece fused into the stack.</li>
 * </ul>
 *
 * <p>Rotation is done by turning coordinates inside the piece's bounding box:
 * {@code (row, col) -> (col, n - 1 - row)}. The box size is what makes each
 * piece behave correctly - O sits in a 2x2 and so is unchanged by rotation,
 * which is the right answer and falls out of the arithmetic rather than being
 * special-cased.
 */
public final class Tetris implements Project {

    public static final Meta META = new Meta(72, "tetris", "Tetris", "Game Development", Kind.GRID,
            Difficulty.INTERMEDIATE, "Falling tetrominoes you rotate and drop to clear lines.",
            "", true);

    /** Column offsets tried when a rotation does not fit where it is. */
    public static final int[] KICKS = {0, -1, 1, -2, 2};

    /** Points for clearing 1, 2, 3 or 4 rows. Four at once is worth far more. */
    private static final int[] LINE_SCORE = {0, 100, 300, 500, 800};

    /** A piece: its cells in a square box, and the size of that box. */
    public enum Shape {
        I('I', 4, new int[][]{{1, 0}, {1, 1}, {1, 2}, {1, 3}}),
        O('O', 2, new int[][]{{0, 0}, {0, 1}, {1, 0}, {1, 1}}),
        T('T', 3, new int[][]{{0, 1}, {1, 0}, {1, 1}, {1, 2}}),
        S('S', 3, new int[][]{{0, 1}, {0, 2}, {1, 0}, {1, 1}}),
        Z('Z', 3, new int[][]{{0, 0}, {0, 1}, {1, 1}, {1, 2}}),
        J('J', 3, new int[][]{{0, 0}, {1, 0}, {1, 1}, {1, 2}}),
        L('L', 3, new int[][]{{0, 2}, {1, 0}, {1, 1}, {1, 2}});

        private final char symbol;
        private final int box;
        private final int[][] cells;
        Shape(char symbol, int box, int[][] cells) {
            this.symbol = symbol;
            this.box = box;
            this.cells = cells;
        }
        public char symbol() { return symbol; }
        public int box() { return box; }

        /** The piece's cells after {@code turns} quarter turns clockwise. */
        public int[][] at(int turns) {
            int[][] current = cells;
            for (int turn = 0; turn < Math.floorMod(turns, 4); turn++) {
                int[][] rotated = new int[current.length][2];
                for (int i = 0; i < current.length; i++) {
                    rotated[i][0] = current[i][1];
                    rotated[i][1] = box - 1 - current[i][0];
                }
                current = rotated;
            }
            return current;
        }
    }

    private final int rows;
    private final int columns;
    private final char[][] settled;
    private final Random random;

    private Shape piece;
    private int rotation;
    private int pieceRow;
    private int pieceCol;
    private int score;
    private int lines;
    private boolean over;

    public Tetris() { this(18, 10, new Random()); }

    public Tetris(int rows, int columns, Random random) {
        if (rows < 6 || rows > 40 || columns < 6 || columns > 20) {
            throw new IllegalArgumentException("Use 6 to 40 rows and 6 to 20 columns.");
        }
        this.rows = rows;
        this.columns = columns;
        this.random = random;
        this.settled = new char[rows][columns];
        for (char[] row : settled) { Arrays.fill(row, '.'); }
        spawn();
    }

    @Override public Meta meta() { return META; }

    public int rows() { return rows; }
    public int columns() { return columns; }
    public int score() { return score; }
    public int lines() { return lines; }
    public boolean over() { return over; }
    public Shape piece() { return piece; }
    public int rotation() { return rotation; }
    public int pieceRow() { return pieceRow; }
    public int pieceCol() { return pieceCol; }

    // ------------------------------------------------------------------
    // Pieces
    // ------------------------------------------------------------------

    private void spawn() {
        spawn(Shape.values()[random.nextInt(Shape.values().length)]);
    }

    /** Spawns a specific piece. Exposed so a test can set up a known board. */
    public void spawn(Shape wanted) {
        piece = wanted;
        rotation = 0;
        pieceRow = 0;
        pieceCol = (columns - piece.box()) / 2;
        // Nowhere to put it means the stack has reached the top.
        if (!fits(pieceRow, pieceCol, rotation)) { over = true; }
    }

    /** Would the piece sit legally at this position and rotation? */
    public boolean fits(int row, int col, int turns) {
        for (int[] cell : piece.at(turns)) {
            int r = row + cell[0];
            int c = col + cell[1];
            if (c < 0 || c >= columns || r >= rows) { return false; }
            // Above the top is allowed while a piece is still entering.
            if (r >= 0 && settled[r][c] != '.') { return false; }
        }
        return true;
    }

    public boolean move(int dCol) {
        if (over) { return false; }
        if (fits(pieceRow, pieceCol + dCol, rotation)) {
            pieceCol += dCol;
            return true;
        }
        return false;
    }

    /**
     * Rotates, shifting sideways if that is what it takes to fit. Without the
     * kick, a piece flat against a wall simply refuses to turn.
     */
    public boolean rotate() {
        if (over) { return false; }
        int wanted = Math.floorMod(rotation + 1, 4);
        for (int kick : KICKS) {
            if (fits(pieceRow, pieceCol + kick, wanted)) {
                pieceCol += kick;
                rotation = wanted;
                return true;
            }
        }
        return false;
    }

    /** One tick of gravity. Returns false once the piece has landed. */
    public boolean drop() {
        if (over) { return false; }
        if (fits(pieceRow + 1, pieceCol, rotation)) {
            pieceRow++;
            return true;
        }
        land();
        return false;
    }

    /** Sends the piece as far down as it will go, then lands it. */
    public int hardDrop() {
        if (over) { return 0; }
        int moved = 0;
        while (fits(pieceRow + 1, pieceCol, rotation)) {
            pieceRow++;
            moved++;
        }
        land();
        return moved;
    }

    private void land() {
        for (int[] cell : piece.at(rotation)) {
            int r = pieceRow + cell[0];
            int c = pieceCol + cell[1];
            if (r >= 0 && r < rows && c >= 0 && c < columns) {
                settled[r][c] = piece.symbol();
            }
        }
        clearLines();
        spawn();
    }

    /**
     * Rebuilds the board from the rows that are not full, bottom upwards.
     *
     * <p>Removing rows in place while scanning shifts the untested rows into
     * indices already passed, which loses one clear out of every adjacent pair
     * - so a four-line clear scores as three.
     */
    public int clearLines() {
        List<char[]> kept = new ArrayList<>();
        for (int row = rows - 1; row >= 0; row--) {
            boolean full = true;
            for (int col = 0; col < columns; col++) {
                if (settled[row][col] == '.') { full = false; break; }
            }
            if (!full) { kept.add(settled[row].clone()); }
        }
        int cleared = rows - kept.size();
        if (cleared == 0) { return 0; }

        for (int row = rows - 1, i = 0; row >= 0; row--, i++) {
            if (i < kept.size()) {
                settled[row] = kept.get(i);
            } else {
                settled[row] = new char[columns];
                Arrays.fill(settled[row], '.');
            }
        }
        lines += cleared;
        score += LINE_SCORE[Math.min(cleared, 4)];
        return cleared;
    }

    /** The settled board only, without the falling piece. */
    public char[][] settled() {
        char[][] copy = new char[rows][];
        for (int row = 0; row < rows; row++) { copy[row] = settled[row].clone(); }
        return copy;
    }

    /** Fills a row completely, for setting up a test. */
    public void fillRow(int row) {
        for (int col = 0; col < columns; col++) { settled[row][col] = 'X'; }
    }

    /**
     * Fills a row except for one gap, for setting up a test.
     *
     * <p>There is deliberately no "no gap" sentinel value here. Spelling it as
     * a column index means some number has to mean "nowhere", and whichever
     * number is chosen, a caller will one day pass it meaning a real column.
     * Passing 0 to mean a full row leaves column 0 empty and the row silently
     * does not clear - so a full row gets its own method above.
     */
    public void fillRow(int row, int gapColumn) {
        if (gapColumn < 0 || gapColumn >= columns) {
            throw new IllegalArgumentException(
                    "The gap must be a real column. Use fillRow(row) for a full row.");
        }
        for (int col = 0; col < columns; col++) {
            settled[row][col] = col == gapColumn ? '.' : 'X';
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public char[][] cells() {
        char[][] grid = settled();
        if (!over) {
            for (int[] cell : piece.at(rotation)) {
                int r = pieceRow + cell[0];
                int c = pieceCol + cell[1];
                if (r >= 0 && r < rows && c >= 0 && c < columns) {
                    grid[r][c] = piece.symbol();
                }
            }
        }
        return grid;
    }

    private String detail() {
        if (over) {
            return String.format("Game over. Score %d from %d lines.", score, lines);
        }
        return String.format("score %d, lines %d, falling %s (rotation %d)",
                score, lines, piece, rotation);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("a and d to move, w to rotate, s to drop one, space to hard drop, q to quit.");
        Tetris game = this;
        while (true) {
            io.println();
            io.grid(game.cells());
            io.muted(game.detail());
            if (game.over()) { return; }
            String key = io.ask("move:").trim().toLowerCase(Locale.ROOT);
            if (key.equals("q")) { return; }
            switch (key) {
                case "a" -> game.move(-1);
                case "d" -> game.move(1);
                case "w" -> game.rotate();
                case "" -> game.hardDrop();
                default -> game.drop();
            }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    // A fresh game replaces this instance's board wholesale.
                    for (char[] row : settled) { Arrays.fill(row, '.'); }
                    score = 0;
                    lines = 0;
                    over = false;
                    spawn();
                    return board("New game. a and d to move, w to rotate, space to drop.");
                }
                case "left" -> { move(-1); return board(""); }
                case "right" -> { move(1); return board(""); }
                case "rotate" -> {
                    return board(rotate() ? "" : "No room to turn, even shifted sideways.");
                }
                case "step" -> { drop(); return board(""); }
                case "solve", "drop" -> {
                    int fell = hardDrop();
                    return board("Dropped " + fell + " rows.");
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
