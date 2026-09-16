package com.randomjava.cube;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Checks the move engine against facts that are true of a real cube, so a
 * direction or orientation mistake cannot hide.
 *
 * <p>The group-order checks are the sharp ones. The order of {@code R U} on a
 * 3x3 is exactly 105: if any part of either turn is wrong the cycle length
 * changes, so this single test catches almost every plausible error.
 *
 * <p>Run it with:
 * <pre>java -cp build/classes com.randomjava.cube.CubeSelfTest</pre>
 */
public final class CubeSelfTest {

    private int passed;
    private final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        CubeSelfTest test = new CubeSelfTest();
        test.runAll();
        System.out.println();
        System.out.println(test.passed + " checks passed, " + test.failures.size() + " failed");
        for (String failure : test.failures) {
            System.out.println("  FAIL " + failure);
        }
        System.exit(test.failures.isEmpty() ? 0 : 1);
    }

    /** How many checks passed on the last run. */
    public int passed() {
        return passed;
    }

    /** Anything that failed on the last run, empty when the engine is sound. */
    public List<String> failures() {
        return failures;
    }

    public void runAll() {
        quarterTurnsReturn();
        stickerDestinations();
        groupOrders();
        scrambleThenInvert();
        sliceAndWideConsistency();
        bigCubeSanity();
    }

    private void check(String what, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failures.add(what);
        }
    }

    private void checkOrder(String notation, int size, int expected) {
        FaceletCube cube = new FaceletCube(size);
        List<Move> moves = Move.parse(notation, size);
        int order = 0;
        do {
            cube.apply(moves);
            order++;
            if (order > 5000) {
                break;
            }
        } while (!cube.isSolved());
        check("order of (" + notation + ") on " + size + "x" + size
                + " should be " + expected + " but was " + order, order == expected);
    }

    // ------------------------------------------------------------------

    /** Any quarter turn done four times must return the cube to where it started. */
    private void quarterTurnsReturn() {
        for (int size = 2; size <= 7; size++) {
            for (char face : new char[]{'U', 'R', 'F', 'D', 'L', 'B'}) {
                for (int layers = 1; layers <= size; layers++) {
                    FaceletCube cube = new FaceletCube(size);
                    Move move = new Move(face, layers, 0, 1);
                    for (int i = 0; i < 4; i++) {
                        cube.apply(move);
                    }
                    check(size + "x" + size + " " + move + " four times returns to solved",
                            cube.isSolved());
                }
            }
        }
    }

    /** Spot-checks that stickers land on the right faces, which pins the direction. */
    private void stickerDestinations() {
        FaceletCube afterU = new FaceletCube(3).apply("U");
        check("U brings R to the front", afterU.get(FaceletCube.F, 0, 0) == 'R');
        check("U brings B to the right", afterU.get(FaceletCube.R, 0, 0) == 'B');
        check("U brings L to the back", afterU.get(FaceletCube.B, 0, 0) == 'L');
        check("U brings F to the left", afterU.get(FaceletCube.L, 0, 0) == 'F');

        FaceletCube afterR = new FaceletCube(3).apply("R");
        check("R brings F up to U", afterR.get(FaceletCube.U, 0, 2) == 'F');
        check("R brings U back to B", afterR.get(FaceletCube.B, 2, 0) == 'U');
        check("R brings B down to D", afterR.get(FaceletCube.D, 0, 2) == 'B');
        check("R brings D to the front", afterR.get(FaceletCube.F, 0, 2) == 'D');

        FaceletCube afterF = new FaceletCube(3).apply("F");
        check("F brings L up to U", afterF.get(FaceletCube.U, 2, 0) == 'L');
        check("F brings U to the right", afterF.get(FaceletCube.R, 0, 0) == 'U');
        check("F brings R down to D", afterF.get(FaceletCube.D, 0, 0) == 'R');
        check("F brings D to the left", afterF.get(FaceletCube.L, 0, 2) == 'D');

        // A whole-cube rotation must leave every face a solid colour.
        FaceletCube rotated = new FaceletCube(3).apply("x y z");
        boolean uniform = true;
        for (int face = 0; face < 6; face++) {
            char first = rotated.get(face, 0, 0);
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    uniform &= rotated.get(face, row, col) == first;
                }
            }
        }
        check("x y z leaves every face solid", uniform);
    }

    /** Known cycle lengths in the cube group. */
    private void groupOrders() {
        checkOrder("R", 3, 4);
        checkOrder("R U", 3, 105);
        checkOrder("R U R' U'", 3, 6);
        checkOrder("R U R' U R U2 R'", 3, 6);
        checkOrder("R U2 D' B D'", 3, 1260);
        checkOrder("F R", 3, 105);
        checkOrder("M2 U M2 U2 M2 U M2", 3, 2);
        checkOrder("M2 E2 S2", 3, 2);
        checkOrder("R U R' F' R U R' U' R' F R2 U' R' U'", 3, 2);
        checkOrder("R U", 2, 15);
    }

    /** Applying a scramble and then its inverse must always come back to solved. */
    private void scrambleThenInvert() {
        Random random = new Random(20260914L);
        for (int size = 2; size <= 7; size++) {
            for (int trial = 0; trial < 20; trial++) {
                FaceletCube cube = new FaceletCube(size);
                List<Move> scramble = cube.scramble(random);
                cube.apply(Move.invert(scramble));
                check(size + "x" + size + " scramble then inverse returns to solved",
                        cube.isSolved());
            }
        }
    }

    /** A wide turn is an outer turn plus the slices under it. */
    private void sliceAndWideConsistency() {
        FaceletCube wide = new FaceletCube(3).apply("Rw");
        FaceletCube parts = new FaceletCube(3).apply("R M'");
        check("Rw equals R M' on a 3x3", wide.toFacelets().equals(parts.toFacelets()));

        FaceletCube rotation = new FaceletCube(3).apply("x");
        FaceletCube built = new FaceletCube(3).apply("R M' L'");
        check("x equals R M' L' on a 3x3", rotation.toFacelets().equals(built.toFacelets()));

        FaceletCube lowercase = new FaceletCube(4).apply("r u f");
        FaceletCube widened = new FaceletCube(4).apply("Rw Uw Fw");
        check("lowercase letters mean wide turns",
                lowercase.toFacelets().equals(widened.toFacelets()));
    }

    /** Big cubes keep their sticker counts and inner layers behave. */
    private void bigCubeSanity() {
        for (int size = 2; size <= 7; size++) {
            FaceletCube cube = new FaceletCube(size);
            cube.scramble(new Random(7L), 60);
            int[] counts = new int[6];
            for (char sticker : cube.toFacelets().toCharArray()) {
                counts["URFDLB".indexOf(sticker)]++;
            }
            boolean balanced = true;
            for (int count : counts) {
                balanced &= count == size * size;
            }
            check(size + "x" + size + " keeps " + (size * size) + " of each colour after scrambling",
                    balanced);
        }

        // An inner slice on a 5x5 must not disturb the outer faces' corners.
        FaceletCube five = new FaceletCube(5).apply(new Move('R', 1, 2, 1));
        check("a 5x5 inner slice leaves the R face untouched",
                five.get(FaceletCube.R, 0, 0) == 'R' && five.get(FaceletCube.R, 4, 4) == 'R');
    }
}
