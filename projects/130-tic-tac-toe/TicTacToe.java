package com.randomjava.projects.tictactoe;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tic-Tac-Toe - two players at one keyboard, or one player against a minimax
 * opponent that cannot be beaten.
 *
 * <p>Minimax explores the whole game tree, which is tiny here: at most 9! =
 * 362,880 orderings, and far fewer once won positions stop the search. It
 * returns the value of a position assuming both sides play their best, so the
 * computer never loses. The depth term in the score makes it prefer winning
 * sooner and losing later, which is what stops it playing a pointless move when
 * every option loses anyway.
 */
public final class TicTacToe implements Project {

    public static final Meta META = new Meta(130, "tic-tac-toe", "Tic-Tac-Toe", "Core Java and Games", Kind.GAME,
            Difficulty.BEGINNER, "Noughts and crosses against a friend or an unbeatable minimax computer.",
            "Core Java, JavaFX, 2D arrays", true);

    private static final char EMPTY = '.';
    private static final char HUMAN = 'X';
    private static final char COMPUTER = 'O';

    /** Every line that wins, as board indices. */
    private static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}};

    private char[] board = new char[9];
    private boolean versusComputer = true;
    private char turn = HUMAN;
    private String outcome = "";

    public TicTacToe() {
        newGame(true);
    }

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // Game state
    // ------------------------------------------------------------------

    public void newGame(boolean againstComputer) {
        java.util.Arrays.fill(board, EMPTY);
        versusComputer = againstComputer;
        turn = HUMAN;
        outcome = "";
    }

    /**
     * Plays a move for the side whose turn it is, then lets the computer reply
     * when it is playing.
     *
     * @param cell 0-8, reading left to right and top to bottom
     */
    public void play(int cell) {
        if (!outcome.isEmpty()) {
            throw new IllegalStateException("That game is over. Start a new one.");
        }
        if (cell < 0 || cell > 8) {
            throw new IllegalArgumentException("Pick a square from 1 to 9.");
        }
        if (board[cell] != EMPTY) {
            throw new IllegalArgumentException("Square " + (cell + 1) + " is already taken.");
        }

        board[cell] = turn;
        if (settle()) {
            return;
        }
        turn = turn == HUMAN ? COMPUTER : HUMAN;

        if (versusComputer && turn == COMPUTER) {
            int reply = bestMove(COMPUTER);
            if (reply >= 0) {
                board[reply] = COMPUTER;
                if (settle()) {
                    return;
                }
                turn = HUMAN;
            }
        }
    }

    /** Records a win or a draw if the position is finished. */
    private boolean settle() {
        char winner = winner();
        if (winner != EMPTY) {
            outcome = versusComputer
                    ? (winner == HUMAN ? "You win" : "Computer wins")
                    : winner + " wins";
            return true;
        }
        if (isFull()) {
            outcome = "Draw";
            return true;
        }
        return false;
    }

    public char winner() {
        for (int[] line : LINES) {
            char first = board[line[0]];
            if (first != EMPTY && first == board[line[1]] && first == board[line[2]]) {
                return first;
            }
        }
        return EMPTY;
    }

    /** The winning line, for highlighting. Empty when nobody has won. */
    public List<Integer> winningLine() {
        for (int[] line : LINES) {
            char first = board[line[0]];
            if (first != EMPTY && first == board[line[1]] && first == board[line[2]]) {
                return List.of(line[0], line[1], line[2]);
            }
        }
        return List.of();
    }

    public boolean isFull() {
        for (char cell : board) {
            if (cell == EMPTY) {
                return false;
            }
        }
        return true;
    }

    public boolean isOver() {
        return !outcome.isEmpty();
    }

    // ------------------------------------------------------------------
    // Minimax
    // ------------------------------------------------------------------

    /** The index the given side should play, or -1 when the board is full. */
    public int bestMove(char side) {
        int bestScore = Integer.MIN_VALUE;
        int bestCell = -1;
        for (int cell = 0; cell < 9; cell++) {
            if (board[cell] != EMPTY) {
                continue;
            }
            board[cell] = side;
            int score = -negamax(other(side), 1);
            board[cell] = EMPTY;
            if (score > bestScore) {
                bestScore = score;
                bestCell = cell;
            }
        }
        return bestCell;
    }

    /**
     * Negamax: the same as minimax, but it negates the child score instead of
     * keeping separate maximising and minimising branches.
     *
     * @param side  who is to move
     * @param depth plies from the root, used to prefer faster wins
     * @return the value of the position from {@code side}'s point of view
     */
    private int negamax(char side, int depth) {
        char winner = winner();
        if (winner != EMPTY) {
            // The side to move has already lost, since the previous move won.
            return depth - 10;
        }
        if (isFull()) {
            return 0;
        }
        int best = Integer.MIN_VALUE;
        for (int cell = 0; cell < 9; cell++) {
            if (board[cell] != EMPTY) {
                continue;
            }
            board[cell] = side;
            int score = -negamax(other(side), depth + 1);
            board[cell] = EMPTY;
            best = Math.max(best, score);
        }
        return best;
    }

    private static char other(char side) {
        return side == HUMAN ? COMPUTER : HUMAN;
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    /** The board as nine strings, blank for empty, for the browser to draw. */
    public List<String> cells() {
        List<String> out = new ArrayList<>(9);
        for (char cell : board) {
            out.add(cell == EMPTY ? "" : String.valueOf(cell));
        }
        return out;
    }

    public String status() {
        if (!outcome.isEmpty()) {
            return outcome + ".";
        }
        if (versusComputer) {
            return turn == HUMAN ? "Your turn, playing X." : "Computer thinking.";
        }
        return "Player " + turn + " to move.";
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        boolean againstComputer = io.askYesNo("Play against the computer?", true);
        newGame(againstComputer);
        if (againstComputer) {
            io.muted("You are X. The computer plays a perfect game, so a draw is a good result.");
        }
        io.muted("Squares are numbered 1-9, left to right and top to bottom. Blank line to stop.");

        while (true) {
            io.println();
            drawBoard(io);
            io.muted(status());
            if (isOver()) {
                io.result("Game over", outcome);
                if (!io.askYesNo("Play again?", true)) {
                    return;
                }
                newGame(againstComputer);
                continue;
            }
            String input = io.ask(versusComputer ? "your square:" : "player " + turn + ", square:");
            if (input.isEmpty()) {
                return;
            }
            try {
                play(Integer.parseInt(input.trim()) - 1);
            } catch (NumberFormatException e) {
                io.error("Type a number from 1 to 9.");
            } catch (RuntimeException e) {
                io.error(e.getMessage());
            }
        }
    }

    private void drawBoard(ConsoleUI io) {
        for (int row = 0; row < 3; row++) {
            StringBuilder line = new StringBuilder("   ");
            for (int column = 0; column < 3; column++) {
                int index = row * 3 + column;
                line.append(board[index] == EMPTY ? String.valueOf(index + 1) : board[index]);
                if (column < 2) {
                    line.append(" | ");
                }
            }
            io.println(line.toString());
            if (row < 2) {
                io.println("  ---+---+---");
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "new" -> {
                    String mode = Json.str(body, "mode", "computer").toLowerCase(Locale.ROOT);
                    newGame(!mode.startsWith("two") && !mode.startsWith("human"));
                }
                case "play" -> play(Json.integer(body, "cell", -1));
                case "state" -> {
                    // fall through to the shared response
                }
                default -> {
                    return Json.error("Unknown action: " + action);
                }
            }
        } catch (RuntimeException e) {
            return Json.ok("cells", cells(), "line", winningLine(),
                    "over", isOver(), "result", status(), "detail", e.getMessage());
        }
        return Json.ok(
                "cells", cells(),
                "line", winningLine(),
                "over", isOver(),
                "result", isOver() ? outcome : status(),
                "detail", isOver()
                        ? "Press New game to play again."
                        : (versusComputer
                                ? "You are X against a perfect opponent - a draw is the best available."
                                : "Two players sharing one board."));
    }
}
