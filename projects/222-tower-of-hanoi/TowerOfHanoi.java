package com.randomjava.projects.towerofhanoi;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Tower of Hanoi, solved optimally and explained.
 *
 * <p>The recursion is the whole puzzle: to move n discs, move n-1 out of the
 * way, move the largest one across, then move the n-1 back on top. That gives
 * exactly 2^n - 1 moves, and no shorter solution exists, because the largest
 * disc must move at least once and everything above it must be cleared off
 * first and put back after.
 *
 * <p>The move count doubles with every disc added, which is why 64 discs is the
 * version in the legend: at one move a second it would take longer than the age
 * of the universe.
 */
public final class TowerOfHanoi implements Project {

    public static final Meta META = new Meta(222, "tower-of-hanoi", "Tower Of Hanoi", "Logic and Puzzle Games", Kind.GRID,
            Difficulty.BEGINNER, "Move a stack of discs between three pegs, never putting a larger disc on a smaller.",
            "", true);

    /** Beyond this the move list is longer than anything useful to display. */
    private static final int MAX_DISCS = 20;

    private int discs = 4;
    private final List<int[]> moves = new ArrayList<>();
    /**
     * Three pegs, each a stack with the smallest disc on top.
     *
     * <p>A List rather than an array. Java cannot create a generic array, so
     * the array version has to build a raw {@code Deque[]} and suppress the
     * warning - which also silences any genuine type error in the same method.
     */
    private final List<Deque<Integer>> pegs = newPegs();
    private int played;

    private static List<Deque<Integer>> newPegs() {
        return List.of(new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>());
    }

    public TowerOfHanoi() {
        reset(4);
    }

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // Core
    // ------------------------------------------------------------------

    public void reset(int count) {
        discs = Math.max(1, Math.min(MAX_DISCS, count));
        for (Deque<Integer> peg : pegs) {
            peg.clear();
        }
        for (int disc = discs; disc >= 1; disc--) {
            pegs.get(0).push(disc);
        }
        moves.clear();
        plan(discs, 0, 2, 1);
        played = 0;
    }

    /** Builds the optimal move list. Each entry is {from, to}. */
    private void plan(int count, int from, int to, int spare) {
        if (count == 0) {
            return;
        }
        plan(count - 1, from, spare, to);
        moves.add(new int[]{from, to});
        plan(count - 1, spare, to, from);
    }

    /** The shortest possible number of moves: 2^n - 1. */
    public long optimalMoves() {
        return (1L << discs) - 1;
    }

    public boolean step() {
        if (played >= moves.size()) {
            return false;
        }
        int[] move = moves.get(played++);
        pegs.get(move[1]).push(pegs.get(move[0]).pop());
        return true;
    }

    public void runToEnd() {
        while (step()) {
            // keep going
        }
    }

    public boolean isSolved() {
        return pegs.get(2).size() == discs;
    }

    /** Moves a disc by hand, refusing anything the rules forbid. */
    public void moveByHand(int from, int to) {
        if (from < 0 || from > 2 || to < 0 || to > 2) {
            throw new IllegalArgumentException("Pegs are numbered 1, 2 and 3.");
        }
        if (pegs.get(from).isEmpty()) {
            throw new IllegalArgumentException("Peg " + (from + 1) + " is empty.");
        }
        if (!pegs.get(to).isEmpty() && pegs.get(to).peek() < pegs.get(from).peek()) {
            throw new IllegalArgumentException(
                    "You cannot put disc " + pegs.get(from).peek() + " on the smaller disc "
                            + pegs.get(to).peek() + ".");
        }
        pegs.get(to).push(pegs.get(from).pop());
    }

    /** The three pegs drawn as a grid, widest disc at the bottom. */
    public char[][] render() {
        int width = discs * 2 + 1;
        char[][] grid = new char[discs][width * 3];
        for (char[] row : grid) {
            java.util.Arrays.fill(row, ' ');
        }
        for (int peg = 0; peg < 3; peg++) {
            List<Integer> stack = new ArrayList<>(pegs.get(peg));
            java.util.Collections.reverse(stack);
            for (int level = 0; level < stack.size(); level++) {
                int disc = stack.get(level);
                int row = discs - 1 - level;
                int centre = peg * width + discs;
                for (int x = centre - disc + 1; x <= centre + disc - 1; x++) {
                    grid[row][x] = '#';
                }
            }
        }
        return grid;
    }

    public String status() {
        return discs + " discs  |  move " + played + " of " + moves.size()
                + "  |  shortest possible is " + optimalMoves()
                + (isSolved() ? "  |  solved" : "");
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        reset(io.askInt("how many discs:", 1, MAX_DISCS, 4));
        while (true) {
            io.println();
            io.grid(render());
            io.muted(status());
            int choice = io.menu("Tower of Hanoi", List.of(
                    "Step once", "Run to the end", "Move a disc myself", "Start again"));
            if (choice < 0) {
                return;
            }
            switch (choice) {
                case 0 -> {
                    if (!step()) {
                        io.warn("Already finished.");
                    }
                }
                case 1 -> {
                    runToEnd();
                    io.ok("Solved in " + moves.size() + " moves, which is the fewest possible.");
                }
                case 2 -> {
                    try {
                        moveByHand(io.askInt("from peg:", 1, 3, 1) - 1,
                                io.askInt("to peg:", 1, 3, 3) - 1);
                    } catch (RuntimeException e) {
                        io.error(e.getMessage());
                    }
                }
                default -> reset(io.askInt("how many discs:", 1, MAX_DISCS, discs));
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate", "reset" -> reset(Json.integer(body, "size", 4));
                case "step" -> step();
                case "solve", "run" -> runToEnd();
                case "move" -> moveByHand(Json.integer(body, "from", 1) - 1,
                        Json.integer(body, "to", 3) - 1);
                case "state" -> {
                    // fall through
                }
                default -> {
                    return Json.error("Unknown action: " + action);
                }
            }
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
        return Json.ok("board", Json.grid(render()),
                "result", isSolved() ? "Solved in " + moves.size() + " moves"
                        : discs + " discs need " + optimalMoves() + " moves",
                "detail", status() + "\nEach extra disc doubles the work, which is why the "
                        + "64-disc version in the legend would outlast the universe.");
    }
}
