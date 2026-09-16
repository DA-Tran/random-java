package com.randomjava.projects.mateinn;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;
import com.randomjava.projects.chessengine.ChessEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mate In N - search a position for a checkmate the defender cannot escape.
 *
 * <h2>Not the same search as playing chess</h2>
 *
 * <p>An ordinary engine scores positions and takes the best line it can find.
 * A mate search asks something stricter and, in one respect, simpler: it wants
 * a <b>forced</b> mate, so material, king safety and every other judgement are
 * irrelevant. Only one question matters at each node, and it is different for
 * the two sides:
 *
 * <ul>
 *   <li>Attacker to move: does <em>some</em> move mate in the remaining
 *       budget?</li>
 *   <li>Defender to move: do <em>all</em> replies still get mated?</li>
 * </ul>
 *
 * <p>That alternation of "exists" and "for all" is the whole algorithm, and it
 * is why a mate search cannot stop at the first promising defence. Missing one
 * reply that escapes means claiming a mate that is not there - so the defender
 * loop has no early exit on success, only on failure.
 *
 * <h2>Stalemate is the trap</h2>
 *
 * <p>The natural base case is "the defender has no legal moves", which is
 * wrong: that is also stalemate, and stalemate is a draw. {@link #findMate}
 * therefore tests {@code checkmate()} rather than an empty move list. It is an
 * easy mistake to make and produces a solver that confidently reports mates
 * which are actually draws - the sort of bug that only shows up on the
 * positions where it matters most.
 *
 * <h2>Depth is measured in the attacker's moves</h2>
 *
 * <p>"Mate in two" means two attacker moves with a defender reply in between,
 * so a ply count would be four and is easy to get wrong by one. The budget
 * here counts only the attacker's moves, which is what the puzzle means.
 *
 * <p>The move generation, legality and checkmate detection all come from
 * {@link ChessEngine}, which already knows the rules. This project supplies
 * only the search on top - there is no reason for two classes in one suite to
 * both know how a knight moves.
 */
public final class MateInN implements Project {

    public static final Meta META = new Meta(
            251, "mate-in-n", "Mate In N", "Logic and Puzzle Games",
            Kind.GRID, Difficulty.ADVANCED,
            "Search a chess position for a forced checkmate in a given number of moves.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    /** A named position with the number of moves it should take. */
    public record Puzzle(String name, String[] rows, boolean whiteToMove, int moves) { }

    /** Back rank mate: the rook lands on the eighth and the pawns box the king in. */
    public static Puzzle backRank() {
        return new Puzzle("back rank mate in one", new String[] {
            "....r.k.",
            ".....ppp",
            "........",
            "........",
            "........",
            "........",
            ".....PPP",
            "....R.K."}, true, 1);
    }

    /** A queen and rook working together; the king has one flight square. */
    public static Puzzle ladder() {
        return new Puzzle("ladder mate in two", new String[] {
            "......k.",
            "........",
            "........",
            "........",
            "........",
            "........",
            "......QR",
            "......K."}, true, 2);
    }

    /** No mate here at all - the solver has to say so rather than invent one. */
    public static Puzzle drawn() {
        return new Puzzle("no mate available", new String[] {
            "....k...",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            "....K..R"}, true, 1);
    }

    public static List<Puzzle> library() {
        return List.of(backRank(), ladder(), drawn());
    }

    private final ChessEngine engine = new ChessEngine();
    private Puzzle current = backRank();
    private List<String> line = new ArrayList<>();
    private int nodes;

    public MateInN() {
        load(backRank());
    }

    public void load(Puzzle puzzle) {
        current = puzzle;
        engine.setPosition(puzzle.rows(), puzzle.whiteToMove());
        line = new ArrayList<>();
    }

    public Puzzle current() {
        return current;
    }

    public int nodes() {
        return nodes;
    }

    // ------------------------------------------------------------------
    // The search
    // ------------------------------------------------------------------

    /**
     * The move that forces mate within {@code moves} attacker moves, or null.
     *
     * <p>Counts only the attacker's moves, so "mate in two" is two calls deep
     * with a defender reply between them.
     */
    public ChessEngine.Move findMate(int moves) {
        if (moves <= 0) {
            return null;
        }
        for (ChessEngine.Move move : engine.legalMoves()) {
            nodes++;
            engine.apply(move);
            // checkmate(), not "no legal moves" - the latter is also stalemate,
            // which is a draw and must not be reported as a win.
            boolean forced = engine.checkmate()
                    || (moves > 1 && defenderIsLost(moves - 1));
            engine.undo(move);
            if (forced) {
                return move;
            }
        }
        return null;
    }

    /**
     * Whether every defending reply still ends in mate within the budget. No
     * early exit on success: one missed escape is a false mate claim.
     */
    private boolean defenderIsLost(int moves) {
        List<ChessEngine.Move> replies = engine.legalMoves();
        if (replies.isEmpty()) {
            return engine.checkmate();   // stalemate is not a win
        }
        for (ChessEngine.Move reply : replies) {
            nodes++;
            engine.apply(reply);
            boolean stillLost = findMate(moves) != null;
            engine.undo(reply);
            if (!stillLost) {
                return false;
            }
        }
        return true;
    }

    /** The shortest forced mate, or 0 if there is none within {@code limit}. */
    public int shortestMate(int limit) {
        for (int moves = 1; moves <= limit; moves++) {
            if (findMate(moves) != null) {
                return moves;
            }
        }
        return 0;
    }

    public String solve() {
        nodes = 0;
        line = new ArrayList<>();
        int found = shortestMate(Math.max(1, current.moves()));
        if (found == 0) {
            return "No forced mate in " + current.moves()
                   + (current.moves() == 1 ? " move." : " moves.");
        }
        // Play the mating move so the board shows it.
        ChessEngine.Move move = findMate(found);
        if (move != null) {
            line.add(describe(move));
            engine.apply(move);
        }
        return "Mate in " + found + (found == 1 ? " move: " : " moves, starting: ")
                + (line.isEmpty() ? "" : line.get(0)) + " (" + nodes + " nodes).";
    }

    private static String describe(ChessEngine.Move move) {
        return "" + (char) ('a' + move.fromCol()) + (8 - move.fromRow())
                + (char) ('a' + move.toCol()) + (8 - move.toRow());
    }

    public List<String> line() {
        return new ArrayList<>(line);
    }

    public char[][] cells() {
        return engine.cells();
    }

    private String detail() {
        return current.name() + ". " + (engine.whiteToMove() ? "White" : "Black")
                + " to move." + (nodes > 0 ? " Searched " + nodes + " positions." : "");
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Uppercase is White. The solver looks for a mate the defender cannot "
                + "escape, not merely a good move.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            List<String> names = new ArrayList<>();
            names.add("Solve");
            for (Puzzle puzzle : library()) {
                names.add("Load: " + puzzle.name());
            }
            int choice = io.menu("Mate In N", names);
            if (choice < 0) {
                return;
            }
            if (choice == 0) {
                io.info(solve());
            } else {
                load(library().get(choice - 1));
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    int which = Json.integer(body, "puzzle", 0);
                    load(library().get(Math.floorMod(which, library().size())));
                    return board("Press Solve to search for a forced mate.");
                }
                case "solve", "step" -> {
                    return board(solve());
                }
                default -> {
                    return Json.error("Unknown action: " + action);
                }
            }
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }

    private Map<String, Object> board(String note) {
        Map<String, Object> out = new LinkedHashMap<>(
                Json.ok("board", Json.grid(cells()), "detail", detail()));
        if (note != null && !note.isEmpty()) {
            out.put("result", note);
        }
        return out;
    }
}
