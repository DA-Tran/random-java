import java.util.Scanner;

public class ChessGame_text {
    private char[][] board = {
        {'R','N','B','Q','K','B','N','R'},
        {'P','P','P','P','P','P','P','P'},
        {' ',' ',' ',' ',' ',' ',' ',' '},
        {' ',' ',' ',' ',' ',' ',' ',' '},
        {' ',' ',' ',' ',' ',' ',' ',' '},
        {' ',' ',' ',' ',' ',' ',' ',' '},
        {'p','p','p','p','p','p','p','p'},
        {'r','n','b','q','k','b','n','r'}
    };
    private boolean whiteTurn = true;
    private Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        ChessGame_text game = new ChessGame_text();
        game.play();
    }

    public void play() {
        System.out.println("Chess Console Text Version - e2 e4 moves. 'quit' to exit.");
        while (true) {
            printBoard();
            System.out.print((whiteTurn ? "White" : "Black") + " move (e.g., e2 e4) or 'quit': ");
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.equals("quit")) break;
            
            String[] parts = input.split("\\s+");
            if (parts.length != 2) {
                System.out.println("Invalid format!");
                continue;
            }
            
            if (move(parts[0], parts[1])) {
                whiteTurn = !whiteTurn;
            } else {
                System.out.println("Invalid move!");
            }
        }
        scanner.close();
    }

    private void printBoard() {
        System.out.println("\n  a b c d e f g h");
        for (int row = 0; row < 8; row++) {
            System.out.print((8 - row) + " ");
            for (int col = 0; col < 8; col++) {
                System.out.print(board[row][col] + " ");
            }
            System.out.println((8 - row));
        }
        System.out.println("  a b c d e f g h\n");
    }

    private boolean move(String from, String to) {
        int fromRow = 8 - Integer.parseInt(from.substring(1));
        int toRow = 8 - Integer.parseInt(to.substring(1));
        int fromCol = from.charAt(0) - 'a';
        int toCol = to.charAt(0) - 'a';
        
        if (fromRow < 0 || fromRow > 7 || toRow < 0 || toRow > 7 || 
            fromCol < 0 || fromCol > 7 || toCol < 0 || toCol > 7) return false;
        
        char piece = board[fromRow][fromCol];
        if (piece == ' ') return false;
        
        boolean isWhite = Character.isUpperCase(piece);
        if ((whiteTurn && !isWhite) || (!whiteTurn && isWhite)) return false;
        
        board[toRow][toCol] = piece;
        board[fromRow][fromCol] = ' ';
        return true;
    }
}

