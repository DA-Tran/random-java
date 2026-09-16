package com.randomjava.projects.rockpaperscissors;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Rock Paper Scissors - best of N against the computer.
 *
 * <p>The opponent is not purely random. People are predictable: they repeat a
 * winning throw and switch after losing. This opponent keeps a frequency count
 * of what you have played and, most of the time, plays the counter to your
 * favourite. It still throws randomly often enough to stay beatable.
 */
public final class RockPaperScissors implements Project {

    public static final Meta META = new Meta(9, "rock-paper-scissors", "Rock Paper Scissors", "Beginner Friendly", Kind.GAME,
            Difficulty.BEGINNER, "Play best-of against the computer and keep a running score.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    public enum Move {
        ROCK, PAPER, SCISSORS;

        /** The move that beats this one. */
        Move beatenBy() {
            return switch (this) {
                case ROCK -> PAPER;
                case PAPER -> SCISSORS;
                case SCISSORS -> ROCK;
            };
        }

        boolean beats(Move other) {
            return other.beatenBy() == this;
        }

        String label() {
            return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
        }

        static Move parse(String text) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Choose rock, paper or scissors.");
            }
            String cleaned = text.trim().toLowerCase(Locale.ROOT);
            if (cleaned.startsWith("r")) {
                return ROCK;
            }
            if (cleaned.startsWith("p")) {
                return PAPER;
            }
            if (cleaned.startsWith("s")) {
                return SCISSORS;
            }
            throw new IllegalArgumentException("'" + text + "' is not rock, paper or scissors.");
        }
    }

    private final Random random = new Random();
    private final int[] playerHistory = new int[3];
    private int target = 3;
    private int playerScore;
    private int computerScore;
    private int draws;

    // ------------------------------------------------------------------
    // Core
    // ------------------------------------------------------------------

    public void newGame(int bestOf) {
        target = Math.max(1, bestOf) / 2 + 1;
        playerScore = 0;
        computerScore = 0;
        draws = 0;
        java.util.Arrays.fill(playerHistory, 0);
    }

    /** Picks the counter to the player's most frequent move, 70% of the time. */
    private Move chooseComputerMove() {
        int total = playerHistory[0] + playerHistory[1] + playerHistory[2];
        if (total < 3 || random.nextInt(100) < 30) {
            return Move.values()[random.nextInt(3)];
        }
        int favourite = 0;
        for (int i = 1; i < 3; i++) {
            if (playerHistory[i] > playerHistory[favourite]) {
                favourite = i;
            }
        }
        return Move.values()[favourite].beatenBy();
    }

    /** The outcome of one round. */
    public record Round(Move player, Move computer, String outcome, boolean gameOver, String winner) {
    }

    public Round play(Move playerMove) {
        if (isOver()) {
            throw new IllegalStateException("That game is finished. Start a new one.");
        }
        playerHistory[playerMove.ordinal()]++;
        Move computerMove = chooseComputerMove();

        String outcome;
        if (playerMove == computerMove) {
            draws++;
            outcome = "Draw";
        } else if (playerMove.beats(computerMove)) {
            playerScore++;
            outcome = playerMove.label() + " beats " + computerMove.label() + " - you win the round";
        } else {
            computerScore++;
            outcome = computerMove.label() + " beats " + playerMove.label() + " - you lose the round";
        }

        boolean over = isOver();
        String winner = over ? (playerScore > computerScore ? "You win the match" : "Computer wins the match") : "";
        return new Round(playerMove, computerMove, outcome, over, winner);
    }

    public boolean isOver() {
        return playerScore >= target || computerScore >= target;
    }

    public String scoreline() {
        return "You " + playerScore + " - " + computerScore + " Computer"
                + (draws > 0 ? "  (" + draws + " drawn)" : "")
                + "  |  first to " + target;
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        int bestOf = io.askInt("best of (odd number):", 1, 99, 3);
        newGame(bestOf);
        io.muted("Type rock, paper or scissors. Blank line to give up.");
        while (!isOver()) {
            String input = io.ask("your move:");
            if (input.isEmpty()) {
                io.muted("Walked away at " + scoreline());
                return;
            }
            try {
                Round round = play(Move.parse(input));
                io.println("  You played " + round.player().label()
                        + ", computer played " + round.computer().label() + ".");
                io.info(round.outcome());
                io.muted(scoreline());
                if (round.gameOver()) {
                    io.result("Match over", round.winner());
                }
            } catch (RuntimeException e) {
                io.error(e.getMessage());
            }
        }
        if (io.askYesNo("Play again?", true)) {
            runText(io);
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        switch (action) {
            case "new" -> {
                newGame(Json.integer(body, "bestOf", 3));
                return Json.ok("result", "New match", "detail", scoreline(),
                        "moves", List.of("Rock", "Paper", "Scissors"));
            }
            case "state" -> {
                return Json.ok(
                        "result", playerScore + computerScore + draws == 0 ? "Ready" : "In progress",
                        "detail", scoreline());
            }
            case "play" -> {
                try {
                    Round round = play(Move.parse(Json.str(body, "move", "")));
                    String headline = round.gameOver() ? round.winner() : round.outcome();
                    String detail = "You played " + round.player().label()
                            + ", computer played " + round.computer().label()
                            + "\n" + scoreline()
                            + (round.gameOver() ? "\nPress New match to play again." : "");
                    return Json.ok("result", headline, "detail", detail);
                } catch (RuntimeException e) {
                    return Json.error(e.getMessage());
                }
            }
            default -> {
                return Json.error("Unknown action: " + action);
            }
        }
    }
}
