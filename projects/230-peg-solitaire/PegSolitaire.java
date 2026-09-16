package com.randomjava.projects.pegsolitaire;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Peg Solitaire - jump pegs over each other until one is left in the middle.
 *
 * <p>Every jump removes exactly one peg, so the English board's 32 pegs need
 * 31 moves and the branching is wide enough that brute force alone is
 * unpleasant. The interesting part is that a great deal can be settled without
 * searching at all.
 *
 * <h2>The colouring argument</h2>
 *
 * <p>Give the cell at {@code (row, column)} the value {@code w^(row + column)},
 * where {@code w} lives in GF(4) - the four-element field, in which
 * {@code 1 + w + w^2 = 0} and, because the characteristic is two, adding and
 * subtracting are the same operation. Add up the values of all the pegs.
 *
 * <p>Now look at what a jump does. The three cells it touches - the peg, the
 * peg it jumps, the hole it lands in - are consecutive along a line, so their
 * exponents are {@code k}, {@code k + 1} and {@code k + 2}. Two pegs leave and
 * one arrives, and since subtraction is addition here the total changes by
 * {@code w^k + w^(k+1) + w^(k+2)}, which is {@code w^k (1 + w + w^2)}, which is
 * zero. <b>The sum does not change. Ever.</b>
 *
 * <p>The same holds for {@code w^(row - column)}, giving a second invariant.
 * Together they are strong enough to name the finishing hole: if one peg is to
 * be left at some cell, the two sums must already equal what a lone peg there
 * would contribute. Any other target is not merely hard, it is impossible, and
 * that is a proof rather than the result of a search that gave up.
 *
 * <p>The verdict is sharper than it sounds. On the English board with the
 * middle empty, 28 of the 33 holes are eliminated outright: the only finishes
 * left standing are the centre and the four tips of the arms. A search then
 * confirms the centre really is reachable, in the expected 31 jumps.
 *
 * <p>The 37-hole European board is where it earns its keep. Empty the middle
 * there and <b>every one of the 37 holes fails the test</b> - that board cannot
 * be reduced to a single peg anywhere at all, let alone in the centre. No
 * search establishes that; no search could, since exhausting the space is what
 * a search would have to do. Two sums over GF(4) settle it in constant time,
 * and {@link #impossibleFinishes()} reports it.
 *
 * <h2>And then the search</h2>
 *
 * <p>The invariants are necessary, not sufficient - they cannot tell a hard
 * board from a merely unlucky one - so {@link #solve()} still has to look. It
 * runs depth-first, remembering the positions that led nowhere, and checks the
 * invariant at every node so that whole subtrees are discarded the moment the
 * target becomes unreachable. Cheap exact reasoning first, expensive search
 * only where the reasoning runs out.
 */
public final class PegSolitaire implements Project {

    public static final Meta META = new Meta(
            230, "peg-solitaire", "Peg Solitaire", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Jump pegs over each other to leave a single peg in the centre.",
            "", true);

    /** The 33-hole cross. Its central game is solvable. */
    public static final String[] ENGLISH = {
        "  ooo  ",
        "  ooo  ",
        "ooooooo",
        "ooo.ooo",
        "ooooooo",
        "  ooo  ",
        "  ooo  ",
    };

    /** The 37-hole board. Its central game is not solvable, provably. */
    public static final String[] EUROPEAN = {
        "  ooo  ",
        " ooooo ",
        "ooooooo",
        "ooo.ooo",
        "ooooooo",
        " ooooo ",
        "  ooo  ",
    };

    private static final char PEG = '#';
    private static final char HOLE = '.';
    private static final char OFF = ' ';
    private static final char HELD = '*';

    /**
     * GF(4) written as two bits: {@code 1} is {@code 01}, {@code w} is
     * {@code 10}, {@code w^2} is {@code 11}. Addition is exclusive or, and
     * {@code 01 ^ 10 ^ 11 == 0} is the identity the whole argument rests on.
     */
    private static final int[] POWERS = {0b01, 0b10, 0b11};

    private static final int NODE_BUDGET = 4_000_000;

    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final String[] DIRECTION_NAMES = {"up", "down", "left", "right"};

    private boolean[][] board;     // which cells exist at all
    private boolean[][] pegs;
    private int rows;
    private int columns;
    private int pegCount;
    private int targetRow;
    private int targetColumn;

    private final Deque<int[]> history = new ArrayDeque<>();
    private int heldRow = -1;
    private int heldColumn = -1;
    private int nodesLeft;

    public PegSolitaire() {
        this(ENGLISH);
    }

    public PegSolitaire(String[] shape) {
        reset(shape);
    }

    @Override
    public Meta meta() {
        return META;
    }

    public void reset(String[] shape) {
        rows = shape.length;
        columns = 0;
        for (String line : shape) {
            columns = Math.max(columns, line.length());
        }
        if (rows == 0 || columns == 0) {
            throw new IllegalArgumentException("That is not a board.");
        }
        board = new boolean[rows][columns];
        pegs = new boolean[rows][columns];
        pegCount = 0;
        for (int row = 0; row < rows; row++) {
            String line = shape[row];
            for (int column = 0; column < columns; column++) {
                char c = column < line.length() ? line.charAt(column) : OFF;
                board[row][column] = c != OFF;
                pegs[row][column] = c == 'o';
                if (pegs[row][column]) {
                    pegCount++;
                }
            }
        }
        if (pegCount == 0) {
            throw new IllegalArgumentException("A board with no pegs has nothing to do.");
        }
        targetRow = rows / 2;
        targetColumn = columns / 2;
        history.clear();
        heldRow = -1;
        heldColumn = -1;
    }

    public int rows() {
        return rows;
    }

    public int columns() {
        return columns;
    }

    public int pegCount() {
        return pegCount;
    }

    public boolean onBoard(int row, int column) {
        return row >= 0 && row < rows && column >= 0 && column < columns && board[row][column];
    }

    public boolean hasPeg(int row, int column) {
        return onBoard(row, column) && pegs[row][column];
    }

    public int targetRow() {
        return targetRow;
    }

    public int targetColumn() {
        return targetColumn;
    }

    /** Where the single remaining peg should end up. */
    public void setTarget(int row, int column) {
        if (!onBoard(row, column)) {
            throw new IllegalArgumentException("There is no hole at " + row + "," + column + ".");
        }
        targetRow = row;
        targetColumn = column;
    }

    // ------------------------------------------------------------------
    // The invariants
    // ------------------------------------------------------------------

    private static int mod3(int value) {
        return ((value % 3) + 3) % 3;
    }

    /** The two GF(4) sums, which no jump can change. */
    public int[] invariants() {
        int diagonal = 0;
        int antidiagonal = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (pegs[row][column]) {
                    diagonal ^= POWERS[mod3(row + column)];
                    antidiagonal ^= POWERS[mod3(row - column)];
                }
            }
        }
        return new int[] {diagonal, antidiagonal};
    }

    /**
     * Whether a single peg could conceivably be left here. Necessary, not
     * sufficient: a cell that fails this is impossible, a cell that passes
     * still has to be searched for.
     */
    public boolean couldFinishAt(int row, int column) {
        if (!onBoard(row, column)) {
            return false;
        }
        int[] sums = invariants();
        return sums[0] == POWERS[mod3(row + column)]
                && sums[1] == POWERS[mod3(row - column)];
    }

    /** Every hole the colouring rules out as a finish, from the position now. */
    public List<int[]> impossibleFinishes() {
        List<int[]> out = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (onBoard(row, column) && !couldFinishAt(row, column)) {
                    out.add(new int[] {row, column});
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Moves
    // ------------------------------------------------------------------

    /** Every legal jump right now, as {@code {row, column, dRow, dColumn}}. */
    public List<int[]> moves() {
        List<int[]> out = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                for (int[] step : DIRECTIONS) {
                    if (legal(row, column, step[0], step[1])) {
                        out.add(new int[] {row, column, step[0], step[1]});
                    }
                }
            }
        }
        return out;
    }

    public boolean legal(int row, int column, int dRow, int dColumn) {
        if (Math.abs(dRow) + Math.abs(dColumn) != 1) {
            return false;   // jumps run along a row or a column, never diagonally
        }
        return hasPeg(row, column)
                && hasPeg(row + dRow, column + dColumn)
                && onBoard(row + 2 * dRow, column + 2 * dColumn)
                && !pegs[row + 2 * dRow][column + 2 * dColumn];
    }

    public void move(int row, int column, int dRow, int dColumn) {
        if (!legal(row, column, dRow, dColumn)) {
            throw new IllegalArgumentException("A peg jumps over a neighbour into an empty hole.");
        }
        apply(new int[] {row, column, dRow, dColumn});
        history.push(new int[] {row, column, dRow, dColumn});
        heldRow = -1;
        heldColumn = -1;
    }

    private void apply(int[] move) {
        pegs[move[0]][move[1]] = false;
        pegs[move[0] + move[2]][move[1] + move[3]] = false;
        pegs[move[0] + 2 * move[2]][move[1] + 2 * move[3]] = true;
        pegCount--;
    }

    private void undo(int[] move) {
        pegs[move[0]][move[1]] = true;
        pegs[move[0] + move[2]][move[1] + move[3]] = true;
        pegs[move[0] + 2 * move[2]][move[1] + 2 * move[3]] = false;
        pegCount++;
    }

    public boolean undo() {
        if (history.isEmpty()) {
            return false;
        }
        undo(history.pop());
        heldRow = -1;
        heldColumn = -1;
        return true;
    }

    public int movesMade() {
        return history.size();
    }

    /** One peg left, and in the hole we were aiming at. */
    public boolean solved() {
        return pegCount == 1 && pegs[targetRow][targetColumn];
    }

    /** Nothing left to do, whether or not it went well. */
    public boolean stuck() {
        return moves().isEmpty();
    }

    // ------------------------------------------------------------------
    // Searching
    // ------------------------------------------------------------------

    /**
     * The jumps that finish the board, or null if there are none.
     *
     * <p>Depth-first, with two prunes that between them make it quick: the
     * colouring, which throws away a subtree the instant the target stops
     * being reachable, and a set of positions already known to lead nowhere,
     * which stops the same arrangement being re-explored down a different
     * order of moves.
     */
    public List<int[]> solve() {
        nodesLeft = NODE_BUDGET;
        List<int[]> path = new ArrayList<>();
        boolean[][] saved = copy(pegs);
        int savedCount = pegCount;
        boolean found = search(path, new HashSet<>());
        pegs = saved;
        pegCount = savedCount;
        return found ? path : null;
    }

    private boolean search(List<int[]> path, HashSet<Long> dead) {
        if (nodesLeft-- <= 0) {
            return false;
        }
        if (pegCount == 1) {
            return pegs[targetRow][targetColumn];
        }
        if (!couldFinishAt(targetRow, targetColumn)) {
            return false;
        }
        long key = mask();
        if (dead.contains(key)) {
            return false;
        }
        for (int[] move : moves()) {
            apply(move);
            path.add(move);
            if (search(path, dead)) {
                return true;
            }
            path.remove(path.size() - 1);
            undo(move);
        }
        dead.add(key);
        return false;
    }

    /** The position as one long, which both boards fit into comfortably. */
    private long mask() {
        long bits = 0;
        int index = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (board[row][column]) {
                    if (pegs[row][column]) {
                        bits |= 1L << index;
                    }
                    index++;
                }
            }
        }
        return bits;
    }

    /** Plays the next move of a winning line, if one exists. */
    public String hint() {
        List<int[]> plan = solve();
        if (plan == null || plan.isEmpty()) {
            return solved() ? "Already done." : "There is no way to finish from here.";
        }
        int[] next = plan.get(0);
        move(next[0], next[1], next[2], next[3]);
        return "Jump the peg at " + next[0] + "," + next[1] + " " + nameOf(next[2], next[3]) + ".";
    }

    public String solveAndPlay() {
        List<int[]> plan = solve();
        if (plan == null) {
            String blocked = couldFinishAt(targetRow, targetColumn)
                    ? "The colouring allows this finish, but no sequence of jumps reaches it."
                    : "The colouring rules this finish out, so no search is needed to know.";
            return "No solution from here. " + blocked;
        }
        for (int[] move : plan) {
            move(move[0], move[1], move[2], move[3]);
        }
        return "Finished in " + plan.size() + " jumps.";
    }

    private static String nameOf(int dRow, int dColumn) {
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (DIRECTIONS[i][0] == dRow && DIRECTIONS[i][1] == dColumn) {
                return DIRECTION_NAMES[i];
            }
        }
        return dRow + "," + dColumn;
    }

    private static boolean[][] copy(boolean[][] grid) {
        boolean[][] out = new boolean[grid.length][];
        for (int i = 0; i < grid.length; i++) {
            out[i] = grid[i].clone();
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Clicking
    // ------------------------------------------------------------------

    /**
     * One click: pick up a peg, or jump the one already held into this hole.
     * Clicking the held peg again puts it down.
     */
    public String touch(int row, int column) {
        if (!onBoard(row, column)) {
            return "";
        }
        if (heldRow == row && heldColumn == column) {
            heldRow = -1;
            heldColumn = -1;
            return "";
        }
        if (heldRow >= 0) {
            int dRow = Integer.signum(row - heldRow);
            int dColumn = Integer.signum(column - heldColumn);
            boolean straight = (row == heldRow) != (column == heldColumn);
            int distance = Math.abs(row - heldRow) + Math.abs(column - heldColumn);
            if (straight && distance == 2 && legal(heldRow, heldColumn, dRow, dColumn)) {
                move(heldRow, heldColumn, dRow, dColumn);
                return solved() ? "One peg, dead centre."
                        : stuck() ? "Stuck with " + pegCount + " pegs left." : "";
            }
        }
        if (pegs[row][column]) {
            heldRow = row;
            heldColumn = column;
            return "";
        }
        heldRow = -1;
        heldColumn = -1;
        return "";
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public char[][] cells() {
        char[][] grid = new char[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (!board[row][column]) {
                    grid[row][column] = OFF;
                } else if (row == heldRow && column == heldColumn) {
                    grid[row][column] = HELD;
                } else {
                    grid[row][column] = pegs[row][column] ? PEG : HOLE;
                }
            }
        }
        return grid;
    }

    private String detail() {
        if (solved()) {
            return "Solved in " + history.size() + " jumps, one peg in the middle.";
        }
        StringBuilder note = new StringBuilder();
        note.append(pegCount).append(" pegs, ").append(history.size()).append(" jumps.");
        if (pegCount > 1 && stuck()) {
            note.append(" No jumps left - undo, or start again.");
        } else if (!couldFinishAt(targetRow, targetColumn)) {
            note.append(" The colouring already rules out finishing in the middle.");
        }
        return note.toString();
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("# is a peg, . an empty hole. Jump a peg over a neighbour into a hole.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            int choice = io.menu("Peg Solitaire",
                    List.of("Jump", "Hint", "Solve", "Undo",
                            "Restart (English)", "Restart (European)"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0 -> {
                        int row = io.askInt("row:", 0, rows - 1, 0);
                        int column = io.askInt("column:", 0, columns - 1, 0);
                        int direction = io.menu("Which way?", List.of(DIRECTION_NAMES));
                        if (direction >= 0) {
                            move(row, column, DIRECTIONS[direction][0], DIRECTIONS[direction][1]);
                        }
                    }
                    case 1 -> io.info(hint());
                    case 2 -> io.info(solveAndPlay());
                    case 3 -> {
                        if (!undo()) {
                            io.warn("Nothing to undo.");
                        }
                    }
                    case 4 -> reset(ENGLISH);
                    default -> {
                        reset(EUROPEAN);
                        io.muted("The European central game has no solution. "
                                + "Ask the solver and it will say so.");
                    }
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
                    boolean european = Json.str(body, "board", "english")
                            .toLowerCase().startsWith("e");
                    reset(european ? EUROPEAN : ENGLISH);
                    return board(european
                            ? "The European board. Its central game cannot be won, and the "
                              + "colouring says so without searching."
                            : "The English board. Click a peg, then the hole two along.");
                }
                case "cycle", "step", "touch" -> {
                    return board(touch(Json.integer(body, "row", -1),
                            Json.integer(body, "col", -1)));
                }
                case "hint" -> {
                    return board(hint());
                }
                case "undo" -> {
                    return board(undo() ? "" : "Nothing to undo.");
                }
                case "solve" -> {
                    return board(solveAndPlay());
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
