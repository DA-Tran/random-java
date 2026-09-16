package com.randomjava.projects.wordscramble;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Word Scramble - unscramble a shuffled word against the clock.
 *
 * <p>Two details decide whether this is playable.
 *
 * <p><b>A scramble must actually scramble.</b> Shuffling at random will, often
 * enough to be annoying, hand back the original word - and on a three-letter
 * word that is one time in six. Worse, it can leave the word nearly intact, so
 * the puzzle is no puzzle. The shuffle here is repeated until the result
 * differs, and gives up gracefully on words that cannot be rearranged at all,
 * like "aaa" or any single letter.
 *
 * <p><b>Anagrams mean a guess is not one word.</b> "Listen" scrambled might be
 * answered with "silent", which is a completely correct unscrambling of the
 * letters given. Comparing against the single word that happened to be chosen
 * marks a right answer wrong. Any word from the dictionary with the same
 * letters is accepted, which is the rule the player is actually playing by.
 */
public final class WordScramble implements Project {

    public static final Meta META = new Meta(78, "word-scramble", "Word Scramble", "Game Development", Kind.GAME,
            Difficulty.BEGINNER, "Unscramble a shuffled word against the clock.",
            "", true);

    private static final List<String> WORDS = List.of(
            "listen", "silent", "enlist", "tinsel", "garden", "danger", "gander",
            "orchestra", "carthorse", "player", "replay", "parley", "marine", "remain",
            "airmen", "stone", "notes", "tones", "onset", "seton", "dusty", "study",
            "night", "thing", "angel", "angle", "glean", "cider", "cried", "dicer",
            "below", "bowel", "elbow", "spare", "pears", "parse", "reaps", "spear",
            "lemon", "melon", "table", "bleat", "bacon", "candle", "lanced", "puzzle",
            "rocket", "planet", "guitar", "pencil", "window", "coffee", "bridge",
            "castle", "jungle", "kettle", "mirror", "napkin", "orange", "pillow");

    /** Words sharing a letter multiset, so every valid answer is accepted. */
    private static final Map<String, List<String>> BY_LETTERS = groupByLetters();

    private static Map<String, List<String>> groupByLetters() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (String word : WORDS) {
            map.computeIfAbsent(letters(word), k -> new ArrayList<>()).add(word);
        }
        return map;
    }

    /** A word's letters, sorted - the canonical form every anagram shares. */
    public static String letters(String word) {
        char[] chars = word.toLowerCase(Locale.ROOT).toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }

    public record Round(String scrambled, int letters, List<String> accepted) { }

    private final Random random;
    private String answer = "";
    private String scrambled = "";
    private int score;
    private int attempts;
    private int solved;
    private int streak;
    private int bestStreak;

    public WordScramble() { this(new Random()); }

    public WordScramble(Random random) {
        this.random = random;
        next();
    }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Scrambling
    // ------------------------------------------------------------------

    /**
     * Shuffles until the result differs from the original.
     *
     * <p>Bounded, because some words cannot be rearranged: one letter, or every
     * letter the same. Those are returned as they are rather than looping - a
     * scramble that cannot scramble is a property of the word, not a failure.
     */
    public static String scramble(String word, Random random) {
        if (word == null || word.length() < 2) { return String.valueOf(word); }
        char[] chars = word.toCharArray();
        for (int attempt = 0; attempt < 20; attempt++) {
            for (int i = chars.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                char swap = chars[i];
                chars[i] = chars[j];
                chars[j] = swap;
            }
            String candidate = new String(chars);
            if (!candidate.equals(word)) { return candidate; }
        }
        return new String(chars);
    }

    /** True when a word has at least two distinct letters to rearrange. */
    public static boolean canScramble(String word) {
        return word != null && word.length() > 1
                && word.chars().distinct().count() > 1;
    }

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    public Round next() {
        answer = WORDS.get(random.nextInt(WORDS.size()));
        scrambled = scramble(answer, random);
        return round();
    }

    public Round round() {
        return new Round(scrambled, answer.length(), accepted());
    }

    /** Every dictionary word with these letters. All of them are correct. */
    public List<String> accepted() {
        return BY_LETTERS.getOrDefault(letters(answer), List.of(answer));
    }

    public String scrambled() { return scrambled; }
    public int score() { return score; }
    public int attempts() { return attempts; }
    public int solved() { return solved; }
    public int streak() { return streak; }
    public int bestStreak() { return bestStreak; }

    /** Scoring one guess. Any anagram of the letters shown counts. */
    public boolean guess(String word) {
        if (word == null || word.isBlank()) {
            throw new IllegalArgumentException("Type a guess.");
        }
        String clean = word.trim().toLowerCase(Locale.ROOT);
        attempts++;
        if (accepted().contains(clean)) {
            solved++;
            streak++;
            bestStreak = Math.max(bestStreak, streak);
            // Longer words are worth more, and a streak compounds.
            score += clean.length() * 10 + streak * 5;
            return true;
        }
        streak = 0;
        return false;
    }

    /** Was the guess a real rearrangement, just not a word we know? */
    public boolean isRearrangement(String word) {
        return word != null && letters(word.trim().toLowerCase(Locale.ROOT))
                .equals(letters(answer));
    }

    public String reveal() { return answer; }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private String detail() {
        List<String> others = new ArrayList<>(accepted());
        return String.format("%d letters. Score %d from %d attempts, streak %d (best %d).%s",
                answer.length(), score, attempts, streak, bestStreak,
                others.size() > 1 ? "\n  This one has " + others.size()
                        + " valid answers - any of them counts." : "");
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            io.result(scrambled.toUpperCase(Locale.ROOT), detail());
            String guess = io.ask("guess (blank to skip, q to quit):");
            if (guess.equalsIgnoreCase("q")) { return; }
            if (guess.isEmpty()) {
                io.muted("It was \"" + reveal() + "\".");
                streak = 0;
                next();
                continue;
            }
            try {
                if (guess(guess)) {
                    io.ok("Right. " + score + " points.");
                    next();
                } else if (isRearrangement(guess)) {
                    io.error("Those are the right letters, but not a word I know.");
                } else {
                    io.error("That does not use the same letters.");
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "start", "generate", "new" -> {
                    next();
                    return Json.ok("result", scrambled.toUpperCase(Locale.ROOT),
                            "detail", detail());
                }
                case "guess", "play", "compute" -> {
                    String word = Json.str(body, "guess", "");
                    if (word.isBlank()) { word = Json.str(body, "input", ""); }
                    boolean right = guess(word);
                    String note;
                    if (right) {
                        note = "Right - \"" + word.trim().toLowerCase(Locale.ROOT) + "\". "
                                + score + " points.";
                        next();
                    } else if (isRearrangement(word)) {
                        note = "Those are the right letters, but not a word in the list.";
                    } else {
                        note = "That does not use the same letters.";
                    }
                    return Json.ok("result", scrambled.toUpperCase(Locale.ROOT),
                            "message", right ? "Correct" : "Not quite",
                            "detail", note + "\n  " + detail());
                }
                case "reveal", "solve" -> {
                    String was = reveal();
                    streak = 0;
                    next();
                    return Json.ok("result", scrambled.toUpperCase(Locale.ROOT),
                            "detail", "It was \"" + was + "\".\n  " + detail());
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
