package com.randomjava.projects.rivercrossing;

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
 * River Crossing - ferry everyone over without leaving a pair alone that must
 * not be left alone.
 *
 * <h2>A puzzle with no board</h2>
 *
 * <p>There is nothing to search over here except the situation itself. A
 * situation is fully described by who is on the near bank and where the boat
 * is, so it fits in a small integer: one bit per passenger, plus one for the
 * boat. The whole wolf-goat-cabbage puzzle has {@code 2^3 * 2 = 16} possible
 * situations, of which only ten are legal.
 *
 * <p>Once that is seen, the puzzle stops being a puzzle and becomes a graph:
 * the legal situations are vertices, a crossing is an edge, and the question
 * is the shortest path from everyone-on-the-near-bank to everyone-across.
 * {@link #solve} is a breadth-first search, which finds the shortest crossing
 * by construction, over a graph small enough to hold in a few bytes.
 *
 * <h2>The rule is about who is left, not who travels</h2>
 *
 * <p>The constraint is easy to state wrongly. The wolf may not be left with the
 * goat - but it is perfectly fine for both to be on the same bank while the
 * farmer is there too, and fine for them to pass each other. What matters is
 * only the bank the boat has just left. {@link #safe} therefore checks each
 * bank <em>without</em> the ferryman, and only the bank he is not on can be in
 * trouble.
 *
 * <p>That is also why the goat has to come back. Every first move except taking
 * the goat leaves a forbidden pair behind, and after the second crossing the
 * only way forward is to carry something back - the step people find
 * counter-intuitive, and the one a breadth-first search finds without any
 * insight at all.
 */
public final class RiverCrossing implements Project {

    public static final Meta META = new Meta(
            245, "river-crossing", "River Crossing", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Get everyone across without leaving the wrong pair alone together.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    /**
     * A puzzle: who travels, which pairs may not be left, the boat's size, and
     * whether there is a separate ferryman.
     *
     * <p>That last flag matters more than it looks. With a ferryman - the
     * farmer of the classic puzzle - he is not one of the travellers, so the
     * boat may cross carrying nobody at all, which is simply him rowing back.
     * Without one, somebody aboard has to row, and an empty crossing is
     * impossible. The wolf-goat-cabbage solution needs the empty crossing
     * twice, so getting this wrong makes the puzzle look unsolvable.
     */
    public record Crossing(List<String> names, List<int[]> forbidden, int capacity,
                           boolean ferryman) { }

    /** Wolf, goat and cabbage - the boat holds the farmer and one thing. */
    public static Crossing classic() {
        return new Crossing(List.of("wolf", "goat", "cabbage"),
                List.of(new int[] {0, 1}, new int[] {1, 2}), 1, true);
    }

    /**
     * Three missionaries and three cannibals, boat of two. The rule is not a
     * list of pairs but a count, so it is expressed as every pairing that would
     * leave cannibals outnumbering missionaries.
     */
    public static Crossing missionaries() {
        List<String> names = List.of("m1", "m2", "m3", "c1", "c2", "c3");
        return new Crossing(names, List.of(), 2, false);
    }

    private Crossing puzzle = classic();
    /** Bit set for the near bank, plus the boat's side. */
    private int nearBank;
    private boolean boatNear = true;
    private List<String> plan = new ArrayList<>();

    private boolean missionaryRules() {
        return puzzle.forbidden().isEmpty() && puzzle.names().size() == 6;
    }

    /**
     * Whether a situation is allowed. Only the bank the ferryman is away from
     * can be unsafe, because his presence keeps the peace.
     */
    public boolean safe(int near, boolean ferrymanNear) {
        int count = puzzle.names().size();
        int far = ((1 << count) - 1) & ~near;
        int unattended = ferrymanNear ? far : near;
        if (missionaryRules()) {
            // Cannibals must never outnumber missionaries on either bank.
            for (int side : new int[] {near, far}) {
                int missionaries = Integer.bitCount(side & 0b000111);
                int cannibals = Integer.bitCount(side & 0b111000);
                if (missionaries > 0 && cannibals > missionaries) {
                    return false;
                }
            }
            return true;
        }
        for (int[] pair : puzzle.forbidden()) {
            boolean both = (unattended >> pair[0] & 1) == 1 && (unattended >> pair[1] & 1) == 1;
            if (both) {
                return false;
            }
        }
        return true;
    }

    /** Every load the boat could take from the bank it is on. */
    private List<Integer> loads(int near, boolean ferrymanNear) {
        int count = puzzle.names().size();
        int available = ferrymanNear ? near : (((1 << count) - 1) & ~near);
        List<Integer> out = new ArrayList<>();
        for (int load = 0; load < (1 << count); load++) {
            if ((load & ~available) != 0) {
                continue;   // cannot carry what is not on this bank
            }
            int size = Integer.bitCount(load);
            // An empty boat is the ferryman rowing back alone. Without a
            // ferryman someone aboard has to row, so it is not an option.
            if (size >= (puzzle.ferryman() ? 0 : 1) && size <= puzzle.capacity()) {
                out.add(load);
            }
        }
        return out;
    }

    /** The shortest sequence of crossings, or null when there is none. */
    public List<String> solve() {
        int count = puzzle.names().size();
        int everyone = (1 << count) - 1;
        int start = everyone * 2 + 1;           // everyone near, and so is the boat
        Map<Integer, int[]> cameFrom = new HashMap<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        cameFrom.put(start, new int[] {-1, -1});
        while (!queue.isEmpty()) {
            int state = queue.poll();
            int near = state >> 1;
            boolean ferrymanNear = (state & 1) == 1;
            if (near == 0 && !ferrymanNear) {
                return trace(cameFrom, state);
            }
            for (int load : loads(near, ferrymanNear)) {
                int nextNear = ferrymanNear ? near & ~load : near | load;
                boolean nextFerryman = !ferrymanNear;
                if (!safe(nextNear, nextFerryman)) {
                    continue;
                }
                int code = nextNear * 2 + (nextFerryman ? 1 : 0);
                if (!cameFrom.containsKey(code)) {
                    cameFrom.put(code, new int[] {state, load});
                    queue.add(code);
                }
            }
        }
        return null;
    }

    private List<String> trace(Map<Integer, int[]> cameFrom, int state) {
        List<String> steps = new ArrayList<>();
        while (cameFrom.get(state)[0] >= 0) {
            int[] from = cameFrom.get(state);
            boolean goingOver = (state & 1) == 0;
            StringBuilder who = new StringBuilder();
            if (from[1] == 0) {
                steps.add(0, goingOver ? "row across alone" : "row back alone");
                state = from[0];
                continue;
            }
            for (int i = 0; i < puzzle.names().size(); i++) {
                if ((from[1] >> i & 1) == 1) {
                    who.append(who.length() > 0 ? " and " : "").append(puzzle.names().get(i));
                }
            }
            steps.add(0, (goingOver ? "take the " : "bring the ") + who
                    + (goingOver ? " across" : " back"));
            state = from[0];
        }
        return steps;
    }

    // ------------------------------------------------------------------
    // Board state
    // ------------------------------------------------------------------

    public void reset(String which) {
        puzzle = "missionaries".equalsIgnoreCase(which) ? missionaries() : classic();
        nearBank = (1 << puzzle.names().size()) - 1;
        boatNear = true;
        plan = new ArrayList<>();
    }

    public int nearBank() {
        return nearBank;
    }

    public boolean boatNear() {
        return boatNear;
    }

    public List<String> plan() {
        return new ArrayList<>(plan);
    }

    public String run() {
        List<String> steps = solve();
        if (steps == null) {
            return "There is no way to get everyone across.";
        }
        plan = steps;
        nearBank = 0;
        boatNear = false;
        return "Everyone across in " + steps.size() + " crossings.";
    }

    public String[][] display() {
        List<String> near = new ArrayList<>();
        List<String> far = new ArrayList<>();
        for (int i = 0; i < puzzle.names().size(); i++) {
            ((nearBank >> i & 1) == 1 ? near : far).add(puzzle.names().get(i));
        }
        if (boatNear) {
            near.add("boat");
        } else {
            far.add("boat");
        }
        return new String[][] {
            {"near", String.join(" ", near.isEmpty() ? List.of("-") : near)},
            {"far", String.join(" ", far.isEmpty() ? List.of("-") : far)}};
    }

    private String detail() {
        int count = puzzle.names().size();
        int situations = (1 << count) * 2;
        int legal = 0;
        for (int near = 0; near < (1 << count); near++) {
            for (int side = 0; side < 2; side++) {
                if (safe(near, side == 1)) {
                    legal++;
                }
            }
        }
        String progress = plan.isEmpty() ? "Nobody has crossed yet."
                : plan.size() + " crossings planned.";
        return String.format("%d travellers, boat holds %d. %d of %d situations are legal. %s",
                count, puzzle.capacity(), legal, situations, progress);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        reset("classic");
        io.muted("The ferryman rows. Nothing may be left on a bank with something that "
                + "would eat it.");
        while (true) {
            io.println();
            for (String[] row : display()) {
                io.println("  " + row[0] + ": " + row[1]);
            }
            io.muted(detail());
            for (String step : plan) {
                io.println("    " + step);
            }
            int choice = io.menu("River Crossing",
                    List.of("Solve", "Wolf, goat and cabbage", "Missionaries and cannibals"));
            if (choice < 0) {
                return;
            }
            if (choice == 0) {
                io.info(run());
            } else {
                reset(choice == 1 ? "classic" : "missionaries");
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    reset(Json.str(body, "puzzle", "classic"));
                    return board("Press Solve to find the shortest set of crossings.");
                }
                case "solve", "step" -> {
                    return board(run());
                }
                default -> {
                    return Json.error("Unknown action: " + action);
                }
            }
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }

    private Map<String, Object> board(String note) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String[] row : display()) {
            items.add(Json.map("label", row[0], "meta", row[1]));
        }
        for (String step : plan) {
            items.add(Json.map("label", step, "meta", ""));
        }
        Map<String, Object> out = Json.ok("items", items, "detail", detail());
        if (note != null && !note.isEmpty()) {
            out.put("result", note);
        }
        return out;
    }
}
