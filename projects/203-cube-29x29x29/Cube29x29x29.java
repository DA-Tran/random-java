package com.randomjava.projects.cube29x29x29;

import com.randomjava.cube.CubeProject;
import com.randomjava.cube.FaceletCube;
import com.randomjava.cube.Solution;
import com.randomjava.cube.Solver2x2Optimal;
import com.randomjava.cube.Solver3x3Beginner;
import com.randomjava.cube.Solver3x3Cfop;
import com.randomjava.cube.SolverNxNReduction;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;

import java.util.List;

/**
 * Cube 29x29x29 - Twenty-nine layers of patience.
 */
public final class Cube29x29x29 extends CubeProject {

    public static final Meta META = new Meta(203, "cube-29x29x29", "Cube 29x29x29", "Big Cubes", Kind.GRID,
            Difficulty.ADVANCED, "Twenty-nine layers of patience.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    @Override
    protected int size() {
        return 29;
    }

    @Override
    protected boolean hasSolver() {
        return true;
    }

    @Override
    protected List<String> methods() {
        return List.of(SolverNxNReduction.METHOD);
    }

    @Override
    protected Solution solvePuzzle(FaceletCube state, String scramble, String method) {
        return new SolverNxNReduction().solve(state, scramble);
    }

    @Override
    protected String note() {
        return "Reduction stage three is implemented: any cube that is already reduced, including one scrambled with outer turns only, solves here. Stages one and two, building the centres and pairing the edges, are not written, so a slice-scrambled cube is reported rather than attempted.";
    }
}
