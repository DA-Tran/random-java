import java.util.ArrayList;
import java.util.List;

public class ChessGame {
    private ChessBoard board;
    private boolean whiteTurn = true;
    private Position selectedPosition;

    public ChessGame() {
        this.board = new ChessBoard();
    }

    public ChessBoard getBoard() {
        return board;
    }

    public void resetGame() {
        this.board = new ChessBoard();
        whiteTurn = true;
        selectedPosition = null;
    }

    public PieceColor getCurrentPlayerColor() {
        return whiteTurn ? PieceColor.WHITE : PieceColor.BLACK;
    }

    public boolean isPieceSelected() {
        return selectedPosition != null;
    }

    public boolean handleSquareSelection(int row, int col) {
        if (selectedPosition == null) {
            Piece piece = board.getPiece(row, col);
            if (piece != null && piece.getColor() == getCurrentPlayerColor()) {
                selectedPosition = new Position(row, col);
                return false;
            }
        } else {
            boolean moved = makeMove(selectedPosition, new Position(row, col));
            selectedPosition = null;
            if (moved) {
                whiteTurn = !whiteTurn;
            }
            return moved;
        }
        return false;
    }

    public boolean makeMove(Position start, Position end) {
        Piece piece = board.getPiece(start.getRow(), start.getColumn());
        if (piece == null || piece.getColor() != getCurrentPlayerColor()) return false;
        
        // Prevent moves that leave own king in check
        if (wouldBeInCheckAfterMove(getCurrentPlayerColor(), start, end)) return false;
        
        if (piece.isValidMove(end, board.getBoard())) {
            board.movePiece(start, end);
            return true;
        }
        return false;
    }


    public boolean isInCheck(PieceColor kingColor) {
        Position kingPos = findKingPosition(kingColor);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board.getPiece(r, c);
                if (p != null && p.getColor() != kingColor && p.isValidMove(kingPos, board.getBoard())) {
                    return true;
                }
            }
        }
        return false;
    }

    private Position findKingPosition(PieceColor color) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board.getPiece(r, c);
                if (p instanceof King && p.getColor() == color) {
                    return new Position(r, c);
                }
            }
        }
        throw new RuntimeException("King not found!");
    }

    public boolean isCheckmate(PieceColor kingColor) {
        if (!isInCheck(kingColor)) return false;
        // Simplified: check if king has moves out of check (full impl complex)
        Position kingPos = findKingPosition(kingColor);
        King king = (King) board.getPiece(kingPos.getRow(), kingPos.getColumn());
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                Position np = new Position(kingPos.getRow() + dr, kingPos.getColumn() + dc);
                if (board.getBoard().length > np.getRow() && np.getRow() >= 0 && np.getColumn() >= 0 && np.getColumn() < board.getBoard()[0].length
                    && king.isValidMove(np, board.getBoard()) && !wouldBeInCheckAfterMove(kingColor, kingPos, np)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean wouldBeInCheckAfterMove(PieceColor kingColor, Position from, Position to) {
        Piece temp = board.getPiece(to.getRow(), to.getColumn());
        board.setPiece(to.getRow(), to.getColumn(), board.getPiece(from.getRow(), from.getColumn()));
        board.setPiece(from.getRow(), from.getColumn(), null);
        boolean inCheck = isInCheck(kingColor);
        board.setPiece(from.getRow(), from.getColumn(), board.getPiece(to.getRow(), to.getColumn()));
        board.setPiece(to.getRow(), to.getColumn(), temp);
        return inCheck;
    }

    public List<Position> getLegalMovesForPieceAt(Position position) {
        Piece piece = board.getPiece(position.getRow(), position.getColumn());
        if (piece == null) return new ArrayList<>();
        List<Position> moves = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Position np = new Position(r, c);
                if (piece.isValidMove(np, board.getBoard())) {
                    moves.add(np);
                }
            }
        }
        return moves;
    }
}

