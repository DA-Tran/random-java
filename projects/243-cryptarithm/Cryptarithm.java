package com.randomjava.projects.cryptarithm;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cryptarithm - each letter stands for a digit, the same letter always the same
 * digit, and the sum has to come out right. {@code SEND + MORE = MONEY}.
 *
 * <h2>Solve columns, not letters</h2>
 *
 * <p>The obvious approach assigns digits to letters and checks the arithmetic at
 * the end: with {@code k} distinct letters that is {@code 10*9*8*...}
 * assignments, and every one of them is carried all the way to a full
 * evaluation before anything can be rejected. For the eight letters of
 * {@code SEND + MORE = MONEY} that is over 1.8 million complete sums.
 *
 * <p>But addition is done in columns, and a column is almost independent of the
 * others - it needs only what carries into it. So {@link #search} walks the
 * columns from the right, assigning digits to letters <em>as it first meets
 * them</em>, and the moment a column is complete it checks that the digits in
 * it actually add up. A wrong guess in the units column is rejected after
 * assigning three letters, not after assigning all eight.
 *
 * <p>That is the whole idea, and it is worth about four orders of magnitude.
 * The classic puzzle is settled in a few thousand steps rather than millions,
 * and the search never evaluates a sum at all - correctness falls out of each
 * column agreeing as it is closed.
 *
 * <h2>The two rules that are easy to forget</h2>
 *
 * <ul>
 *   <li><b>Letters are distinct.</b> Two letters may not share a digit, which
 *       is what makes the puzzle finite and is also the main source of
 *       pruning.</li>
 *   <li><b>No leading zeros.</b> A multi-letter word may not start with 0,
 *       otherwise {@code SEND + MORE = MONEY} admits nonsense where MONEY is a
 *       four-digit number wearing a zero. Single-letter words are exempt,
 *       since 0 is a perfectly good number on its own.</li>
 * </ul>
 *
 * <p>Both are enforced at the moment a letter is first assigned, so a bad
 * choice dies immediately rather than at the end.
 *
 * <h2>Counting rather than stopping</h2>
 *
 * <p>{@link #countSolutions} runs the same search without stopping at the first
 * answer, because "this puzzle has exactly one solution" is a more interesting
 * thing to be able to say than "here is an answer". A good cryptarithm is
 * expected to have precisely one, and plenty of plausible-looking ones do not.
 */
public final class Cryptarithm implements Project {

    public static final Meta META = new Meta(
            243, "cryptarithm", "Cryptarithm", "Logic and Puzzle Games",
            Kind.TOOL, Difficulty.INTERMEDIATE,
            "Solve puzzles like SEND + MORE = MONEY by assigning a digit to each letter.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    /** A parsed puzzle: some addends and the total they must reach. */
    public record Puzzle(List<String> addends, String total) {

        /** Every distinct letter used, in the order first seen. */
        public String letters() {
            StringBuilder seen = new StringBuilder();
            for (String word : all()) {
                for (char letter : word.toCharArray()) {
                    if (seen.indexOf(String.valueOf(letter)) < 0) {
                        seen.append(letter);
                    }
                }
            }
            return seen.toString();
        }

        public List<String> all() {
            List<String> words = new ArrayList<>(addends);
            words.add(total);
            return words;
        }
    }

    /**
     * Reads {@code "SEND + MORE = MONEY"}. Case and spacing are free; anything
     * that is not a letter, a plus or an equals is rejected.
     */
    public static Puzzle parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Nothing to solve.");
        }
        String cleaned = text.toUpperCase().replaceAll("\\s+", "");
        if (!cleaned.matches("[A-Z]+(\\+[A-Z]+)*=[A-Z]+")) {
            throw new IllegalArgumentException(
                    "Write it like SEND + MORE = MONEY - letters, plus signs, one equals.");
        }
        String[] sides = cleaned.split("=");
        List<String> addends = new ArrayList<>(List.of(sides[0].split("\\+")));
        Puzzle puzzle = new Puzzle(addends, sides[1]);
        if (puzzle.letters().length() > 10) {
            throw new IllegalArgumentException(
                    "There are only ten digits, and that uses " + puzzle.letters().length()
                    + " letters.");
        }
        return puzzle;
    }

    // ------------------------------------------------------------------
    // The search
    // ------------------------------------------------------------------

    private static final int NO_DIGIT = -1;

    /**
     * Counts solutions, stopping at {@code cap}, and keeps the first one.
     *
     * <p>Columns are walked from the right. Within a column each addend's
     * letter is assigned if it is not already, and when the column runs out of
     * addends the total's letter for that column is forced by the digits above
     * it - which is where a wrong guess is caught.
     */
    private static int search(Puzzle puzzle, int column, int index, int sum,
                              int[] valueOf, boolean[] digitUsed,
                              int cap, Map<Character, Integer>[] found) {
        int columns = puzzle.total().length();
        if (column == columns) {
            // Every column closed; any carry left over means the total is short.
            if (sum != 0) {
                return 0;
            }
            if (found[0] == null) {
                Map<Character, Integer> answer = new LinkedHashMap<>();
                for (char letter : puzzle.letters().toCharArray()) {
                    answer.put(letter, valueOf[letter - 'A']);
                }
                found[0] = answer;
            }
            return 1;
        }

        if (index < puzzle.addends().size()) {
            String word = puzzle.addends().get(index);
            if (column >= word.length()) {
                return search(puzzle, column, index + 1, sum, valueOf, digitUsed, cap, found);
            }
            char letter = word.charAt(word.length() - 1 - column);
            if (valueOf[letter - 'A'] != NO_DIGIT) {
                return search(puzzle, column, index + 1, sum + valueOf[letter - 'A'],
                        valueOf, digitUsed, cap, found);
            }
            int total = 0;
            for (int digit = 0; digit <= 9; digit++) {
                if (digitUsed[digit] || (digit == 0 && word.length() > 1
                        && column == word.length() - 1)) {
                    continue;
                }
                valueOf[letter - 'A'] = digit;
                digitUsed[digit] = true;
                total += search(puzzle, column, index + 1, sum + digit,
                        valueOf, digitUsed, cap - total, found);
                valueOf[letter - 'A'] = NO_DIGIT;
                digitUsed[digit] = false;
                if (total >= cap) {
                    break;
                }
            }
            return total;
        }

        // The column is complete, so the total's digit here is determined.
        char letter = puzzle.total().charAt(columns - 1 - column);
        int digit = sum % 10;
        int carry = sum / 10;
        if (valueOf[letter - 'A'] != NO_DIGIT) {
            if (valueOf[letter - 'A'] != digit) {
                return 0;
            }
            return search(puzzle, column + 1, 0, carry, valueOf, digitUsed, cap, found);
        }
        if (digitUsed[digit] || (digit == 0 && columns > 1 && column == columns - 1)) {
            return 0;
        }
        valueOf[letter - 'A'] = digit;
        digitUsed[digit] = true;
        int total = search(puzzle, column + 1, 0, carry, valueOf, digitUsed, cap, found);
        valueOf[letter - 'A'] = NO_DIGIT;
        digitUsed[digit] = false;
        return total;
    }

    @SuppressWarnings("unchecked")
    private static Map<Character, Integer>[] slot() {
        return new Map[1];
    }

    /** How many assignments satisfy the puzzle, counted no further than {@code cap}. */
    public static int countSolutions(Puzzle puzzle, int cap) {
        int[] valueOf = new int[26];
        java.util.Arrays.fill(valueOf, NO_DIGIT);
        // An addend longer than the total can never be reached.
        for (String word : puzzle.addends()) {
            if (word.length() > puzzle.total().length()) {
                return 0;
            }
        }
        return search(puzzle, 0, 0, 0, valueOf, new boolean[10], cap, slot());
    }

    /** The letter-to-digit map that solves the puzzle, or null if none does. */
    public static Map<Character, Integer> solve(Puzzle puzzle) {
        int[] valueOf = new int[26];
        java.util.Arrays.fill(valueOf, NO_DIGIT);
        for (String word : puzzle.addends()) {
            if (word.length() > puzzle.total().length()) {
                return null;
            }
        }
        Map<Character, Integer>[] found = slot();
        search(puzzle, 0, 0, 0, valueOf, new boolean[10], 1, found);
        return found[0];
    }

    /** Substitutes an assignment back into a word. */
    public static long valueOf(String word, Map<Character, Integer> assignment) {
        long value = 0;
        for (char letter : word.toCharArray()) {
            value = value * 10 + assignment.get(letter);
        }
        return value;
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private String compute(String input) {
        if (input == null || input.isBlank()) {
            return "Try SEND + MORE = MONEY";
        }
        Puzzle puzzle = parse(input);
        Map<Character, Integer> answer = solve(puzzle);
        if (answer == null) {
            return "No assignment of digits makes that sum work.";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < puzzle.addends().size(); i++) {
            out.append(i > 0 ? " + " : "").append(valueOf(puzzle.addends().get(i), answer));
        }
        out.append(" = ").append(valueOf(puzzle.total(), answer));
        return out.toString();
    }

    /** The letter mapping and whether the puzzle is a good one, as a note. */
    private String detail(String input) {
        try {
            Puzzle puzzle = parse(input);
            Map<Character, Integer> answer = solve(puzzle);
            if (answer == null) {
                return puzzle.letters().length() + " letters, no solution.";
            }
            StringBuilder out = new StringBuilder();
            answer.forEach((letter, digit) -> out.append(letter).append('=')
                    .append(digit).append(' '));
            int count = countSolutions(puzzle, 2);
            out.append(count == 1
                    ? " - and it is the only one, which is what makes it a good puzzle."
                    : " - but it is not the only assignment that works.");
            return out.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Write a sum in letters, like SEND + MORE = MONEY. Blank line to finish.");
        while (true) {
            String input = io.ask("puzzle:");
            if (input.isEmpty()) {
                return;
            }
            try {
                io.result("solution", compute(input));
                String note = detail(input);
                if (!note.isEmpty()) {
                    io.muted(note);
                }
            } catch (RuntimeException e) {
                io.error(e.getMessage());
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) {
            return Json.error("Unknown action: " + action);
        }
        String input = Json.str(body, "input", "");
        try {
            return Json.ok("result", compute(input), "detail", detail(input));
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }
}
