import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.*;


public class ChessGame_interactive extends JFrame {
    private final ChessSquareComponent[][] squares = new ChessSquareComponent[8][8];
    private final ChessGame game = new ChessGame();
    private String getPieceSymbol(Piece piece) {
        String white, black;
        if (piece instanceof Pawn) { white = "\u2659"; black = "\u265F"; }
        else if (piece instanceof Rook) { white = "\u2656"; black = "\u265C"; }
        else if (piece instanceof Knight) { white = "\u2658"; black = "\u265E"; }
        else if (piece instanceof Bishop) { white = "\u2657"; black = "\u265D"; }
        else if (piece instanceof Queen) { white = "\u2655"; black = "\u265B"; }
        else if (piece instanceof King) { white = "\u2654"; black = "\u265A"; }
        else return "?";
        return piece.getColor() == PieceColor.WHITE ? white : black;
    }




    public ChessGame_interactive() {
        setTitle("Chess Game Interactive");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(8, 8));
        initializeBoard();
        addGameResetOption();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initializeBoard() {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                final int r = row, c = col;
                ChessSquareComponent square = new ChessSquareComponent(row, col);
                square.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        handleSquareClick(r, c);
                    }
                });
                add(square);
                squares[row][col] = square;
            }
        }
        refreshBoard();
    }

    private void refreshBoard() {
        ChessBoard board = game.getBoard();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Piece piece = board.getPiece(row, col);
                if (piece != null) {
                    String symbol = getPieceSymbol(piece);
                    Color color = (piece.getColor() == PieceColor.WHITE) ? Color.BLACK : Color.WHITE; // Invert for contrast
                    squares[row][col].setPieceSymbol(symbol, color);
                } else {
                    squares[row][col].clearPieceSymbol();
                }

            }
        }
    }

    private void handleSquareClick(int row, int col) {
        boolean moveResult = game.handleSquareSelection(row, col);
        clearHighlights();
        if (moveResult) {
            refreshBoard();
            checkGameState();
            checkGameOver();
        } else if (game.isPieceSelected()) {
            highlightLegalMoves(new Position(row, col));
        }
        refreshBoard();
    }

    private void highlightLegalMoves(Position position) {
        List<Position> legalMoves = game.getLegalMovesForPieceAt(position);
        for (Position move : legalMoves) {
            squares[move.getRow()][move.getColumn()].setBackground(Color.GREEN);
        }
    }

    private void clearHighlights() {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                squares[row][col].setBackground((row + col) % 2 == 0 ? Color.LIGHT_GRAY : new Color(205, 133, 63));
            }
        }
    }

    private void checkGameState() {
        PieceColor currentPlayer = game.getCurrentPlayerColor();
        boolean inCheck = game.isInCheck(currentPlayer);
        if (inCheck) {
            JOptionPane.showMessageDialog(this, currentPlayer + " KING IN CHECK!");
        }
    }

    private void checkGameOver() {
        if (game.isCheckmate(game.getCurrentPlayerColor())) {
            JOptionPane.showConfirmDialog(this, "CHECKMATE! Play again?", "Game Over", JOptionPane.YES_NO_OPTION);
            if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, "Play again?", "CHECKMATE!", JOptionPane.YES_NO_OPTION)) {
                resetGame();
            } else {
                System.exit(0);
            }
        }
    }

    private void addGameResetOption() {
        JMenuBar menuBar = new JMenuBar();
        JMenu gameMenu = new JMenu("Game");
        JMenuItem resetItem = new JMenuItem("Reset");
        resetItem.addActionListener(e -> resetGame());
        gameMenu.add(resetItem);
        menuBar.add(gameMenu);
        setJMenuBar(menuBar);
    }

    private void resetGame() {
        game.resetGame();
        refreshBoard();
        clearHighlights();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChessGame_interactive::new);
    }
}

