package com.randomjava.projects.fishercube;

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
 * Fisher Cube - A 3x3 turned 45 degrees about one axis, so edges and centres swap roles.
 */
public final class FisherCube extends CubeProject {

    public static final Meta META = new Meta(209, "fisher-cube", "Fisher Cube", "Shape Modifications", Kind.GRID,
            Difficulty.ADVANCED, "A 3x3 turned 45 degrees about one axis, so edges and centres swap roles.",
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
        return "Mechanically a 3x3 rotated 45 degrees about one axis, so what were edges behave as centres and the puzzle changes shape as you turn it.";
    }
}
