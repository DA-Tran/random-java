package com.randomjava.projects.handwrittendigitrecognizer;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Handwritten Digit Recognizer - draw a digit on the grid and have it named.
 *
 * <p>Classification is nearest-template matching, which sounds too simple to
 * work and mostly is, until the drawing is <b>normalised</b> first. That step
 * is the whole project.
 *
 * <p>Comparing the drawn pixels to a stored template directly fails on almost
 * everything a person actually draws. A 3 sketched small in the top-left corner
 * shares barely a pixel with a 3 drawn large in the middle, so raw matching
 * scores it near zero against its own template and hands the answer to whatever
 * template happens to have ink in the corner. The digit is right there and
 * perfectly legible; the comparison is just being asked the wrong question.
 *
 * <p>So before anything is compared, the drawing is:
 *
 * <ol>
 *   <li><b>cropped</b> to the bounding box of the ink, which removes position
 *       entirely - corner and centre become the same picture;</li>
 *   <li><b>rescaled</b> to the template grid, which removes size;</li>
 *   <li><b>rescaled keeping its aspect ratio</b>, and padded, which is the
 *       fiddly part but not optional. Stretching the box to fill the grid turns
 *       a 1 - tall, thin, and the one digit whose shape is mostly its aspect
 *       ratio - into a wide smear that matches almost anything.</li>
 * </ol>
 *
 * <p>Rescaling is done by supersampling, so a target cell half covered by ink
 * comes out as 0.5 rather than being rounded to on or off. Those grey values
 * carry the stroke weight that makes similar digits separable.
 *
 * <p>Like the other retrieval projects here, it declines when the best and
 * second-best templates are too close to call, rather than reporting a coin
 * flip as an answer.
 */
public final class HandwrittenDigitRecognizer implements Project {

    public static final Meta META = new Meta(52, "handwritten-digit-recognizer", "Handwritten Digit Recognizer", "AI and Machine Learning", Kind.GRID,
            Difficulty.ADVANCED, "Draw a digit on a grid and classify it against trained templates.",
            "", true);

    @Override public Meta meta() { return META; }

    /** Template grid. Small on purpose: the point is the normalising, not the resolution. */
    public static final int TEMPLATE_WIDTH = 7;
    public static final int TEMPLATE_HEIGHT = 9;

    /** Points sampled per axis per target cell when rescaling. */
    private static final int SUPERSAMPLE = 6;

    /**
     * The stored digits, seven wide by nine tall.
     *
     * <p>The size is set by one pair: 0 and 8. At five by seven they differed
     * in a single row - the 8's waist - while sharing the entire outer ring,
     * and since cosine similarity is dominated by what two shapes have in
     * common, a clean 8 scored 0.879 against its own template and 0.865
     * against the 0. Correct, but by a margin too thin to act on.
     *
     * <p>Nine rows are not simply more pixels. They are enough to draw the
     * difference a person actually sees: the 8 pinches at the waist and bulges
     * into two lobes, the 0 is a single oval at its widest in the middle. Six
     * of the nine rows now differ. Raising the resolution without redrawing
     * those two would have changed nothing, because one row in nine is a
     * smaller difference than one row in seven.
     */
    private static final String[][] GLYPHS = {
            {"0", "..###..", ".##.##.", "##...##", "##...##", "##...##",
                  "##...##", "##...##", ".##.##.", "..###.."},
            {"1", "...##..", "..###..", ".####..", "...##..", "...##..",
                  "...##..", "...##..", "...##..", ".######"},
            {"2", ".#####.", "##...##", ".....##", "....##.", "...##..",
                  "..##...", ".##....", "##.....", "#######"},
            {"3", ".#####.", "##...##", ".....##", "..####.", ".....##",
                  ".....##", ".....##", "##...##", ".#####."},
            {"4", ".....#.", "....##.", "...###.", "..#.##.", ".#..##.",
                  "##..##.", "#######", "....##.", "....##."},
            {"5", "#######", "##.....", "##.....", "######.", ".....##",
                  ".....##", ".....##", "##...##", ".#####."},
            {"6", "..####.", ".##....", "##.....", "##.....", "######.",
                  "##...##", "##...##", "##...##", ".#####."},
            {"7", "#######", "#....##", "....##.", "....##.", "...##..",
                  "...##..", "..##...", "..##...", "..##..."},
            {"8", ".#####.", "##...##", "##...##", ".#####.", ".#####.",
                  "##...##", "##...##", "##...##", ".#####."},
            {"9", ".#####.", "##...##", "##...##", "##...##", ".######",
                  ".....##", ".....##", "....##.", ".####.."},
    };

    /** A stored digit and its pixels. */
    public record Template(int digit, double[][] pixels) { }

    /** One candidate and how well it matched. */
    public record Score(int digit, double similarity) { }

    /** What the classifier decided. */
    public record Guess(int digit, double confidence, double margin,
                        List<Score> ranked, String note) {
        public boolean decided() { return digit >= 0; }
    }

    private static final List<Template> TEMPLATES = buildTemplates();

    private static List<Template> buildTemplates() {
        List<Template> out = new ArrayList<>();
        for (String[] glyph : GLYPHS) {
            if (glyph.length != TEMPLATE_HEIGHT + 1) {
                throw new IllegalStateException("Glyph " + glyph[0] + " has "
                        + (glyph.length - 1) + " rows, expected " + TEMPLATE_HEIGHT);
            }
            double[][] pixels = new double[TEMPLATE_HEIGHT][TEMPLATE_WIDTH];
            for (int row = 0; row < TEMPLATE_HEIGHT; row++) {
                String line = glyph[row + 1];
                if (line.length() != TEMPLATE_WIDTH) {
                    throw new IllegalStateException("Glyph " + glyph[0] + " row " + row
                            + " is " + line.length() + " wide, expected " + TEMPLATE_WIDTH);
                }
                for (int col = 0; col < TEMPLATE_WIDTH; col++) {
                    pixels[row][col] = line.charAt(col) == '#' ? 1.0 : 0.0;
                }
            }
            out.add(new Template(Integer.parseInt(glyph[0]), pixels));
        }
        return List.copyOf(out);
    }

    public static List<Template> templates() { return TEMPLATES; }

    // ------------------------------------------------------------------
    // Canvas
    // ------------------------------------------------------------------

    private int size = 12;
    private boolean[][] canvas = new boolean[size][size];

    public int size() { return size; }

    public void resize(int newSize) {
        if (newSize < 5 || newSize > 40) {
            throw new IllegalArgumentException("The canvas must be between 5 and 40 cells.");
        }
        size = newSize;
        canvas = new boolean[size][size];
    }

    public void clear() { canvas = new boolean[size][size]; }

    public void paint(int row, int col, boolean on) {
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new IllegalArgumentException("That cell is off the canvas.");
        }
        canvas[row][col] = on;
    }

    public boolean toggle(int row, int col) {
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new IllegalArgumentException("That cell is off the canvas.");
        }
        canvas[row][col] = !canvas[row][col];
        return canvas[row][col];
    }

    public boolean[][] canvas() {
        boolean[][] copy = new boolean[size][];
        for (int row = 0; row < size; row++) { copy[row] = canvas[row].clone(); }
        return copy;
    }

    /**
     * Paints a stored digit onto the canvas at a given position and size, for
     * demos and tests.
     *
     * <p>Scaling is by <b>area coverage</b>, not by picking the nearest source
     * pixel. Nearest-neighbour is the obvious way to write this and it silently
     * deletes rows: rendering a nine-row glyph into seven rows maps each output
     * row to one input row and never visits the other two, so the waist of an 8
     * or the middle bar of a 3 can vanish entirely. The result is a drawing
     * that is not a small 3, it is a small 9 - and no amount of care in the
     * classifier can recover a feature the renderer threw away.
     *
     * <p>Area coverage asks instead what fraction of each output cell is ink,
     * so every input row contributes to whichever output rows overlap it. That
     * is the same reasoning behind the supersampling in {@link #normalise}, and
     * it would be odd to insist on it in one direction and not the other.
     */
    public void drawDigit(int digit, int top, int left, int height) {
        if (digit < 0 || digit > 9) {
            throw new IllegalArgumentException("Pick a digit from 0 to 9.");
        }
        if (height < 1) { throw new IllegalArgumentException("The digit needs some height."); }
        Template template = TEMPLATES.get(digit);
        int width = Math.max(1, Math.round(height * (float) TEMPLATE_WIDTH / TEMPLATE_HEIGHT));
        double rowScale = (double) TEMPLATE_HEIGHT / height;
        double colScale = (double) TEMPLATE_WIDTH / width;

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                double topEdge = row * rowScale;
                double bottomEdge = (row + 1) * rowScale;
                double leftEdge = col * colScale;
                double rightEdge = (col + 1) * colScale;
                double inked = 0;

                for (int sr = (int) Math.floor(topEdge); sr < Math.ceil(bottomEdge); sr++) {
                    if (sr < 0 || sr >= TEMPLATE_HEIGHT) { continue; }
                    double overlapRows = Math.min(bottomEdge, sr + 1) - Math.max(topEdge, sr);
                    if (overlapRows <= 0) { continue; }
                    for (int sc = (int) Math.floor(leftEdge); sc < Math.ceil(rightEdge); sc++) {
                        if (sc < 0 || sc >= TEMPLATE_WIDTH) { continue; }
                        double overlapCols = Math.min(rightEdge, sc + 1) - Math.max(leftEdge, sc);
                        if (overlapCols <= 0) { continue; }
                        if (template.pixels()[sr][sc] > 0.5) { inked += overlapRows * overlapCols; }
                    }
                }

                double area = (bottomEdge - topEdge) * (rightEdge - leftEdge);
                if (area > 0 && inked / area >= 0.5) {
                    int r = top + row;
                    int c = left + col;
                    if (r >= 0 && r < size && c >= 0 && c < size) { canvas[r][c] = true; }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Normalising
    // ------------------------------------------------------------------

    /**
     * Crops to the ink, rescales to the template grid keeping the aspect ratio,
     * and returns grey coverage values. Null when there is no ink at all.
     */
    public static double[][] normalise(boolean[][] ink) {
        int rows = ink.length;
        int cols = ink[0].length;
        int minRow = rows;
        int maxRow = -1;
        int minCol = cols;
        int maxCol = -1;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (ink[row][col]) {
                    minRow = Math.min(minRow, row);
                    maxRow = Math.max(maxRow, row);
                    minCol = Math.min(minCol, col);
                    maxCol = Math.max(maxCol, col);
                }
            }
        }
        if (maxRow < 0) { return null; }

        double boxHeight = maxRow - minRow + 1;
        double boxWidth = maxCol - minCol + 1;

        // Source units per target cell, the same on both axes so the shape is
        // not distorted. Whichever axis is relatively larger sets the scale.
        double scale = Math.max(boxWidth / TEMPLATE_WIDTH, boxHeight / TEMPLATE_HEIGHT);
        // Centre the smaller axis in the grid.
        double padCols = (TEMPLATE_WIDTH - boxWidth / scale) / 2.0;
        double padRows = (TEMPLATE_HEIGHT - boxHeight / scale) / 2.0;

        double[][] out = new double[TEMPLATE_HEIGHT][TEMPLATE_WIDTH];
        for (int row = 0; row < TEMPLATE_HEIGHT; row++) {
            for (int col = 0; col < TEMPLATE_WIDTH; col++) {
                int hits = 0;
                for (int sr = 0; sr < SUPERSAMPLE; sr++) {
                    for (int sc = 0; sc < SUPERSAMPLE; sc++) {
                        double targetRow = row + (sr + 0.5) / SUPERSAMPLE - padRows;
                        double targetCol = col + (sc + 0.5) / SUPERSAMPLE - padCols;
                        int sourceRow = (int) Math.floor(minRow + targetRow * scale);
                        int sourceCol = (int) Math.floor(minCol + targetCol * scale);
                        if (sourceRow >= minRow && sourceRow <= maxRow
                                && sourceCol >= minCol && sourceCol <= maxCol
                                && ink[sourceRow][sourceCol]) {
                            hits++;
                        }
                    }
                }
                out[row][col] = (double) hits / (SUPERSAMPLE * SUPERSAMPLE);
            }
        }
        return out;
    }

    /** Cosine similarity between two grids of the same shape: 0 apart, 1 aligned. */
    public static double similarity(double[][] a, double[][] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int row = 0; row < a.length; row++) {
            for (int col = 0; col < a[row].length; col++) {
                dot += a[row][col] * b[row][col];
                normA += a[row][col] * a[row][col];
                normB += b[row][col] * b[row][col];
            }
        }
        if (normA == 0 || normB == 0) { return 0; }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ------------------------------------------------------------------
    // Classifying
    // ------------------------------------------------------------------

    /**
     * How close the runner-up may get before the answer is treated as a tie.
     * Several digits genuinely are near-identical at this resolution, so the
     * honest output is sometimes "8 or 9" rather than a confident wrong pick.
     */
    public static final double MARGIN = 0.02;

    public Guess classify() { return classify(canvas); }

    public Guess classify(boolean[][] ink) {
        double[][] normalised = normalise(ink);
        if (normalised == null) {
            return new Guess(-1, 0, 0, List.of(), "Nothing drawn yet.");
        }
        List<Score> ranked = new ArrayList<>();
        for (Template template : TEMPLATES) {
            ranked.add(new Score(template.digit(), similarity(normalised, template.pixels())));
        }
        ranked.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));

        Score best = ranked.get(0);
        Score second = ranked.get(1);
        double margin = best.similarity() - second.similarity();

        if (best.similarity() < 0.55) {
            return new Guess(-1, best.similarity(), margin, ranked,
                    String.format("That does not look like any digit. The closest is %d at %.2f, "
                            + "under the 0.55 needed.", best.digit(), best.similarity()));
        }
        if (margin < MARGIN) {
            return new Guess(-1, best.similarity(), margin, ranked,
                    String.format("Could be %d or %d - they score %.3f and %.3f, too close to "
                            + "call at this resolution.", best.digit(), second.digit(),
                            best.similarity(), second.similarity()));
        }
        return new Guess(best.digit(), best.similarity(), margin, ranked,
                String.format("That is a %d (%.2f, clear of %d by %.3f).",
                        best.digit(), best.similarity(), second.digit(), margin));
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private char[][] cells() {
        char[][] out = new char[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                out[row][col] = canvas[row][col] ? '#' : '.';
            }
        }
        return out;
    }

    private static char[][] greyCells(double[][] grid) {
        char[][] out = new char[grid.length][grid[0].length];
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                double value = grid[row][col];
                out[row][col] = value > 0.66 ? '#' : value > 0.33 ? '*' : value > 0.05 ? '+' : '.';
            }
        }
        return out;
    }

    private String detail(Guess guess) {
        StringBuilder sb = new StringBuilder(guess.note());
        if (!guess.ranked().isEmpty()) {
            sb.append("\n\n  ");
            for (int i = 0; i < 4 && i < guess.ranked().size(); i++) {
                Score score = guess.ranked().get(i);
                sb.append(String.format("%d:%.2f  ", score.digit(), score.similarity()));
            }
            double[][] normalised = normalise(canvas);
            if (normalised != null) {
                sb.append("\n\n  normalised to ")
                  .append(TEMPLATE_WIDTH).append('x').append(TEMPLATE_HEIGHT)
                  .append(" (position and size removed):\n");
                for (char[] row : greyCells(normalised)) {
                    sb.append("    ").append(new String(row)).append('\n');
                }
            }
        }
        return sb.toString().stripTrailing();
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private int demo = 0;

    @Override public void runText(ConsoleUI io) {
        io.muted("Draw with 'row col' to toggle a cell, or try the demo digits.");
        while (true) {
            io.println();
            io.grid(cells());
            int choice = io.menu("Digit Recognizer (" + size + "x" + size + ")", List.of(
                    "Toggle a cell", "Classify", "Draw a demo digit", "Clear", "Resize"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> toggle(io.askInt("row:", 0, size - 1, 0),
                            io.askInt("col:", 0, size - 1, 0));
                    case 1 -> io.println(detail(classify()));
                    case 2 -> {
                        clear();
                        int digit = io.askInt("digit 0-9:", 0, 9, demo);
                        demo = (digit + 1) % 10;
                        drawDigit(digit, 1, 1, size - 2);
                    }
                    case 3 -> clear();
                    default -> resize(io.askInt("size 5-40:", 5, 40, size));
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    resize(Math.min(Math.max(Json.integer(body, "size", size), 5), 40));
                    return board("Canvas cleared. Click cells to draw, then Run to classify.");
                }
                case "paint", "toggle" -> {
                    toggle(Json.integer(body, "row", -1), Json.integer(body, "col", -1));
                    return board("");
                }
                case "clear" -> { clear(); return board("Cleared."); }
                case "step" -> {
                    // Cycles through the built-in digits, so the page does
                    // something useful before anyone has drawn anything.
                    clear();
                    int digit = demo;
                    demo = (demo + 1) % 10;
                    drawDigit(digit, 1, 1, size - 2);
                    return board("Drew a " + digit + ". Press Run to see if it is recognised.");
                }
                case "solve", "classify" -> {
                    Guess guess = classify();
                    return Json.ok("board", Json.grid(cells()),
                            "result", guess.decided() ? String.valueOf(guess.digit()) : "unsure",
                            "detail", detail(guess));
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }

    private Map<String, Object> board(String message) {
        return Json.ok("board", Json.grid(cells()), "detail", message);
    }
}
