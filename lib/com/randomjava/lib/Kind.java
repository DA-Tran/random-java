package com.randomjava.lib;

/**
 * The shape of a project, which decides both the scaffold it was generated from
 * and how the launcher groups it.
 */
public enum Kind {
    /** Inputs in, computed answer out. Calculators, converters, checkers. */
    TOOL("Tool"),
    /** A managed collection of records. To-do lists, trackers, budgets. */
    LIST("List"),
    /** Round or turn based play with a score. */
    GAME("Game"),
    /** A 2D board that is rendered and stepped. Puzzles, mazes, visualisers. */
    GRID("Grid");

    private final String label;

    Kind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
