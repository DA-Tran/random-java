package com.randomjava.cube;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * An NxN cube held as coloured stickers, which is the representation that makes
 * both rendering and user data entry straightforward.
 *
 * <p>Faces are numbered {@code U R F D L B} and each holds {@code size * size}
 * stickers in reading order. Every face is stored as you would see it looking
 * straight at it with the cube held normally, which fixes these conventions:
 *
 * <ul>
 *   <li>U: row 0 is the back edge, row size-1 the front edge</li>
 *   <li>D: row 0 is the front edge, row size-1 the back edge</li>
 *   <li>F, R, L, B: row 0 is the top edge</li>
 *   <li>R: column 0 touches F. L: column 0 touches B. B: column 0 touches R.</li>
 * </ul>
 *
 * <p>Turning a layer cycles a ring of {@code 4 * size} stickers. Each ring below
 * is written in the direction the stickers travel, so a clockwise quarter turn
 * is always "advance by size" and there is only one place per face to get a
 * direction wrong. {@link CubeSelfTest} then checks the result against known
 * group orders, which catches any that slipped through.
 */
public final class FaceletCube {

    public static final int U = 0;
    public static final int R = 1;
    public static final int F = 2;
    public static final int D = 3;
    public static final int L = 4;
    public static final int B = 5;

    /** Face letters in storage order. */
    public static final char[] FACES = {'U', 'R', 'F', 'D', 'L', 'B'};

    private final int size;
    private final char[] stickers;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * The largest cube supported. 100 is where the real world stops: a
     * 100x100x100 has been built, and at 60,000 stickers it is still only a few
     * hundred kilobytes here. The ring-based turning below is generic in N, so
     * nothing else changes as the cube grows.
     */
    public static final int MAX_SIZE = 100;

    public FaceletCube(int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Cube size must be between 1 and " + MAX_SIZE);
        }
        this.size = size;
        this.stickers = new char[6 * size * size];
        reset();
    }

    private FaceletCube(int size, char[] stickers) {
        this.size = size;
        this.stickers = stickers;
    }

    /** Returns the cube to solved. */
    public void reset() {
        for (int face = 0; face < 6; face++) {
            Arrays.fill(stickers, face * size * size, (face + 1) * size * size, FACES[face]);
        }
    }

    public FaceletCube copy() {
        return new FaceletCube(size, stickers.clone());
    }

    public int size() {
        return size;
    }

    // ------------------------------------------------------------------
    // Sticker access
    // ------------------------------------------------------------------

    public int index(int face, int row, int col) {
        return (face * size + row) * size + col;
    }

    public char get(int face, int row, int col) {
        return stickers[index(face, row, col)];
    }

    public void set(int face, int row, int col, char colour) {
        stickers[index(face, row, col)] = colour;
    }

    public char[] raw() {
        return stickers;
    }

    public boolean isSolved() {
        for (int face = 0; face < 6; face++) {
            char first = stickers[face * size * size];
            for (int i = 1; i < size * size; i++) {
                if (stickers[face * size * size + i] != first) {
                    return false;
                }
            }
        }
        return true;
    }

    /** All 6*n*n stickers as one string, faces in U R F D L B order. */
    public String toFacelets() {
        return new String(stickers);
    }

    /** Loads a facelet string produced by {@link #toFacelets()}. */
    public static FaceletCube fromFacelets(String facelets, int size) {
        String cleaned = facelets == null ? "" : facelets.replaceAll("\\s+", "").toUpperCase();
        int expected = 6 * size * size;
        if (cleaned.length() != expected) {
            throw new IllegalArgumentException(
                    "Expected " + expected + " stickers for a " + size + "x" + size
                            + " cube but got " + cleaned.length());
        }
        for (int i = 0; i < cleaned.length(); i++) {
            if ("URFDLB".indexOf(cleaned.charAt(i)) < 0) {
                throw new IllegalArgumentException(
                        "'" + cleaned.charAt(i) + "' is not a face letter. Use U R F D L B.");
            }
        }
        return new FaceletCube(size, cleaned.toCharArray());
    }

    // ------------------------------------------------------------------
    // Turning
    // ------------------------------------------------------------------

    public FaceletCube apply(Move move) {
        int amount = move.amount();
        if (amount == 0) {
            return this;
        }
        int faceIndex = faceIndex(move.face());
        int last = move.from() + move.layers() - 1;
        if (last >= size) {
            throw new IllegalArgumentException(move + " is deeper than a " + size + "x" + size + " cube");
        }

        for (int turn = 0; turn < amount; turn++) {
            // The outer face itself spins when the turn includes layer 0.
            if (move.from() == 0) {
                rotateFace(faceIndex, true);
            }
            // Turning every layer is the same as rotating the whole cube, which
            // means the far face spins the other way.
            if (last == size - 1) {
                rotateFace(opposite(faceIndex), false);
            }
            for (int depth = move.from(); depth <= last; depth++) {
                cycleRing(ring(move.face(), depth));
            }
        }
        return this;
    }

    public FaceletCube apply(List<Move> moves) {
        for (Move move : moves) {
            apply(move);
        }
        return this;
    }

    public FaceletCube apply(String notation) {
        return apply(Move.parse(notation, size));
    }

    /** Advances every sticker in the ring by {@code size} places. */
    private void cycleRing(int[] ring) {
        char[] buffer = new char[ring.length];
        for (int i = 0; i < ring.length; i++) {
            buffer[i] = stickers[ring[i]];
        }
        for (int i = 0; i < ring.length; i++) {
            stickers[ring[(i + size) % ring.length]] = buffer[i];
        }
    }

    private void rotateFace(int face, boolean clockwise) {
        char[] before = new char[size * size];
        System.arraycopy(stickers, face * size * size, before, 0, size * size);
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                char value = clockwise
                        ? before[(size - 1 - col) * size + row]
                        : before[col * size + (size - 1 - row)];
                stickers[index(face, row, col)] = value;
            }
        }
    }

    /**
     * The ring of stickers a turn of {@code face} at {@code depth} moves, listed
     * in the direction they travel for a clockwise quarter turn.
     */
    private int[] ring(char face, int depth) {
        int n = size;
        int[] ring = new int[4 * n];
        int near = depth;            // index counted from the turning face
        int far = n - 1 - depth;     // the mirrored index on the opposite side

        switch (face) {
            case 'U' -> {
                // Clockwise from above sends F to L.
                for (int i = 0; i < n; i++) {
                    ring[i] = index(F, near, n - 1 - i);
                    ring[n + i] = index(L, near, n - 1 - i);
                    ring[2 * n + i] = index(B, near, n - 1 - i);
                    ring[3 * n + i] = index(R, near, n - 1 - i);
                }
            }
            case 'D' -> {
                // Clockwise from below sends F to R.
                for (int i = 0; i < n; i++) {
                    ring[i] = index(F, far, i);
                    ring[n + i] = index(R, far, i);
                    ring[2 * n + i] = index(B, far, i);
                    ring[3 * n + i] = index(L, far, i);
                }
            }
            case 'R' -> {
                // Clockwise from the right sends F up to U, then over to B.
                for (int i = 0; i < n; i++) {
                    ring[i] = index(F, n - 1 - i, far);
                    ring[n + i] = index(U, n - 1 - i, far);
                    ring[2 * n + i] = index(B, i, near);
                    ring[3 * n + i] = index(D, n - 1 - i, far);
                }
            }
            case 'L' -> {
                // Clockwise from the left sends F down to D, then under to B.
                for (int i = 0; i < n; i++) {
                    ring[i] = index(F, i, near);
                    ring[n + i] = index(D, i, near);
                    ring[2 * n + i] = index(B, n - 1 - i, far);
                    ring[3 * n + i] = index(U, i, near);
                }
            }
            case 'F' -> {
                // Clockwise from the front sends U to R.
                for (int i = 0; i < n; i++) {
                    ring[i] = index(U, far, i);
                    ring[n + i] = index(R, i, near);
                    ring[2 * n + i] = index(D, near, n - 1 - i);
                    ring[3 * n + i] = index(L, n - 1 - i, far);
                }
            }
            default -> {
                // B, clockwise from behind, sends U to L.
                for (int i = 0; i < n; i++) {
                    ring[i] = index(U, near, n - 1 - i);
                    ring[n + i] = index(L, i, near);
                    ring[2 * n + i] = index(D, far, i);
                    ring[3 * n + i] = index(R, n - 1 - i, far);
                }
            }
        }
        return ring;
    }

    private static int faceIndex(char face) {
        return switch (face) {
            case 'U' -> U;
            case 'R' -> R;
            case 'F' -> F;
            case 'D' -> D;
            case 'L' -> L;
            case 'B' -> B;
            default -> throw new IllegalArgumentException("Not a face: " + face);
        };
    }

    private static int opposite(int face) {
        return switch (face) {
            case U -> D;
            case D -> U;
            case R -> L;
            case L -> R;
            case F -> B;
            default -> F;
        };
    }

    // ------------------------------------------------------------------
    // Scrambling
    // ------------------------------------------------------------------

    /** A random scramble of sensible length for this cube size. */
    public List<Move> scramble(Random random) {
        return scramble(random, defaultScrambleLength(size));
    }

    public static int defaultScrambleLength(int size) {
        return switch (size) {
            case 1 -> 0;
            case 2 -> 11;
            case 3 -> 20;
            case 4 -> 40;
            case 5 -> 60;
            case 6 -> 80;
            default -> 100;
        };
    }

    /**
     * Generates a scramble and applies it. Consecutive turns never use the same
     * face, and turns on opposite faces never repeat three in a row, which is
     * what stops a scramble containing pointless cancelling moves.
     */
    public List<Move> scramble(Random random, int length) {
        List<Move> moves = new ArrayList<>();
        char[] faces = {'U', 'R', 'F', 'D', 'L', 'B'};
        char previous = ' ';
        char beforePrevious = ' ';
        int outerLayers = Math.max(1, size / 2);

        while (moves.size() < length) {
            char face = faces[random.nextInt(6)];
            if (face == previous) {
                continue;
            }
            if (face == beforePrevious && opposite(faceIndex(face)) == faceIndex(previous)) {
                continue;
            }
            int layers = size <= 3 ? 1 : 1 + random.nextInt(outerLayers);
            Move move = new Move(face, layers, 0, 1 + random.nextInt(3));
            moves.add(move);
            apply(move);
            beforePrevious = previous;
            previous = face;
        }
        return moves;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * The unfolded net as rows of text, which is what the terminal prints. Faces
     * are laid out as:
     * <pre>
     *     U
     *   L F R B
     *     D
     * </pre>
     */
    public List<String> net() {
        List<String> rows = new ArrayList<>();
        String pad = " ".repeat(size * 2);
        for (int row = 0; row < size; row++) {
            rows.add(pad + faceRow(U, row));
        }
        for (int row = 0; row < size; row++) {
            rows.add(faceRow(L, row) + faceRow(F, row) + faceRow(R, row) + faceRow(B, row));
        }
        for (int row = 0; row < size; row++) {
            rows.add(pad + faceRow(D, row));
        }
        return rows;
    }

    private String faceRow(int face, int row) {
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col < size; col++) {
            sb.append(get(face, row, col)).append(' ');
        }
        return sb.toString();
    }

    /**
     * Each face as a grid of colour letters, for the browser to draw. The order
     * matches {@link #FACES}.
     */
    public List<List<String>> faceGrids() {
        List<List<String>> out = new ArrayList<>();
        for (int face = 0; face < 6; face++) {
            List<String> cells = new ArrayList<>(size * size);
            for (int i = 0; i < size * size; i++) {
                cells.add(String.valueOf(stickers[face * size * size + i]));
            }
            out.add(cells);
        }
        return out;
    }

    /** How many stickers sit on the face they belong to, as a progress measure. */
    public int correctStickers() {
        int count = 0;
        for (int face = 0; face < 6; face++) {
            char home = centreColour(face);
            for (int i = 0; i < size * size; i++) {
                if (stickers[face * size * size + i] == home) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * The colour that identifies a face. Odd cubes have a fixed centre; even
     * cubes have none, so the face's own letter is used instead.
     */
    public char centreColour(int face) {
        if (size % 2 == 1) {
            return get(face, size / 2, size / 2);
        }
        return FACES[face];
    }

    @Override
    public String toString() {
        return String.join("\n", net());
    }
}
