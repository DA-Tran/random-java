import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;

public class ChessBoardGUI extends JPanel {
    private static final int BOARD_SIZE = 8;
    private static final int SQUARE_SIZE = 60;
    private String[][] board = initializeBoard();
    private int fromRow = -1, fromCol = -1;
    private boolean whiteTurn = true;

    private static String[][] initializeBoard() {
        String[][] b = new String[8][8];
        // Pawns
        for (int col = 0; col < 8; col++) {
            b[1][col] = "wp";
            b[6][col] = "bp";
        }
        // Rooks
        b[0][0] = b[0][7] = "wr"; b[7][0] = b[7][7] = "br";
        // Knights
        b[0][1] = b[0][6] = "wn"; b[7][1] = b[7][6] = "bn";
        // Bishops
        b[0][2] = b[0][5] = "wb"; b[7][2] = b[7][5] = "bb";
        // Queens
        b[0][3] = "wq"; b[7][3] = "bq";
        // Kings
        b[0][4] = "wk"; b[7][4] = "bk";
        return b;
    }

    public ChessBoardGUI() {
        setPreferredSize(new Dimension(BOARD_SIZE * SQUARE_SIZE, BOARD_SIZE * SQUARE_SIZE));
        setBackground(Color.GRAY);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = 7 - e.getY() / SQUARE_SIZE;
                int col = e.getX() / SQUARE_SIZE;
                if (board[row][col] != null && ((whiteTurn && board[row][col].startsWith("w")) || (!whiteTurn && board[row][col].startsWith("b")))) {
                    fromRow = row;
                    fromCol = col;
                } else if (fromRow != -1) {
                    movePiece(fromRow, fromCol, row, col);
                    fromRow = -1;
                    fromCol = -1;
                    whiteTurn = !whiteTurn;
                    repaint();
                }
            }
        });
    }

    private void movePiece(int fromRow, int fromCol, int toRow, int toCol) {
        // Basic move (no full validation, demo)
        if (Math.abs(toRow - fromRow) <= 2 && Math.abs(toCol - fromCol) <= 2) { // Nearby moves
            board[toRow][toCol] = board[fromRow][fromCol];
            board[fromRow][fromCol] = null;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                Color color = (row + col) % 2 == 0 ? Color.WHITE : Color.DARK_GRAY;
                g.setColor(color);
                g.fillRect(col * SQUARE_SIZE, (7 - row) * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
                g.setColor(Color.BLACK);
                g.drawRect(col * SQUARE_SIZE, (7 - row) * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
                
                String piece = board[row][col];
                if (piece != null) {
                    g.setColor(Color.BLACK);
                    Font font = new Font("Serif", Font.BOLD, 32);
                    g.setFont(font);
                    char symbol = getSymbol(piece);
                    g.drawString(String.valueOf(symbol), col * SQUARE_SIZE + 10, (8 - row) * SQUARE_SIZE - 10);
                }
            }
        }
    }

    private char getSymbol(String piece) {
        return switch (piece) {
            case "wp", "bp" -> '♟';
            case "wr", "br" -> '♜';
            case "wn", "bn" -> '♞';
            case "wb", "bb" -> '♝';
            case "wq", "bq" -> '♛';
            case "wk", "bk" -> '♚';
            default -> '?';
        };
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Chess Board GUI");
        ChessBoardGUI board = new ChessBoardGUI();
        frame.add(board);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

