package com.randomjava.projects.cube1x1x1;

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
 * Cube 1x1x1 - A single cubie with nothing to turn, so it is solved the moment you pick it up.
 */
public final class Cube1x1x1 extends CubeProject {

    public static final Meta META = new Meta(186, "cube-1x1x1", "Cube 1x1x1", "Big Cubes", Kind.GRID,
            Difficulty.BEGINNER, "A single cubie with nothing to turn, so it is solved the moment you pick it up.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    @Override
    protected int size() {
        return 1;
    }

    @Override
    protected boolean hasSolver() {
        return true;
    }

    @Override
    protected List<String> methods() {
        return List.of("Nothing to do");
    }

    @Override
    protected Solution solvePuzzle(FaceletCube state, String scramble, String method) {
        return new Solution.Builder("Nothing to do", scramble, 1).build();
    }

    @Override
    protected String note() {
        return "";
    }
}
