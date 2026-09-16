package com.randomjava.projects.pyramorphix;

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
 * Pyramorphix - A 2x2 in a tetrahedron shell, so it deforms as you turn it.
 */
public final class Pyramorphix extends CubeProject {

    public static final Meta META = new Meta(179, "pyramorphix", "Pyramorphix", "Twisty Puzzles", Kind.GRID,
            Difficulty.INTERMEDIATE, "A 2x2 in a tetrahedron shell, so it deforms as you turn it.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    @Override
    protected int size() {
        return 2;
    }

    @Override
    protected boolean hasSolver() {
        return true;
    }

    @Override
    protected List<String> methods() {
        return List.of(Solver2x2Optimal.METHOD);
    }

    @Override
    protected Solution solvePuzzle(FaceletCube state, String scramble, String method) {
        return new Solver2x2Optimal().solve(state, scramble);
    }

    @Override
    protected String note() {
        return "Mechanically a 2x2 in a tetrahedron shell, so the optimal 2x2 solver applies. The real puzzle changes shape as you turn it.";
    }
}
