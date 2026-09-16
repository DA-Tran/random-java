package com.randomjava.projects.rushhour;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Rush Hour - slide the blocking cars aside so the red one can drive out.
 *
 * <h2>The board is not the state</h2>
 *
 * <p>A six-by-six grid has 36 cells, but the cars cannot be anywhere: each one
 * is stuck on its own row or column and can only slide along it. So the state
 * is not the grid at all - it is one number per car saying how far along its
 * lane it sits. A dozen cars give a dozen small numbers, and the grid is only
 * ever reconstructed to check what is blocking what.
 *
 * <p>That matters for the visited set. Two arrangements are the same position
 * if every car is in the same place, and comparing a dozen offsets is cheaper
 * and safer than comparing grids - safer because a grid comparison would
 * happily treat two different cars of the same length as interchangeable and
 * merge positions that are genuinely distinct.
 *
 * <h2>Breadth-first, because the question is "fewest moves"</h2>
 *
 * <p>Depth-first would find <em>a</em> way out, usually a ridiculous one. Rush
 * Hour is always posed as a shortest-solution puzzle, so {@link #solve} is
 * breadth-first, which finds the minimum by construction.
 *
 * <p>One subtlety in what counts as a move: sliding a car three squares is one
 * move, not three. So the search generates, for each car, every reachable
 * offset in both directions as a single step. Counting each square separately
 * would still find a route but would report the wrong number, and would make
 * the queue much larger for no gain.
 *
 * <h2>Making one is the same search, run for its length</h2>
 *
 * <p>A random arrangement of cars is usually either already solved or solvable
 * in two dull moves. So {@link #generate} lays cars out at random and then
 * <em>solves</em> the result, keeping it only if the shortest way out takes at
 * least a few moves. The solver is the generator's quality filter, which is
 * cheaper than trying to design difficulty directly.
 */
public final class RushHour implements Project {

    public static final Meta META = new Meta(
            247, "rush-hour", "Rush Hour", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.ADVANCED,
            "Slide blocking cars aside to drive the red car out of the jam.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    private static final int SIZE = 6;
    /** The red car sits on this row and leaves by the right-hand edge. */
    private static final int EXIT_ROW = 2;

    private final Random random;

    /** Per car: {@code {lane, length, horizontal}} - fixed for the whole puzzle. */
    private List<int[]> cars = new ArrayList<>();
    /** How far along its lane each car sits. Mutable; this is the state. */
    private int[] offsets = new int[0];
    private int[] start = new int[0];
    private List<String> plan = new ArrayList<>();
    /** Where the cars end up once the plan is played out. */
    private int[] finish = new int[0];

    public int carCount() {
        return cars.size();
    }

    public int[] offsets() {
        return offsets.clone();
    }

    /** Puts the cars back where the puzzle started. */
    public void restart() {
        offsets = start.clone();
        plan = new ArrayList<>();
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    private char[][] gridOf(int[] state) {
        char[][] grid = new char[SIZE][SIZE];
        for (char[] line : grid) {
            Arrays.fill(line, '.');
        }
        for (int car = 0; car < cars.size(); car++) {
            int[] shape = cars.get(car);
            char mark = car == 0 ? '*' : (char) ('a' + car - 1);
            for (int i = 0; i < shape[1]; i++) {
                if (shape[2] == 1) {
                    grid[shape[0]][state[car] + i] = mark;
                } else {
                    grid[state[car] + i][shape[0]] = mark;
                }
            }
        }
        return grid;
    }

    /** Whether a car could sit at this offset without meeting another. */
    private boolean fits(int[] state, int car, int offset) {
        int[] shape = cars.get(car);
        if (offset < 0 || offset + shape[1] > SIZE) {
            return false;
        }
        for (int other = 0; other < cars.size(); other++) {
            if (other == car) {
                continue;
            }
            int[] otherShape = cars.get(other);
            for (int i = 0; i < shape[1]; i++) {
                int row = shape[2] == 1 ? shape[0] : offset + i;
                int column = shape[2] == 1 ? offset + i : shape[0];
                for (int j = 0; j < otherShape[1]; j++) {
                    int otherRow = otherShape[2] == 1 ? otherShape[0] : state[other] + j;
                    int otherColumn = otherShape[2] == 1 ? state[other] + j : otherShape[0];
                    if (row == otherRow && column == otherColumn) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** The red car is out when its tail reaches the right-hand edge. */
    private boolean escaped(int[] state) {
        return state[0] + cars.get(0)[1] == SIZE;
    }

    private static String key(int[] state) {
        StringBuilder out = new StringBuilder();
        for (int offset : state) {
            out.append((char) ('0' + offset));
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Searching
    // ------------------------------------------------------------------

    /** The shortest sequence of slides that frees the red car, or null. */
    public List<String> solve() {
        Map<String, String[]> cameFrom = new HashMap<>();
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(start.clone());
        cameFrom.put(key(start), null);
        while (!queue.isEmpty()) {
            int[] state = queue.poll();
            if (escaped(state)) {
                finish = state.clone();
                return trace(cameFrom, key(state));
            }
            for (int car = 0; car < cars.size(); car++) {
                int[] shape = cars.get(car);
                for (int direction : new int[] {-1, 1}) {
                    // A slide of any distance is one move, so every reachable
                    // offset in this direction is a single step.
                    for (int step = 1; step < SIZE; step++) {
                        int offset = state[car] + direction * step;
                        if (!fits(state, car, offset)) {
                            break;   // blocked, and everything beyond it too
                        }
                        int[] next = state.clone();
                        next[car] = offset;
                        String code = key(next);
                        if (cameFrom.containsKey(code)) {
                            continue;
                        }
                        String name = (car == 0 ? "the red car" : "car "
                                + (char) ('a' + car - 1));
                        String way = shape[2] == 1
                                ? (direction < 0 ? " left " : " right ")
                                : (direction < 0 ? " up " : " down ");
                        cameFrom.put(code, new String[] {
                            key(state), "slide " + name + way + step});
                        queue.add(next);
                    }
                }
            }
        }
        return null;
    }

    private List<String> trace(Map<String, String[]> cameFrom, String at) {
        List<String> steps = new ArrayList<>();
        while (cameFrom.get(at) != null) {
            String[] from = cameFrom.get(at);
            steps.add(0, from[1]);
            at = from[0];
        }
        return steps;
    }

    public String run() {
        List<String> steps = solve();
        if (steps == null) {
            return "The red car cannot get out of this one.";
        }
        plan = steps;
        // Show the position the plan actually reaches. Jumping the red car to
        // the exit without moving the cars it had to get past would draw a
        // board with two cars in the same place.
        offsets = finish.clone();
        return "Out in " + steps.size() + (steps.size() == 1 ? " move." : " moves.");
    }

    public List<String> plan() {
        return new ArrayList<>(plan);
    }

    // ------------------------------------------------------------------
    // Generating
    // ------------------------------------------------------------------

    /**
     * Lays cars out at random, then uses the solver as a quality filter -
     * keeping only jams that actually take some untangling.
     */
    public void generate(int blockers) {
        int wanted = Math.max(3, Math.min(11, blockers));
        for (int attempt = 0; attempt < 4000; attempt++) {
            cars = new ArrayList<>();
            List<Integer> places = new ArrayList<>();
            // The red car: horizontal, on the exit row, not already out.
            cars.add(new int[] {EXIT_ROW, 2, 1});
            places.add(random.nextInt(3));
            for (int i = 0; i < wanted; i++) {
                boolean horizontal = random.nextBoolean();
                int lane = random.nextInt(SIZE);
                int length = 2 + random.nextInt(2);
                int offset = random.nextInt(SIZE - length + 1);
                if (horizontal && lane == EXIT_ROW) {
                    continue;   // nothing else shares the red car's row
                }
                cars.add(new int[] {lane, length, horizontal ? 1 : 0});
                places.add(offset);
                int[] candidate = new int[places.size()];
                for (int j = 0; j < candidate.length; j++) {
                    candidate[j] = places.get(j);
                }
                if (!fits(candidate, cars.size() - 1, offset)) {
                    cars.remove(cars.size() - 1);
                    places.remove(places.size() - 1);
                }
            }
            start = new int[places.size()];
            for (int i = 0; i < start.length; i++) {
                start[i] = places.get(i);
            }
            offsets = start.clone();
            if (escaped(start)) {
                continue;
            }
            List<String> steps = solve();
            if (steps != null && steps.size() >= 5) {
                plan = new ArrayList<>();
                return;
            }
        }
        plan = new ArrayList<>();
    }

    public RushHour() {
        this(new Random());
    }

    public RushHour(Random random) {
        this.random = random;
        generate(8);
    }

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    /** Slides a car, returning false when it cannot move that far. */
    public boolean slide(int car, int distance) {
        if (car < 0 || car >= cars.size()) {
            throw new IllegalArgumentException("There is no car " + car + ".");
        }
        int step = Integer.signum(distance);
        int[] trial = offsets.clone();
        for (int i = 0; i < Math.abs(distance); i++) {
            if (!fits(trial, car, trial[car] + step)) {
                return false;
            }
            trial[car] += step;
        }
        offsets = trial;
        return true;
    }

    public boolean solved() {
        return escaped(offsets);
    }

    public char[][] cells() {
        return gridOf(offsets);
    }

    private String detail() {
        if (solved()) {
            return "The red car is out.";
        }
        String note = plan.isEmpty() ? "" : " Shortest way out: " + plan.size() + " moves.";
        return String.format("%d cars in the jam. The red car leaves by the right of row %d.%s",
                cars.size(), EXIT_ROW, note);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("* is the red car, letters are the blockers. It leaves by the right "
                + "of row " + EXIT_ROW + ".");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            for (String step : plan) {
                io.println("    " + step);
            }
            int choice = io.menu("Rush Hour",
                    List.of("Slide a car", "Solve", "Restart", "New jam"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0 -> {
                        int car = io.askInt("car (0 is red):", 0, cars.size() - 1, 0);
                        int by = io.askInt("slide by (negative for left or up):", -5, 5, 1);
                        if (!slide(car, by)) {
                            io.warn("That car cannot move that far.");
                        }
                    }
                    case 1 -> io.info(run());
                    case 2 -> restart();
                    default -> generate(io.askInt("blocking cars:", 3, 11, 8));
                }
            } catch (RuntimeException e) {
                io.error(e.getMessage());
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    generate(Json.integer(body, "cars", 8));
                    return board("Slide the blockers out of the way. * is the red car.");
                }
                case "solve" -> {
                    return board(run());
                }
                case "step", "restart" -> {
                    restart();
                    return board("Back to the start.");
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
        Map<String, Object> out = Json.ok("board", Json.grid(cells()), "detail", detail());
        if (note != null && !note.isEmpty()) {
            out.put("result", note);
        }
        return out;
    }
}
