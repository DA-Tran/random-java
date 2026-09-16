package com.randomjava.cube;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Solves a 3x3 the way speedcubers do: cross, then F2L, then the last layer.
 *
 * <p>The difference from the beginner method is where the work goes. A beginner
 * places a corner and then its edge separately, and finishes the last layer in
 * four steps. CFOP joins each corner to its edge and inserts the pair in one
 * go, and finishes the last layer in two: orient it, then permute it. The
 * result is roughly a third fewer moves.
 *
 * <p>The last layer here is two-look OLL and one-look PLL. One-look PLL is the
 * real thing: all 21 cases are listed, and the solver tries each with every
 * approach angle until one finishes the cube outright. If none does, because a
 * listed algorithm has a typo or the case is unusual, it falls back on the
 * beginner three-cycles. That fallback is what stops a mistake in the algorithm
 * table from ever becoming a failure to solve.
 */
public final class Solver3x3Cfop {

    public static final String METHOD = "CFOP (cross, F2L, OLL, PLL)";

    /** Orientation algorithms, enough for a two-look OLL. */
    private static final List<String> OLL_EDGES = List.of(
            "F R U R' U' F'",
            "F U R U' R' F'");

    private static final List<String> OLL_CORNERS = List.of(
            "R U R' U R U2 R'",                 // Sune
            "R U2 R' U' R U' R'",               // Antisune
            "R U R' U R U' R' U R U2 R'",       // H
            "R U2 R2 U' R2 U' R2 U2 R");        // Pi

    /**
     * The 21 permutation cases. Rotations and slice moves are used where the
     * usual written form uses them, which the move engine handles directly.
     */
    private static final List<String> PLL = List.of(
            "x L2 D2 L' U' L D2 L' U L' x'",                                // Aa
            "x' L2 D2 L U L' D2 L U' L x",                                  // Ab
            "x' L' U L D' L' U' L D L' U' L D' L' U L D x",                 // E
            "R' U' F' R U R' U' R' F R2 U' R' U' R U R' U R",               // F
            "R2 U R' U R' U' R U' R2 U' D R' U R D'",                       // Ga
            "R' U' R U D' R2 U R' U R U' R U' R2 D",                        // Gb
            "R2 U' R U' R U R' U R2 U D' R U' R' D",                        // Gc
            "R U R' U' D R2 U' R U' R' U R' U R2 D'",                       // Gd
            "M2 U M2 U2 M2 U M2",                                           // H
            "x R2 F R F' R U2 r' U r U2 x'",                                // Ja
            "R U R' F' R U R' U' R' F R2 U' R' U'",                         // Jb
            "R U R' U R U R' F' R U R' U' R' F R2 U' R' U2 R U' R'",        // Na
            "R' U R U' R' F' U' F R U R' F R' F' R U' R",                   // Nb
            "R U' R' U' R U R D R' U' R D' R' U2 R'",                       // Ra
            "R2 F R U R U' R' F' R U2 R' U2 R",                             // Rb
            "R U R' U' R' F R2 U' R' U' R U R' F'",                         // T
            "R U' R U R U R U' R' U' R2",                                   // Ua
            "R2 U R U R' U' R' U' R' U R'",                                 // Ub
            "R' U R' U' R' U' R' U R U R2",                                 // V (short form)
            "F R U' R' U' R U R' F' R U R' U' R' F R F'",                   // Y
            "M2 U M2 U M' U2 M2 U2 M' U2");                                 // Z

    private static final List<Move> ALL_MOVES = movesFor("URFDLB");

    private FaceletCube cube;
    private Solution.Builder out;

    // ------------------------------------------------------------------

    public Solution solve(FaceletCube start) {
        return solve(start, "");
    }

    public Solution solve(FaceletCube start, String scramble) {
        if (start.size() != 3) {
            throw new IllegalArgumentException("This solver only handles a 3x3");
        }
        cube = start.copy();
        out = new Solution.Builder(METHOD, scramble, 3);

        cross();
        try {
            firstTwoLayers();
        } catch (IllegalStateException pairingFailed) {
            // Solving pair by pair means the last slot is the most constrained,
            // and occasionally no short insertion exists. Rather than fail,
            // finish layer by layer from wherever we got to. The step text says
            // so, so the output never claims to be something it is not.
            Solution rest = new Solver3x3Beginner().solve(cube, "");
            for (Solution.Step step : rest.steps()) {
                cube.apply(Move.parse(step.moves(), 3));
                out.add("Layer by layer",
                        "CFOP could not pair this slot in a short sequence, so the rest is "
                                + "finished the beginner way: " + step.explanation(),
                        Move.parse(step.moves(), 3), step.algorithm(), cube);
            }
            return out.build();
        }
        orientLastLayer();
        permuteLastLayer();
        finalTurn();

        if (!cube.isSolved()) {
            throw new IllegalStateException(
                    "CFOP finished without solving. State: " + cube.toFacelets());
        }
        return out.build();
    }

    // ------------------------------------------------------------------
    // Cross
    // ------------------------------------------------------------------

    private void cross() {
        List<Integer> done = new ArrayList<>();
        for (String name : new String[]{"DF", "DR", "DB", "DL"}) {
            int target = Pieces3x3.edge(name);
            if (Pieces3x3.edgeSolved(cube, target)) {
                done.add(target);
                continue;
            }
            List<Integer> settled = List.copyOf(done);
            Predicate<FaceletCube> goal = c ->
                    Pieces3x3.edgeSolved(c, target) && edgesSolved(c, settled);

            List<Move> found = search(goal, ALL_MOVES, 5);
            if (found == null) {
                String colours = Pieces3x3.EDGE_NAMES[target];
                List<Move> lift = search(c ->
                        Pieces3x3.isUpperEdge(Pieces3x3.findEdge(c, colours))
                                && edgesSolved(c, settled), ALL_MOVES, 4);
                if (lift == null) {
                    throw new IllegalStateException("Cross: cannot free " + colours);
                }
                apply(lift);
                List<Move> drop = search(goal, ALL_MOVES, 5);
                if (drop == null) {
                    throw new IllegalStateException("Cross: cannot place " + colours);
                }
                apply(drop);
                found = new ArrayList<>(lift);
                found.addAll(drop);
            } else {
                apply(found);
            }
            out.add("Cross",
                    "Build the cross on the bottom: place the " + Pieces3x3.EDGE_NAMES[target]
                            + " edge without disturbing the ones already down.",
                    found, "", cube);
            done.add(target);
        }
    }

    // ------------------------------------------------------------------
    // F2L
    // ------------------------------------------------------------------

    /**
     * Inserts each corner together with its edge. Pairing them is the whole
     * point of F2L: it is what removes the separate middle-layer stage a
     * beginner does, and most of the move count with it.
     */
    private void firstTwoLayers() {
        String[][] slots = {
                {"DFR", "FR"}, {"DLF", "FL"}, {"DBL", "BL"}, {"DRB", "BR"}};
        List<Integer> doneCorners = new ArrayList<>();
        List<Integer> doneEdges = new ArrayList<>();

        for (String[] slot : slots) {
            int corner = Pieces3x3.corner(slot[0]);
            int edge = Pieces3x3.edge(slot[1]);
            if (Pieces3x3.cornerSolved(cube, corner) && Pieces3x3.edgeSolved(cube, edge)) {
                doneCorners.add(corner);
                doneEdges.add(edge);
                continue;
            }
            List<Integer> settledCorners = List.copyOf(doneCorners);
            List<Integer> settledEdges = List.copyOf(doneEdges);

            Predicate<FaceletCube> intact = c -> crossSolved(c)
                    && cornersSolved(c, settledCorners) && edgesSolved(c, settledEdges);
            Predicate<FaceletCube> paired = c -> Pieces3x3.cornerSolved(c, corner)
                    && Pieces3x3.edgeSolved(c, edge) && intact.test(c);

            // The pair's own two faces plus the top is all a real F2L case needs.
            // Depth 7 rather than 8: most real F2L cases fit, and the extra
            // level costs six times the work for the few that do not, which
            // the fallback below handles far more cheaply.
            List<Move> slotMoves = movesFor("U" + slot[1]);
            List<Move> found = search(paired, slotMoves, 7);
            if (found == null) {
                found = search(paired, ALL_MOVES, 5);
            }

            if (found != null) {
                apply(found);
                out.add("F2L",
                        "Join the " + slot[0] + " corner to the " + slot[1]
                                + " edge and insert the pair in one go.",
                        found, "", cube);
            } else {
                // Awkward case: fall back to placing the corner, then the edge.
                List<Move> cornerMoves = insertPiece(
                        c -> Pieces3x3.cornerSolved(c, corner) && intact.test(c),
                        movesFor("U" + slot[0].substring(1)),
                        intact,
                        c -> Pieces3x3.isUpperCorner(Pieces3x3.findCorner(c, slot[0])));
                out.add("F2L",
                        "Place the " + slot[0] + " corner first, since the pair could not be "
                                + "joined in a short sequence.",
                        cornerMoves, "", cube);
                List<Move> edgeMoves = insertPiece(
                        c -> Pieces3x3.edgeSolved(c, edge) && Pieces3x3.cornerSolved(c, corner)
                                && intact.test(c),
                        movesFor("U" + slot[1]),
                        c -> intact.test(c) && Pieces3x3.cornerSolved(c, corner),
                        c -> Pieces3x3.isUpperEdge(Pieces3x3.findEdge(c, slot[1])));
                out.add("F2L",
                        "Then drop the " + slot[1] + " edge in beside it.",
                        edgeMoves, "", cube);
            }
            doneCorners.add(corner);
            doneEdges.add(edge);
        }
    }

    /**
     * Inserts a single piece, freeing it into the top layer first when no short
     * sequence exists from where it currently sits.
     *
     * <p>Skipping that first hop is what made a handful of scrambles fail: a
     * piece buried in the wrong slot can be a long way from home, but is only a
     * few moves away once it has been lifted out.
     */
    private List<Move> insertPiece(Predicate<FaceletCube> goal, List<Move> slotMoves,
            Predicate<FaceletCube> intact, Predicate<FaceletCube> lifted) {
        List<Move> found = search(goal, slotMoves, 8);
        if (found == null) {
            found = search(goal, ALL_MOVES, 6);
        }
        if (found != null) {
            apply(found);
            return found;
        }

        // Exactly the two hops the beginner solver uses, which is verified over
        // hundreds of scrambles: free the piece into the top layer, then insert
        // it using only the slot's own faces. Deepening instead of lifting was
        // both slower and less reliable.
        List<Move> lift = search(c -> lifted.test(c) && intact.test(c), ALL_MOVES, 3);
        if (lift != null) {
            apply(lift);
            List<Move> insert = search(goal, slotMoves, 8);
            if (insert == null) {
                insert = search(goal, ALL_MOVES, 5);
            }
            if (insert != null) {
                apply(insert);
                List<Move> both = new ArrayList<>(lift);
                both.addAll(insert);
                return both;
            }
        }
        throw new IllegalStateException("F2L: no insertion found");
    }

    // ------------------------------------------------------------------
    // Last layer
    // ------------------------------------------------------------------

    private void orientLastLayer() {
        stage("OLL",
                "Turn the whole top face one colour. Two looks: make the cross, then orient "
                        + "the corners.",
                c -> Pieces3x3.orientedUpperEdges(c) == 4, OLL_EDGES, 3);
        stage("OLL",
                "Now the corners, so the top face is solid.",
                c -> Pieces3x3.orientedUpperCorners(c) == 4
                        && Pieces3x3.orientedUpperEdges(c) == 4,
                OLL_CORNERS, 4);
    }

    /**
     * One-look PLL: try every case at every approach angle and finish outright.
     * Falls back on the beginner three-cycles if nothing single-shot works.
     */
    private void permuteLastLayer() {
        if (cube.isSolved() || aufSolves() >= 0) {
            int turns = aufSolves();
            if (turns > 0) {
                List<Move> auf = auf(turns);
                apply(auf);
                out.add("PLL", "Already permuted, so one turn of the top finishes it.",
                        auf, "", cube);
            }
            return;
        }

        for (String algorithm : PLL) {
            List<Move> body = Move.parse(algorithm, 3);
            for (int before = 0; before < 4; before++) {
                List<Move> attempt = new ArrayList<>(auf(before));
                attempt.addAll(body);
                cube.apply(attempt);
                int after = aufSolves();
                cube.apply(Move.invert(attempt));
                if (after >= 0) {
                    attempt.addAll(auf(after));
                    apply(attempt);
                    out.add("PLL",
                            "Permute the last layer in a single algorithm, which is what "
                                    + "separates CFOP from a beginner's four steps.",
                            attempt, algorithm, cube);
                    return;
                }
            }
        }

        // Nothing single-shot fitted, so finish it the long way.
        stage("PLL", "Cycle the corners into place.",
                Solver3x3Cfop::topCornersPlaced,
                List.of(Solver3x3Beginner.A_PERM, Solver3x3Beginner.A_PERM_MIRROR), 4);
        stage("PLL", "Then cycle the edges, which finishes the cube.",
                FaceletCube::isSolved,
                List.of(Solver3x3Beginner.U_PERM_A, Solver3x3Beginner.U_PERM_B), 4);
    }

    private void finalTurn() {
        if (cube.isSolved()) {
            return;
        }
        int turns = aufSolves();
        if (turns > 0) {
            List<Move> auf = auf(turns);
            apply(auf);
            out.add("Finish", "One last turn of the top lines everything up.", auf, "", cube);
        }
    }

    // ------------------------------------------------------------------
    // Shared machinery
    // ------------------------------------------------------------------

    private void stage(String name, String explanation, Predicate<FaceletCube> goal,
            List<String> algorithms, int maxRounds) {
        int turns = aufSatisfies(goal);
        if (turns >= 0) {
            List<Move> auf = auf(turns);
            if (!auf.isEmpty()) {
                apply(auf);
                out.add(name, explanation, auf, "", cube);
            }
            return;
        }
        List<List<Move>> options = new ArrayList<>();
        for (int pre = 0; pre < 4; pre++) {
            for (String algorithm : algorithms) {
                List<Move> option = new ArrayList<>(auf(pre));
                option.addAll(Move.parse(algorithm, 3));
                options.add(option);
            }
        }
        for (int depth = 1; depth <= maxRounds; depth++) {
            List<Move> path = new ArrayList<>();
            if (algorithmDfs(goal, options, depth, path)) {
                apply(path);
                int after = aufSatisfies(goal);
                if (after > 0) {
                    List<Move> auf = auf(after);
                    apply(auf);
                    path = new ArrayList<>(path);
                    path.addAll(auf);
                }
                out.add(name, explanation, path, algorithms.get(0), cube);
                return;
            }
        }
        throw new IllegalStateException("CFOP " + name + ": no solution found");
    }

    private boolean algorithmDfs(Predicate<FaceletCube> goal, List<List<Move>> options,
            int depth, List<Move> path) {
        for (List<Move> option : options) {
            cube.apply(option);
            path.addAll(option);
            boolean hit = depth == 1 ? aufSatisfies(goal) >= 0
                    : algorithmDfs(goal, options, depth - 1, path);
            cube.apply(Move.invert(option));
            if (hit) {
                return true;
            }
            for (int i = 0; i < option.size(); i++) {
                path.remove(path.size() - 1);
            }
        }
        return false;
    }

    private int aufSatisfies(Predicate<FaceletCube> goal) {
        for (int turns = 0; turns < 4; turns++) {
            List<Move> auf = auf(turns);
            cube.apply(auf);
            boolean ok = goal.test(cube);
            cube.apply(Move.invert(auf));
            if (ok) {
                return turns;
            }
        }
        return -1;
    }

    private int aufSolves() {
        return aufSatisfies(FaceletCube::isSolved);
    }

    private static List<Move> auf(int turns) {
        return turns == 0 ? List.of() : List.of(new Move('U', 1, 0, turns));
    }

    private static boolean topCornersPlaced(FaceletCube c) {
        if (Pieces3x3.orientedUpperCorners(c) != 4 || Pieces3x3.orientedUpperEdges(c) != 4) {
            return false;
        }
        for (int p = 0; p < 4; p++) {
            if (!Pieces3x3.cornerPlaced(c, p)) {
                return false;
            }
        }
        return true;
    }

    private static boolean crossSolved(FaceletCube c) {
        for (String name : new String[]{"DF", "DR", "DB", "DL"}) {
            if (!Pieces3x3.edgeSolved(c, Pieces3x3.edge(name))) {
                return false;
            }
        }
        return true;
    }

    private static boolean edgesSolved(FaceletCube c, List<Integer> positions) {
        for (int p : positions) {
            if (!Pieces3x3.edgeSolved(c, p)) {
                return false;
            }
        }
        return true;
    }

    private static boolean cornersSolved(FaceletCube c, List<Integer> positions) {
        for (int p : positions) {
            if (!Pieces3x3.cornerSolved(c, p)) {
                return false;
            }
        }
        return true;
    }

    private static List<Move> movesFor(String letters) {
        List<Move> moves = new ArrayList<>();
        for (char face : letters.toCharArray()) {
            for (int amount = 1; amount <= 3; amount++) {
                moves.add(new Move(face, 1, 0, amount));
            }
        }
        return moves;
    }

    private void apply(List<Move> moves) {
        cube.apply(moves);
    }

    private List<Move> search(Predicate<FaceletCube> goal, List<Move> moveSet, int maxDepth) {
        if (goal.test(cube)) {
            return new ArrayList<>();
        }
        for (int depth = 1; depth <= maxDepth; depth++) {
            List<Move> path = new ArrayList<>();
            if (dfs(goal, moveSet, depth, path, ' ')) {
                return path;
            }
        }
        return null;
    }

    private boolean dfs(Predicate<FaceletCube> goal, List<Move> moveSet, int depth,
            List<Move> path, char lastFace) {
        for (Move move : moveSet) {
            if (move.face() == lastFace) {
                continue;
            }
            cube.apply(move);
            path.add(move);
            boolean hit = depth == 1 ? goal.test(cube)
                    : dfs(goal, moveSet, depth - 1, path, move.face());
            cube.apply(move.inverse());
            if (hit) {
                return true;
            }
            path.remove(path.size() - 1);
        }
        return false;
    }
}
