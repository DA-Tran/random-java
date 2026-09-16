package com.randomjava.cube;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Solves a 3x3 the way a person is taught to: bottom cross, bottom corners,
 * middle edges, then the last layer in four steps.
 *
 * <p>The first two layers are found by a small constrained search rather than a
 * table of a hundred hand-written cases. Each piece is solved with a short
 * sequence chosen so that everything already finished stays finished, which is
 * exactly the constraint a person works under. It keeps the code honest: there
 * is no case to forget, and the result is verified by solving thousands of
 * random scrambles rather than by trusting a table.
 *
 * <p>The last layer uses the real beginner algorithms. Each stage searches over
 * combinations of a top-layer turn and one of the stage's algorithms, so the
 * output is the same sequence a person would perform, and the step text can
 * name the algorithm being used.
 */
public final class Solver3x3Beginner {

    public static final String METHOD = "Beginner layer-by-layer";

    /** Last-layer algorithms, in the form a beginner learns them. */
    public static final String CROSS_ALG = "F R U R' U' F'";
    public static final String SUNE = "R U R' U R U2 R'";
    public static final String ANTISUNE = "R U2 R' U' R U' R'";
    /**
     * Pure corner three-cycles. These are the PLL A-perms rather than the
     * commutator many beginner guides give, because that one cycles the corners
     * but can also twist them, which would undo the stage before.
     */
    public static final String A_PERM = "R' F R' B2 R F' R' B2 R2";
    public static final String A_PERM_MIRROR = "R B' R F2 R' B R F2 R2";
    public static final String U_PERM_A = "R U' R U R U R U' R' U' R2";
    public static final String U_PERM_B = "R2 U R U R' U' R' U' R' U R'";

    private static final List<Move> ALL_MOVES = allMoves();

    private FaceletCube cube;
    private Solution.Builder out;

    // ------------------------------------------------------------------
    // Entry point
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

        bottomCross();
        bottomCorners();
        middleEdges();
        topCross();
        orientTopCorners();
        permuteTopCorners();
        permuteTopEdges();
        finalTurn();

        if (!cube.isSolved()) {
            throw new IllegalStateException(
                    "Solver finished without solving the cube. State: " + cube.toFacelets());
        }
        return out.build();
    }

    // ------------------------------------------------------------------
    // Stage 1: the bottom cross
    // ------------------------------------------------------------------

    private void bottomCross() {
        int[] targets = {Pieces3x3.edge("DF"), Pieces3x3.edge("DR"),
                Pieces3x3.edge("DB"), Pieces3x3.edge("DL")};
        List<Integer> done = new ArrayList<>();

        for (int target : targets) {
            if (Pieces3x3.edgeSolved(cube, target)) {
                done.add(target);
                continue;
            }
            String colours = Pieces3x3.EDGE_NAMES[target];
            List<Integer> settled = List.copyOf(done);

            Predicate<FaceletCube> finished = c ->
                    Pieces3x3.edgeSolved(c, target) && edgesStillSolved(c, settled);

            List<Move> found = search(finished, ALL_MOVES, 4);
            if (found == null) {
                // Lift the edge to the top layer first, then drop it in.
                Predicate<FaceletCube> lifted = c -> {
                    int at = Pieces3x3.findEdge(c, colours);
                    return Pieces3x3.isUpperEdge(at) && edgesStillSolved(c, settled);
                };
                List<Move> lift = search(lifted, ALL_MOVES, 4);
                if (lift != null) {
                    applyAll(lift);
                    List<Move> drop = search(finished, ALL_MOVES, 5);
                    if (drop == null) {
                        throw new IllegalStateException("Could not place the " + colours + " edge");
                    }
                    applyAll(drop);
                    found = new ArrayList<>(lift);
                    found.addAll(drop);
                    record("Bottom cross",
                            "Put the " + colours + " edge into place on the bottom face, "
                                    + "keeping the cross edges already solved where they are.",
                            found, "");
                    done.add(target);
                    continue;
                }
                found = search(finished, ALL_MOVES, 6);
                if (found == null) {
                    throw new IllegalStateException("Could not place the " + colours + " edge");
                }
            }
            applyAll(found);
            record("Bottom cross",
                    "Put the " + colours + " edge into place on the bottom face, "
                            + "keeping the cross edges already solved where they are.",
                    found, "");
            done.add(target);
        }
    }

    // ------------------------------------------------------------------
    // Stage 2: the bottom corners
    // ------------------------------------------------------------------

    private void bottomCorners() {
        int[] targets = {Pieces3x3.corner("DFR"), Pieces3x3.corner("DLF"),
                Pieces3x3.corner("DBL"), Pieces3x3.corner("DRB")};
        List<Integer> done = new ArrayList<>();

        for (int target : targets) {
            if (Pieces3x3.cornerSolved(cube, target)) {
                done.add(target);
                continue;
            }
            String colours = Pieces3x3.CORNER_NAMES[target];
            List<Integer> settledCorners = List.copyOf(done);

            Predicate<FaceletCube> intact = c -> crossSolved(c) && cornersStillSolved(c, settledCorners);

            // Get the corner into the top layer, where it can be aimed at its slot.
            int at = Pieces3x3.findCorner(cube, colours);
            if (!Pieces3x3.isUpperCorner(at)) {
                Predicate<FaceletCube> lifted = c ->
                        Pieces3x3.isUpperCorner(Pieces3x3.findCorner(c, colours)) && intact.test(c);
                List<Move> lift = search(lifted, ALL_MOVES, 3);
                if (lift == null) {
                    throw new IllegalStateException("Could not free the " + colours + " corner");
                }
                applyAll(lift);
                record("Bottom corners",
                        "Take the " + colours + " corner out of the wrong slot so it can be "
                                + "brought back into the right one.", lift, "");
            }

            // Insert it using only the two faces of its slot plus the top.
            List<Move> slotMoves = movesFor("U" + colours.substring(1));
            Predicate<FaceletCube> finished = c -> Pieces3x3.cornerSolved(c, target) && intact.test(c);
            List<Move> insert = search(finished, slotMoves, 8);
            if (insert == null) {
                insert = search(finished, ALL_MOVES, 6);
            }
            if (insert == null) {
                throw new IllegalStateException("Could not insert the " + colours + " corner");
            }
            applyAll(insert);
            record("Bottom corners",
                    "Line the " + colours + " corner up over its slot and turn it in. "
                            + "Repeating a pair of turns brings it down the right way up.",
                    insert, "");
            done.add(target);
        }
    }

    // ------------------------------------------------------------------
    // Stage 3: the middle layer edges
    // ------------------------------------------------------------------

    private void middleEdges() {
        int[] targets = {Pieces3x3.edge("FR"), Pieces3x3.edge("FL"),
                Pieces3x3.edge("BL"), Pieces3x3.edge("BR")};
        List<Integer> done = new ArrayList<>();

        for (int target : targets) {
            if (Pieces3x3.edgeSolved(cube, target)) {
                done.add(target);
                continue;
            }
            String colours = Pieces3x3.EDGE_NAMES[target];
            List<Integer> settled = List.copyOf(done);

            Predicate<FaceletCube> intact = c ->
                    firstLayerSolved(c) && edgesStillSolved(c, settled);

            int at = Pieces3x3.findEdge(cube, colours);
            if (Pieces3x3.isMiddleEdge(at)) {
                // It is in a middle slot but wrong, so push it up to the top first.
                List<Move> ejectMoves = movesFor("U" + Pieces3x3.EDGE_NAMES[at]);
                Predicate<FaceletCube> lifted = c ->
                        Pieces3x3.isUpperEdge(Pieces3x3.findEdge(c, colours)) && intact.test(c);
                List<Move> eject = search(lifted, ejectMoves, 8);
                if (eject == null) {
                    eject = search(lifted, ALL_MOVES, 6);
                }
                if (eject == null) {
                    throw new IllegalStateException("Could not free the " + colours + " edge");
                }
                applyAll(eject);
                record("Middle layer",
                        "Push the " + colours + " edge out of the slot it is stuck in so it can "
                                + "be put back the right way round.", eject, "");
            }

            List<Move> slotMoves = movesFor("U" + colours);
            Predicate<FaceletCube> finished = c -> Pieces3x3.edgeSolved(c, target) && intact.test(c);
            List<Move> insert = search(finished, slotMoves, 8);
            if (insert == null) {
                insert = search(finished, ALL_MOVES, 6);
            }
            if (insert == null) {
                throw new IllegalStateException("Could not insert the " + colours + " edge");
            }
            applyAll(insert);
            record("Middle layer",
                    "Send the " + colours + " edge down into its slot between the two centres "
                            + "it belongs to.", insert, "");
            done.add(target);
        }
    }

    // ------------------------------------------------------------------
    // Stage 4-7: the last layer
    // ------------------------------------------------------------------

    private void topCross() {
        topStage("Top cross",
                "Make a cross on the top face. The algorithm turns a dot into a line, a line "
                        + "into an L, and an L into the finished cross.",
                c -> Pieces3x3.orientedUpperEdges(c) == 4,
                List.of(CROSS_ALG), 4, CROSS_ALG);
    }

    private void orientTopCorners() {
        topStage("Top face",
                "Turn the last four corners the right way up. Sune moves three corners at a "
                        + "time, so repeating it with the top turned between goes round them all.",
                c -> Pieces3x3.orientedUpperCorners(c) == 4 && Pieces3x3.orientedUpperEdges(c) == 4,
                List.of(SUNE, ANTISUNE), 6, SUNE);
    }

    private void permuteTopCorners() {
        topStage("Corner positions",
                "Move the top corners to the right places. This algorithm cycles three of them "
                        + "and leaves everything else alone.",
                Solver3x3Beginner::topCornersPlaced,
                List.of(A_PERM, A_PERM_MIRROR), 4, A_PERM);
    }

    private void permuteTopEdges() {
        topStage("Edge positions",
                "Finally cycle the last edges into place, which finishes the cube.",
                FaceletCube::isSolved,
                List.of(U_PERM_A, U_PERM_B), 4, U_PERM_A);
    }

    /**
     * Runs one last-layer stage by searching over combinations of a top-face
     * turn and one of the stage's algorithms.
     */
    private void topStage(String stage, String explanation, Predicate<FaceletCube> goal,
            List<String> algorithms, int maxRounds, String named) {
        if (aufSatisfies(goal) >= 0) {
            int turns = aufSatisfies(goal);
            List<Move> auf = auf(turns);
            applyAll(auf);
            record(stage, explanation, auf, "");
            return;
        }
        List<Move> found = algorithmSearch(goal, algorithms, maxRounds);
        if (found == null) {
            throw new IllegalStateException(
                    "No " + stage + " solution found. State: " + cube.toFacelets());
        }
        applyAll(found);
        int turns = aufSatisfies(goal);
        if (turns > 0) {
            List<Move> auf = auf(turns);
            applyAll(auf);
            found = new ArrayList<>(found);
            found.addAll(auf);
        }
        record(stage, explanation, found, named);
    }

    /** Iterative deepening over (top turn, algorithm) pairs. */
    /**
     * Searches combinations of the known algorithms, with each of the four
     * setup turns in front, until the goal is met.
     *
     * <p>Roughly 48 options expanded to depth three, which costs about 230 ms
     * per solve. Memoising the parsed algorithms was tried and changed nothing
     * measurable: the node expansions dominate so completely that the string
     * handling is noise, and the cache was complexity buying nothing.
     *
     * <p>The real speed-up would be recognising the last-layer case outright
     * and applying its algorithm, which is what the CFOP solver does. Searching
     * is slower and is the honest shape of a beginner method: a small set of
     * algorithms applied repeatedly until the pattern comes out.
     */
    private List<Move> algorithmSearch(Predicate<FaceletCube> goal, List<String> algorithms,
            int maxRounds) {
        List<List<Move>> options = new ArrayList<>();
        for (int turns = 0; turns < 4; turns++) {
            for (String algorithm : algorithms) {
                List<Move> option = new ArrayList<>(auf(turns));
                option.addAll(Move.parse(algorithm, 3));
                options.add(option);
            }
        }
        for (int depth = 1; depth <= maxRounds; depth++) {
            List<Move> path = new ArrayList<>();
            if (algorithmDfs(goal, options, depth, path)) {
                return path;
            }
        }
        return null;
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

    /** The last turn of the top face that lines the finished cube up. */
    private void finalTurn() {
        if (cube.isSolved()) {
            return;
        }
        for (int turns = 1; turns < 4; turns++) {
            List<Move> auf = auf(turns);
            cube.apply(auf);
            if (cube.isSolved()) {
                record("Finish", "One last turn of the top face lines everything up.", auf, "");
                return;
            }
            cube.apply(Move.invert(auf));
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** How many top turns make the goal true, or -1 if none do. */
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

    private static List<Move> auf(int turns) {
        if (turns == 0) {
            return List.of();
        }
        return List.of(new Move('U', 1, 0, turns));
    }

    /**
     * Every top corner in its right place and still the right way up. The
     * orientation half matters: without it the search would happily accept a
     * sequence that positions the corners but twists them, undoing the stage
     * before this one.
     */
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

    private static boolean firstLayerSolved(FaceletCube c) {
        if (!crossSolved(c)) {
            return false;
        }
        for (String name : new String[]{"DFR", "DLF", "DBL", "DRB"}) {
            if (!Pieces3x3.cornerSolved(c, Pieces3x3.corner(name))) {
                return false;
            }
        }
        return true;
    }

    private static boolean edgesStillSolved(FaceletCube c, List<Integer> positions) {
        for (int p : positions) {
            if (!Pieces3x3.edgeSolved(c, p)) {
                return false;
            }
        }
        return true;
    }

    private static boolean cornersStillSolved(FaceletCube c, List<Integer> positions) {
        for (int p : positions) {
            if (!Pieces3x3.cornerSolved(c, p)) {
                return false;
            }
        }
        return true;
    }

    /** Quarter and half turns of the faces named in {@code letters}. */
    private static List<Move> movesFor(String letters) {
        List<Move> moves = new ArrayList<>();
        for (char face : letters.toCharArray()) {
            for (int amount = 1; amount <= 3; amount++) {
                moves.add(new Move(face, 1, 0, amount));
            }
        }
        return moves;
    }

    private static List<Move> allMoves() {
        return movesFor("URFDLB");
    }

    private void applyAll(List<Move> moves) {
        cube.apply(moves);
    }

    private void record(String stage, String explanation, List<Move> moves, String algorithm) {
        out.add(stage, explanation, moves, algorithm, cube);
    }

    /**
     * Iterative deepening search for the shortest sequence from the current cube
     * that satisfies {@code goal}, using only {@code moveSet}.
     *
     * <p>The cube is mutated and restored rather than copied at every node, which
     * is what keeps a depth-8 search quick.
     */
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
            // Always undo, so a successful search leaves the cube untouched and
            // the caller is free to apply the returned sequence itself.
            cube.apply(move.inverse());
            if (hit) {
                return true;
            }
            path.remove(path.size() - 1);
        }
        return false;
    }
}
