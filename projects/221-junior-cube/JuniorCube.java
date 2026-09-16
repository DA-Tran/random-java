package com.randomjava.projects.juniorcube;

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
 * Junior Cube - A 2x2 sold for children. Corners only, and every state within eleven turns.
 */
public final class JuniorCube extends CubeProject {

    public static final Meta META = new Meta(221, "junior-cube", "Junior Cube", "Shape Modifications", Kind.GRID,
            Difficulty.ADVANCED, "A 2x2 sold for children. Corners only, and every state within eleven turns.",
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
        return "A 2x2 by another name, so it is solved optimally like one.";
    }
}
