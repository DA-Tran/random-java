package com.randomjava.projects.cube33x33x33;

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
 * Cube 33x33x33 - Thirty-three layers. Gregoire Pfennig's build, over 6,000 pieces.
 */
public final class Cube33x33x33 extends CubeProject {

    public static final Meta META = new Meta(204, "cube-33x33x33", "Cube 33x33x33", "Big Cubes", Kind.GRID,
            Difficulty.ADVANCED, "Thirty-three layers. Gregoire Pfennig's build, over 6,000 pieces.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    @Override
    protected int size() {
        return 33;
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
