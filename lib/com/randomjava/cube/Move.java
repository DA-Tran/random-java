package com.randomjava.cube;

import java.util.ArrayList;
import java.util.List;

/**
 * A single turn in standard cube notation.
 *
 * <p>Notation handled here:
 * <ul>
 *   <li>{@code U D L R F B} - a quarter turn of one outer face, clockwise</li>
 *   <li>{@code '} for anticlockwise, {@code 2} for a half turn</li>
 *   <li>{@code Rw} or {@code r} - a wide turn taking two layers</li>
 *   <li>{@code 3Rw} - a wide turn taking three layers, for big cubes</li>
 *   <li>{@code M E S} - slice turns, which follow L, D and F respectively</li>
 *   <li>{@code x y z} - whole-cube rotations, following R, U and F</li>
 * </ul>
 *
 * @param face   the face letter this turn follows, one of U D L R F B
 * @param layers how many layers deep the turn bites, 1 for an outer turn
 * @param from   the first layer index turned, 0 for an outer turn. Slices set
 *               this to 1 so they turn the middle without the outer face.
 * @param amount quarter turns clockwise: 1, 2 or 3
 */
public record Move(char face, int layers, int from, int amount) {

    private static final String FACES = "UDLRFB";

    public Move {
        if (FACES.indexOf(face) < 0) {
            throw new IllegalArgumentException("Not a face: " + face);
        }
        amount = ((amount % 4) + 4) % 4;
    }

    /** The same turn in the opposite direction. */
    public Move inverse() {
        return new Move(face, layers, from, 4 - amount);
    }

    /** True when this turn moves every layer, i.e. it is a whole-cube rotation. */
    public boolean isRotation(int size) {
        return from == 0 && layers >= size;
    }

    @Override
    public String toString() {
        String suffix = switch (amount) {
            case 2 -> "2";
            case 3 -> "'";
            default -> "";
        };
        if (from == 1 && layers == 1) {
            // A pure slice, rendered in its traditional letter.
            char slice = switch (face) {
                case 'L' -> 'M';
                case 'D' -> 'E';
                case 'F' -> 'S';
                default -> face;
            };
            return slice + suffix;
        }
        if (layers <= 1) {
            return face + suffix;
        }
        String prefix = layers > 2 ? String.valueOf(layers) : "";
        return prefix + face + "w" + suffix;
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /**
     * Parses a whitespace-separated sequence such as {@code "R U R' U' Rw2 M'"}.
     *
     * @param text text to parse
     * @param size cube size, needed to expand rotations into full-depth turns
     * @throws IllegalArgumentException with a useful message on bad input
     */
    public static List<Move> parse(String text, int size) {
        List<Move> moves = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return moves;
        }
        for (String token : text.trim().split("[\\s,]+")) {
            if (!token.isEmpty()) {
                moves.add(parseOne(token, size));
            }
        }
        return moves;
    }

    /** Parses one token such as {@code R'}, {@code 3Rw2}, {@code M} or {@code x}. */
    public static Move parseOne(String token, int size) {
        String work = token.trim();
        if (work.isEmpty()) {
            throw new IllegalArgumentException("Empty move");
        }

        int amount = 1;
        if (work.endsWith("'") || work.endsWith("’")) {
            amount = 3;
            work = work.substring(0, work.length() - 1);
        } else if (work.endsWith("2")) {
            amount = 2;
            work = work.substring(0, work.length() - 1);
        }
        if (work.isEmpty()) {
            throw new IllegalArgumentException("'" + token + "' has no face letter");
        }

        // Whole-cube rotations.
        char first = work.charAt(0);
        if (work.length() == 1 && "xyzXYZ".indexOf(first) >= 0) {
            return switch (Character.toLowerCase(first)) {
                case 'x' -> new Move('R', size, 0, amount);
                case 'y' -> new Move('U', size, 0, amount);
                default -> new Move('F', size, 0, amount);
            };
        }

        // Slice turns. M follows L, E follows D, S follows F.
        if (work.length() == 1 && "MES".indexOf(first) >= 0) {
            if (size < 3) {
                throw new IllegalArgumentException("Slice moves need a 3x3 or larger cube");
            }
            char face = switch (first) {
                case 'M' -> 'L';
                case 'E' -> 'D';
                default -> 'F';
            };
            // Every layer between the two outer faces.
            return new Move(face, size - 2, 1, amount);
        }

        // An optional leading number is the wide depth, e.g. 3Rw.
        int depth = 0;
        int index = 0;
        while (index < work.length() && Character.isDigit(work.charAt(index))) {
            depth = depth * 10 + (work.charAt(index) - '0');
            index++;
        }
        if (index >= work.length()) {
            throw new IllegalArgumentException("'" + token + "' has no face letter");
        }

        char face = work.charAt(index);
        index++;
        boolean wide = false;
        if (index < work.length() && (work.charAt(index) == 'w' || work.charAt(index) == 'W')) {
            wide = true;
            index++;
        }
        if (index != work.length()) {
            throw new IllegalArgumentException("Could not read '" + token + "'");
        }

        // A lowercase face letter is the other way of writing a wide turn.
        if (Character.isLowerCase(face) && FACES.indexOf(Character.toUpperCase(face)) >= 0) {
            wide = true;
            face = Character.toUpperCase(face);
        }
        face = Character.toUpperCase(face);
        if (FACES.indexOf(face) < 0) {
            throw new IllegalArgumentException("'" + face + "' is not a face. Use U D L R F B.");
        }

        int layers = depth > 0 ? depth : (wide ? 2 : 1);
        if (layers > size) {
            throw new IllegalArgumentException(
                    token + " turns " + layers + " layers but the cube is only " + size + " deep");
        }
        return new Move(face, layers, 0, amount);
    }

    /** Renders a sequence back to notation. */
    public static String format(List<Move> moves) {
        StringBuilder sb = new StringBuilder();
        for (Move move : moves) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(move);
        }
        return sb.toString();
    }

    /** Reverses a sequence, so applying it undoes the original. */
    public static List<Move> invert(List<Move> moves) {
        List<Move> out = new ArrayList<>(moves.size());
        for (int i = moves.size() - 1; i >= 0; i--) {
            out.add(moves.get(i).inverse());
        }
        return out;
    }

    /**
     * Removes redundancy: drops no-op turns and merges consecutive turns of the
     * same layers, so {@code R R} becomes {@code R2} and {@code R R'} vanishes.
     */
    public static List<Move> tidy(List<Move> moves) {
        List<Move> out = new ArrayList<>();
        for (Move move : moves) {
            if (move.amount() == 0) {
                continue;
            }
            if (!out.isEmpty()) {
                Move last = out.get(out.size() - 1);
                if (last.face() == move.face() && last.layers() == move.layers()
                        && last.from() == move.from()) {
                    out.remove(out.size() - 1);
                    int combined = (last.amount() + move.amount()) % 4;
                    if (combined != 0) {
                        out.add(new Move(move.face(), move.layers(), move.from(), combined));
                    }
                    continue;
                }
            }
            out.add(move);
        }
        return out;
    }

    /** Convenience for building a sequence from notation on a given cube size. */
    public static List<Move> of(String notation, int size) {
        return parse(notation, size);
    }
}
