package com.randomjava.projects.kakuro;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Kakuro - a crossword whose answers are digits. Each run of white cells adds
 * up to the number written against it, and no digit repeats inside a run.
 *
 * <h2>The state that makes a run cheap to solve</h2>
 *
 * <p>A run of {@code k} cells summing to {@code s} looks like it needs the
 * subsets of {@code 1..9} of size {@code k} that total {@code s}, and then an
 * assignment of one to each cell. Enumerating assignments directly is
 * {@code 9*8*7*...}, which is far too slow to sit inside a search that runs
 * thousands of times.
 *
 * <p>The observation that collapses it: <b>the digits already placed determine
 * how much is left to reach.</b> Carry a 9-bit set of which digits the run has
 * used, and the remaining sum is {@code s} minus the sum of that set - it does
 * not need to be tracked separately. So the state is just
 * {@code (cell index, used set)}: at most {@code 10 * 512} of them however the
 * run is shaped, and each is settled in one pass over the nine digits.
 *
 * <p>{@link #refineRun} fills that table backwards to find which states can
 * still be completed, then walks forwards through the states actually
 * reachable from the start and records, for each cell, which digits appear in
 * some assignment that works. A digit in none of them is impossible; a cell
 * left with nothing is a contradiction. This is the same intersection argument
 * as the nonogram line solver, over a different alphabet, and as there it
 * yields every deduction a single run can support and no more.
 *
 * <p>The familiar Kakuro tables - that 3 in two cells must be 1 and 2, that 45
 * in nine cells is every digit - are just the cases where the intersection
 * happens to be total. They never have to be written down.
 *
 * <h2>Generating: the layout and the filling are separate choices</h2>
 *
 * <p>{@link #generate} lays out blocks, repairs the layout until every run is
 * between two and four cells, fills the white cells with digits that do not
 * repeat within a run, reads off the sums, and keeps the result only if those
 * sums admit one grid. Runs of one are removed because they are not
 * deductions, and long runs are split: short runs mean each white cell sits at
 * the crossing of two tightly constrained runs, which is where a Kakuro's
 * answer actually gets pinned down.
 *
 * <p>The structure of the search matters more than any of that. <b>Only the
 * filling decides the clues</b> - the layout merely decides which cells are
 * white. An earlier version redrew the layout for every attempt, which spent
 * all its effort on the expensive half while sampling the half that matters
 * exactly once, and produced puzzles with several answers almost always above
 * a 6x6. Drawing one layout and then re-filling it many times took a 7x7 from
 * no unique puzzles in twelve to seven, and made 5x5 and 6x6 reliable.
 *
 * <p>Sizes stop at six because seven and eight are still not dependable, and
 * because every candidate filling costs a full uniqueness check, which makes
 * generation slow by this suite's standards. Published Kakuro avoids the whole
 * problem by drawing from symmetric block patterns with known-good run
 * structure rather than sampling layouts at random.
 */
public final class Kakuro implements Project {

    public static final Meta META = new Meta(
            232, "kakuro", "Kakuro", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "A crossword with sums: fill runs of digits that add to the given totals.",
            "", true);

    private static final int MIN_SIZE = 5;
    private static final int MAX_SIZE = 6;
    private static final int MAX_RUN = 4;
    private static final int NODE_BUDGET = 150_000;

    /** Sum of the digits in a 9-bit set, for every set. */
    private static final int[] SET_SUM = new int[1 << 9];

    static {
        for (int set = 0; set < SET_SUM.length; set++) {
            int total = 0;
            for (int digit = 1; digit <= 9; digit++) {
                if ((set >> (digit - 1) & 1) == 1) {
                    total += digit;
                }
            }
            SET_SUM[set] = total;
        }
    }

    private final Random random;

    private int size;
    /** True where the cell is a block rather than a white cell to fill. */
    private boolean[][] block;
    private int[][] downClue;
    private int[][] rightClue;
    private int[][] digits;
    private int[][] answer;
    /** Each run as {@code {sum, cell, cell, ...}} with cells as row*size+column. */
    private List<int[]> runs;
    /** Whether each run reads across or down, recorded when it is built. */
    private List<Boolean> runAcross;

    private int nodesLeft;
    private int guesses;

    public Kakuro() {
        this(new Random());
    }

    public Kakuro(Random random) {
        this.random = random;
        generate(6);
    }

    @Override
    public Meta meta() {
        return META;
    }

    public int size() {
        return size;
    }

    public boolean isBlock(int row, int column) {
        return block[row][column];
    }

    public int downClue(int row, int column) {
        return downClue[row][column];
    }

    public int rightClue(int row, int column) {
        return rightClue[row][column];
    }

    public int digit(int row, int column) {
        return digits[row][column];
    }

    public int runCount() {
        return runs.size();
    }

    // ------------------------------------------------------------------
    // The run solver
    // ------------------------------------------------------------------

    /**
     * Narrows every cell of a run to the digits that appear in some complete,
     * repeat-free assignment reaching the target. Returns null when the run
     * cannot be satisfied at all.
     *
     * <p>{@code masks[i]} has bit {@code d-1} set while digit {@code d} is
     * still possible in cell {@code i}.
     */
    public static int[] refineRun(int[] masks, int target) {
        int cells = masks.length;
        int sets = 1 << 9;

        // feasible[i][used] - can cells i.. be filled, avoiding the digits in
        // `used`, so the run reaches its target? The remaining sum is implied
        // by `used`, which is why it is not part of the state.
        boolean[][] feasible = new boolean[cells + 1][sets];
        for (int used = 0; used < sets; used++) {
            feasible[cells][used] = SET_SUM[used] == target;
        }
        for (int index = cells - 1; index >= 0; index--) {
            for (int used = 0; used < sets; used++) {
                // Reaching cell `index` means exactly `index` digits are gone,
                // so every other set is unreachable and need not be examined.
                // That turns 512 sets per cell into C(9, index), which sums to
                // 512 over the whole run rather than per position.
                if (Integer.bitCount(used) != index || SET_SUM[used] >= target) {
                    continue;
                }
                int options = masks[index] & ~used;
                boolean any = false;
                while (options != 0 && !any) {
                    int bit = options & -options;
                    options ^= bit;
                    any = feasible[index + 1][used | bit];
                }
                feasible[index][used] = any;
            }
        }
        if (!feasible[0][0]) {
            return null;
        }

        boolean[][] reachable = new boolean[cells + 1][sets];
        reachable[0][0] = true;
        int[] found = new int[cells];
        for (int index = 0; index < cells; index++) {
            for (int used = 0; used < sets; used++) {
                if (Integer.bitCount(used) != index || !reachable[index][used]) {
                    continue;
                }
                int options = masks[index] & ~used;
                while (options != 0) {
                    int bit = options & -options;
                    options ^= bit;
                    if (feasible[index + 1][used | bit]) {
                        found[index] |= bit;
                        reachable[index + 1][used | bit] = true;
                    }
                }
            }
        }
        for (int mask : found) {
            if (mask == 0) {
                return null;
            }
        }
        return found;
    }

    private boolean propagate(int[] masks) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int[] run : runs) {
                int cells = run.length - 1;
                int[] part = new int[cells];
                for (int i = 0; i < cells; i++) {
                    part[i] = masks[run[i + 1]];
                }
                int[] refined = refineRun(part, run[0]);
                if (refined == null) {
                    return false;
                }
                for (int i = 0; i < cells; i++) {
                    if (masks[run[i + 1]] != refined[i]) {
                        masks[run[i + 1]] = refined[i];
                        changed = true;
                    }
                }
            }
        }
        return true;
    }

    private int search(int[] masks, int cap, int[][] found) {
        if (cap <= 0 || nodesLeft-- <= 0) {
            return 0;
        }
        if (!propagate(masks)) {
            return 0;
        }
        int pick = -1;
        int fewest = Integer.MAX_VALUE;
        for (int cell = 0; cell < masks.length; cell++) {
            if (masks[cell] == 0) {
                continue;
            }
            int options = Integer.bitCount(masks[cell]);
            if (options > 1 && options < fewest) {
                fewest = options;
                pick = cell;
            }
        }
        if (pick < 0) {
            if (found[0] == null) {
                found[0] = masks.clone();
            }
            return 1;
        }
        guesses++;
        int total = 0;
        int options = masks[pick];
        while (options != 0) {
            int bit = options & -options;
            options ^= bit;
            int[] next = masks.clone();
            next[pick] = bit;
            total += search(next, cap - total, found);
            if (total >= cap) {
                break;
            }
        }
        return total;
    }

    private int[] startingMasks() {
        int[] masks = new int[size * size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                masks[row * size + column] = block[row][column] ? 0 : 0b111111111;
            }
        }
        return masks;
    }

    public int countSolutions(int cap) {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        return search(startingMasks(), cap, new int[1][]);
    }

    public String solve() {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        int[][] found = new int[1][];
        int count = search(startingMasks(), 2, found);
        if (count == 0) {
            return "These sums cannot be satisfied.";
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                digits[row][column] = block[row][column]
                        ? 0 : Integer.numberOfTrailingZeros(found[0][row * size + column]) + 1;
            }
        }
        return (guesses == 0
                ? "The run sums alone were enough - no guessing."
                : "Propagation stalled, so the solver branched " + guesses + " times.")
                + (count == 1 ? "" : " These sums allow more than one grid.");
    }

    public String hint() {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (!block[row][column] && digits[row][column] != answer[row][column]) {
                    digits[row][column] = answer[row][column];
                    return "Row " + row + ", column " + column + " is "
                            + answer[row][column] + ".";
                }
            }
        }
        return "Nothing left to give away.";
    }

    // ------------------------------------------------------------------
    // Generating
    // ------------------------------------------------------------------

    public void generate(int requested) {
        if (requested < MIN_SIZE || requested > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Use a size between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        size = requested;
        // A layout and a filling are separate choices, and only the filling
        // decides the clues. Rebuilding the layout for every try wastes the
        // expensive half: one layout supports many fillings, and it is the
        // filling that determines whether the sums pin the grid down. So the
        // outer loop draws a layout and the inner one keeps re-filling it.
        for (int attempt = 0; attempt < 40; attempt++) {
            layout();
            buildRuns();
            if (runs.isEmpty()) {
                continue;
            }
            for (int filling = 0; filling < 120; filling++) {
                if (!fillDigits()) {
                    break;
                }
                readClues();
                digits = new int[size][size];
                if (countSolutions(2) == 1) {
                    return;
                }
            }
        }
        digits = new int[size][size];
    }

    /**
     * Blocks down the top row and left column, a scattering inside, then a
     * repair pass: runs of one are not deductions so they become blocks, and
     * runs longer than {@link #MAX_RUN} are split. Repairs only ever add
     * blocks, so the pass terminates.
     */
    private void layout() {
        block = new boolean[size][size];
        for (int index = 0; index < size; index++) {
            block[0][index] = true;
            block[index][0] = true;
        }
        for (int row = 1; row < size; row++) {
            for (int column = 1; column < size; column++) {
                block[row][column] = random.nextInt(100) < 12;
            }
        }
        for (int pass = 0; pass < size * size; pass++) {
            if (!repairOnce()) {
                return;
            }
        }
    }

    private boolean repairOnce() {
        for (boolean acrossRun : new boolean[] {true, false}) {
            for (int line = 0; line < size; line++) {
                int start = -1;
                for (int index = 0; index <= size; index++) {
                    boolean isBlock = index == size
                            || (acrossRun ? block[line][index] : block[index][line]);
                    if (!isBlock) {
                        if (start < 0) {
                            start = index;
                        }
                        continue;
                    }
                    if (start >= 0) {
                        int length = index - start;
                        if (length == 1) {
                            setBlock(acrossRun, line, start);
                            return true;
                        }
                        if (length > MAX_RUN) {
                            setBlock(acrossRun, line, start + MAX_RUN);
                            return true;
                        }
                    }
                    start = -1;
                }
            }
        }
        return false;
    }

    private void setBlock(boolean acrossRun, int line, int index) {
        if (acrossRun) {
            block[line][index] = true;
        } else {
            block[index][line] = true;
        }
    }

    private void buildRuns() {
        runs = new ArrayList<>();
        runAcross = new ArrayList<>();
        for (boolean acrossRun : new boolean[] {true, false}) {
            for (int line = 0; line < size; line++) {
                List<Integer> current = new ArrayList<>();
                for (int index = 0; index <= size; index++) {
                    boolean isBlock = index == size
                            || (acrossRun ? block[line][index] : block[index][line]);
                    if (isBlock) {
                        if (current.size() >= 2) {
                            int[] run = new int[current.size() + 1];
                            for (int i = 0; i < current.size(); i++) {
                                run[i + 1] = current.get(i);
                            }
                            runs.add(run);
                            runAcross.add(acrossRun);
                        }
                        current.clear();
                    } else {
                        current.add(acrossRun ? line * size + index : index * size + line);
                    }
                }
            }
        }
    }

    /** Digits that do not repeat within any run, by backtracking. */
    private boolean fillDigits() {
        answer = new int[size][size];
        List<int[]> white = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (!block[row][column]) {
                    white.add(new int[] {row, column});
                }
            }
        }
        return placeDigit(white, 0);
    }

    private boolean placeDigit(List<int[]> white, int at) {
        if (at == white.size()) {
            return true;
        }
        int row = white.get(at)[0];
        int column = white.get(at)[1];
        List<Integer> order = new ArrayList<>();
        for (int digit = 1; digit <= 9; digit++) {
            order.add(digit);
        }
        Collections.shuffle(order, random);
        for (int digit : order) {
            if (repeatsInRun(row, column, digit)) {
                continue;
            }
            answer[row][column] = digit;
            if (placeDigit(white, at + 1)) {
                return true;
            }
            answer[row][column] = 0;
        }
        return false;
    }

    private boolean repeatsInRun(int row, int column, int digit) {
        for (int index = column - 1; index >= 0 && !block[row][index]; index--) {
            if (answer[row][index] == digit) {
                return true;
            }
        }
        for (int index = column + 1; index < size && !block[row][index]; index++) {
            if (answer[row][index] == digit) {
                return true;
            }
        }
        for (int index = row - 1; index >= 0 && !block[index][column]; index--) {
            if (answer[index][column] == digit) {
                return true;
            }
        }
        for (int index = row + 1; index < size && !block[index][column]; index++) {
            if (answer[index][column] == digit) {
                return true;
            }
        }
        return false;
    }

    /**
     * Writes each run's total onto the block just before it - to the left for
     * a run reading across, above for one reading down. Both exist, because
     * the top row and left column are always blocks.
     */
    private void readClues() {
        downClue = new int[size][size];
        rightClue = new int[size][size];
        for (int index = 0; index < runs.size(); index++) {
            int[] run = runs.get(index);
            int sum = 0;
            for (int i = 1; i < run.length; i++) {
                sum += answer[run[i] / size][run[i] % size];
            }
            run[0] = sum;
            int row = run[1] / size;
            int column = run[1] % size;
            if (runAcross.get(index)) {
                rightClue[row][column - 1] = sum;
            } else {
                downClue[row - 1][column] = sum;
            }
        }
    }

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    public void place(int row, int column, int digit) {
        if (row < 0 || row >= size || column < 0 || column >= size) {
            throw new IllegalArgumentException("There is no cell " + row + "," + column + ".");
        }
        if (block[row][column]) {
            throw new IllegalArgumentException("That cell is a block.");
        }
        if (digit < 0 || digit > 9) {
            throw new IllegalArgumentException("Use 1 to 9, or 0 to clear.");
        }
        digits[row][column] = digit;
    }

    public boolean complete() {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (!block[row][column] && digits[row][column] != answer[row][column]) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The first run that is already wrong, or an empty string. */
    public String firstFault() {
        for (int[] run : runs) {
            int sum = 0;
            int seen = 0;
            boolean full = true;
            for (int i = 1; i < run.length; i++) {
                int value = digits[run[i] / size][run[i] % size];
                if (value == 0) {
                    full = false;
                    continue;
                }
                if ((seen >> (value - 1) & 1) == 1) {
                    return "A run repeats the digit " + value + ".";
                }
                seen |= 1 << (value - 1);
                sum += value;
            }
            if (sum > run[0]) {
                return "A run already exceeds its total of " + run[0] + ".";
            }
            if (full && sum != run[0]) {
                return "A run adds to " + sum + ", not " + run[0] + ".";
            }
        }
        return "";
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * The board as data rather than characters. A clue cell carries two
     * numbers split by a diagonal, which no single character can express, so
     * the page draws this itself.
     */
    public List<List<Map<String, Object>>> board() {
        List<List<Map<String, Object>>> grid = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            List<Map<String, Object>> line = new ArrayList<>();
            for (int column = 0; column < size; column++) {
                Map<String, Object> cell = new LinkedHashMap<>();
                if (block[row][column]) {
                    cell.put("type", "block");
                    cell.put("down", downClue[row][column]);
                    cell.put("right", rightClue[row][column]);
                } else {
                    cell.put("type", "white");
                    cell.put("value", digits[row][column]);
                }
                line.add(cell);
            }
            grid.add(line);
        }
        return grid;
    }

    /** A plain-text rendering, for the terminal. */
    public String[][] display() {
        String[][] grid = new String[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (!block[row][column]) {
                    grid[row][column] = digits[row][column] == 0
                            ? "." : String.valueOf(digits[row][column]);
                } else {
                    int down = downClue[row][column];
                    int right = rightClue[row][column];
                    grid[row][column] = down == 0 && right == 0 ? "###"
                            : (down == 0 ? "" : String.valueOf(down))
                              + "\\" + (right == 0 ? "" : String.valueOf(right));
                }
            }
        }
        return grid;
    }

    private String detail() {
        if (complete()) {
            return "Solved, across " + runs.size() + " runs.";
        }
        String fault = firstFault();
        if (!fault.isEmpty()) {
            return fault;
        }
        int blank = 0;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (!block[row][column] && digits[row][column] == 0) {
                    blank++;
                }
            }
        }
        return String.format("%dx%d, %d runs, %d cells still blank.",
                size, size, runs.size(), blank);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("board size:", MIN_SIZE, MAX_SIZE, 6));
        io.muted("A block written a\\b wants a going down and b going across. "
                + "No digit repeats inside a run.");
        while (true) {
            io.println();
            print(io);
            io.muted(detail());
            int choice = io.menu("Kakuro",
                    List.of("Place a digit", "Hint", "Solve", "New puzzle"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0 -> {
                        int row = io.askInt("row:", 0, size - 1, 0);
                        int column = io.askInt("column:", 0, size - 1, 0);
                        place(row, column, io.askInt("digit (0 clears):", 0, 9, 0));
                    }
                    case 1 -> io.info(hint());
                    case 2 -> io.info(solve());
                    default -> generate(io.askInt("board size:", MIN_SIZE, MAX_SIZE, size));
                }
            } catch (RuntimeException e) {
                io.error(e.getMessage());
            }
        }
    }

    private void print(ConsoleUI io) {
        String[][] grid = display();
        int width = 3;
        for (String[] row : grid) {
            for (String cell : row) {
                width = Math.max(width, cell.length());
            }
        }
        for (String[] row : grid) {
            StringBuilder line = new StringBuilder("  ");
            for (String cell : row) {
                line.append(" ".repeat(width - cell.length())).append(cell).append(' ');
            }
            io.println(line.toString());
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    generate(Json.integer(body, "size", 6));
                    return payload("A block written a\\b wants a going down and b going "
                            + "across, with no digit repeated inside a run.");
                }
                case "place", "cycle", "step" -> {
                    int row = Json.integer(body, "row", -1);
                    int column = Json.integer(body, "col", -1);
                    if (row < 0 || column < 0 || row >= size || column >= size
                            || block[row][column]) {
                        return payload("");
                    }
                    int digit = Json.integer(body, "value", 1);
                    place(row, column, digits[row][column] == digit ? 0 : digit);
                    return payload(complete() ? "That is the grid." : "");
                }
                case "hint" -> {
                    return payload(hint());
                }
                case "solve" -> {
                    return payload(solve());
                }
                default -> {
                    return Json.error("Unknown action: " + action);
                }
            }
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }

    /**
     * The same strings the terminal prints, one per cell. A clue reads
     * {@code down\across}, which does not fit the shell's default 30px cell -
     * the page widens them rather than the server inventing a second format.
     */
    private Map<String, Object> payload(String note) {
        Map<String, Object> out = Json.ok("board", asLists(display()),
                "size", size, "detail", detail());
        if (note != null && !note.isEmpty()) {
            out.put("result", note);
        }
        return out;
    }

    private static List<List<String>> asLists(String[][] grid) {
        List<List<String>> out = new ArrayList<>(grid.length);
        for (String[] row : grid) {
            out.add(List.of(row));
        }
        return out;
    }
}
