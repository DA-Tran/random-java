package com.randomjava.projects.palindromechecker;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.Map;

/**
 * Palindrome Checker - tests a string, ignoring case, punctuation and spacing,
 * and also reports the longest palindromic substring inside it.
 */
public final class PalindromeChecker implements Project {

    public static final Meta META = new Meta(28, "palindrome-checker", "Palindrome Checker", "Algorithms and Data Structures", Kind.TOOL,
            Difficulty.BEGINNER, "Test words, phrases and numbers for palindromes, ignoring punctuation.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // Core
    // ------------------------------------------------------------------

    /** Keeps only letters and digits, lower-cased. */
    public static String normalise(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /** Two pointers walking inward. O(n) time, no extra allocation past the normalise. */
    public boolean isPalindrome(String text) {
        String cleaned = normalise(text);
        int left = 0;
        int right = cleaned.length() - 1;
        while (left < right) {
            if (cleaned.charAt(left) != cleaned.charAt(right)) {
                return false;
            }
            left++;
            right--;
        }
        return true;
    }

    /**
     * Longest palindromic substring by expanding around every centre.
     * O(n^2) time, O(1) extra space, which is plenty at human input sizes.
     *
     * @return the longest run, taken from the normalised text
     */
    public String longestPalindrome(String text) {
        String cleaned = normalise(text);
        if (cleaned.isEmpty()) {
            return "";
        }
        int bestStart = 0;
        int bestLength = 1;
        for (int centre = 0; centre < cleaned.length(); centre++) {
            for (int offset = 0; offset < 2; offset++) {
                int left = centre;
                int right = centre + offset;
                while (left >= 0 && right < cleaned.length()
                        && cleaned.charAt(left) == cleaned.charAt(right)) {
                    if (right - left + 1 > bestLength) {
                        bestStart = left;
                        bestLength = right - left + 1;
                    }
                    left--;
                    right++;
                }
            }
        }
        return cleaned.substring(bestStart, bestStart + bestLength);
    }

    private String detail(String text) {
        String cleaned = normalise(text);
        if (cleaned.isEmpty()) {
            return "Nothing left after removing punctuation and spacing.";
        }
        String longest = longestPalindrome(text);
        StringBuilder sb = new StringBuilder();
        sb.append("Normalised: ").append(cleaned);
        sb.append("\nReversed:   ").append(new StringBuilder(cleaned).reverse());
        sb.append("\nLongest palindromic run: ").append(longest)
                .append(" (").append(longest.length()).append(" characters)");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Try:  A man, a plan, a canal: Panama    racecar    12321    hello");
        io.muted("Blank line to finish.");
        while (true) {
            String input = io.ask("text:");
            if (input.isEmpty()) {
                return;
            }
            boolean palindrome = isPalindrome(input);
            io.result(palindrome ? "Palindrome" : "Not a palindrome", normalise(input));
            io.muted("Longest palindromic run: " + longestPalindrome(input));
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) {
            return Json.error("Unknown action: " + action);
        }
        String input = Json.str(body, "input", "");
        if (input.isBlank()) {
            return Json.error("Type something to check.");
        }
        return Json.ok(
                "result", isPalindrome(input) ? "Yes, a palindrome" : "No, not a palindrome",
                "detail", detail(input));
    }
}
