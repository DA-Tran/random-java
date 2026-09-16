package com.randomjava.projects.textbasedadventure;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.Map;
import java.util.Random;

/**
 * Text Based Adventure - Navigate rooms by typing commands, carrying an inventory and saving progress.
 *
 * <p>Scaffold. Both front ends already work and talk to this class; what is
 * missing is the real logic. Fill in the marked method and the terminal and
 * the browser both start behaving properly, with nothing written twice.
 */
public final class TextBasedAdventure implements Project {

    public static final Meta META = new Meta(
            132, "text-based-adventure", "Text Based Adventure", "Core Java and Games",
            Kind.GAME, Difficulty.BEGINNER, "Navigate rooms by typing commands, carrying an inventory and saving progress.",
            "Core Java, Java Collections, file I/O", false);

    @Override
    public Meta meta() {
        return META;
    }

    private final Random random = new Random();
    private int round;
    private int wins;
    private int losses;
    private int draws;

    // ------------------------------------------------------------------
    // The one method to implement
    // ------------------------------------------------------------------

    /**
     * Plays a single round.
     *
     * @param move what the player chose
     * @return a short description of what happened
     */
    private String playRound(String move) {
        // TODO: Navigate rooms by typing commands, carrying an inventory and saving progress.
        round++;
        int outcome = random.nextInt(3);
        if (outcome == 0) {
            wins++;
            return "You played '" + move + "' and won this round.";
        }
        if (outcome == 1) {
            losses++;
            return "You played '" + move + "' and lost this round.";
        }
        draws++;
        return "You played '" + move + "' and drew.";
    }

    private void reset() {
        round = 0;
        wins = 0;
        losses = 0;
        draws = 0;
    }

    private String scoreline() {
        return "Round " + round + "  |  " + wins + " won, " + losses + " lost, " + draws + " drawn";
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        reset();
        io.muted("Enter a move each round. Blank line to stop.");
        while (true) {
            String move = io.ask("your move:");
            if (move.isEmpty()) {
                io.result("final score", scoreline());
                return;
            }
            io.println("  " + playRound(move));
            io.muted(scoreline());
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        switch (action) {
            case "new" -> {
                reset();
                return Json.ok("result", "New game", "detail", scoreline());
            }
            case "play" -> {
                String move = Json.str(body, "move", "").trim();
                if (move.isEmpty()) {
                    return Json.error("Pick a move first.");
                }
                String outcome = playRound(move);
                return Json.ok("result", outcome, "detail", scoreline());
            }
            case "state" -> {
                return Json.ok("result", round == 0 ? "Ready" : "In progress", "detail", scoreline());
            }
            default -> {
                return Json.error("Unknown action: " + action);
            }
        }
    }
}
