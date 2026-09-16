package com.randomjava.projects.pocketcube2x2;

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
 * Pocket Cube 2x2 - Solved in the fewest turns possible, by measuring the whole puzzle and walking downhill.
 */
public final class PocketCube2x2 extends CubeProject {

    public static final Meta META = new Meta(166, "pocket-cube-2x2", "Pocket Cube 2x2", "Twisty Puzzles", Kind.GRID,
            Difficulty.INTERMEDIATE, "Solved in the fewest turns possible, by measuring the whole puzzle and walking downhill.",
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
        return "";
    }
}
