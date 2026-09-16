package com.randomjava.projects.multiplayertrivia;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Multiplayer Trivia - timed questions with several players racing to answer.
 *
 * <p>Scoring by speed is the obvious design and it ruins the game. If the first
 * correct answer takes the points, one fast player wins every round and the
 * rest stop trying by question three. If everyone correct scores the same,
 * speed is worth nothing and it is not a race.
 *
 * <p>The compromise here: a correct answer always scores a base, and the speed
 * bonus decays with the time taken. Being quick is worth something; being slow
 * but right is still worth more than being fast and wrong. A wrong answer
 * scores nothing and, importantly, <b>does not lock the player out</b> - but it
 * does forfeit their speed bonus, so guessing early is a real risk rather than
 * a free option.
 *
 * <p>Time is supplied rather than read from the clock, so a test can play a
 * whole round deterministically instead of sleeping.
 */
public final class MultiplayerTrivia implements Project {

    public static final Meta META = new Meta(76, "multiplayer-trivia", "Multiplayer Trivia", "Game Development", Kind.GAME,
            Difficulty.INTERMEDIATE, "Timed questions with several players racing to answer.",
            "", true);

    public static final int BASE_POINTS = 100;
    public static final int MAX_SPEED_BONUS = 100;
    public static final int TIME_LIMIT = 20;

    public record Question(String text, List<String> options, int answer) {
        public String correctOption() { return options.get(answer); }
    }

    public static final List<Question> QUESTIONS = List.of(
            new Question("Which planet has the most moons?",
                    List.of("Jupiter", "Saturn", "Uranus", "Neptune"), 1),
            new Question("What is the capital of Australia?",
                    List.of("Sydney", "Melbourne", "Canberra", "Perth"), 2),
            new Question("How many bits are in a byte?",
                    List.of("4", "8", "16", "32"), 1),
            new Question("Which element has the symbol Fe?",
                    List.of("Fluorine", "Iron", "Francium", "Lead"), 1),
            new Question("In what year did the Berlin Wall fall?",
                    List.of("1987", "1989", "1991", "1993"), 1),
            new Question("What is the largest ocean?",
                    List.of("Atlantic", "Indian", "Arctic", "Pacific"), 3));

    public static final class Player {
        private final String name;
        private int score;
        private int correct;
        private int answered;
        private boolean answeredThisRound;

        Player(String name) { this.name = name; }
        public String name() { return name; }
        public int score() { return score; }
        public int correct() { return correct; }
        public int answered() { return answered; }
        public boolean answeredThisRound() { return answeredThisRound; }
    }

    private final Map<String, Player> players = new LinkedHashMap<>();
    private final Random random;
    private Question current;
    private int round;

    public MultiplayerTrivia() { this(new Random()); }

    public MultiplayerTrivia(Random random) {
        this.random = random;
        join("Alex");
        join("Bo");
        nextQuestion();
    }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Players and rounds
    // ------------------------------------------------------------------

    public Player join(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A player needs a name.");
        }
        String key = name.trim();
        if (players.containsKey(key)) {
            throw new IllegalArgumentException(key + " is already playing.");
        }
        Player player = new Player(key);
        players.put(key, player);
        return player;
    }

    public Question nextQuestion() {
        current = QUESTIONS.get(random.nextInt(QUESTIONS.size()));
        round++;
        for (Player player : players.values()) { player.answeredThisRound = false; }
        return current;
    }

    public Question current() { return current; }
    public int round() { return round; }
    public Collection<Player> players() { return List.copyOf(players.values()); }

    /**
     * The speed bonus, decaying linearly to nothing at the time limit.
     * Answering instantly is worth {@value #MAX_SPEED_BONUS} on top of the base;
     * answering at the buzzer is worth the base alone.
     */
    public static int speedBonus(int secondsTaken) {
        if (secondsTaken < 0) { throw new IllegalArgumentException("Time cannot be negative."); }
        if (secondsTaken >= TIME_LIMIT) { return 0; }
        return Math.round(MAX_SPEED_BONUS * (TIME_LIMIT - secondsTaken) / (float) TIME_LIMIT);
    }

    public record Result(String player, boolean correct, int points, String detail) { }

    /**
     * One player's answer. Everyone gets to answer each question - there is no
     * first-past-the-post, because that stops the slower half of the room
     * playing at all.
     */
    public Result answer(String playerName, int choice, int secondsTaken) {
        Player player = players.get(String.valueOf(playerName).trim());
        if (player == null) {
            throw new IllegalArgumentException("Nobody here called \"" + playerName + "\".");
        }
        if (player.answeredThisRound) {
            throw new IllegalArgumentException(player.name() + " has already answered this one.");
        }
        if (choice < 0 || choice >= current.options().size()) {
            throw new IllegalArgumentException("Pick an option from 0 to "
                    + (current.options().size() - 1) + ".");
        }
        player.answeredThisRound = true;
        player.answered++;

        if (choice != current.answer()) {
            return new Result(player.name(), false, 0,
                    "Wrong - it was \"" + current.correctOption() + "\".");
        }
        int bonus = speedBonus(secondsTaken);
        int points = BASE_POINTS + bonus;
        player.score += points;
        player.correct++;
        return new Result(player.name(), true, points,
                String.format("Correct. %d base + %d for answering in %ds.",
                        BASE_POINTS, bonus, secondsTaken));
    }

    public boolean roundComplete() {
        return players.values().stream().allMatch(p -> p.answeredThisRound);
    }

    public List<Player> leaderboard() {
        List<Player> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> b.score() - a.score());
        return sorted;
    }

    public void reset() {
        for (Player player : players.values()) {
            player.score = 0;
            player.correct = 0;
            player.answered = 0;
            player.answeredThisRound = false;
        }
        round = 0;
        nextQuestion();
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private String detail() {
        StringBuilder sb = new StringBuilder("Round " + round + ": " + current.text());
        for (int i = 0; i < current.options().size(); i++) {
            sb.append(String.format("%n  %d) %s", i, current.options().get(i)));
        }
        sb.append("\n\n  Leaderboard:");
        for (Player player : leaderboard()) {
            sb.append(String.format("%n    %-8s %4d  (%d of %d right)%s",
                    player.name(), player.score(), player.correct(), player.answered(),
                    player.answeredThisRound() ? " - answered" : ""));
        }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            io.println(detail());
            if (roundComplete()) {
                io.muted("Everyone has answered.");
                if (!io.askYesNo("Next question?", true)) { return; }
                nextQuestion();
                continue;
            }
            try {
                String who = io.ask("player (blank to quit):");
                if (who.isEmpty()) { return; }
                Result result = answer(who,
                        io.askInt("answer 0-3:", 0, current.options().size() - 1, 0),
                        io.askInt("seconds taken:", 0, 120, 5));
                if (result.correct()) { io.ok(result.detail()); }
                else { io.error(result.detail()); }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "start", "new" -> { reset(); return Json.ok("result", "Round " + round,
                        "detail", detail()); }
                case "next" -> { nextQuestion(); return Json.ok("result", "Round " + round,
                        "detail", detail()); }
                case "join" -> {
                    join(Json.str(body, "player", ""));
                    return Json.ok("result", "Joined", "detail", detail());
                }
                case "answer", "play", "compute" -> {
                    Result result = answer(Json.str(body, "player", "Alex"),
                            Json.integer(body, "choice", -1),
                            Json.integer(body, "seconds", 5));
                    String note = result.detail();
                    if (roundComplete()) {
                        note += "\n  Everyone has answered. Press Next.";
                    }
                    return Json.ok("result", result.correct()
                                    ? "+" + result.points() : "no points",
                            "message", result.correct() ? "Correct" : "Wrong",
                            "detail", note + "\n\n" + detail());
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
