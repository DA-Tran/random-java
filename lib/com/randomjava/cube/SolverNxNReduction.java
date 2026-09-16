package com.randomjava.cube;

import java.util.ArrayList;
import java.util.List;

/**
 * Reduction: the method every big cube is solved by.
 *
 * <p>A 4x4 or larger is not solved directly. It is <em>reduced</em> until it
 * behaves like a 3x3, in three stages:
 *
 * <ol>
 *   <li><b>Centres</b> - gather each face's inner block into one colour, so the
 *       block behaves as a single 3x3 centre.</li>
 *   <li><b>Edges</b> - pair the wings along each of the twelve edges, so each
 *       run behaves as a single 3x3 edge.</li>
 *   <li><b>Finish as a 3x3</b> - with centres and edges reduced, only outer
 *       turns are needed, and an ordinary 3x3 solver does the rest.</li>
 * </ol>
 *
 * <p><b>Stage three is implemented and verified here. Stages one and two are
 * not.</b> That means this solver handles any cube that is already reduced,
 * including one scrambled with outer turns only, at any size up to 100x100. A
 * cube scrambled with inner slices needs the centre and edge stages first, and
 * is reported as such rather than attempted.
 *
 * <p>Why the first two stages are not a search problem. Placing one centre is
 * easy; placing the sixth face's centres without disturbing the first five is
 * not, because every move that reaches an unsolved piece also passes through
 * solved ones. Real methods use <em>commutators</em> - sequences of the form
 * A B A' B' that move three pieces and leave everything else exactly as it was.
 * A naive search cannot find those at useful depth, which is why this stage is
 * written out rather than searched for.
 */
public final class SolverNxNReduction {

    public static final String METHOD = "Reduction (centres, edges, then 3x3)";

    private FaceletCube cube;
    private Solution.Builder out;

    // ------------------------------------------------------------------

    public Solution solve(FaceletCube start, String scramble) {
        int n = start.size();
        if (n < 4) {
            throw new IllegalArgumentException(
                    "Reduction is for 4x4 and larger. Smaller cubes have their own solvers.");
        }
        cube = start.copy();
        out = new Solution.Builder(METHOD, scramble, n);

        if (!centresReduced(cube)) {
            throw new IllegalStateException(
                    "The centres are not built yet. Stage one of reduction, gathering each "
                            + "face's inner block into one colour, is not written.");
        }
        if (!edgesReduced(cube)) {
            throw new IllegalStateException(
                    "The edges are not paired yet. Stage two of reduction, joining the wings "
                            + "along each edge, is not written.");
        }

        finishAsThreeByThree();

        if (!cube.isSolved()) {
            throw new IllegalStateException("Reduction finished without solving the cube.");
        }
        return out.build();
    }

    // ------------------------------------------------------------------
    // Recognising a reduced cube
    // ------------------------------------------------------------------

    /** True when every face's inner block is a single colour. */
    public static boolean centresReduced(FaceletCube cube) {
        int n = cube.size();
        for (int face = 0; face < 6; face++) {
            char first = cube.get(face, 1, 1);
            for (int row = 1; row < n - 1; row++) {
                for (int col = 1; col < n - 1; col++) {
                    if (cube.get(face, row, col) != first) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * True when every edge's wings match along its whole length, on both of the
     * two faces the edge lies on.
     */
    public static boolean edgesReduced(FaceletCube cube) {
        int n = cube.size();
        if (n < 4) {
            return true;
        }
        for (int face = 0; face < 6; face++) {
            // Top and bottom rows, and left and right columns, excluding corners.
            for (int i = 1; i < n - 1; i++) {
                if (cube.get(face, 0, i) != cube.get(face, 0, 1)) {
                    return false;
                }
                if (cube.get(face, n - 1, i) != cube.get(face, n - 1, 1)) {
                    return false;
                }
                if (cube.get(face, i, 0) != cube.get(face, 1, 0)) {
                    return false;
                }
                if (cube.get(face, i, n - 1) != cube.get(face, 1, n - 1)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** True when the cube can be finished by stage three alone. */
    public static boolean isReduced(FaceletCube cube) {
        return centresReduced(cube) && edgesReduced(cube);
    }

    // ------------------------------------------------------------------
    // Stage three
    // ------------------------------------------------------------------

    /**
     * Collapses the reduced cube onto a 3x3, solves that, and replays the
     * solution here as outer turns.
     *
     * <p>Outer turns are the only ones used, and on a reduced cube an outer turn
     * moves whole centre blocks and whole edge runs together, so reduction
     * survives the stage that depends on it.
     */
    private void finishAsThreeByThree() {
        FaceletCube small = collapse(cube);

        if (!CubeValidator.check(small).valid()) {
            throw new IllegalStateException(
                    "The reduced cube is not a legal 3x3, which on an even cube means parity: "
                            + "a single flipped edge or a single swapped pair that cannot occur "
                            + "on a real 3x3. The parity algorithms are part of stages one and "
                            + "two and are not written.");
        }

        Solution inner = new Solver3x3Beginner().solve(small, "");
        for (Solution.Step step : inner.steps()) {
            List<Move> translated = translate(Move.parse(step.moves(), 3));
            cube.apply(translated);
            out.add(step.stage(),
                    step.explanation() + " On a big cube this is an outer turn, which moves "
                            + "whole centre blocks and whole edge runs at once.",
                    translated, step.algorithm(), cube);
        }
    }

    /**
     * Builds the 3x3 this reduced cube stands for: corners from the corners,
     * edges from any one wing, centres from the inner block.
     */
    public static FaceletCube collapse(FaceletCube big) {
        int n = big.size();
        FaceletCube small = new FaceletCube(3);
        for (int face = 0; face < 6; face++) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int bigRow = row == 0 ? 0 : row == 2 ? n - 1 : 1;
                    int bigCol = col == 0 ? 0 : col == 2 ? n - 1 : 1;
                    small.set(face, row, col, big.get(face, bigRow, bigCol));
                }
            }
        }
        return small;
    }

    /** Turns a 3x3 solution into outer turns of the big cube. */
    private static List<Move> translate(List<Move> moves) {
        List<Move> out = new ArrayList<>(moves.size());
        for (Move move : moves) {
            if (move.from() != 0 || move.layers() != 1) {
                throw new IllegalStateException(
                        "Only outer turns can be replayed onto a reduced cube, but the 3x3 "
                                + "solution used " + move + ".");
            }
            out.add(new Move(move.face(), 1, 0, move.amount()));
        }
        return out;
    }
}
