package com.randomjava.projects.wordsearchgenerator;

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
 * Word Search - hide a list of words in a grid of letters, then find them again.
 *
 * <h2>Overlapping is the whole craft</h2>
 *
 * <p>Placing words so they never touch is easy and makes a poor puzzle: the
 * words sit in obvious isolated streaks and the filler between them is visibly
 * random. A good grid has words <em>crossing</em> each other, sharing letters
 * wherever they agree, so no streak stands out.
 *
 * <p>So {@link #canPlace} permits a word to run over cells that are already
 * occupied, provided the letter there is the one it wants anyway. That single
 * relaxation is what produces a dense, interlocking grid, and it costs nothing:
 * a shared letter serves both words, and neither is disturbed.
 *
 * <p>Placement then tries the eight directions in a random order at random
 * starts, keeping the first fit. Words go in longest first, because a long word
 * has far fewer legal positions than a short one and placing it into an empty
 * grid is much easier than squeezing it into a full one - the same
 * most-constrained-first instinct that drives the solvers elsewhere in this
 * suite.
 *
 * <h2>Finding is not the same problem as hiding</h2>
 *
 * <p>{@link #find} does not consult the placements. It scans every cell and
 * every direction and reads letters off the grid, exactly as a person does.
 * That matters for one reason: it means the filler can accidentally spell a
 * listed word, and the search will honestly report where it actually appears
 * rather than where it was put. A finder that simply returned the answer key
 * would be unable to notice.
 *
 * <p>The letters used as filler are drawn from the words themselves rather than
 * the alphabet, so the grid has the same letter distribution throughout. Filler
 * of uniformly random letters is a giveaway - a grid full of Q, X and Z with
 * the hidden words made of ordinary letters can nearly be solved by squinting.
 */
public final class WordSearchGenerator implements Project {

    public static final Meta META = new Meta(
            249, "word-search-generator", "Word Search Generator", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.INTERMEDIATE,
            "Hide a word list in a grid of letters, then solve it back.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    private static final int MIN_SIZE = 8;
    private static final int MAX_SIZE = 16;
    private static final int[][] DIRECTIONS = {
        {0, 1}, {1, 0}, {1, 1}, {1, -1}, {0, -1}, {-1, 0}, {-1, -1}, {-1, 1}};

    private static final List<String> DEFAULT_WORDS = List.of(
            "NONOGRAM", "KAKURO", "HITORI", "SUDOKU", "KENKEN",
            "SHIKAKU", "SLITHER", "BRIDGE", "LOOP", "GRID");

    private final Random random;

    private int size = 12;
    private char[][] grid = new char[0][0];
    private List<String> words = new ArrayList<>();
    /** Where each word was actually hidden: {row, column, dRow, dColumn}. */
    private Map<String, int[]> placements = new LinkedHashMap<>();
    private List<String> revealed = new ArrayList<>();

    public WordSearchGenerator() {
        this(new Random());
    }

    public WordSearchGenerator(Random random) {
        this.random = random;
        generate(12, DEFAULT_WORDS);
    }

    public int size() {
        return size;
    }

    public List<String> words() {
        return new ArrayList<>(words);
    }

    public char letterAt(int row, int column) {
        return grid[row][column];
    }

    /** Which words actually made it into the grid. */
    public List<String> placed() {
        return new ArrayList<>(placements.keySet());
    }

    // ------------------------------------------------------------------
    // Hiding
    // ------------------------------------------------------------------

    /**
     * Whether a word fits here, allowing it to cross letters that already
     * agree with it. That overlap is what makes the grid interlock.
     */
    public boolean canPlace(String word, int row, int column, int dRow, int dColumn) {
        int endRow = row + dRow * (word.length() - 1);
        int endColumn = column + dColumn * (word.length() - 1);
        if (endRow < 0 || endRow >= size || endColumn < 0 || endColumn >= size) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            char here = grid[row + dRow * i][column + dColumn * i];
            if (here != 0 && here != word.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public void generate(int requested, List<String> requestedWords) {
        if (requested < MIN_SIZE || requested > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Use a grid between " + MIN_SIZE + " and " + MAX_SIZE + ".");
        }
        List<String> cleaned = new ArrayList<>();
        for (String word : requestedWords) {
            String trimmed = word.toUpperCase().replaceAll("[^A-Z]", "");
            if (trimmed.length() >= 3 && !cleaned.contains(trimmed)) {
                cleaned.add(trimmed);
            }
        }
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Give at least one word of three letters or more.");
        }
        for (String word : cleaned) {
            if (word.length() > requested) {
                throw new IllegalArgumentException(
                        word + " is longer than the grid is wide.");
            }
        }

        size = requested;
        words = cleaned;
        grid = new char[size][size];
        placements = new LinkedHashMap<>();
        revealed = new ArrayList<>();

        // Longest first: a long word has the fewest places it can go, so it
        // should choose while the grid is still mostly empty.
        List<String> order = new ArrayList<>(cleaned);
        order.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String word : order) {
            place(word);
        }
        fill();
    }

    private void place(String word) {
        List<int[]> starts = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                for (int[] step : DIRECTIONS) {
                    starts.add(new int[] {row, column, step[0], step[1]});
                }
            }
        }
        Collections.shuffle(starts, random);
        for (int[] start : starts) {
            if (canPlace(word, start[0], start[1], start[2], start[3])) {
                for (int i = 0; i < word.length(); i++) {
                    grid[start[0] + start[2] * i][start[1] + start[3] * i] = word.charAt(i);
                }
                placements.put(word, start);
                return;
            }
        }
    }

    /**
     * Fills the gaps with letters taken from the words themselves, so the
     * filler has the same distribution as what is hidden in it.
     */
    private void fill() {
        StringBuilder pool = new StringBuilder();
        for (String word : words) {
            pool.append(word);
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (grid[row][column] == 0) {
                    grid[row][column] = pool.charAt(random.nextInt(pool.length()));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Finding
    // ------------------------------------------------------------------

    /**
     * Where a word appears in the grid, read off the letters rather than
     * looked up in the placements. Returns {row, column, dRow, dColumn}, or
     * null if it is not there.
     */
    public int[] find(String word) {
        String target = word.toUpperCase();
        if (target.isEmpty() || grid.length == 0) {
            return null;
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                for (int[] step : DIRECTIONS) {
                    if (reads(target, row, column, step[0], step[1])) {
                        return new int[] {row, column, step[0], step[1]};
                    }
                }
            }
        }
        return null;
    }

    private boolean reads(String word, int row, int column, int dRow, int dColumn) {
        int endRow = row + dRow * (word.length() - 1);
        int endColumn = column + dColumn * (word.length() - 1);
        if (endRow < 0 || endRow >= size || endColumn < 0 || endColumn >= size) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            if (grid[row + dRow * i][column + dColumn * i] != word.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public String solve() {
        revealed = new ArrayList<>();
        int missing = 0;
        for (String word : words) {
            if (find(word) != null) {
                revealed.add(word);
            } else {
                missing++;
            }
        }
        return missing == 0
                ? "Found all " + words.size() + " words."
                : "Found " + revealed.size() + " of " + words.size()
                  + "; " + missing + " would not fit in a grid this size.";
    }

    public String hint() {
        for (String word : words) {
            if (!revealed.contains(word) && find(word) != null) {
                revealed.add(word);
                int[] at = find(word);
                return word + " starts at " + at[0] + "," + at[1] + ".";
            }
        }
        return "Nothing left to find.";
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** The grid, with revealed words shown and everything else dimmed to a dot. */
    public char[][] cells() {
        boolean[][] shown = new boolean[size][size];
        for (String word : revealed) {
            int[] at = find(word);
            if (at == null) {
                continue;
            }
            for (int i = 0; i < word.length(); i++) {
                shown[at[0] + at[2] * i][at[1] + at[3] * i] = true;
            }
        }
        char[][] out = new char[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                out[row][column] = revealed.isEmpty() || shown[row][column]
                        ? grid[row][column] : '.';
            }
        }
        return out;
    }

    private String detail() {
        List<String> missing = new ArrayList<>(words);
        missing.removeAll(placements.keySet());
        String note = missing.isEmpty() ? ""
                : " Could not fit: " + String.join(", ", missing) + ".";
        return String.format("%dx%d, %d words hidden in eight directions, %d found.%s%s",
                size, size, placements.size(), revealed.size(), note,
                revealed.isEmpty() ? " Words: " + String.join(", ", words) : "");
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(12, DEFAULT_WORDS);
        io.muted("Words run in any of eight directions and may cross each other.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            int choice = io.menu("Word Search",
                    List.of("Find one word", "Solve", "New grid", "Use my own words"));
            if (choice < 0) {
                return;
            }
            try {
                switch (choice) {
                    case 0 -> io.info(hint());
                    case 1 -> io.info(solve());
                    case 2 -> generate(io.askInt("grid size:", MIN_SIZE, MAX_SIZE, size),
                            DEFAULT_WORDS);
                    default -> {
                        String typed = io.ask("words, comma separated:");
                        generate(size, List.of(typed.split("[,\\s]+")));
                    }
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
                    String typed = Json.str(body, "words", "").trim();
                    List<String> requested = typed.isEmpty()
                            ? DEFAULT_WORDS : List.of(typed.split("[,\\s]+"));
                    generate(Json.integer(body, "size", 12), requested);
                    return board("Hidden in eight directions, crossing where letters agree.");
                }
                case "hint", "step" -> {
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
        Map<String, Object> out = Json.ok("board", Json.grid(cells()), "detail", detail());
        if (note != null && !note.isEmpty()) {
            out.put("result", note);
        }
        return out;
    }
}
