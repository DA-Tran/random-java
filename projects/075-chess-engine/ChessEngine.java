package com.randomjava.projects.chessengine;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Chess Engine - legal move generation and a minimax search with alpha-beta.
 *
 * <p>Two halves, and the first is where chess engines actually go wrong.
 *
 * <p><b>Legal is not the same as pseudo-legal.</b> Generating the squares a
 * piece can reach is straightforward. The rule that costs everyone a day is
 * that a move is illegal if it leaves your own king attacked - which includes
 * moving a piece that was pinned, and failing to address an existing check.
 * There is no way to see that from the moving piece alone, so every candidate
 * move is played, the resulting position is tested, and the move is taken back.
 * Slow, and correct. An engine that skips this will happily hang its king and
 * then report a winning score.
 *
 * <p><b>Alpha-beta must return the same move as plain minimax.</b> It is an
 * optimisation, not a different algorithm: it skips branches that cannot affect
 * the result. If pruning changes the chosen move, the bounds are wrong. That is
 * exactly what {@link #search} is tested against - the same position searched
 * with and without pruning, and the evaluations compared.
 *
 * <p>Castling, en passant and promotion to anything but a queen are left out,
 * and said so here rather than discovered later. Everything else is real.
 */
public final class ChessEngine implements Project {

    public static final Meta META = new Meta(75, "chess-engine", "Chess Engine", "Game Development", Kind.GRID,
            Difficulty.ADVANCED, "Legal move generation and a minimax search with alpha-beta pruning.",
            "", true);

    /** Uppercase is white, lowercase is black, '.' is empty. */
    private char[][] board = startingPosition();
    private boolean whiteToMove = true;
    private final List<String> history = new ArrayList<>();

    public record Move(int fromRow, int fromCol, int toRow, int toCol, char captured,
                       char promotion) {
        @Override public String toString() {
            return "" + (char) ('a' + fromCol) + (8 - fromRow)
                     + (char) ('a' + toCol) + (8 - toRow)
                     + (promotion == '.' ? "" : String.valueOf(promotion));
        }
    }

    private static char[][] startingPosition() {
        return new char[][]{
            "rnbqkbnr".toCharArray(),
            "pppppppp".toCharArray(),
            "........".toCharArray(),
            "........".toCharArray(),
            "........".toCharArray(),
            "........".toCharArray(),
            "PPPPPPPP".toCharArray(),
            "RNBQKBNR".toCharArray()};
    }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Board access
    // ------------------------------------------------------------------

    public char at(int row, int col) { return board[row][col]; }
    public boolean whiteToMove() { return whiteToMove; }
    public List<String> history() { return List.copyOf(history); }

    public void reset() {
        board = startingPosition();
        whiteToMove = true;
        history.clear();
    }

    /** Loads a position from eight rows of eight characters, for tests. */
    public void setPosition(String[] rows, boolean whiteMovesNext) {
        if (rows.length != 8) { throw new IllegalArgumentException("A board has 8 rows."); }
        char[][] fresh = new char[8][];
        for (int r = 0; r < 8; r++) {
            if (rows[r].length() != 8) {
                throw new IllegalArgumentException("Row " + r + " is not 8 squares.");
            }
            fresh[r] = rows[r].toCharArray();
        }
        board = fresh;
        whiteToMove = whiteMovesNext;
        history.clear();
    }

    private static boolean onBoard(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    private static boolean isWhite(char piece) { return piece != '.' && Character.isUpperCase(piece); }
    private static boolean isBlack(char piece) { return piece != '.' && Character.isLowerCase(piece); }

    private static boolean mine(char piece, boolean white) {
        return white ? isWhite(piece) : isBlack(piece);
    }

    // ------------------------------------------------------------------
    // Move generation
    // ------------------------------------------------------------------

    /** Moves that ignore whether the king is left in check. */
    private List<Move> pseudoLegal(boolean white) {
        List<Move> moves = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                char piece = board[row][col];
                if (!mine(piece, white)) { continue; }
                switch (Character.toLowerCase(piece)) {
                    case 'p' -> pawnMoves(moves, row, col, white);
                    case 'n' -> stepMoves(moves, row, col, white,
                            new int[][]{{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
                                        {1, -2}, {1, 2}, {2, -1}, {2, 1}});
                    case 'k' -> stepMoves(moves, row, col, white,
                            new int[][]{{-1, -1}, {-1, 0}, {-1, 1}, {0, -1},
                                        {0, 1}, {1, -1}, {1, 0}, {1, 1}});
                    case 'b' -> slideMoves(moves, row, col, white,
                            new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}});
                    case 'r' -> slideMoves(moves, row, col, white,
                            new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}});
                    case 'q' -> slideMoves(moves, row, col, white,
                            new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1},
                                        {-1, 0}, {1, 0}, {0, -1}, {0, 1}});
                    default -> { }
                }
            }
        }
        return moves;
    }

    private void pawnMoves(List<Move> moves, int row, int col, boolean white) {
        int forward = white ? -1 : 1;
        int startRow = white ? 6 : 1;
        int lastRow = white ? 0 : 7;

        if (onBoard(row + forward, col) && board[row + forward][col] == '.') {
            addPawn(moves, row, col, row + forward, col, '.', lastRow);
            if (row == startRow && board[row + 2 * forward][col] == '.') {
                moves.add(new Move(row, col, row + 2 * forward, col, '.', '.'));
            }
        }
        for (int dc : new int[]{-1, 1}) {
            int toRow = row + forward;
            int toCol = col + dc;
            if (onBoard(toRow, toCol) && board[toRow][toCol] != '.'
                    && !mine(board[toRow][toCol], white)) {
                addPawn(moves, row, col, toRow, toCol, board[toRow][toCol], lastRow);
            }
        }
    }

    /** Promotion is to a queen only. Under-promotion is left out. */
    private void addPawn(List<Move> moves, int fromRow, int fromCol, int toRow, int toCol,
            char captured, int lastRow) {
        char promotion = toRow == lastRow ? (isWhite(board[fromRow][fromCol]) ? 'Q' : 'q') : '.';
        moves.add(new Move(fromRow, fromCol, toRow, toCol, captured, promotion));
    }

    private void stepMoves(List<Move> moves, int row, int col, boolean white, int[][] deltas) {
        for (int[] d : deltas) {
            int toRow = row + d[0];
            int toCol = col + d[1];
            if (onBoard(toRow, toCol) && !mine(board[toRow][toCol], white)) {
                moves.add(new Move(row, col, toRow, toCol, board[toRow][toCol], '.'));
            }
        }
    }

    private void slideMoves(List<Move> moves, int row, int col, boolean white, int[][] deltas) {
        for (int[] d : deltas) {
            int toRow = row + d[0];
            int toCol = col + d[1];
            while (onBoard(toRow, toCol)) {
                char target = board[toRow][toCol];
                if (mine(target, white)) { break; }
                moves.add(new Move(row, col, toRow, toCol, target, '.'));
                if (target != '.') { break; }
                toRow += d[0];
                toCol += d[1];
            }
        }
    }

    /**
     * The legal moves: every pseudo-legal move played, checked, and taken back.
     *
     * <p>There is no shortcut here. A pinned piece looks perfectly mobile from
     * its own square, and the only way to know is to make the move and ask
     * whether the king is now attacked.
     */
    public List<Move> legalMoves() {
        List<Move> legal = new ArrayList<>();
        for (Move move : pseudoLegal(whiteToMove)) {
            boolean mover = whiteToMove;
            apply(move);
            if (!kingAttacked(mover)) { legal.add(move); }
            undo(move);
        }
        return legal;
    }

    public void apply(Move move) {
        char piece = board[move.fromRow()][move.fromCol()];
        board[move.fromRow()][move.fromCol()] = '.';
        board[move.toRow()][move.toCol()] =
                move.promotion() == '.' ? piece : move.promotion();
        whiteToMove = !whiteToMove;
    }

    public void undo(Move move) {
        whiteToMove = !whiteToMove;
        char piece = board[move.toRow()][move.toCol()];
        board[move.fromRow()][move.fromCol()] =
                move.promotion() == '.' ? piece : (isWhite(piece) ? 'P' : 'p');
        board[move.toRow()][move.toCol()] = move.captured();
    }

    /** Is the given side's king currently attacked? */
    public boolean kingAttacked(boolean white) {
        int kingRow = -1;
        int kingCol = -1;
        char king = white ? 'K' : 'k';
        for (int row = 0; row < 8 && kingRow < 0; row++) {
            for (int col = 0; col < 8; col++) {
                if (board[row][col] == king) { kingRow = row; kingCol = col; break; }
            }
        }
        // A position with no king is a test fixture, not a game. Nothing to attack.
        if (kingRow < 0) { return false; }
        for (Move reply : pseudoLegal(!white)) {
            if (reply.toRow() == kingRow && reply.toCol() == kingCol) { return true; }
        }
        return false;
    }

    public boolean inCheck() { return kingAttacked(whiteToMove); }
    public boolean checkmate() { return inCheck() && legalMoves().isEmpty(); }
    public boolean stalemate() { return !inCheck() && legalMoves().isEmpty(); }

    // ------------------------------------------------------------------
    // Evaluation and search
    // ------------------------------------------------------------------

    private static int pieceValue(char piece) {
        return switch (Character.toLowerCase(piece)) {
            case 'p' -> 100;
            case 'n', 'b' -> 300;
            case 'r' -> 500;
            case 'q' -> 900;
            case 'k' -> 20000;
            default -> 0;
        };
    }

    /** Material only, from white's point of view. Positive favours white. */
    public int evaluate() {
        int score = 0;
        for (char[] row : board) {
            for (char piece : row) {
                if (piece == '.') { continue; }
                score += isWhite(piece) ? pieceValue(piece) : -pieceValue(piece);
            }
        }
        return score;
    }

    public record Assessment(Move best, int score, int nodes) { }

    /**
     * Minimax to a fixed depth. With {@code prune} it is alpha-beta; without,
     * it is the plain full search. Both must choose equally good moves, since
     * pruning only skips branches that cannot change the answer.
     */
    public Assessment search(int depth, boolean prune) {
        if (depth < 1 || depth > 4) {
            throw new IllegalArgumentException("Search between 1 and 4 ply.");
        }
        int[] nodes = {0};
        Move best = null;
        int bestScore = whiteToMove ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (Move move : legalMoves()) {
            apply(move);
            int score = minimax(depth - 1, alpha, beta, prune, nodes);
            undo(move);
            if (whiteToMove ? score > bestScore : score < bestScore) {
                bestScore = score;
                best = move;
            }
            if (prune) {
                if (whiteToMove) { alpha = Math.max(alpha, bestScore); }
                else { beta = Math.min(beta, bestScore); }
            }
        }
        return new Assessment(best, bestScore, nodes[0]);
    }

    private int minimax(int depth, int alpha, int beta, boolean prune, int[] nodes) {
        nodes[0]++;
        List<Move> moves = legalMoves();
        if (moves.isEmpty()) {
            // Checkmate is scored with the depth folded in, so a mate in one is
            // preferred over a mate in three. Stalemate is exactly level.
            if (inCheck()) {
                return whiteToMove ? -100000 - depth : 100000 + depth;
            }
            return 0;
        }
        if (depth == 0) { return evaluate(); }

        if (whiteToMove) {
            int best = Integer.MIN_VALUE;
            for (Move move : moves) {
                apply(move);
                best = Math.max(best, minimax(depth - 1, alpha, beta, prune, nodes));
                undo(move);
                if (prune) {
                    alpha = Math.max(alpha, best);
                    if (beta <= alpha) { break; }
                }
            }
            return best;
        }
        int best = Integer.MAX_VALUE;
        for (Move move : moves) {
            apply(move);
            best = Math.min(best, minimax(depth - 1, alpha, beta, prune, nodes));
            undo(move);
            if (prune) {
                beta = Math.min(beta, best);
                if (beta <= alpha) { break; }
            }
        }
        return best;
    }

    public Move parseMove(String text) {
        String clean = String.valueOf(text).trim().toLowerCase(Locale.ROOT);
        if (clean.length() < 4) {
            throw new IllegalArgumentException("Write moves like e2e4.");
        }
        int fromCol = clean.charAt(0) - 'a';
        int fromRow = 8 - (clean.charAt(1) - '0');
        int toCol = clean.charAt(2) - 'a';
        int toRow = 8 - (clean.charAt(3) - '0');
        for (Move move : legalMoves()) {
            if (move.fromRow() == fromRow && move.fromCol() == fromCol
                    && move.toRow() == toRow && move.toCol() == toCol) {
                return move;
            }
        }
        throw new IllegalArgumentException("\"" + text + "\" is not legal here.");
    }

    public char[][] cells() {
        char[][] copy = new char[8][];
        for (int row = 0; row < 8; row++) { copy[row] = board[row].clone(); }
        return copy;
    }

    private String detail() {
        if (checkmate()) {
            return "Checkmate. " + (whiteToMove ? "Black" : "White") + " wins.";
        }
        if (stalemate()) { return "Stalemate. Drawn."; }
        return String.format("%s to move, %d legal moves, material %+d%s",
                whiteToMove ? "White" : "Black", legalMoves().size(), evaluate(),
                inCheck() ? ", in check" : "");
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("Moves like e2e4. Blank to let the engine play. No castling or en passant.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            if (legalMoves().isEmpty()) { return; }
            String typed = io.ask("move:").trim();
            if (typed.equalsIgnoreCase("q")) { return; }
            try {
                if (typed.isEmpty()) {
                    Assessment found = search(3, true);
                    apply(found.best());
                    history.add(found.best().toString());
                    io.ok("Engine played " + found.best() + " (" + found.nodes() + " nodes).");
                } else {
                    Move move = parseMove(typed);
                    apply(move);
                    history.add(move.toString());
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> { reset(); return board("New game. White to move."); }
                case "move" -> {
                    Move move = parseMove(Json.str(body, "move", ""));
                    apply(move);
                    history.add(move.toString());
                    return board("You played " + move + ".");
                }
                case "step", "solve" -> {
                    if (legalMoves().isEmpty()) { return board("The game is over."); }
                    Assessment found = search(3, true);
                    apply(found.best());
                    history.add(found.best().toString());
                    return board("Engine played " + found.best()
                            + " after searching " + found.nodes() + " positions.");
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
