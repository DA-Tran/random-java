import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.animation.AnimationTimer;

public class chess_interactive extends Application {
    private static final int SIZE = 8;
    private static final int SQUARE = 60;
    private static final int WIDTH = SIZE * SQUARE;
    private static final int HEIGHT = SIZE * SQUARE + 60;
    private String[][] board = initializeBoard();
    private int fromX = -1, fromY = -1;
    private boolean whiteTurn = true;

    private static String[][] initializeBoard() {
        String[][] b = new String[8][8];
        // White pieces
        b[7][0] = b[7][7] = "wr"; b[7][1] = b[7][6] = "wn";
        b[7][2] = b[7][5] = "wb"; b[7][3] = "wq"; b[7][4] = "wk";
        for (int i = 0; i < 8; i++) b[6][i] = "wp";
        // Black pieces
        b[0][0] = b[0][7] = "br"; b[0][1] = b[0][6] = "bn";
        b[0][2] = b[0][5] = "bb"; b[0][3] = "bq"; b[0][4] = "bk";
        for (int i = 0; i < 8; i++) b[1][i] = "bp";
        return b;
    }

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawBoard(gc);

        canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> handleClick(e, gc));

        Scene scene = new Scene(new StackPane(canvas));
        stage.setScene(scene);
        stage.setTitle("Chess Interactive - JavaFX");
        stage.show();
    }

    private void handleClick(MouseEvent e, GraphicsContext gc) {
        int col = (int)(e.getX() / SQUARE);
        int row = 7 - (int)(e.getY() / SQUARE);
        if (row < 0 || row >= 8 || col < 0 || col >= 8) return;

        String piece = board[row][col];
        if (fromX == -1) {
            // Select piece
            if (piece != null && ((whiteTurn && piece.startsWith("w")) || (!whiteTurn && piece.startsWith("b")))) {
                fromX = col;
                fromY = row;
                drawBoard(gc);
                highlightSquare(gc, col, row, Color.YELLOW);
            }
        } else {
            // Move
            movePiece(fromY, fromX, row, col);
            whiteTurn = !whiteTurn;
            fromX = -1;
            fromY = -1;
            drawBoard(gc);
        }
    }

    private void movePiece(int fromRow, int fromCol, int toRow, int toCol) {
        // Basic move validation (pawn, knight demo)
        String piece = board[fromRow][fromCol];
        if (piece == null) return;
        
        int dx = Math.abs(toCol - fromCol);
        int dy = Math.abs(toRow - fromRow);
        if (dx <= 2 && dy <= 2) { // Simple range
            board[toRow][toCol] = piece;
            board[fromRow][fromCol] = null;
        }
    }

    private void highlightSquare(GraphicsContext gc, int col, int row) {
        gc.setFill(Color.YELLOW.deriveColor(0, 1, 1, 0.5));
        gc.fillRect(col * SQUARE, (7 - row) * SQUARE, SQUARE, SQUARE);
    }

    private void drawBoard(GraphicsContext gc) {
        // Board
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                gc.setFill((row + col) % 2 == 0 ? Color.BEIGE : Color.SADDLEBROWN);
                gc.fillRect(col * SQUARE, (7 - row) * SQUARE, SQUARE, SQUARE);
                gc.setStroke(Color.BLACK);
                gc.setLineWidth(2);
                gc.strokeRect(col * SQUARE, (7 - row) * SQUARE, SQUARE, SQUARE);
            }
        }

        // Pieces
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                String piece = board[row][col];
                if (piece != null) {
                    gc.setFill(piece.startsWith("w") ? Color.WHITE : Color.BLACK);
                    gc.setFont(javafx.scene.text.Font.font(36));
                    gc.fillText(getSymbol(piece), col * SQUARE + 12, (8 - row) * SQUARE - 8);
                }
            }
        }

        // Turn indicator
        gc.setFill(whiteTurn ? Color.WHITE : Color.BLACK);
        gc.setFont(javafx.scene.text.Font.font(20));
        gc.fillText((whiteTurn ? "White" : "Black") + " turn", 10, HEIGHT - 30);
    }

    private String getSymbol(String piece) {
        return switch (piece) {
            case "wp", "bp" -> "♟";
            case "wr", "br" -> "♖";
            case "wn", "bn" -> "♘";
            case "wb", "bb" -> "♗";
            case "wq", "bq" -> "♕";
            case "wk", "bk" -> "♔";
            default -> "?";
        };
    }

    public static void main(String[] args) {
        launch(args);
    }
}

