package com.randomjava.projects.mirrorcube;

import com.randomjava.cube.CubeProject;
import com.randomjava.cube.FaceletCube;
import com.randomjava.cube.Solution;
import com.randomjava.cube.Solver2x2Optimal;
import com.randomjava.cube.Solver3x3Beginner;
import com.randomjava.cube.Solver3x3Cfop;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;

import java.util.List;

/**
 * Mirror Cube - A 3x3 in one colour where the pieces are different sizes, so shape is the only clue.
 */
public final class MirrorCube extends CubeProject {

    public static final Meta META = new Meta(172, "mirror-cube", "Mirror Cube", "Twisty Puzzles", Kind.GRID,
            Difficulty.ADVANCED, "A 3x3 in one colour where the pieces are different sizes, so shape is the only clue.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    @Override
    protected int size() {
        return 3;
    }

    @Override
    protected boolean hasSolver() {
        return true;
    }

    /** Two real methods: the one people learn, and the one people race with. */
    @Override
    protected List<String> methods() {
        return List.of(Solver3x3Beginner.METHOD, Solver3x3Cfop.METHOD);
    }

    @Override
    protected Solution solvePuzzle(FaceletCube state, String scramble, String method) {
        if (Solver3x3Cfop.METHOD.equals(method)) {
            return new Solver3x3Cfop().solve(state, scramble);
        }
        return new Solver3x3Beginner().solve(state, scramble);
    }

    @Override
    protected String note() {
        return "Mechanically a 3x3. On the real puzzle every piece is a different size and it is all one colour, so you solve by shape rather than colour.";
    }
}
