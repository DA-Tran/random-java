package com.randomjava.cube;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether a cube someone typed in could actually exist.
 *
 * <p>This matters more than it sounds. A solver handed an impossible cube does
 * not fail politely: it searches forever for something that is not there. Three
 * of the checks below catch states that look completely normal but cannot be
 * reached by turning a real cube:
 *
 * <ul>
 *   <li><b>Corner twist.</b> Turning a cube can never change the total twist of
 *       all eight corners, so that total must be a multiple of three. A single
 *       corner rotated in its place is the classic impossible cube.</li>
 *   <li><b>Edge flip.</b> Likewise an even number of edges must be flipped, so
 *       one edge put back the wrong way round is impossible.</li>
 *   <li><b>Permutation parity.</b> Every turn swaps four corners and four edges
 *       at once, so the corner and edge permutations always have the same
 *       parity. Two stickers swapped over breaks this.</li>
 * </ul>
 *
 * <p>Between them these reject the three ways a cube gets "broken" when someone
 * takes it apart and reassembles it carelessly.
 */
public final class CubeValidator {

    private CubeValidator() {
    }

    /** The verdict, with every problem found rather than only the first. */
    public record Result(boolean valid, List<String> problems) {
        public String summary() {
            return valid ? "This is a valid cube and can be solved."
                    : String.join(" ", problems);
        }
    }

    public static Result check(FaceletCube cube) {
        List<String> problems = new ArrayList<>();
        if (cube.size() != 3) {
            countStickers(cube, problems);
            return new Result(problems.isEmpty(), problems);
        }

        countStickers(cube, problems);
        if (!problems.isEmpty()) {
            return new Result(false, problems);
        }

        centresDistinct(cube, problems);

        int[] cornerPerm = new int[8];
        int[] cornerOri = new int[8];
        if (!readCorners(cube, cornerPerm, cornerOri, problems)) {
            return new Result(false, problems);
        }

        int[] edgePerm = new int[12];
        int[] edgeOri = new int[12];
        if (!readEdges(cube, edgePerm, edgeOri, problems)) {
            return new Result(false, problems);
        }

        int twist = 0;
        for (int ori : cornerOri) {
            twist += ori;
        }
        if (twist % 3 != 0) {
            problems.add("One corner is twisted in place. A real cube can never have a single "
                    + "corner rotated on its own, so check the three stickers of each corner.");
        }

        int flip = 0;
        for (int ori : edgeOri) {
            flip += ori;
        }
        if (flip % 2 != 0) {
            problems.add("One edge is flipped the wrong way round. Edges can only ever be "
                    + "flipped in pairs, so check the two stickers of each edge.");
        }

        if (parity(cornerPerm) != parity(edgePerm)) {
            problems.add("Two pieces look swapped over. Corners and edges always move together, "
                    + "so a single swapped pair cannot happen on a real cube.");
        }

        return new Result(problems.isEmpty(), problems);
    }

    // ------------------------------------------------------------------

    private static void countStickers(FaceletCube cube, List<String> problems) {
        int[] counts = new int[6];
        for (char sticker : cube.raw()) {
            int index = "URFDLB".indexOf(sticker);
            if (index < 0) {
                problems.add("'" + sticker + "' is not a face letter. Use U R F D L B.");
                return;
            }
            counts[index]++;
        }
        int expected = cube.size() * cube.size();
        for (int i = 0; i < 6; i++) {
            if (counts[i] != expected) {
                problems.add("There are " + counts[i] + " " + "URFDLB".charAt(i)
                        + " stickers but there should be exactly " + expected + ".");
            }
        }
    }

    private static void centresDistinct(FaceletCube cube, List<String> problems) {
        boolean[] seen = new boolean[6];
        for (int face = 0; face < 6; face++) {
            char centre = cube.get(face, 1, 1);
            int index = "URFDLB".indexOf(centre);
            if (index < 0 || seen[index]) {
                problems.add("Two centres show the same colour. On a 3x3 the centres never move, "
                        + "so all six must be different.");
                return;
            }
            seen[index] = true;
        }
    }

    /** Reads which corner sits where and how it is twisted. */
    private static boolean readCorners(FaceletCube cube, int[] perm, int[] ori,
            List<String> problems) {
        boolean[] used = new boolean[8];
        for (int position = 0; position < 8; position++) {
            String found = Pieces3x3.cornerAt(cube, position);
            int cubie = -1;
            int rotation = 0;
            for (int candidate = 0; candidate < 8 && cubie < 0; candidate++) {
                String home = Pieces3x3.CORNER_NAMES[candidate];
                for (int r = 0; r < 3; r++) {
                    if (found.charAt(r) == home.charAt(0)
                            && found.charAt((r + 1) % 3) == home.charAt(1)
                            && found.charAt((r + 2) % 3) == home.charAt(2)) {
                        cubie = candidate;
                        rotation = r;
                        break;
                    }
                }
            }
            if (cubie < 0) {
                problems.add("The corner at " + Pieces3x3.CORNER_NAMES[position] + " shows "
                        + found + ", which is not a real corner of a cube.");
                return false;
            }
            if (used[cubie]) {
                problems.add("The " + Pieces3x3.CORNER_NAMES[cubie]
                        + " corner appears more than once.");
                return false;
            }
            used[cubie] = true;
            perm[position] = cubie;
            ori[position] = rotation;
        }
        return true;
    }

    /** Reads which edge sits where and whether it is flipped. */
    private static boolean readEdges(FaceletCube cube, int[] perm, int[] ori,
            List<String> problems) {
        boolean[] used = new boolean[12];
        for (int position = 0; position < 12; position++) {
            String found = Pieces3x3.edgeAt(cube, position);
            int cubie = -1;
            int flip = 0;
            for (int candidate = 0; candidate < 12 && cubie < 0; candidate++) {
                String home = Pieces3x3.EDGE_NAMES[candidate];
                if (found.charAt(0) == home.charAt(0) && found.charAt(1) == home.charAt(1)) {
                    cubie = candidate;
                    flip = 0;
                } else if (found.charAt(1) == home.charAt(0) && found.charAt(0) == home.charAt(1)) {
                    cubie = candidate;
                    flip = 1;
                }
            }
            if (cubie < 0) {
                problems.add("The edge at " + Pieces3x3.EDGE_NAMES[position] + " shows "
                        + found + ", which is not a real edge of a cube.");
                return false;
            }
            if (used[cubie]) {
                problems.add("The " + Pieces3x3.EDGE_NAMES[cubie] + " edge appears more than once.");
                return false;
            }
            used[cubie] = true;
            perm[position] = cubie;
            ori[position] = flip;
        }
        return true;
    }

    /** 0 for an even permutation, 1 for odd, counted by transpositions. */
    private static int parity(int[] permutation) {
        int swaps = 0;
        int[] work = permutation.clone();
        for (int i = 0; i < work.length; i++) {
            while (work[i] != i) {
                int target = work[i];
                int temp = work[target];
                work[target] = target;
                work[i] = temp;
                swaps++;
            }
        }
        return swaps % 2;
    }
}
