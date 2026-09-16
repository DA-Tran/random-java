package com.randomjava.cube;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Solves a 2x2 in the fewest possible turns, every time.
 *
 * <p>A 2x2 has no centres, so the cube is first rotated to put one corner in a
 * fixed place. That corner then never moves, only the other seven matter, and
 * only three faces are ever needed: turning U, R and F reaches every state,
 * because turning the other three is the same thing with the cube held
 * differently. What is left is 7! orderings times 3^6 twists, or 3,674,160
 * states.
 *
 * <p>Rather than store all of those, the search splits the puzzle into two
 * smaller ones: where the corners are (5,040 states) and which way up they are
 * (729 states). Each is small enough to measure exactly, and the larger of the
 * two distances is a lower bound on the real answer. IDA* then deepens until it
 * finds a solution, and because the bound never overestimates, the first
 * solution found is optimal.
 *
 * <p>Both tables build in a few milliseconds, which is why this does not pause
 * before its first solve.
 */
public final class Solver2x2Optimal {

    public static final String METHOD = "Optimal computer search";

    private static final int PERMUTATIONS = 5040;   // 7!
    private static final int ORIENTATIONS = 729;    // 3^6
    public static final int STATES = PERMUTATIONS * ORIENTATIONS;

    /** God's number for a 2x2 counting a half turn as one move. */
    public static final int GODS_NUMBER = 11;

    /** Corner positions in play, leaving DBL fixed as the reference. */
    private static final int[] MOVING = {0, 1, 2, 3, 4, 5, 7};

    /** Facelet indices of each corner on a 2x2, in the same order as a 3x3. */
    private static final int[][] CORNERS = {
            {3, 4, 9},    // URF
            {2, 8, 17},   // UFL
            {0, 16, 21},  // ULB
            {1, 20, 5},   // UBR
            {13, 11, 6},  // DFR
            {12, 19, 10}, // DLF
            {14, 23, 18}, // DBL
            {15, 7, 22}}; // DRB

    private static final List<Move> MOVES = buildMoves();

    private static int[][] permMove;
    private static int[][] oriMove;
    private static byte[] permDistance;
    private static byte[] oriDistance;

    // ------------------------------------------------------------------
    // Tables
    // ------------------------------------------------------------------

    private static synchronized void buildTables() {
        if (permMove != null) {
            return;
        }
        int moveCount = MOVES.size();
        int[][] source = new int[moveCount][7];
        int[][] twistDelta = new int[moveCount][7];

        // Read each move's effect straight off a real cube: after the move,
        // which piece sits in each slot and how far it has been turned.
        for (int m = 0; m < moveCount; m++) {
            FaceletCube cube = new FaceletCube(2).apply(MOVES.get(m));
            for (int slot = 0; slot < 7; slot++) {
                int[] read = readCorner(cube, MOVING[slot]);
                source[m][slot] = reduced(read[0]);
                twistDelta[m][slot] = read[1];
            }
        }

        permMove = new int[PERMUTATIONS][moveCount];
        int[] pieces = new int[7];
        int[] moved = new int[7];
        for (int p = 0; p < PERMUTATIONS; p++) {
            decodePermutation(p, pieces);
            for (int m = 0; m < moveCount; m++) {
                for (int slot = 0; slot < 7; slot++) {
                    moved[slot] = pieces[source[m][slot]];
                }
                permMove[p][m] = encodePermutation(moved);
            }
        }

        oriMove = new int[ORIENTATIONS][moveCount];
        int[] twists = new int[7];
        int[] turned = new int[7];
        for (int o = 0; o < ORIENTATIONS; o++) {
            decodeOrientation(o, twists);
            for (int m = 0; m < moveCount; m++) {
                for (int slot = 0; slot < 7; slot++) {
                    turned[slot] = (twists[source[m][slot]] + twistDelta[m][slot]) % 3;
                }
                oriMove[o][m] = encodeOrientation(turned);
            }
        }

        permDistance = sweep(permMove, PERMUTATIONS, encodePermutation(new int[]{0, 1, 2, 3, 4, 5, 6}));
        oriDistance = sweep(oriMove, ORIENTATIONS, 0);
    }

    /** Breadth-first distance from the solved value across one coordinate. */
    private static byte[] sweep(int[][] transitions, int size, int solved) {
        byte[] distance = new byte[size];
        Arrays.fill(distance, (byte) -1);
        distance[solved] = 0;
        int[] frontier = {solved};
        for (int depth = 0; frontier.length > 0; depth++) {
            int[] next = new int[size];
            int count = 0;
            for (int state : frontier) {
                for (int m = 0; m < transitions[state].length; m++) {
                    int target = transitions[state][m];
                    if (distance[target] < 0) {
                        distance[target] = (byte) (depth + 1);
                        next[count++] = target;
                    }
                }
            }
            frontier = Arrays.copyOf(next, count);
        }
        return distance;
    }

    // ------------------------------------------------------------------
    // Solving
    // ------------------------------------------------------------------

    public Solution solve(FaceletCube start) {
        return solve(start, "");
    }

    public Solution solve(FaceletCube start, String scramble) {
        if (start.size() != 2) {
            throw new IllegalArgumentException("This solver only handles a 2x2");
        }
        buildTables();

        FaceletCube cube = start.copy();
        List<Move> regrip = orientReference(cube);
        cube.apply(regrip);

        Solution.Builder out = new Solution.Builder(METHOD, scramble, 2);
        if (!regrip.isEmpty()) {
            out.add("Hold it this way",
                    "Turn the whole cube so one corner is in a fixed place. A 2x2 has no "
                            + "centres, so this changes nothing about the puzzle, and it means "
                            + "only three faces are ever needed.",
                    regrip, "", cube);
        }

        int[] coords = coordinates(cube);
        if (coords == null) {
            throw new IllegalArgumentException("That is not a state a real 2x2 can be in.");
        }

        List<Move> solution = search(coords[0], coords[1]);
        if (solution == null) {
            throw new IllegalStateException("No solution found, which should be impossible.");
        }
        cube.apply(solution);

        out.add("Optimal solution",
                "Found by deepening search under a bound that never overestimates, so this is "
                        + "genuinely the shortest sequence that solves it. No 2x2 ever needs "
                        + "more than " + GODS_NUMBER + " turns.",
                solution, "", cube);
        return out.build();
    }

    /** IDA*: deepen the limit until a solution appears, which is then optimal. */
    private List<Move> search(int perm, int ori) {
        for (int limit = heuristic(perm, ori); limit <= GODS_NUMBER; limit++) {
            List<Move> path = new ArrayList<>();
            if (descend(perm, ori, 0, limit, -1, path)) {
                return path;
            }
        }
        return null;
    }

    private boolean descend(int perm, int ori, int depth, int limit, int lastFace, List<Move> path) {
        if (perm == 0 && ori == 0) {
            return true;
        }
        if (depth + heuristic(perm, ori) > limit) {
            return false;
        }
        for (int m = 0; m < MOVES.size(); m++) {
            int face = m / 3;
            if (face == lastFace) {
                continue;
            }
            path.add(MOVES.get(m));
            if (descend(permMove[perm][m], oriMove[ori][m], depth + 1, limit, face, path)) {
                return true;
            }
            path.remove(path.size() - 1);
        }
        return false;
    }

    private static int heuristic(int perm, int ori) {
        return Math.max(permDistance[perm], oriDistance[ori]);
    }

    /**
     * Rotates the whole cube so the DBL corner is both home and the right way
     * up. Checking only that the right corner is there is not enough: if it is
     * left twisted, every other corner's twist is measured against the wrong
     * reference and the coordinates come out meaningless.
     */
    private static List<Move> orientReference(FaceletCube cube) {
        String[] rotations = {
                "", "y", "y2", "y'", "x", "x y", "x y2", "x y'",
                "x2", "x2 y", "x2 y2", "x2 y'", "x'", "x' y", "x' y2", "x' y'",
                "z", "z y", "z y2", "z y'", "z'", "z' y", "z' y2", "z' y'"};
        for (String rotation : rotations) {
            FaceletCube trial = cube.copy();
            trial.apply(rotation);
            char[] raw = trial.raw();
            if (raw[CORNERS[6][0]] == 'D' && raw[CORNERS[6][1]] == 'B' && raw[CORNERS[6][2]] == 'L') {
                return Move.parse(rotation, 2);
            }
        }
        throw new IllegalArgumentException(
                "No way of holding this cube puts the D B L corner in place, so it is not a "
                        + "real 2x2 state.");
    }

    // ------------------------------------------------------------------
    // Coordinates
    // ------------------------------------------------------------------

    /** Which piece sits at a corner position and how far it is turned. */
    private static int[] readCorner(FaceletCube cube, int position) {
        char[] raw = cube.raw();
        String found = "" + raw[CORNERS[position][0]] + raw[CORNERS[position][1]]
                + raw[CORNERS[position][2]];
        for (int candidate = 0; candidate < 8; candidate++) {
            String home = Pieces3x3.CORNER_NAMES[candidate];
            for (int r = 0; r < 3; r++) {
                if (found.charAt(r) == home.charAt(0)
                        && found.charAt((r + 1) % 3) == home.charAt(1)
                        && found.charAt((r + 2) % 3) == home.charAt(2)) {
                    return new int[]{candidate, r};
                }
            }
        }
        return null;
    }

    private static int reduced(int cornerIndex) {
        for (int i = 0; i < MOVING.length; i++) {
            if (MOVING[i] == cornerIndex) {
                return i;
            }
        }
        return -1;
    }

    /** The permutation and orientation coordinates of a cube, or null if invalid. */
    static int[] coordinates(FaceletCube cube) {
        int[] pieces = new int[7];
        int[] twists = new int[7];
        for (int slot = 0; slot < 7; slot++) {
            int[] read = readCorner(cube, MOVING[slot]);
            if (read == null) {
                return null;
            }
            int index = reduced(read[0]);
            if (index < 0) {
                return null;
            }
            pieces[slot] = index;
            twists[slot] = read[1];
        }
        return new int[]{encodePermutation(pieces), encodeOrientation(twists)};
    }

    private static int encodePermutation(int[] pieces) {
        int index = 0;
        for (int i = 0; i < 7; i++) {
            int smaller = 0;
            for (int j = i + 1; j < 7; j++) {
                if (pieces[j] < pieces[i]) {
                    smaller++;
                }
            }
            index = index * (7 - i) + smaller;
        }
        return index;
    }

    private static void decodePermutation(int index, int[] pieces) {
        int[] lehmer = new int[7];
        for (int i = 6; i >= 0; i--) {
            lehmer[i] = index % (7 - i);
            index /= (7 - i);
        }
        List<Integer> available = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            available.add(i);
        }
        for (int i = 0; i < 7; i++) {
            pieces[i] = available.remove(lehmer[i]);
        }
    }

    private static int encodeOrientation(int[] twists) {
        int index = 0;
        for (int i = 0; i < 6; i++) {
            index = index * 3 + twists[i];
        }
        return index;
    }

    private static void decodeOrientation(int index, int[] twists) {
        int total = 0;
        for (int i = 5; i >= 0; i--) {
            twists[i] = index % 3;
            index /= 3;
            total += twists[i];
        }
        twists[6] = (3 - total % 3) % 3;
    }

    private static List<Move> buildMoves() {
        List<Move> moves = new ArrayList<>();
        for (char face : new char[]{'U', 'R', 'F'}) {
            for (int amount = 1; amount <= 3; amount++) {
                moves.add(new Move(face, 1, 0, amount));
            }
        }
        return moves;
    }

    // ------------------------------------------------------------------
    // Diagnostics, used by the self test
    // ------------------------------------------------------------------

    /** The worst case over the whole puzzle, which should equal God's number. */
    public static int measureGodsNumber() {
        buildTables();
        byte[] distance = new byte[STATES];
        Arrays.fill(distance, (byte) -1);
        int solved = encodePermutation(new int[]{0, 1, 2, 3, 4, 5, 6}) * ORIENTATIONS;
        distance[solved] = 0;
        int[] frontier = {solved};
        int worst = 0;
        for (int depth = 0; frontier.length > 0; depth++) {
            int[] next = new int[STATES / 4 + 16];
            int count = 0;
            for (int state : frontier) {
                int perm = state / ORIENTATIONS;
                int ori = state % ORIENTATIONS;
                for (int m = 0; m < MOVES.size(); m++) {
                    int target = permMove[perm][m] * ORIENTATIONS + oriMove[ori][m];
                    if (distance[target] < 0) {
                        distance[target] = (byte) (depth + 1);
                        if (count == next.length) {
                            next = Arrays.copyOf(next, count * 2);
                        }
                        next[count++] = target;
                        worst = depth + 1;
                    }
                }
            }
            frontier = Arrays.copyOf(next, count);
        }
        for (byte value : distance) {
            if (value < 0) {
                return -1;
            }
        }
        return worst;
    }
}
