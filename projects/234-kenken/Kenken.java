package com.randomjava.projects.kenken;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * KenKen - a Latin square cut into cages, where each cage's cells combine by
 * one arithmetic operation to reach a stated target.
 *
 * <h2>A cage is small, so ask it directly</h2>
 *
 * <p>Cages hold at most four cells and digits run to at most six, so a cage has
 * a few hundred possible fillings at the outside. That is small enough to stop
 * reasoning about arithmetic altogether: {@link #refineCage} enumerates every
 * assignment the cage could take, discards those that miss the target, and
 * keeps for each cell the digits that survive somewhere. A digit in no
 * surviving assignment is impossible; a cell left with none is a
 * contradiction.
 *
 * <p>That is the same intersection move as the nonogram and binary-puzzle line
 * solvers, and it has the same payoff: the published KenKen tricks - that a
 * two-cell cage totalling 3 must be 1 and 2, that a three-cell product of 5 on
 * a 5x5 needs a 1 and a 5, that a cage of {@code n} cells summing to the
 * maximum is forced - are all just cases where the intersection happens to be
 * decisive. None of them has to be written down.
 *
 * <h2>Subtraction and division are different, and the difference matters</h2>
 *
 * <p>Addition and multiplication do not care what order a cage's cells are
 * read in, so any cage size works. Subtraction and division do care, which is
 * why real KenKen only ever puts them on two-cell cages and reads them as
 * {@code |a - b|} and {@code max / min}. That is not a simplification, it is
 * what makes the clue well defined at all - without a fixed reading, a cage
 * marked {@code 2-} would mean different things depending on which cell you
 * started from. {@link #applyOperation} encodes exactly that, and
 * {@link #generate} never assigns those two operations to a larger cage.
 *
 * <h2>The Latin square is doing half the work</h2>
 *
 * <p>Cage arithmetic alone is weak. What makes KenKen tractable is that every
 * digit appears once per row and once per column, so a digit settled anywhere
 * is struck from two whole lines, which feeds the cages, which settle more
 * cells. {@link #propagate} alternates the two to a fixpoint and only then
 * does the solver guess, taking the cell with the fewest candidates left.
 */
public final class Kenken implements Project {

    public static final Meta META = new Meta(
            234, "kenken", "Kenken", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "A Latin square where outlined cages must reach a target by one operation.",
            "", true);

    private static final int MIN_SIZE = 3;
    private static final int MAX_SIZE = 6;
    private static final int MAX_CAGE = 4;
    private static final int NODE_BUDGET = 200_000;

    private final Random random;

    private int size;
    private int[][] answer;
    private int[][] digits;
    /** Which cage owns each cell. */
    private int[][] cageOf;
    /** Per cage: the cells it covers, as row*size+column. */
    private List<int[]> cages;
    private int[] targets;
    private char[] operations;

    private int nodesLeft;
    private int guesses;

    public Kenken() {
        this(new Random());
    }

    public Kenken(Random random) {
        this.random = random;
        generate(4);
    }

    @Override
    public Meta meta() {
        return META;
    }

    public int size() {
        return size;
    }

    public int digit(int row, int column) {
        return digits[row][column];
    }

    public int cageCount() {
        return cages.size();
    }

    public int cageOf(int row, int column) {
        return cageOf[row][column];
    }

    public int targetOf(int cage) {
        return targets[cage];
    }

    public char operationOf(int cage) {
        return operations[cage];
    }

    // ------------------------------------------------------------------
    // Cage arithmetic
    // ------------------------------------------------------------------

    /**
     * Combines a cage's values. Addition and multiplication fold in any order;
     * subtraction and division are only defined for two cells and are read as
     * {@code |a - b|} and {@code max / min}, which is what makes those clues
     * mean one thing rather than two.
     */
    public static int applyOperation(int[] values, char operation) {
        switch (operation) {
            case '+' -> {
                int sum = 0;
                for (int value : values) {
                    sum += value;
                }
                return sum;
            }
            case 'x' -> {
                int product = 1;
                for (int value : values) {
                    product *= value;
                }
                return product;
            }
            case '-' -> {
                return Math.abs(values[0] - values[1]);
            }
            case '/' -> {
                int high = Math.max(values[0], values[1]);
                int low = Math.min(values[0], values[1]);
                return low != 0 && high % low == 0 ? high / low : -1;
            }
            default -> {
                return values[0];
            }
        }
    }

    /**
     * Narrows a cage to the digits that appear in some assignment hitting its
     * target, or returns null when nothing does.
     */
    public static int[] refineCage(int[] masks, int target, char operation, int size) {
        int[] found = new int[masks.length];
        int[] values = new int[masks.length];
        if (!walk(masks, values, 0, target, operation, found)) {
            return null;
        }
        for (int mask : found) {
            if (mask == 0) {
                return null;
            }
        }
        return found;
    }

    private static boolean walk(int[] masks, int[] values, int at, int target,
                                char operation, int[] found) {
        if (at == values.length) {
            if (applyOperation(values, operation) != target) {
                return false;
            }
            for (int i = 0; i < values.length; i++) {
                found[i] |= 1 << (values[i] - 1);
            }
            return true;
        }
        boolean any = false;
        int options = masks[at];
        while (options != 0) {
            int bit = options & -options;
            options ^= bit;
            values[at] = Integer.numberOfTrailingZeros(bit) + 1;
            any |= walk(masks, values, at + 1, target, operation, found);
        }
        return any;
    }

    // ------------------------------------------------------------------
    // Propagation and search
    // ------------------------------------------------------------------

    private boolean propagate(int[] masks) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int index = 0; index < cages.size(); index++) {
                int[] cage = cages.get(index);
                int[] part = new int[cage.length];
                for (int i = 0; i < cage.length; i++) {
                    part[i] = masks[cage[i]];
                }
                int[] refined = refineCage(part, targets[index], operations[index], size);
                if (refined == null) {
                    return false;
                }
                for (int i = 0; i < cage.length; i++) {
                    if (masks[cage[i]] != refined[i]) {
                        masks[cage[i]] = refined[i];
                        changed = true;
                    }
                }
            }
            // A settled cell takes its digit out of its row and its column.
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    int cell = row * size + column;
                    if (Integer.bitCount(masks[cell]) != 1) {
                        continue;
                    }
                    for (int other = 0; other < size; other++) {
                        changed |= strike(masks, row * size + other, cell);
                        changed |= strike(masks, other * size + column, cell);
                    }
                }
            }
            for (int mask : masks) {
                if (mask == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean strike(int[] masks, int target, int source) {
        if (target == source || (masks[target] & masks[source]) == 0) {
            return false;
        }
        masks[target] &= ~masks[source];
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

    private int[] blankMasks() {
        int[] masks = new int[size * size];
        Arrays.fill(masks, (1 << size) - 1);
        return masks;
    }

    public int countSolutions(int cap) {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        return search(blankMasks(), cap, new int[1][]);
    }

    public String solve() {
        nodesLeft = NODE_BUDGET;
        guesses = 0;
        int[][] found = new int[1][];
        int count = search(blankMasks(), 2, found);
        if (count == 0) {
            return "These cages cannot be satisfied.";
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                digits[row][column] =
                        Integer.numberOfTrailingZeros(found[0][row * size + column]) + 1;
            }
        }
        return (guesses == 0
                ? "The cages and the Latin rule alone were enough - no guessing."
                : "Propagation stalled, so the solver branched " + guesses + " times.")
                + (count == 1 ? "" : " These cages allow more than one square.");
    }

    public String hint() {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (digits[row][column] != answer[row][column]) {
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
        for (int attempt = 0; attempt < 300; attempt++) {
            answer = randomLatinSquare();
            buildCages();
            digits = new int[size][size];
            if (countSolutions(2) == 1) {
                return;
            }
        }
        digits = new int[size][size];
    }

    /** Cages grown from unclaimed cells, then given a target they satisfy. */
    private void buildCages() {
        cageOf = new int[size][size];
        for (int[] line : cageOf) {
            Arrays.fill(line, -1);
        }
        cages = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        for (int cell = 0; cell < size * size; cell++) {
            order.add(cell);
        }
        Collections.shuffle(order, random);
        for (int start : order) {
            if (cageOf[start / size][start % size] >= 0) {
                continue;
            }
            int index = cages.size();
            List<Integer> members = new ArrayList<>();
            members.add(start);
            cageOf[start / size][start % size] = index;
            int wanted = 1 + random.nextInt(MAX_CAGE);
            while (members.size() < wanted) {
                List<Integer> frontier = new ArrayList<>();
                for (int cell : members) {
                    int row = cell / size;
                    int column = cell % size;
                    for (int[] step : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                        int nextRow = row + step[0];
                        int nextColumn = column + step[1];
                        if (nextRow >= 0 && nextRow < size && nextColumn >= 0
                                && nextColumn < size && cageOf[nextRow][nextColumn] < 0) {
                            frontier.add(nextRow * size + nextColumn);
                        }
                    }
                }
                if (frontier.isEmpty()) {
                    break;
                }
                int pick = frontier.get(random.nextInt(frontier.size()));
                members.add(pick);
                cageOf[pick / size][pick % size] = index;
            }
            int[] cage = new int[members.size()];
            for (int i = 0; i < cage.length; i++) {
                cage[i] = members.get(i);
            }
            cages.add(cage);
        }
        targets = new int[cages.size()];
        operations = new char[cages.size()];
        for (int index = 0; index < cages.size(); index++) {
            int[] cage = cages.get(index);
            int[] values = new int[cage.length];
            for (int i = 0; i < cage.length; i++) {
                values[i] = answer[cage[i] / size][cage[i] % size];
            }
            operations[index] = chooseOperation(values);
            targets[index] = applyOperation(values, operations[index]);
        }
    }

    /**
     * Subtraction and division are only offered to two-cell cages, because
     * those are the only sizes on which they have one unambiguous reading.
     */
    private char chooseOperation(int[] values) {
        if (values.length == 1) {
            return '=';
        }
        List<Character> options = new ArrayList<>(List.of('+', 'x'));
        if (values.length == 2) {
            options.add('-');
            int high = Math.max(values[0], values[1]);
            int low = Math.min(values[0], values[1]);
            if (high % low == 0) {
                options.add('/');
            }
        }
        return options.get(random.nextInt(options.size()));
    }

    private int[][] randomLatinSquare() {
        List<Integer> row = new ArrayList<>();
        for (int digit = 1; digit <= size; digit++) {
            row.add(digit);
        }
        Collections.shuffle(row, random);
        int[][] square = new int[size][size];
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                square[r][c] = row.get((c + r) % size);
            }
        }
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] swap = square[i];
            square[i] = square[j];
            square[j] = swap;
        }
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            for (int[] line : square) {
                int held = line[i];
                line[i] = line[j];
                line[j] = held;
            }
        }
        return square;
    }

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    public void place(int row, int column, int digit) {
        if (row < 0 || row >= size || column < 0 || column >= size) {
            throw new IllegalArgumentException("There is no cell " + row + "," + column + ".");
        }
        if (digit < 0 || digit > size) {
            throw new IllegalArgumentException("Use 1 to " + size + ", or 0 to clear.");
        }
        digits[row][column] = digit;
    }

    public boolean complete() {
        for (int row = 0; row < size; row++) {
            if (!Arrays.equals(digits[row], answer[row])) {
                return false;
            }
        }
        return true;
    }

    public String firstFault() {
        for (int line = 0; line < size; line++) {
            for (int a = 0; a < size; a++) {
                for (int b = a + 1; b < size; b++) {
                    if (digits[line][a] != 0 && digits[line][a] == digits[line][b]) {
                        return "Row " + line + " has two " + digits[line][a] + "s.";
                    }
                    if (digits[a][line] != 0 && digits[a][line] == digits[b][line]) {
                        return "Column " + line + " has two " + digits[a][line] + "s.";
                    }
                }
            }
        }
        for (int index = 0; index < cages.size(); index++) {
            int[] cage = cages.get(index);
            int[] values = new int[cage.length];
            boolean full = true;
            for (int i = 0; i < cage.length; i++) {
                values[i] = digits[cage[i] / size][cage[i] % size];
                full &= values[i] != 0;
            }
            if (full && applyOperation(values, operations[index]) != targets[index]) {
                return "Cage " + label(index) + " does not make "
                        + targets[index] + operationText(operations[index]) + ".";
            }
        }
        return "";
    }

    private static String operationText(char operation) {
        return operation == '=' ? "" : String.valueOf(operation);
    }

    private static String label(int index) {
        return String.valueOf((char) ('a' + index % 26));
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Each cell shows its cage's letter and whatever digit is in it, so the
     * cages are visible without drawing borders. The targets are listed
     * alongside rather than crammed into a corner of the grid.
     */
    public String[][] display() {
        String[][] grid = new String[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                grid[row][column] = label(cageOf[row][column])
                        + (digits[row][column] == 0 ? "." : String.valueOf(digits[row][column]));
            }
        }
        return grid;
    }

    /** The cage legend: which letter wants what. */
    public List<String> legend() {
        List<String> out = new ArrayList<>();
        for (int index = 0; index < cages.size(); index++) {
            out.add(label(index) + " " + targets[index] + operationText(operations[index]));
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

    private String detail() {
        if (complete()) {
            return "Solved, across " + cages.size() + " cages.";
        }
        String fault = firstFault();
        if (!fault.isEmpty()) {
            return fault;
        }
        return String.format("%dx%d, %d cages: %s", size, size, cages.size(),
                String.join("  ", legend()));
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("board size:", MIN_SIZE, MAX_SIZE, 4));
        io.muted("Each digit once per row and column. A cell shows its cage letter, "
                + "then its digit. Cage targets are listed below the grid.");
        while (true) {
            io.println();
            print(io);
            io.muted(detail());
            int choice = io.menu("KenKen",
                    List.of("Place a digit", "Hint", "Solve", "New puzzle"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0 -> {
                        int row = io.askInt("row:", 0, size - 1, 0);
                        int column = io.askInt("column:", 0, size - 1, 0);
                        place(row, column, io.askInt("digit (0 clears):", 0, size, 0));
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
        for (String[] row : display()) {
            StringBuilder line = new StringBuilder("  ");
            for (String cell : row) {
                line.append(cell).append(' ');
            }
            io.println(line.toString());
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    generate(Json.integer(body, "size", 4));
                    return board("A cell shows its cage letter and its digit. "
                            + "Pick a digit above, then click a cell.");
                }
                case "place", "cycle", "step" -> {
                    int row = Json.integer(body, "row", -1);
                    int column = Json.integer(body, "col", -1);
                    if (row < 0 || column < 0 || row >= size || column >= size) {
                        return board("");
                    }
                    int digit = Json.integer(body, "value", 1);
                    place(row, column, digits[row][column] == digit ? 0 : digit);
                    return board(complete() ? "That is the square." : "");
                }
                case "hint" -> {
                    return board(hint());
                }
                case "solve" -> {
                    return board(solve());
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
        Map<String, Object> out = Json.ok("board", asLists(display()), "detail", detail());
        if (note != null && !note.isEmpty()) {
            out.put("result", note);
        }
        return out;
    }
}
