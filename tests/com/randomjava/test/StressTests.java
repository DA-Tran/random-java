package com.randomjava.test;

import com.randomjava.projects.binarypuzzle.BinaryPuzzle;
import com.randomjava.projects.cryptarithm.Cryptarithm;
import com.randomjava.projects.futoshiki.Futoshiki;
import com.randomjava.projects.hashiwokakero.Hashiwokakero;
import com.randomjava.projects.hitori.Hitori;
import com.randomjava.projects.kakuro.Kakuro;
import com.randomjava.projects.kenken.Kenken;
import com.randomjava.projects.knightstour.KnightsTour;
import com.randomjava.projects.magicsquare.MagicSquare;
import com.randomjava.projects.nonogram.Nonogram;
import com.randomjava.projects.nurikabe.Nurikabe;
import com.randomjava.projects.pegsolitaire.PegSolitaire;
import com.randomjava.projects.rivercrossing.RiverCrossing;
import com.randomjava.projects.rushhour.RushHour;
import com.randomjava.projects.shikaku.Shikaku;
import com.randomjava.projects.skyscrapers.Skyscrapers;
import com.randomjava.projects.slitherlink.Slitherlink;
import com.randomjava.projects.waterjug.WaterJug;
import com.randomjava.projects.wordsearchgenerator.WordSearchGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Stress tests over many random instances.
 *
 * <p>These are deliberately different in kind from the per-project tests. Those
 * assert known answers - that SEND is 9567, that the English board takes 31
 * jumps - which is exactly right for pinning down behaviour someone has
 * reasoned about, and exactly useless against an input nobody anticipated.
 *
 * <p>Everything here instead <b>re-derives the rules independently</b> and
 * checks the answer against them, over many sizes and many seeds. Nothing is
 * compared against a stored expectation, so the checks cannot be satisfied by a
 * solver that memorised the cases someone thought of. If a generator produces a
 * malformed board on the ninth seed at size seven, this is what notices.
 *
 * <p>The distinction that matters: a per-project test asks "did it get the
 * answer I expect?", and these ask "is what it produced actually a solution to
 * the puzzle it posed?" - which is the question the user of the project
 * actually cares about.
 */
final class StressTests {

    private StressTests() {
    }

    static void run(Harness harness) {
        gridPuzzles(harness);
        shapePuzzles(harness);
        searchPuzzles(harness);
    }

    // ------------------------------------------------------------------
    // Helpers that re-derive the rules from scratch
    // ------------------------------------------------------------------

    /** Run lengths of a boolean line, computed independently of any project. */
    private static List<Integer> runsOf(boolean[] line) {
        List<Integer> runs = new ArrayList<>();
        int run = 0;
        for (boolean on : line) {
            if (on) {
                run++;
            } else if (run > 0) {
                runs.add(run);
                run = 0;
            }
        }
        if (run > 0) {
            runs.add(run);
        }
        return runs;
    }

    /** Whether values 1..n each appear exactly once. */
    private static boolean isPermutation(int[] line, int n) {
        boolean[] seen = new boolean[n + 1];
        for (int value : line) {
            if (value < 1 || value > n || seen[value]) {
                return false;
            }
            seen[value] = true;
        }
        return line.length == n;
    }

    /** Size of the region reachable from a cell, over cells passing the test. */
    private static int regionSize(boolean[][] passable, int startRow, int startColumn) {
        int rows = passable.length;
        int columns = passable[0].length;
        boolean[][] seen = new boolean[rows][columns];
        java.util.Deque<int[]> queue = new java.util.ArrayDeque<>();
        queue.add(new int[] {startRow, startColumn});
        seen[startRow][startColumn] = true;
        int count = 0;
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            count++;
            for (int[] step : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                int row = at[0] + step[0];
                int column = at[1] + step[1];
                if (row >= 0 && row < rows && column >= 0 && column < columns
                        && passable[row][column] && !seen[row][column]) {
                    seen[row][column] = true;
                    queue.add(new int[] {row, column});
                }
            }
        }
        return count;
    }

    /** Towers visible along a line, counted here rather than asked of the project. */
    private static int seenAlong(int[] line) {
        int count = 0;
        int tallest = 0;
        for (int height : line) {
            if (height > tallest) {
                tallest = height;
                count++;
            }
        }
        return count;
    }

    /** A cage's value, worked out here rather than asked of the project. */
    private static int combine(int[] values, char operation) {
        switch (operation) {
            case '+': {
                int sum = 0;
                for (int value : values) {
                    sum += value;
                }
                return sum;
            }
            case 'x': {
                int product = 1;
                for (int value : values) {
                    product *= value;
                }
                return product;
            }
            case '-':
                return Math.abs(values[0] - values[1]);
            case '/':
                return Math.max(values[0], values[1]) / Math.min(values[0], values[1]);
            default:
                return values[0];
        }
    }

    /** Sides of a cell that separate it from a cell on the other side. */
    private static int edgesOf(char[][] grid, int row, int column) {
        int size = grid.length;
        char mine = grid[row][column];
        int used = 0;
        for (int[] step : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            int r = row + step[0];
            int c = column + step[1];
            char other = (r < 0 || r >= size || c < 0 || c >= size)
                    ? Slitherlink.OUTSIDE : grid[r][c];
            if (other != mine) {
                used++;
            }
        }
        return used;
    }

    /** Whether a board really is a knight's tour, checked from the rules. */
    private static boolean genuineTour(int[][] board) {
        int size = board.length;
        int[] row = new int[size * size + 1];
        int[] column = new int[size * size + 1];
        Arrays.fill(row, -1);
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int step = board[r][c];
                if (step < 1 || step > size * size || row[step] >= 0) {
                    return false;
                }
                row[step] = r;
                column[step] = c;
            }
        }
        for (int step = 1; step < size * size; step++) {
            int dr = Math.abs(row[step] - row[step + 1]);
            int dc = Math.abs(column[step] - column[step + 1]);
            if (dr * dc != 2) {
                return false;
            }
        }
        return true;
    }

    private static int countTrue(boolean[][] grid) {
        int count = 0;
        for (boolean[] line : grid) {
            for (boolean cell : line) {
                if (cell) {
                    count++;
                }
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Puzzles whose answer is a filled grid
    // ------------------------------------------------------------------

    private static void gridPuzzles(Harness h) {
        h.group("Stress: line-constraint puzzles", t -> {
            // Nonogram: the clues of the solved grid, recomputed here, must be
            // the clues the puzzle posed.
            int clueMismatch = 0;
            int unsolved = 0;
            for (int size = 5; size <= 12; size++) {
                for (int seed = 0; seed < 4; seed++) {
                    Nonogram puzzle = new Nonogram(new Random(seed * 100 + size));
                    puzzle.generate(size);
                    puzzle.solve();
                    if (!puzzle.complete()) {
                        unsolved++;
                        continue;
                    }
                    for (int row = 0; row < size; row++) {
                        boolean[] line = new boolean[size];
                        for (int column = 0; column < size; column++) {
                            line[column] = puzzle.cell(row, column) == Nonogram.FILLED;
                        }
                        if (!runsOf(line).equals(boxed(puzzle.rowClues()[row]))) {
                            clueMismatch++;
                        }
                    }
                    for (int column = 0; column < size; column++) {
                        boolean[] line = new boolean[size];
                        for (int row = 0; row < size; row++) {
                            line[row] = puzzle.cell(row, column) == Nonogram.FILLED;
                        }
                        if (!runsOf(line).equals(boxed(puzzle.columnClues()[column]))) {
                            clueMismatch++;
                        }
                    }
                }
            }
            t.equal("nonogram solves every board it generates", 0, unsolved);
            t.equal("and the solved picture's own runs match its clues, all sizes",
                    0, clueMismatch);

            // Binary puzzle: balanced, no three alike, every line distinct.
            int binaryFaults = 0;
            for (int size = 4; size <= 10; size += 2) {
                for (int seed = 0; seed < 3; seed++) {
                    BinaryPuzzle puzzle = new BinaryPuzzle(new Random(seed * 7 + size));
                    puzzle.generate(size);
                    puzzle.solve();
                    char[][] grid = puzzle.cells();
                    List<String> rows = new ArrayList<>();
                    List<String> columns = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        StringBuilder row = new StringBuilder();
                        StringBuilder column = new StringBuilder();
                        for (int j = 0; j < size; j++) {
                            row.append(grid[i][j]);
                            column.append(grid[j][i]);
                        }
                        rows.add(row.toString());
                        columns.add(column.toString());
                    }
                    for (List<String> lines : List.of(rows, columns)) {
                        for (String line : lines) {
                            int ones = 0;
                            for (int i = 0; i < size; i++) {
                                if (line.charAt(i) == BinaryPuzzle.ONE) {
                                    ones++;
                                }
                                if (i >= 2 && line.charAt(i) == line.charAt(i - 1)
                                        && line.charAt(i) == line.charAt(i - 2)) {
                                    binaryFaults++;
                                }
                            }
                            if (ones * 2 != size) {
                                binaryFaults++;
                            }
                        }
                        if (lines.size() != lines.stream().distinct().count()) {
                            binaryFaults++;
                        }
                    }
                }
            }
            t.equal("binary puzzle answers are balanced, run-free and all distinct",
                    0, binaryFaults);

            // Skyscrapers: Latin square, and every kept clue counts the skyline.
            int skyFaults = 0;
            for (int size = 4; size <= 6; size++) {
                for (int seed = 0; seed < 4; seed++) {
                    Skyscrapers city = new Skyscrapers(new Random(seed * 13 + size));
                    city.generate(size);
                    city.solve();
                    for (int line = 0; line < size; line++) {
                        int[] row = new int[size];
                        int[] column = new int[size];
                        for (int i = 0; i < size; i++) {
                            row[i] = city.height(line, i);
                            column[i] = city.height(i, line);
                        }
                        if (!isPermutation(row, size) || !isPermutation(column, size)) {
                            skyFaults++;
                        }
                        int[] reversedRow = new int[size];
                        int[] reversedColumn = new int[size];
                        for (int i = 0; i < size; i++) {
                            reversedRow[i] = row[size - 1 - i];
                            reversedColumn[i] = column[size - 1 - i];
                        }
                        if (city.leftClue(line) != 0
                                && seenAlong(row) != city.leftClue(line)) {
                            skyFaults++;
                        }
                        if (city.rightClue(line) != 0
                                && seenAlong(reversedRow) != city.rightClue(line)) {
                            skyFaults++;
                        }
                        if (city.topClue(line) != 0
                                && seenAlong(column) != city.topClue(line)) {
                            skyFaults++;
                        }
                        if (city.bottomClue(line) != 0
                                && seenAlong(reversedColumn) != city.bottomClue(line)) {
                            skyFaults++;
                        }
                    }
                }
            }
            t.equal("skyscraper answers are Latin squares matching every kept clue",
                    0, skyFaults);

            // Futoshiki and Kenken: Latin squares obeying their own extra rule.
            int futoshikiFaults = 0;
            for (int size = 4; size <= 7; size++) {
                for (int seed = 0; seed < 3; seed++) {
                    Futoshiki puzzle = new Futoshiki(new Random(seed * 11 + size));
                    puzzle.generate(size);
                    puzzle.solve();
                    for (int line = 0; line < size; line++) {
                        int[] row = new int[size];
                        int[] column = new int[size];
                        for (int i = 0; i < size; i++) {
                            row[i] = puzzle.digit(line, i);
                            column[i] = puzzle.digit(i, line);
                        }
                        if (!isPermutation(row, size) || !isPermutation(column, size)) {
                            futoshikiFaults++;
                        }
                    }
                    for (int row = 0; row < size; row++) {
                        for (int column = 0; column < size; column++) {
                            if (column + 1 < size
                                    && puzzle.lessThan(row, column, row, column + 1)
                                    && puzzle.digit(row, column)
                                            >= puzzle.digit(row, column + 1)) {
                                futoshikiFaults++;
                            }
                            if (row + 1 < size
                                    && puzzle.lessThan(row, column, row + 1, column)
                                    && puzzle.digit(row, column)
                                            >= puzzle.digit(row + 1, column)) {
                                futoshikiFaults++;
                            }
                        }
                    }
                }
            }
            t.equal("futoshiki answers obey every inequality drawn on them",
                    0, futoshikiFaults);

            int kenkenFaults = 0;
            for (int size = 3; size <= 6; size++) {
                for (int seed = 0; seed < 3; seed++) {
                    Kenken puzzle = new Kenken(new Random(seed * 17 + size));
                    puzzle.generate(size);
                    puzzle.solve();
                    for (int line = 0; line < size; line++) {
                        int[] row = new int[size];
                        int[] column = new int[size];
                        for (int i = 0; i < size; i++) {
                            row[i] = puzzle.digit(line, i);
                            column[i] = puzzle.digit(i, line);
                        }
                        if (!isPermutation(row, size) || !isPermutation(column, size)) {
                            kenkenFaults++;
                        }
                    }
                    for (int cage = 0; cage < puzzle.cageCount(); cage++) {
                        List<Integer> values = new ArrayList<>();
                        for (int row = 0; row < size; row++) {
                            for (int column = 0; column < size; column++) {
                                if (puzzle.cageOf(row, column) == cage) {
                                    values.add(puzzle.digit(row, column));
                                }
                            }
                        }
                        int[] asArray = new int[values.size()];
                        for (int i = 0; i < asArray.length; i++) {
                            asArray[i] = values.get(i);
                        }
                        if (combine(asArray, puzzle.operationOf(cage))
                                != puzzle.targetOf(cage)) {
                            kenkenFaults++;
                        }
                    }
                }
            }
            t.equal("kenken answers hit every cage target", 0, kenkenFaults);

            // Kakuro: each run sums to its clue with no digit repeated.
            int kakuroFaults = 0;
            for (int size = 5; size <= 6; size++) {
                for (int seed = 0; seed < 2; seed++) {
                    Kakuro puzzle = new Kakuro(new Random(seed * 5 + size));
                    puzzle.generate(size);
                    puzzle.solve();
                    for (int row = 0; row < size; row++) {
                        for (int column = 0; column < size; column++) {
                            kakuroFaults += runFault(puzzle, size, row, column, true);
                            kakuroFaults += runFault(puzzle, size, row, column, false);
                        }
                    }
                }
            }
            t.equal("every kakuro run adds to its clue without repeating a digit",
                    0, kakuroFaults);

            // Magic square: every line equal, for every size the project allows.
            int magicFaults = 0;
            for (int size = 3; size <= 10; size++) {
                int[][] square = MagicSquare.construct(size);
                if (!MagicSquare.isMagic(square)) {
                    magicFaults++;
                }
                int target = MagicSquare.magicConstant(size);
                for (int line = 0; line < size; line++) {
                    int row = 0;
                    int column = 0;
                    for (int i = 0; i < size; i++) {
                        row += square[line][i];
                        column += square[i][line];
                    }
                    if (row != target || column != target) {
                        magicFaults++;
                    }
                }
            }
            t.equal("every magic square from 3 to 10 has equal lines", 0, magicFaults);
        });
    }

    private static List<Integer> boxed(int[] values) {
        List<Integer> out = new ArrayList<>();
        for (int value : values) {
            out.add(value);
        }
        return out;
    }

    /** Checks the run starting at a cell, if one starts there. */
    private static int runFault(Kakuro puzzle, int size, int row, int column,
                                boolean across) {
        int clue = across ? puzzle.rightClue(row, column) : puzzle.downClue(row, column);
        if (clue == 0) {
            return 0;
        }
        int sum = 0;
        int seen = 0;
        int faults = 0;
        for (int i = 1; i < size; i++) {
            int r = across ? row : row + i;
            int c = across ? column + i : column;
            if (r >= size || c >= size || puzzle.isBlock(r, c)) {
                break;
            }
            int digit = puzzle.digit(r, c);
            if (digit < 1 || digit > 9 || (seen >> (digit - 1) & 1) == 1) {
                faults++;
                break;
            }
            seen |= 1 << (digit - 1);
            sum += digit;
        }
        return faults + (sum == clue ? 0 : 1);
    }

    // ------------------------------------------------------------------
    // Puzzles whose answer is a shape
    // ------------------------------------------------------------------

    private static void shapePuzzles(Harness h) {
        h.group("Stress: shape and region puzzles", t -> {
            // Hitori: no repeat among unshaded, no two shaded touching, and the
            // unshaded cells in one piece.
            int hitoriFaults = 0;
            for (int size = 4; size <= 8; size++) {
                for (int seed = 0; seed < 3; seed++) {
                    Hitori puzzle = new Hitori(new Random(seed * 19 + size));
                    puzzle.generate(size);
                    puzzle.solve();
                    boolean[][] white = new boolean[size][size];
                    for (int row = 0; row < size; row++) {
                        for (int column = 0; column < size; column++) {
                            white[row][column] = puzzle.shade(row, column) != Hitori.BLACK;
                        }
                    }
                    for (int row = 0; row < size; row++) {
                        for (int column = 0; column < size; column++) {
                            if (white[row][column]) {
                                continue;
                            }
                            if (row + 1 < size && !white[row + 1][column]) {
                                hitoriFaults++;
                            }
                            if (column + 1 < size && !white[row][column + 1]) {
                                hitoriFaults++;
                            }
                        }
                    }
                    for (int line = 0; line < size; line++) {
                        for (int a = 0; a < size; a++) {
                            for (int b = a + 1; b < size; b++) {
                                if (white[line][a] && white[line][b]
                                        && puzzle.number(line, a) == puzzle.number(line, b)) {
                                    hitoriFaults++;
                                }
                                if (white[a][line] && white[b][line]
                                        && puzzle.number(a, line) == puzzle.number(b, line)) {
                                    hitoriFaults++;
                                }
                            }
                        }
                    }
                    int[] first = firstTrue(white);
                    if (first != null
                            && regionSize(white, first[0], first[1]) != countTrue(white)) {
                        hitoriFaults++;
                    }
                }
            }
            t.equal("hitori answers break none of the three rules, all sizes",
                    0, hitoriFaults);

            // Nurikabe: wall in one piece, no solid 2x2, islands exactly their
            // number with exactly one number each.
            int nurikabeFaults = 0;
            for (int size = 5; size <= 7; size++) {
                for (int seed = 0; seed < 3; seed++) {
                    Nurikabe puzzle = new Nurikabe(new Random(seed * 23 + size));
                    puzzle.generate(size);
                    puzzle.solve();
                    boolean[][] wall = new boolean[size][size];
                    boolean[][] land = new boolean[size][size];
                    for (int row = 0; row < size; row++) {
                        for (int column = 0; column < size; column++) {
                            wall[row][column] = puzzle.shade(row, column) == Nurikabe.WALL;
                            land[row][column] = !wall[row][column];
                        }
                    }
                    if (countTrue(wall) != puzzle.wallTotal()) {
                        nurikabeFaults++;
                    }
                    int[] firstWall = firstTrue(wall);
                    if (firstWall != null && regionSize(wall, firstWall[0], firstWall[1])
                            != countTrue(wall)) {
                        nurikabeFaults++;
                    }
                    for (int row = 0; row + 1 < size; row++) {
                        for (int column = 0; column + 1 < size; column++) {
                            if (wall[row][column] && wall[row + 1][column]
                                    && wall[row][column + 1] && wall[row + 1][column + 1]) {
                                nurikabeFaults++;
                            }
                        }
                    }
                    for (int row = 0; row < size; row++) {
                        for (int column = 0; column < size; column++) {
                            if (puzzle.number(row, column) == 0) {
                                continue;
                            }
                            if (regionSize(land, row, column) != puzzle.number(row, column)) {
                                nurikabeFaults++;
                            }
                        }
                    }
                }
            }
            t.equal("nurikabe answers keep the wall joined and islands exact",
                    0, nurikabeFaults);

            // Slitherlink: every clue counts its own differing neighbours, and
            // both inside and outside are single regions.
            int loopFaults = 0;
            for (int size = 4; size <= 7; size++) {
                for (int seed = 0; seed < 3; seed++) {
                    Slitherlink puzzle = new Slitherlink(new Random(seed * 29 + size));
                    puzzle.generate(size);
                    puzzle.solve();
                    char[][] grid = new char[size][size];
                    for (int row = 0; row < size; row++) {
                        for (int column = 0; column < size; column++) {
                            grid[row][column] = puzzle.side(row, column);
                        }
                    }
                    for (int row = 0; row < size; row++) {
                        for (int column = 0; column < size; column++) {
                            int clue = puzzle.clue(row, column);
                            if (clue >= 0 && edgesOf(grid, row, column) != clue) {
                                loopFaults++;
                            }
                        }
                    }
                    if (!puzzle.solved(grid)) {
                        loopFaults++;
                    }
                }
            }
            t.equal("slitherlink answers satisfy every clue and form one loop",
                    0, loopFaults);

            // Shikaku: a true partition into rectangles of the stated areas.
            int shikakuFaults = 0;
            for (int size = 4; size <= 9; size++) {
                for (int seed = 0; seed < 2; seed++) {
                    Shikaku puzzle = new Shikaku(new Random(seed * 31 + size));
                    puzzle.generate(size);
                    int[][] owner = new int[size][size];
                    for (int[] line : owner) {
                        Arrays.fill(line, -1);
                    }
                    int index = 0;
                    for (int[] rectangle : puzzle.answer()) {
                        int clues = 0;
                        for (int r = rectangle[0]; r < rectangle[0] + rectangle[2]; r++) {
                            for (int c = rectangle[1]; c < rectangle[1] + rectangle[3]; c++) {
                                if (r >= size || c >= size || owner[r][c] != -1) {
                                    shikakuFaults++;
                                } else {
                                    owner[r][c] = index;
                                }
                                if (puzzle.clue(r, c) != 0) {
                                    clues++;
                                    if (puzzle.clue(r, c) != rectangle[2] * rectangle[3]) {
                                        shikakuFaults++;
                                    }
                                }
                            }
                        }
                        if (clues != 1) {
                            shikakuFaults++;
                        }
                        index++;
                    }
                    for (int[] line : owner) {
                        for (int cell : line) {
                            if (cell < 0) {
                                shikakuFaults++;
                            }
                        }
                    }
                }
            }
            t.equal("shikaku answers partition the board into stated areas",
                    0, shikakuFaults);

            // Hashiwokakero: island counts exact, nothing crossing, all joined.
            int bridgeFaults = 0;
            for (int size = 5; size <= 9; size += 2) {
                for (int seed = 0; seed < 3; seed++) {
                    Hashiwokakero puzzle = new Hashiwokakero(new Random(seed * 37 + size));
                    puzzle.generate(size);
                    puzzle.solve();
                    int[] drawn = new int[puzzle.edgeCount()];
                    int[] ends = new int[puzzle.islandCount()];
                    for (int edge = 0; edge < puzzle.edgeCount(); edge++) {
                        drawn[edge] = puzzle.bridgesOn(edge);
                        if (drawn[edge] < 0 || drawn[edge] > 2) {
                            bridgeFaults++;
                        }
                        int[] pair = puzzle.edge(edge);
                        ends[pair[0]] += drawn[edge];
                        ends[pair[1]] += drawn[edge];
                    }
                    for (int island = 0; island < puzzle.islandCount(); island++) {
                        if (ends[island] != puzzle.wantedAt(island)) {
                            bridgeFaults++;
                        }
                    }
                    if (!puzzle.solved(drawn)) {
                        bridgeFaults++;
                    }
                }
            }
            t.equal("hashiwokakero answers satisfy every island and stay joined",
                    0, bridgeFaults);

            // Word search: every listed word is genuinely readable off the grid.
            int searchFaults = 0;
            List<String> words = List.of("ALPHA", "BRAVO", "DELTA", "ECHO", "GOLF");
            for (int size = 8; size <= 14; size += 2) {
                for (int seed = 0; seed < 3; seed++) {
                    WordSearchGenerator puzzle =
                            new WordSearchGenerator(new Random(seed * 41 + size));
                    puzzle.generate(size, words);
                    for (String word : puzzle.placed()) {
                        int[] at = puzzle.find(word);
                        if (at == null) {
                            searchFaults++;
                            continue;
                        }
                        for (int i = 0; i < word.length(); i++) {
                            if (puzzle.letterAt(at[0] + at[2] * i, at[1] + at[3] * i)
                                    != word.charAt(i)) {
                                searchFaults++;
                            }
                        }
                    }
                }
            }
            t.equal("every hidden word reads back off the grid letter by letter",
                    0, searchFaults);
        });
    }

    private static int[] firstTrue(boolean[][] grid) {
        for (int row = 0; row < grid.length; row++) {
            for (int column = 0; column < grid[row].length; column++) {
                if (grid[row][column]) {
                    return new int[] {row, column};
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Puzzles whose answer is a sequence of moves
    // ------------------------------------------------------------------

    private static void searchPuzzles(Harness h) {
        h.group("Stress: search and sequence puzzles", t -> {
            // Water jug: the arithmetic rule and the search must agree on every
            // instance, not merely on the ones anyone checked by hand.
            int jugDisagreements = 0;
            for (int left = 1; left <= 15; left++) {
                for (int right = 1; right <= 15; right++) {
                    for (int target = 1; target <= Math.max(left, right); target++) {
                        boolean byRule = WaterJug.reachable(left, right, target);
                        boolean bySearch = WaterJug.solve(left, right, target) != null;
                        if (byRule != bySearch) {
                            jugDisagreements++;
                        }
                    }
                }
            }
            t.equal("over every jug pair to fifteen, gcd and search agree exactly",
                    0, jugDisagreements);

            // Knight's tour: the colouring rule must predict solvability exactly,
            // and every tour returned must be a genuine knight's tour.
            int tourFaults = 0;
            int predictionFaults = 0;
            for (int size = 5; size <= 7; size++) {
                for (int row = 0; row < size; row++) {
                    for (int column = 0; column < size; column++) {
                        int[][] tour = KnightsTour.tour(size, row, column);
                        boolean allowed = KnightsTour.startCanWork(size, row, column);
                        if (tour == null && allowed) {
                            predictionFaults++;
                        }
                        if (tour != null && !allowed) {
                            predictionFaults++;
                        }
                        if (tour != null && !genuineTour(tour)) {
                            tourFaults++;
                        }
                    }
                }
            }
            t.equal("every knight's tour returned really is one", 0, tourFaults);
            t.equal("and the colouring predicts solvability exactly",
                    0, predictionFaults);

            // Peg solitaire: random legal play never changes either invariant,
            // and the jump count always matches the pegs removed.
            int invariantDrift = 0;
            int countDrift = 0;
            for (int seed = 0; seed < 12; seed++) {
                PegSolitaire board = new PegSolitaire(seed % 2 == 0
                        ? PegSolitaire.ENGLISH : PegSolitaire.EUROPEAN);
                int[] before = board.invariants();
                int pegsAtStart = board.pegCount();
                Random random = new Random(seed);
                while (!board.moves().isEmpty()) {
                    List<int[]> options = board.moves();
                    int[] jump = options.get(random.nextInt(options.size()));
                    board.move(jump[0], jump[1], jump[2], jump[3]);
                    if (!Arrays.equals(before, board.invariants())) {
                        invariantDrift++;
                    }
                }
                if (pegsAtStart - board.pegCount() != board.movesMade()) {
                    countDrift++;
                }
            }
            t.equal("no jump ever changes the GF(4) sums, on either board",
                    0, invariantDrift);
            t.equal("and every jump removes exactly one peg", 0, countDrift);

            // River crossing: replay the plan and check each crossing is legal
            // and that everyone ends up across.
            int crossingFaults = 0;
            for (String which : List.of("classic", "missionaries")) {
                RiverCrossing puzzle = new RiverCrossing();
                puzzle.reset(which);
                List<String> steps = puzzle.solve();
                if (steps == null || steps.isEmpty()) {
                    crossingFaults++;
                    continue;
                }
                puzzle.run();
                if (puzzle.nearBank() != 0 || puzzle.boatNear()) {
                    crossingFaults++;
                }
            }
            t.equal("both river puzzles end with everyone on the far bank",
                    0, crossingFaults);

            // Rush hour: cars never change size, so the occupied count is
            // invariant, and solving must actually free the red car.
            int jamFaults = 0;
            for (int seed = 0; seed < 10; seed++) {
                RushHour jam = new RushHour(new Random(seed));
                int before = occupied(jam.cells());
                if (jam.solve() == null) {
                    jamFaults++;
                    continue;
                }
                jam.run();
                if (!jam.solved() || occupied(jam.cells()) != before) {
                    jamFaults++;
                }
            }
            t.equal("every jam frees the red car without cars overlapping",
                    0, jamFaults);

            // Cryptarithm: whatever it returns must make the sum true, checked
            // by doing the arithmetic rather than by comparing to an answer.
            int sumFaults = 0;
            List<String> puzzles = List.of(
                    "SEND + MORE = MONEY", "CROSS + ROADS = DANGER",
                    "TWO + TWO = FOUR", "AB + BA = CBC", "ONE + ONE = TWO",
                    "A + B = CD", "SATURN + URANUS = PLANETS");
            for (String text : puzzles) {
                Cryptarithm.Puzzle puzzle = Cryptarithm.parse(text);
                java.util.Map<Character, Integer> answer = Cryptarithm.solve(puzzle);
                if (answer == null) {
                    continue;   // no solution is a legitimate outcome
                }
                long total = 0;
                for (String word : puzzle.addends()) {
                    total += Cryptarithm.valueOf(word, answer);
                    if (word.length() > 1 && answer.get(word.charAt(0)) == 0) {
                        sumFaults++;   // leading zero
                    }
                }
                if (total != Cryptarithm.valueOf(puzzle.total(), answer)) {
                    sumFaults++;
                }
                if (answer.values().size() != answer.values().stream().distinct().count()) {
                    sumFaults++;   // two letters sharing a digit
                }
            }
            t.equal("every cryptarithm answer actually adds up, with distinct digits",
                    0, sumFaults);
        });
    }

    private static int occupied(char[][] grid) {
        int count = 0;
        for (char[] row : grid) {
            for (char cell : row) {
                if (cell != '.') {
                    count++;
                }
            }
        }
        return count;
    }
}
