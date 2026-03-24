public class Queen extends Piece {
    public Queen(PieceColor color, Position position) {
        super(color, position);
    }

    @Override
    public boolean isValidMove(Position newPosition, Piece[][] board) {
        if (newPosition.getRow() == position.getRow() || newPosition.getColumn() == position.getColumn() || 
            Math.abs(newPosition.getRow() - position.getRow()) == Math.abs(newPosition.getColumn() - position.getColumn())) {
            // Check path clear (simplified)
            int rowDir = Integer.signum(newPosition.getRow() - position.getRow());
            int colDir = Integer.signum(newPosition.getColumn() - position.getColumn());
            int r = position.getRow() + rowDir;
            int c = position.getColumn() + colDir;
            while (r != newPosition.getRow() || c != newPosition.getColumn()) {
                if (board[r][c] != null) return false;
                r += rowDir;
                c += colDir;
            }
            Piece dest = board[newPosition.getRow()][newPosition.getColumn()];
            return dest == null || dest.getColor() != getColor();
        }
        return false;
    }
}

