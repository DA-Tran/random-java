package com.randomjava.projects.windmillmorphix;

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
 * Windmill Morphix - A windmill cube and a mastermorphix crossed, shifting shape twice over.
 */
public final class WindmillMorphix extends CubeProject {

    public static final Meta META = new Meta(220, "windmill-morphix", "Windmill Morphix", "Shape Modifications", Kind.GRID,
            Difficulty.ADVANCED, "A windmill cube and a mastermorphix crossed, shifting shape twice over.",
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
        return "A windmill cube and a mastermorphix crossed. Still 3x3 mechanics underneath.";
    }
}
