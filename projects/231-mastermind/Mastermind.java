package com.randomjava.projects.mastermind;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Mastermind - guess the hidden code from black and white peg feedback.
 *
 * <p><b>The scoring is the whole project, and duplicate colours are where it
 * goes wrong.</b> Black pegs (right colour, right place) are easy. White pegs -
 * right colour, wrong place - are not, and the naive rule "count how many of my
 * colours appear anywhere in the code" double-counts as soon as a colour
 * repeats.
 *
 * <p>Code {@code R R G B}, guess {@code R B B B}. The guess has one R in the
 * right place, so one black. For whites, the naive count asks "is B in the
 * code?" three times and answers yes three times, reporting three whites for a
 * single B in the code. Total pegs then exceed the code length, which is
 * impossible and immediately visible - but the same bug produces merely
 * <em>wrong</em> answers in cases where it is not.
 *
 * <p>The fix is that every peg consumes something. Exact matches are removed
 * from both sides first; then each remaining guess colour is matched against
 * the remaining code colours and both are struck off. A colour appearing twice
 * in the guess and once in the code can only earn one peg.
 *
 * <p>The invariant worth holding onto: {@code black + white} can never exceed
 * the code length, for any code and any guess. {@link #score} is tested against
 * that exhaustively over every pair of codes.
 */
public final class Mastermind implements Project {

    public static final Meta META = new Meta(231, "mastermind", "Mastermind", "Logic and Puzzle Games", Kind.GAME,
            Difficulty.BEGINNER, "Deduce a hidden colour code from black and white peg feedback.",
            "", true);

    public static final String COLOURS = "RGBYOP";
    public static final int LENGTH = 4;
    public static final int MAX_GUESSES = 10;

    /** Black: right colour and place. White: right colour, wrong place. */
    public record Score(int black, int white) {
        public boolean solved() { return black == LENGTH; }
        @Override public String toString() { return black + " black, " + white + " white"; }
    }

    public record Guess(String code, Score score) { }

    private String secret;
    private final List<Guess> guesses = new ArrayList<>();
    private final Random random;

    public Mastermind() { this(new Random()); }

    public Mastermind(Random random) {
        this.random = random;
        newGame();
    }

    @Override public Meta meta() { return META; }

    public void newGame() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LENGTH; i++) {
            sb.append(COLOURS.charAt(random.nextInt(COLOURS.length())));
        }
        secret = sb.toString();
        guesses.clear();
    }

    public void setSecret(String code) {
        validate(code);
        secret = code.toUpperCase(Locale.ROOT);
        guesses.clear();
    }

    public String secret() { return secret; }
    public List<Guess> guesses() { return List.copyOf(guesses); }
    public int remaining() { return MAX_GUESSES - guesses.size(); }
    public boolean won() {
        return !guesses.isEmpty() && guesses.get(guesses.size() - 1).score().solved();
    }
    public boolean over() { return won() || guesses.size() >= MAX_GUESSES; }

    public static void validate(String code) {
        if (code == null || code.length() != LENGTH) {
            throw new IllegalArgumentException("A code is exactly " + LENGTH + " colours.");
        }
        for (char c : code.toUpperCase(Locale.ROOT).toCharArray()) {
            if (COLOURS.indexOf(c) < 0) {
                throw new IllegalArgumentException(
                        "\"" + c + "\" is not a colour. Use any of " + COLOURS + ".");
            }
        }
    }

    /**
     * Scores a guess against a code.
     *
     * <p>Two passes, and the order matters. Exact matches are taken out first
     * and both sides marked used, so a peg can never be counted twice - which
     * is precisely what goes wrong when a colour appears more often in the
     * guess than in the code.
     */
    public static Score score(String code, String guess) {
        validate(code);
        validate(guess);
        String a = code.toUpperCase(Locale.ROOT);
        String b = guess.toUpperCase(Locale.ROOT);

        boolean[] codeUsed = new boolean[LENGTH];
        boolean[] guessUsed = new boolean[LENGTH];
        int black = 0;
        for (int i = 0; i < LENGTH; i++) {
            if (a.charAt(i) == b.charAt(i)) {
                black++;
                codeUsed[i] = true;
                guessUsed[i] = true;
            }
        }

        int white = 0;
        for (int i = 0; i < LENGTH; i++) {
            if (guessUsed[i]) { continue; }
            for (int j = 0; j < LENGTH; j++) {
                if (codeUsed[j] || a.charAt(j) != b.charAt(i)) { continue; }
                // Both are consumed, so neither can be matched again.
                white++;
                codeUsed[j] = true;
                break;
            }
        }
        return new Score(black, white);
    }

    public Score guess(String code) {
        if (over()) { throw new IllegalStateException("The game is over."); }
        Score result = score(secret, code);
        guesses.add(new Guess(code.toUpperCase(Locale.ROOT), result));
        return result;
    }

    /**
     * Every code still consistent with the feedback so far.
     *
     * <p>This is the actual deduction: a candidate survives only if scoring it
     * against each past guess reproduces the score that guess received. The
     * count falling is what "narrowing it down" means, and it is the honest
     * measure of how much a guess was worth.
     */
    public List<String> consistent() {
        List<String> out = new ArrayList<>();
        int total = (int) Math.pow(COLOURS.length(), LENGTH);
        for (int n = 0; n < total; n++) {
            StringBuilder sb = new StringBuilder();
            int value = n;
            for (int i = 0; i < LENGTH; i++) {
                sb.append(COLOURS.charAt(value % COLOURS.length()));
                value /= COLOURS.length();
            }
            String candidate = sb.toString();
            boolean fits = true;
            for (Guess past : guesses) {
                if (!score(candidate, past.code()).equals(past.score())) { fits = false; break; }
            }
            if (fits) { out.add(candidate); }
        }
        return out;
    }

    private String detail() {
        StringBuilder sb = new StringBuilder();
        for (Guess g : guesses) {
            sb.append(String.format("  %s   %s%n", g.code(), g.score()));
        }
        if (won()) {
            sb.append("Cracked it in ").append(guesses.size()).append(" guesses.");
        } else if (over()) {
            sb.append("Out of guesses. It was ").append(secret).append('.');
        } else {
            sb.append(String.format("%d guesses left. %d codes still fit.",
                    remaining(), consistent().size()));
        }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Four colours from " + COLOURS + ", repeats allowed. " + MAX_GUESSES + " tries.");
        while (true) {
            io.println();
            io.println(detail());
            if (over()) { return; }
            try {
                Score result = guess(io.ask("guess:").trim());
                io.result(result.toString(), result.solved() ? "cracked it" : "keep going");
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "start", "new", "generate" -> {
                    newGame();
                    return Json.ok("result", "New code set",
                            "detail", "Four colours from " + COLOURS + ", repeats allowed.\n  "
                                    + (int) Math.pow(COLOURS.length(), LENGTH)
                                    + " possible codes.");
                }
                case "guess", "play", "compute" -> {
                    String code = Json.str(body, "guess", "");
                    if (code.isBlank()) { code = Json.str(body, "input", ""); }
                    Score result = guess(code);
                    return Json.ok("result", result.toString(),
                            "message", result.solved() ? "Cracked it" : "Not yet",
                            "detail", detail());
                }
                case "reveal", "solve" -> {
                    String was = secret;
                    newGame();
                    return Json.ok("result", was, "detail", "It was " + was + ". New code set.");
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
