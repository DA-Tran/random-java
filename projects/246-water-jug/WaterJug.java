package com.randomjava.projects.waterjug;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Water Jug - two unmarked jugs, a tap and a drain. Measure out an exact amount.
 *
 * <h2>You can know the answer before searching for it</h2>
 *
 * <p>Every move either fills a jug to the brim, empties one completely, or
 * pours one into the other until the source runs dry or the target is full. So
 * whatever is in the jugs is always some whole number of {@code a}s plus some
 * whole number of {@code b}s - a combination {@code xa + yb} with {@code x} and
 * {@code y} integers, positive or negative.
 *
 * <p>Bézout's identity says exactly which numbers those are: the integer
 * combinations of {@code a} and {@code b} are precisely the multiples of
 * {@code gcd(a, b)}. Since a jug also cannot hold more than it holds, the
 * measurable amounts are the multiples of the gcd up to the larger jug, and
 * nothing else. {@link #reachable} is that one line, and it settles any
 * instance in the time it takes to run Euclid.
 *
 * <p>So 3 and 5 can measure anything up to 5, because their gcd is 1. Jugs of 4
 * and 6 can never measure 5, however long you pour - their gcd is 2 and 5 is
 * odd. No search is needed to know that, and a solver that shrugs and reports
 * failure after exploring every state has proved nothing the arithmetic could
 * not have said immediately.
 *
 * <h2>And then the search, for the route rather than the verdict</h2>
 *
 * <p>Knowing an amount is measurable does not say how, so {@link #solve} runs a
 * breadth-first search over the {@code (left, right)} states. Breadth-first
 * rather than depth-first because the interesting answer is the <em>shortest</em>
 * pouring sequence, and BFS finds that by construction. The state space is only
 * {@code (a+1)(b+1)} pairs, so it is small however large the puzzle looks.
 *
 * <p>The two are worth keeping separate. The gcd test is a proof and costs
 * nothing; the search is a route and costs a little. Asking the cheap question
 * first means the expensive one is only ever asked when it has an answer.
 */
public final class WaterJug implements Project {

    public static final Meta META = new Meta(
            246, "water-jug", "Water Jug", "Logic and Puzzle Games",
            Kind.TOOL, Difficulty.INTERMEDIATE,
            "Measure an exact amount using jugs that have no markings.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    public static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /**
     * Whether the target can be measured at all - a multiple of the gcd, and no
     * more than the larger jug holds. Bézout, not searching.
     */
    public static boolean reachable(int left, int right, int target) {
        if (left <= 0 || right <= 0 || target < 0) {
            return false;
        }
        if (target > Math.max(left, right)) {
            return false;
        }
        return target % gcd(left, right) == 0;
    }

    /**
     * The shortest sequence of moves reaching the target, or null when there is
     * none. Breadth-first, because the shortest route is the interesting one.
     */
    public static List<String> solve(int left, int right, int target) {
        if (!reachable(left, right, target)) {
            return null;
        }
        int start = 0;
        Map<Integer, int[]> cameFrom = new HashMap<>();   // state -> {previous, move}
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        cameFrom.put(start, new int[] {-1, -1});
        while (!queue.isEmpty()) {
            int state = queue.poll();
            int a = state / (right + 1);
            int b = state % (right + 1);
            if (a == target || b == target) {
                return trace(cameFrom, state, left, right);
            }
            for (int move = 0; move < 6; move++) {
                int[] next = apply(a, b, left, right, move);
                int code = next[0] * (right + 1) + next[1];
                if (!cameFrom.containsKey(code)) {
                    cameFrom.put(code, new int[] {state, move});
                    queue.add(code);
                }
            }
        }
        return null;
    }

    /** The six things you can do: fill either, empty either, pour either way. */
    private static int[] apply(int a, int b, int left, int right, int move) {
        return switch (move) {
            case 0 -> new int[] {left, b};
            case 1 -> new int[] {a, right};
            case 2 -> new int[] {0, b};
            case 3 -> new int[] {a, 0};
            case 4 -> {
                int poured = Math.min(a, right - b);
                yield new int[] {a - poured, b + poured};
            }
            default -> {
                int poured = Math.min(b, left - a);
                yield new int[] {a + poured, b - poured};
            }
        };
    }

    private static String name(int move, int left, int right) {
        return switch (move) {
            case 0 -> "fill the " + left;
            case 1 -> "fill the " + right;
            case 2 -> "empty the " + left;
            case 3 -> "empty the " + right;
            case 4 -> "pour the " + left + " into the " + right;
            default -> "pour the " + right + " into the " + left;
        };
    }

    private static List<String> trace(Map<Integer, int[]> cameFrom, int state,
                                      int left, int right) {
        List<String> steps = new ArrayList<>();
        while (cameFrom.get(state)[0] >= 0) {
            int[] from = cameFrom.get(state);
            int a = state / (right + 1);
            int b = state % (right + 1);
            steps.add(0, name(from[1], left, right) + " -> (" + a + ", " + b + ")");
            state = from[0];
        }
        return steps;
    }

    // ------------------------------------------------------------------
    // Front end
    // ------------------------------------------------------------------

    private int[] parse(String input) {
        String[] parts = input.trim().split("[^0-9]+");
        List<Integer> numbers = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                numbers.add(Integer.parseInt(part));
            }
        }
        if (numbers.size() != 3) {
            throw new IllegalArgumentException(
                    "Give three numbers: the two jug sizes and the amount wanted.");
        }
        if (numbers.get(0) <= 0 || numbers.get(1) <= 0) {
            throw new IllegalArgumentException("A jug has to hold something.");
        }
        if (numbers.get(0) > 200 || numbers.get(1) > 200) {
            throw new IllegalArgumentException("Keep the jugs under 200.");
        }
        return new int[] {numbers.get(0), numbers.get(1), numbers.get(2)};
    }

    private String compute(String input) {
        if (input == null || input.isBlank()) {
            return "Try 5 3 4 - two jugs and the amount to measure.";
        }
        int[] parsed = parse(input);
        int left = parsed[0];
        int right = parsed[1];
        int target = parsed[2];
        if (!reachable(left, right, target)) {
            int step = gcd(left, right);
            return target > Math.max(left, right)
                    ? "Impossible: " + target + " is more than either jug holds."
                    : "Impossible: every amount you can reach is a multiple of "
                      + step + ", and " + target + " is not.";
        }
        List<String> steps = solve(left, right, target);
        if (steps == null || steps.isEmpty()) {
            return target + " is already there - both jugs start empty.";
        }
        return "Measured " + target + " in " + steps.size()
               + (steps.size() == 1 ? " move." : " moves.");
    }

    private String detail(String input) {
        try {
            int[] parsed = parse(input);
            int step = gcd(parsed[0], parsed[1]);
            List<String> steps = solve(parsed[0], parsed[1], parsed[2]);
            StringBuilder out = new StringBuilder("gcd(" + parsed[0] + ", " + parsed[1]
                    + ") = " + step + ", so the reachable amounts are the multiples of "
                    + step + " up to " + Math.max(parsed[0], parsed[1]) + ".");
            if (steps != null) {
                for (String line : steps) {
                    out.append("\n  ").append(line);
                }
            }
            return out.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Two jug sizes and the amount to measure, like 5 3 4. "
                + "Blank line to finish.");
        while (true) {
            String input = io.ask("jugs and target:");
            if (input.isEmpty()) {
                return;
            }
            try {
                io.result("answer", compute(input));
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
