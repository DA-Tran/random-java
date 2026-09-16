package com.randomjava.projects.zebrapuzzle;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Zebra Puzzle - five houses, five owners, and fifteen clues that between them
 * pin down who keeps the zebra.
 *
 * <h2>Permutations, not cells</h2>
 *
 * <p>The natural model is a five-by-five grid of facts, but that invites
 * searching over 25 independent cells and then discovering far too late that a
 * colour has been used twice. The better model falls out of what the puzzle
 * actually says: each of the five categories - colour, nationality, drink,
 * smoke, pet - assigns its five values to the five houses <em>bijectively</em>.
 * A category is therefore a permutation, and a solution is five of them.
 *
 * <p>That makes "no value used twice" true by construction rather than
 * something to check, which removes a whole class of wrong states before the
 * search starts. It also shrinks the space to {@code (5!)^5}, and almost all of
 * that is cut away by the ordering below.
 *
 * <h2>Check a clue the moment it becomes checkable</h2>
 *
 * <p>Brute force would fix all five permutations and then test the clues, at
 * which point a contradiction between the first two categories has cost the
 * work of choosing the other three. Instead {@link #search} fixes one category
 * at a time and, after each, tests every clue whose <em>both</em> categories
 * are now decided.
 *
 * <p>So "the Englishman lives in the red house" is checked as soon as colours
 * and nationalities are known, discarding those pairings without ever
 * considering a drink. This is the same principle as solving a cryptarithm
 * column by column rather than letter by letter: reject as early as the
 * information allows, not as late as the structure permits.
 *
 * <h2>The clues that mention no category twice</h2>
 *
 * <p>Two clue shapes carry the arithmetic rather than a pairing: {@code at}
 * fixes a value to a house outright, and {@code rightOf} and {@code nextTo}
 * constrain the distance between two values. The positional ones are worth
 * noticing because they are what make the puzzle solvable at all - without
 * "the Norwegian lives in the first house" and "milk is drunk in the middle
 * house" anchoring two categories to actual positions, the clues would only
 * describe the arrangement up to a relabelling.
 */
public final class ZebraPuzzle implements Project {

    public static final Meta META = new Meta(
            244, "zebra-puzzle", "Zebra Puzzle", "Logic and Puzzle Games",
            Kind.TOOL, Difficulty.ADVANCED,
            "Work out who owns the zebra from a list of constraints, by elimination.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    public static final String[] CATEGORIES = {
        "colour", "nation", "drink", "smoke", "pet"};

    public static final String[][] VALUES = {
        {"red", "green", "ivory", "yellow", "blue"},
        {"english", "spaniard", "ukrainian", "norwegian", "japanese"},
        {"coffee", "tea", "milk", "juice", "water"},
        {"oldgold", "kools", "chesterfields", "luckystrike", "parliaments"},
        {"dog", "snails", "fox", "horse", "zebra"}};

    /** How a clue relates two values, or fixes one to a house. */
    private enum Kind2 { SAME, RIGHT_OF, NEXT_TO, AT }

    /** {@code kind} over ({@code catA},{@code valA}) and ({@code catB},{@code valB}). */
    private record Clue(Kind2 kind, int catA, int valA, int catB, int valB, int house) { }

    private static int value(int category, String name) {
        for (int i = 0; i < VALUES[category].length; i++) {
            if (VALUES[category][i].equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No " + CATEGORIES[category] + " called " + name);
    }

    private static Clue same(int ca, String a, int cb, String b) {
        return new Clue(Kind2.SAME, ca, value(ca, a), cb, value(cb, b), -1);
    }

    private static Clue rightOf(int ca, String a, int cb, String b) {
        return new Clue(Kind2.RIGHT_OF, ca, value(ca, a), cb, value(cb, b), -1);
    }

    private static Clue nextTo(int ca, String a, int cb, String b) {
        return new Clue(Kind2.NEXT_TO, ca, value(ca, a), cb, value(cb, b), -1);
    }

    private static Clue at(int ca, String a, int house) {
        return new Clue(Kind2.AT, ca, value(ca, a), ca, value(ca, a), house);
    }

    /** The 1962 Life International puzzle, as fifteen clues. */
    public static List<String> clues() {
        return List.of(
                "The Englishman lives in the red house.",
                "The Spaniard owns the dog.",
                "Coffee is drunk in the green house.",
                "The Ukrainian drinks tea.",
                "The green house is immediately right of the ivory house.",
                "The Old Gold smoker owns snails.",
                "Kools are smoked in the yellow house.",
                "Milk is drunk in the middle house.",
                "The Norwegian lives in the first house.",
                "Chesterfields are smoked next to the man with the fox.",
                "Kools are smoked next to the house with the horse.",
                "The Lucky Strike smoker drinks orange juice.",
                "The Japanese smokes Parliaments.",
                "The Norwegian lives next to the blue house.");
    }

    private static final int COLOUR = 0;
    private static final int NATION = 1;
    private static final int DRINK = 2;
    private static final int SMOKE = 3;
    private static final int PET = 4;

    private static List<Clue> constraints() {
        return List.of(
                same(NATION, "english", COLOUR, "red"),
                same(NATION, "spaniard", PET, "dog"),
                same(DRINK, "coffee", COLOUR, "green"),
                same(NATION, "ukrainian", DRINK, "tea"),
                rightOf(COLOUR, "green", COLOUR, "ivory"),
                same(SMOKE, "oldgold", PET, "snails"),
                same(SMOKE, "kools", COLOUR, "yellow"),
                at(DRINK, "milk", 2),
                at(NATION, "norwegian", 0),
                nextTo(SMOKE, "chesterfields", PET, "fox"),
                nextTo(SMOKE, "kools", PET, "horse"),
                same(SMOKE, "luckystrike", DRINK, "juice"),
                same(NATION, "japanese", SMOKE, "parliaments"),
                nextTo(NATION, "norwegian", COLOUR, "blue"));
    }

    // ------------------------------------------------------------------
    // Solving
    // ------------------------------------------------------------------

    private static final List<int[]> PERMUTATIONS = permutationsOfFive();

    private static List<int[]> permutationsOfFive() {
        List<int[]> out = new ArrayList<>();
        permute(new int[] {0, 1, 2, 3, 4}, 0, out);
        return out;
    }

    private static void permute(int[] order, int at, List<int[]> out) {
        if (at == order.length) {
            out.add(order.clone());
            return;
        }
        for (int i = at; i < order.length; i++) {
            int held = order[at];
            order[at] = order[i];
            order[i] = held;
            permute(order, at + 1, out);
            held = order[at];
            order[at] = order[i];
            order[i] = held;
        }
    }

    /**
     * Whether a clue holds. {@code assigned} says which categories are decided
     * so far; a clue touching an undecided category is not yet judged.
     */
    private static boolean holds(Clue clue, int[][] houseOf, boolean[] assigned) {
        if (!assigned[clue.catA()] || !assigned[clue.catB()]) {
            return true;
        }
        int a = houseOf[clue.catA()][clue.valA()];
        int b = houseOf[clue.catB()][clue.valB()];
        return switch (clue.kind()) {
            case SAME -> a == b;
            case RIGHT_OF -> a == b + 1;
            case NEXT_TO -> Math.abs(a - b) == 1;
            case AT -> a == clue.house();
        };
    }

    /**
     * Fixes one category at a time, testing every clue that has become
     * checkable. Counts solutions up to {@code cap} and keeps the first.
     */
    private static int search(List<Clue> clues, int category, int[][] houseOf,
                              boolean[] assigned, int cap, int[][][] found) {
        if (category == CATEGORIES.length) {
            if (found[0] == null) {
                int[][] copy = new int[houseOf.length][];
                for (int i = 0; i < houseOf.length; i++) {
                    copy[i] = houseOf[i].clone();
                }
                found[0] = copy;
            }
            return 1;
        }
        int total = 0;
        for (int[] order : PERMUTATIONS) {
            houseOf[category] = order;
            assigned[category] = true;
            boolean ok = true;
            for (Clue clue : clues) {
                if (!holds(clue, houseOf, assigned)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                total += search(clues, category + 1, houseOf, assigned, cap - total, found);
            }
            assigned[category] = false;
            if (total >= cap) {
                break;
            }
        }
        return total;
    }

    /** {@code [category][value]} gives the house, 0 to 4, or null if unsolvable. */
    public static int[][] solve() {
        int[][][] found = new int[1][][];
        search(constraints(), 0, new int[CATEGORIES.length][], new boolean[CATEGORIES.length],
                1, found);
        return found[0];
    }

    /** How many arrangements satisfy every clue, counted no further than {@code cap}. */
    public static int countSolutions(int cap) {
        return search(constraints(), 0, new int[CATEGORIES.length][],
                new boolean[CATEGORIES.length], cap, new int[1][][]);
    }

    /** The value of a category in a given house. */
    public static String valueIn(int[][] houseOf, int category, int house) {
        for (int v = 0; v < VALUES[category].length; v++) {
            if (houseOf[category][v] == house) {
                return VALUES[category][v];
            }
        }
        return "?";
    }

    /** Who has a given thing - "japanese" for the zebra, say. */
    public static String whoHas(int[][] houseOf, int category, String value) {
        int house = houseOf[category][value(category, value)];
        return valueIn(houseOf, NATION, house);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private String compute(String input) {
        int[][] answer = solve();
        if (answer == null) {
            return "The clues contradict each other.";
        }
        String asked = input == null ? "" : input.trim().toLowerCase();
        if (asked.contains("water")) {
            return "The " + whoHas(answer, DRINK, "water") + " drinks the water.";
        }
        if (!asked.isEmpty() && !asked.contains("zebra")) {
            for (int category = 0; category < CATEGORIES.length; category++) {
                for (String value : VALUES[category]) {
                    if (asked.contains(value)) {
                        return "The " + whoHas(answer, category, value) + " has the "
                                + value + ".";
                    }
                }
            }
        }
        return "The " + whoHas(answer, PET, "zebra") + " owns the zebra.";
    }

    private String detail() {
        int[][] answer = solve();
        if (answer == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int category = 0; category < CATEGORIES.length; category++) {
            out.append(String.format("%-8s", CATEGORIES[category]));
            for (int house = 0; house < 5; house++) {
                out.append(String.format("%-15s", valueIn(answer, category, house)));
            }
            out.append('\n');
        }
        out.append("Exactly ").append(countSolutions(2))
                .append(" arrangement satisfies all fourteen clues.");
        return out.toString();
    }

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Five houses in a row. Ask about the zebra, the water, or any other "
                + "thing in the puzzle. Blank line to finish.");
        for (String clue : clues()) {
            io.println("    " + clue);
        }
        while (true) {
            String input = io.ask("ask about:");
            if (input.isEmpty()) {
                return;
            }
            io.result("answer", compute(input));
            io.println(detail());
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) {
            return Json.error("Unknown action: " + action);
        }
        try {
            return Json.ok("result", compute(Json.str(body, "input", "")),
                    "detail", detail());
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }
}
