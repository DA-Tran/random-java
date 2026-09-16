package com.randomjava.projects.passwordstrengthchecker;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Password Strength Checker - scores a password by search-space entropy and
 * then penalises the patterns that make a theoretically large space small in
 * practice: dictionary words, keyboard runs, repeats and dates.
 *
 * <p>Entropy is reported in bits, which is the honest measure: it answers "how
 * many guesses would this take" rather than the meaningless "must contain a
 * symbol" rule that pushes people toward {@code Password1!}.
 *
 * <p>Nothing is stored, logged or sent anywhere; scoring happens in memory.
 */
public final class PasswordStrengthChecker implements Project {

    public static final Meta META = new Meta(61, "password-strength-checker", "Password Strength Checker", "Cybersecurity", Kind.TOOL,
            Difficulty.BEGINNER, "Score a password on length, variety and entropy, and explain the score.",
            "", true);

    /** A deliberately short sample; a real build would load a leaked-password list. */
    private static final Set<String> COMMON = Set.of(
            "password", "123456", "qwerty", "letmein", "admin", "welcome", "monkey",
            "dragon", "football", "iloveyou", "abc123", "login", "master", "sunshine",
            "princess", "passw0rd", "trustno1", "starwars", "whatever", "hello");

    private static final String[] KEYBOARD_RUNS = {
            "qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890"};

    @Override
    public Meta meta() {
        return META;
    }

    /** The outcome of scoring: entropy in bits, a verdict, and the reasons for it. */
    public record Score(double bits, String verdict, List<String> notes, String crackTime) {
    }

    // ------------------------------------------------------------------
    // Core
    // ------------------------------------------------------------------

    public Score score(String password) {
        if (password == null || password.isEmpty()) {
            return new Score(0, "empty", List.of("Nothing to score."), "instant");
        }

        List<String> notes = new ArrayList<>();
        int alphabet = alphabetSize(password);
        double bits = password.length() * (Math.log(alphabet) / Math.log(2));

        String lower = password.toLowerCase(java.util.Locale.ROOT);

        // Penalties. Each one shrinks the effective search space.
        if (COMMON.contains(lower)) {
            bits = Math.min(bits, 8);
            notes.add("This is one of the most common passwords in existence.");
        }
        for (String common : COMMON) {
            if (lower.contains(common) && !lower.equals(common)) {
                bits -= 12;
                notes.add("Contains the common word '" + common + "'.");
                break;
            }
        }
        if (hasKeyboardRun(lower, 4)) {
            bits -= 10;
            notes.add("Contains a keyboard run such as qwer or 1234.");
        }
        if (hasRepeat(password, 3)) {
            bits -= 8;
            notes.add("Contains a character repeated three or more times in a row.");
        }
        if (looksLikeYear(password)) {
            bits -= 6;
            notes.add("Contains something that looks like a year.");
        }
        if (password.length() < 12) {
            notes.add("Short. Length buys more security than any other single change.");
        }
        if (alphabet <= 26) {
            notes.add("Only one character class is in use.");
        }

        bits = Math.max(0, bits);
        if (notes.isEmpty()) {
            notes.add("No obvious patterns found.");
        }

        return new Score(bits, verdict(bits), notes, crackTime(bits));
    }

    private static int alphabetSize(String password) {
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean symbol = false;
        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else {
                symbol = true;
            }
        }
        int size = 0;
        if (lower) {
            size += 26;
        }
        if (upper) {
            size += 26;
        }
        if (digit) {
            size += 10;
        }
        if (symbol) {
            size += 33;
        }
        return Math.max(size, 2);
    }

    private static boolean hasKeyboardRun(String lower, int minimumLength) {
        for (String row : KEYBOARD_RUNS) {
            for (int i = 0; i + minimumLength <= row.length(); i++) {
                String run = row.substring(i, i + minimumLength);
                String reversed = new StringBuilder(run).reverse().toString();
                if (lower.contains(run) || lower.contains(reversed)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasRepeat(String password, int run) {
        int streak = 1;
        for (int i = 1; i < password.length(); i++) {
            streak = password.charAt(i) == password.charAt(i - 1) ? streak + 1 : 1;
            if (streak >= run) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeYear(String password) {
        for (int i = 0; i + 4 <= password.length(); i++) {
            String chunk = password.substring(i, i + 4);
            if (chunk.chars().allMatch(Character::isDigit)) {
                int value = Integer.parseInt(chunk);
                if (value >= 1900 && value <= 2100) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String verdict(double bits) {
        if (bits < 28) {
            return "very weak";
        }
        if (bits < 36) {
            return "weak";
        }
        if (bits < 60) {
            return "reasonable";
        }
        if (bits < 128) {
            return "strong";
        }
        return "very strong";
    }

    /** Rough offline-attack estimate at 100 billion guesses per second. */
    private static String crackTime(double bits) {
        double guesses = Math.pow(2, bits) / 2;
        double seconds = guesses / 1e11;
        if (seconds < 1) {
            return "under a second";
        }
        if (seconds < 60) {
            return Math.round(seconds) + " seconds";
        }
        if (seconds < 3600) {
            return Math.round(seconds / 60) + " minutes";
        }
        if (seconds < 86400) {
            return Math.round(seconds / 3600) + " hours";
        }
        if (seconds < 31557600.0) {
            return Math.round(seconds / 86400) + " days";
        }
        double years = seconds / 31557600.0;
        if (years < 1000) {
            return Math.round(years) + " years";
        }
        if (years < 1e9) {
            return String.format("%.0f thousand years", years / 1000);
        }
        return "longer than the age of the universe";
    }

    private String detail(Score result) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Entropy: %.1f bits", result.bits()));
        sb.append("\nOffline guessing at 100 billion tries per second: ").append(result.crackTime());
        sb.append("\n");
        for (String note : result.notes()) {
            sb.append("\n- ").append(note);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Nothing is stored or sent anywhere. Blank line to finish.");
        while (true) {
            String password = io.ask("password:");
            if (password.isEmpty()) {
                return;
            }
            Score result = score(password);
            io.result(result.verdict(), String.format("%.1f bits of entropy", result.bits()));
            io.muted("Time to crack offline: " + result.crackTime());
            for (String note : result.notes()) {
                io.muted("- " + note);
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) {
            return Json.error("Unknown action: " + action);
        }
        String password = Json.str(body, "input", "");
        if (password.isEmpty()) {
            return Json.error("Type a password to score it.");
        }
        Score result = score(password);
        return Json.ok("result", result.verdict(), "detail", detail(result));
    }
}
