package com.randomjava.projects.memorymatchinggame;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Memory Matching Game - flip two cards at a time and remember where the pairs
 * were.
 *
 * <p>The rules are trivial. What is not trivial is that a shuffle must be
 * <em>fair</em>, and the obvious way to write one is not.
 *
 * <p>The naive shuffle swaps each position with a random position anywhere in
 * the deck. It looks correct, produces convincingly jumbled output, and is
 * measurably biased: it can produce n<sup>n</sup> equally likely outcomes
 * spread across n! arrangements, and since n<sup>n</sup> is not divisible by n!
 * some arrangements come up more often than others. Fisher-Yates picks each
 * position's card from only the cards not yet placed, giving exactly n!
 * outcomes, one per arrangement.
 *
 * <p>Nobody notices this in a memory game, which is the point - it is a bug
 * that hides until the day the same code shuffles something that matters.
 * {@link #shuffle} is Fisher-Yates and {@link #biasedShuffle} is the naive one,
 * kept so the difference can be measured rather than asserted.
 *
 * <p>The other place this goes wrong is the flip cycle. A pair that does not
 * match has to stay visible long enough to be seen, so a third flip has to
 * first clear the previous two rather than being treated as a new selection.
 */
public final class MemoryMatchingGame implements Project {

    public static final Meta META = new Meta(73, "memory-matching-game", "Memory Matching Game", "Game Development", Kind.GRID,
            Difficulty.BEGINNER, "Flip cards two at a time and remember where the pairs are.",
            "", true);

    private static final String FACES = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    public record Card(int index, char face, boolean matched, boolean faceUp) { }

    private int rows;
    private int columns;
    private char[] faces;
    private boolean[] matched;
    private final List<Integer> flipped = new ArrayList<>();
    private int moves;
    private int pairsFound;
    private final Random random;

    public MemoryMatchingGame() { this(4, 4, new Random()); }

    public MemoryMatchingGame(int rows, int columns, Random random) {
        this.random = random;
        deal(rows, columns);
    }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Dealing
    // ------------------------------------------------------------------

    public void deal(int newRows, int newColumns) {
        if (newRows < 2 || newColumns < 2 || newRows > 8 || newColumns > 8) {
            throw new IllegalArgumentException("Use 2 to 8 rows and columns.");
        }
        if ((newRows * newColumns) % 2 != 0) {
            throw new IllegalArgumentException(
                    "An odd number of cards cannot be dealt into pairs.");
        }
        int pairs = newRows * newColumns / 2;
        if (pairs > FACES.length()) {
            throw new IllegalArgumentException("That board needs more faces than exist.");
        }
        rows = newRows;
        columns = newColumns;
        faces = new char[rows * columns];
        for (int pair = 0; pair < pairs; pair++) {
            faces[pair * 2] = FACES.charAt(pair);
            faces[pair * 2 + 1] = FACES.charAt(pair);
        }
        shuffle(faces, random);
        matched = new boolean[faces.length];
        flipped.clear();
        moves = 0;
        pairsFound = 0;
    }

    /**
     * Fisher-Yates. Each position draws from the cards not yet placed, so every
     * one of the n! arrangements is equally likely.
     */
    public static void shuffle(char[] cards, Random random) {
        for (int i = cards.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char swap = cards[i];
            cards[i] = cards[j];
            cards[j] = swap;
        }
    }

    /**
     * The naive shuffle, for comparison only. Swapping each position with any
     * position produces n^n equally likely sequences, and n^n is not a multiple
     * of n!, so some arrangements are strictly more likely than others.
     */
    public static void biasedShuffle(char[] cards, Random random) {
        for (int i = 0; i < cards.length; i++) {
            int j = random.nextInt(cards.length);
            char swap = cards[i];
            cards[i] = cards[j];
            cards[j] = swap;
        }
    }

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    public int rows() { return rows; }
    public int columns() { return columns; }
    public int moves() { return moves; }
    public int pairsFound() { return pairsFound; }
    public int pairs() { return faces.length / 2; }
    public boolean won() { return pairsFound == pairs(); }
    public List<Integer> faceUp() { return List.copyOf(flipped); }

    public Card card(int index) {
        return new Card(index, faces[index], matched[index],
                matched[index] || flipped.contains(index));
    }

    /**
     * Flips one card and returns what the flip did.
     *
     * <p>A third flip while two unmatched cards are showing clears them first.
     * Treating it as a fresh selection would leave three cards face up, and
     * turning the pair down the instant it fails to match gives the player no
     * chance to see the second card at all.
     */
    public String flip(int index) {
        if (index < 0 || index >= faces.length) {
            throw new IllegalArgumentException("There is no card " + index + ".");
        }
        if (won()) { return "The board is already clear."; }

        if (flipped.size() == 2) {
            flipped.clear();
        }
        if (matched[index]) { return "That pair is already found."; }
        if (flipped.contains(index)) { return "That card is already face up."; }

        flipped.add(index);
        if (flipped.size() < 2) { return "Face up: " + faces[index] + "."; }

        moves++;
        int first = flipped.get(0);
        int second = flipped.get(1);
        if (faces[first] == faces[second]) {
            matched[first] = true;
            matched[second] = true;
            pairsFound++;
            flipped.clear();
            return won()
                    ? "Matched " + faces[first] + ". The board is clear in " + moves + " moves."
                    : "Matched " + faces[first] + ".";
        }
        return "No match: " + faces[first] + " and " + faces[second]
                + ". They turn back over on the next flip.";
    }

    /** Position to index, for the grid front end. */
    public int indexOf(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            throw new IllegalArgumentException("That square is off the board.");
        }
        return row * columns + column;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public char[][] cells() {
        char[][] grid = new char[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = indexOf(row, column);
                grid[row][column] = matched[index] ? faces[index]
                        : flipped.contains(index) ? faces[index] : '#';
            }
        }
        return grid;
    }

    private String detail() {
        if (won()) {
            return String.format("Cleared in %d moves. The best possible is %d.",
                    moves, pairs());
        }
        return String.format("%d of %d pairs, %d moves. %d card%s face up.",
                pairsFound, pairs(), moves, flipped.size(), flipped.size() == 1 ? "" : "s");
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("Enter a row and column to turn a card over.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            if (won()) {
                if (!io.askYesNo("Deal again?", true)) { return; }
                deal(rows, columns);
                continue;
            }
            try {
                int row = io.askInt("row:", 0, rows - 1, 0);
                int column = io.askInt("column:", 0, columns - 1, 0);
                io.println("  " + flip(indexOf(row, column)));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    int side = Math.min(Math.max(Json.integer(body, "size", 4), 2), 8);
                    // An odd square has no pairing, so nudge it to the next even.
                    if (side % 2 != 0) { side++; }
                    deal(side, side);
                    return board("Dealt " + pairs() + " pairs. Click a card to turn it over.");
                }
                case "flip", "step" -> {
                    int index = Json.integer(body, "index", -1);
                    if (index < 0) {
                        index = indexOf(Json.integer(body, "row", 0), Json.integer(body, "col", 0));
                    }
                    return board(flip(index));
                }
                case "solve" -> {
                    for (int i = 0; i < faces.length; i++) { matched[i] = true; }
                    pairsFound = pairs();
                    flipped.clear();
                    return board("Revealed. That is not winning, it is just looking.");
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }

    private Map<String, Object> board(String note) {
        return Json.ok("board", Json.grid(cells()),
                "detail", note.isEmpty() ? detail() : note + "\n  " + detail());
    }
}
