package com.randomjava.test;

import com.randomjava.cube.CubeSelfTest;
import com.randomjava.cube.CubeValidator;
import com.randomjava.cube.FaceletCube;
import com.randomjava.cube.Move;
import com.randomjava.cube.Pieces3x3;
import com.randomjava.cube.Solution;
import com.randomjava.cube.Solver2x2Optimal;
import com.randomjava.cube.Solver3x3Beginner;
import com.randomjava.lib.Json;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** Tests for the shared library and the twisty-puzzle engine. */
final class CoreTests {

    private CoreTests() {
    }

    static void run(Harness h) {
        json(h);
        moves(h);
        engine(h);
        validator(h);
        solver3x3(h);
        solver2x2(h);
    }

    // ------------------------------------------------------------------

    private static void json(Harness h) {
        h.group("Json", t -> {
            t.equal("writes a string", "\"hi\"", Json.write("hi"));
            t.equal("writes a whole double without a trailing zero", "5", Json.write(5.0));
            t.equal("writes a fractional double", "2.5", Json.write(2.5));
            t.equal("writes null", "null", Json.write(null));
            t.equal("writes a boolean", "true", Json.write(true));
            t.equal("writes a list", "[1,2,3]", Json.write(List.of(1, 2, 3)));
            t.equal("writes nested maps", "{\"a\":{\"b\":1}}",
                    Json.write(Json.map("a", Json.map("b", 1))));
            t.equal("escapes quotes", "\"say \\\"hi\\\"\"", Json.write("say \"hi\""));
            t.equal("escapes newlines", "\"a\\nb\"", Json.write("a\nb"));
            t.equal("turns NaN into null", "null", Json.write(Double.NaN));
            t.equal("turns infinity into null", "null", Json.write(Double.POSITIVE_INFINITY));

            Map<String, Object> parsed = Json.readObject(
                    "{\"n\":42,\"d\":1.5,\"s\":\"x\",\"b\":true,\"z\":null,\"l\":[1,2]}");
            t.equal("reads an int", 42, parsed.get("n"));
            // Small whole numbers must stay Integer. A ternary in the parser
            // once promoted them to Long, which printed identically and broke
            // every caller doing an instanceof check.
            t.check("a small whole number parses as an Integer, not a Long",
                    parsed.get("n") instanceof Integer);
            t.check("a number too big for an int parses as a Long",
                    Json.readObject("{\"n\":9999999999}").get("n") instanceof Long);
            t.check("a decimal parses as a Double", parsed.get("d") instanceof Double);
            t.equal("negative numbers survive", -7,
                    Json.readObject("{\"n\":-7}").get("n"));
            t.equal("exponents parse", 1500.0, Json.readObject("{\"n\":1.5e3}").get("n"));
            t.equal("reads a double", 1.5, parsed.get("d"));
            t.equal("reads a string", "x", parsed.get("s"));
            t.equal("reads a boolean", true, parsed.get("b"));
            t.check("reads a null", parsed.containsKey("z") && parsed.get("z") == null);
            t.equal("reads a list", 2, Json.list(parsed, "l").size());

            t.equal("survives malformed input", 0, Json.readObject("{oops").size());
            t.equal("survives empty input", 0, Json.readObject("").size());
            t.equal("survives null input", 0, Json.readObject(null).size());

            // A round trip must preserve awkward text exactly.
            String awkward = "line1\nline2\t\"quoted\" \\ backslash / slash";
            Map<String, Object> trip = Json.readObject(Json.write(Json.map("k", awkward)));
            t.equal("round trips awkward text", awkward, trip.get("k"));

            t.equal("reads a missing string as the fallback", "def",
                    Json.str(Json.map(), "nope", "def"));
            t.equal("coerces a numeric string", 7, Json.integer(Json.map("v", "7"), "v", 0));
            t.equal("falls back on unparseable numbers", 3,
                    Json.integer(Json.map("v", "abc"), "v", 3));
            t.equal("reads a boolean from text", true, Json.bool(Json.map("v", "true"), "v", false));
            t.check("error payloads are marked not ok", Json.error("bad").get("ok").equals(false));
            t.check("ok payloads are marked ok", Json.ok().get("ok").equals(true));
            t.throwsError("map() rejects an odd number of arguments", () -> Json.map("a"));

            // A grid must be the same shape in Java as it is on the wire.
            List<List<String>> grid = Json.grid(new char[][]{{'a', 'b'}, {'c', 'd'}});
            t.equal("grid keeps its rows", 2, grid.size());
            t.equal("grid keeps its columns", 2, grid.get(0).size());
            t.equal("grid cells become strings", "a", grid.get(0).get(0));
            t.equal("grid serializes the same as a raw array",
                    Json.write(new char[][]{{'a', 'b'}}), Json.write(Json.grid(new char[][]{{'a', 'b'}})));
        });
    }

    private static void moves(Harness h) {
        h.group("Move notation", t -> {
            t.equal("plain face", "R", Move.parseOne("R", 3).toString());
            t.equal("prime", "R'", Move.parseOne("R'", 3).toString());
            t.equal("double", "R2", Move.parseOne("R2", 3).toString());
            t.equal("wide with w", "Rw", Move.parseOne("Rw", 3).toString());
            t.equal("lowercase means wide", "Rw", Move.parseOne("r", 3).toString());
            t.equal("deep wide keeps its number", "3Rw", Move.parseOne("3Rw", 5).toString());
            t.equal("slice M", "M", Move.parseOne("M", 3).toString());
            t.equal("slice E", "E", Move.parseOne("E", 3).toString());
            t.equal("slice S", "S", Move.parseOne("S", 3).toString());
            t.equal("curly apostrophe is accepted", "R'", Move.parseOne("R’", 3).toString());

            t.equal("inverse of a quarter turn", "R'", Move.parseOne("R", 3).inverse().toString());
            t.equal("inverse of a half turn", "R2", Move.parseOne("R2", 3).inverse().toString());

            t.equal("parses a sequence", 4, Move.parse("R U R' U'", 3).size());
            t.equal("commas are separators", 2, Move.parse("R, U", 3).size());
            t.equal("blank parses to nothing", 0, Move.parse("   ", 3).size());
            t.equal("null parses to nothing", 0, Move.parse(null, 3).size());

            t.equal("tidy merges same-face turns", "R2",
                    Move.format(Move.tidy(Move.parse("R R", 3))));
            t.equal("tidy cancels opposites", "",
                    Move.format(Move.tidy(Move.parse("R R'", 3))));
            t.equal("tidy folds three quarters", "R'",
                    Move.format(Move.tidy(Move.parse("R R R", 3))));
            t.equal("tidy leaves different faces alone", "R U",
                    Move.format(Move.tidy(Move.parse("R U", 3))));
            t.equal("invert reverses and flips", "U R'",
                    Move.format(Move.invert(Move.parse("R U'", 3))));

            t.throwsError("rejects an unknown letter", () -> Move.parseOne("Q", 3));
            t.throwsError("rejects a bare modifier", () -> Move.parseOne("'", 3));
            t.throwsError("rejects trailing junk", () -> Move.parseOne("Rx", 3));
            t.throwsError("rejects a turn deeper than the cube", () -> Move.parseOne("5Rw", 3));
            t.throwsError("rejects slices on a 2x2", () -> Move.parseOne("M", 2));
        });
    }

    private static void engine(Harness h) {
        h.group("Cube engine", t -> {
            CubeSelfTest self = new CubeSelfTest();
            self.runAll();
            for (String failure : self.failures()) {
                t.check("engine: " + failure, false);
            }
            t.check("engine self test ran a meaningful number of checks", self.passed() > 300);
            t.equal("engine self test has no failures", 0, self.failures().size());

            // Facelet round trip.
            FaceletCube cube = new FaceletCube(3);
            cube.scramble(new Random(5));
            String facelets = cube.toFacelets();
            t.equal("facelets round trip",
                    facelets, FaceletCube.fromFacelets(facelets, 3).toFacelets());
            t.throwsError("rejects the wrong sticker count",
                    () -> FaceletCube.fromFacelets("UUU", 3));
            t.throwsError("rejects a bad sticker letter",
                    () -> FaceletCube.fromFacelets("Z".repeat(54), 3));

            t.equal("a solved cube reports solved", true, new FaceletCube(3).isSolved());
            t.equal("a solved cube has every sticker home", 54,
                    new FaceletCube(3).correctStickers());
            t.check("a scrambled cube is not solved", !cube.isSolved());
            t.equal("the net has the right number of rows", 9, new FaceletCube(3).net().size());
            t.equal("face grids come back six at a time", 6,
                    new FaceletCube(3).faceGrids().size());
            t.survives("accepts a 100x100, the largest cube ever built",
                    () -> new FaceletCube(100));
            t.equal("a 100x100 has 60,000 stickers", 60000,
                    new FaceletCube(100).toFacelets().length());
            t.throwsError("rejects a cube bigger than the cap",
                    () -> new FaceletCube(FaceletCube.MAX_SIZE + 1));
            t.throwsError("rejects a cube of size zero", () -> new FaceletCube(0));

            // Turning must stay correct at scale, not just on a 3x3.
            for (int big : new int[]{8, 17, 33, 49, 100}) {
                FaceletCube huge = new FaceletCube(big);
                List<Move> scramble = huge.scramble(new Random(big), 40);
                huge.apply(Move.invert(scramble));
                t.check(big + "x" + big + " scramble then inverse returns to solved",
                        huge.isSolved());
            }

            // Piece lookup.
            FaceletCube solved = new FaceletCube(3);
            t.equal("finds a corner where it belongs",
                    Pieces3x3.corner("DFR"), Pieces3x3.findCorner(solved, "DFR"));
            t.equal("finds an edge where it belongs",
                    Pieces3x3.edge("FR"), Pieces3x3.findEdge(solved, "FR"));
            t.check("a solved cube has every corner solved", Pieces3x3.cornerSolved(solved, 0));
            t.equal("a solved cube has four oriented top edges", 4,
                    Pieces3x3.orientedUpperEdges(solved));
        });
    }

    private static void validator(Harness h) {
        h.group("Cube validator", t -> {
            t.check("accepts a solved cube", CubeValidator.check(new FaceletCube(3)).valid());

            for (int seed = 0; seed < 25; seed++) {
                FaceletCube cube = new FaceletCube(3);
                cube.scramble(new Random(seed));
                t.check("accepts scrambled cube " + seed, CubeValidator.check(cube).valid());
            }

            FaceletCube twisted = new FaceletCube(3);
            char[] raw = twisted.raw();
            char keep = raw[8];
            raw[8] = raw[9];
            raw[9] = raw[20];
            raw[20] = keep;
            CubeValidator.Result twist = CubeValidator.check(twisted);
            t.check("rejects a single twisted corner", !twist.valid());
            t.contains("explains the twist", twist.summary(), "twisted");

            FaceletCube flipped = new FaceletCube(3);
            raw = flipped.raw();
            keep = raw[7];
            raw[7] = raw[19];
            raw[19] = keep;
            CubeValidator.Result flip = CubeValidator.check(flipped);
            t.check("rejects a single flipped edge", !flip.valid());
            t.contains("explains the flip", flip.summary(), "flipped");

            FaceletCube swapped = new FaceletCube(3);
            raw = swapped.raw();
            char a = raw[7];
            char b = raw[19];
            raw[7] = raw[5];
            raw[19] = raw[10];
            raw[5] = a;
            raw[10] = b;
            CubeValidator.Result swap = CubeValidator.check(swapped);
            t.check("rejects two swapped pieces", !swap.valid());

            FaceletCube miscounted = new FaceletCube(3);
            miscounted.raw()[0] = 'R';
            CubeValidator.Result counts = CubeValidator.check(miscounted);
            t.check("rejects a wrong sticker count", !counts.valid());
            t.contains("explains the count", counts.summary(), "stickers");
        });
    }

    private static void solver3x3(Harness h) {
        h.group("3x3 layer-by-layer solver", t -> {
            Solver3x3Beginner solver = new Solver3x3Beginner();
            Random random = new Random(2026);
            int trials = 40;
            int longest = 0;

            for (int i = 0; i < trials; i++) {
                FaceletCube cube = new FaceletCube(3);
                List<Move> scramble = cube.scramble(random);
                Solution solution = solver.solve(cube, Move.format(scramble));

                // Replay onto a fresh cube: the solver must not rely on its own state.
                FaceletCube replay = new FaceletCube(3);
                replay.apply(scramble);
                replay.apply(solution.moves());
                t.check("scramble " + i + " is solved by replaying the solution",
                        replay.isSolved());
                longest = Math.max(longest, solution.moveCount());

                if (i == 0) {
                    t.equal("names its method", Solver3x3Beginner.METHOD, solution.method());
                    t.check("reports stages", solution.steps().size() >= 5);
                    for (Solution.Step step : solution.steps()) {
                        t.check("every stage explains itself",
                                step.explanation() != null && step.explanation().length() > 20);
                        t.check("every stage records the cube after it",
                                step.facelets().length() == 54);
                        t.check("every stage has moves", step.moveCount() > 0);
                    }
                    // Stage facelets must actually follow the moves.
                    FaceletCube walk = new FaceletCube(3);
                    walk.apply(scramble);
                    boolean tracks = true;
                    for (Solution.Step step : solution.steps()) {
                        walk.apply(step.moves());
                        tracks &= walk.toFacelets().equals(step.facelets());
                    }
                    t.check("stepping through the stages matches the recorded states", tracks);
                }
            }
            t.check("solutions stay a sane length for a beginner method", longest < 220);

            // An already solved cube produces nothing to do.
            Solution none = solver.solve(new FaceletCube(3), "");
            t.equal("a solved cube needs no moves", 0, none.moveCount());

            t.throwsError("refuses a cube of the wrong size",
                    () -> new Solver3x3Beginner().solve(new FaceletCube(2), ""));
        });
    }

    private static void solver2x2(Harness h) {
        h.group("2x2 optimal solver", t -> {
            t.equal("measured God's number matches theory", 11,
                    Solver2x2Optimal.measureGodsNumber());

            Solver2x2Optimal solver = new Solver2x2Optimal();
            Random random = new Random(7);
            int worst = 0;
            for (int i = 0; i < 150; i++) {
                FaceletCube cube = new FaceletCube(2);
                List<Move> scramble = cube.scramble(random);
                Solution solution = solver.solve(cube, Move.format(scramble));

                FaceletCube replay = new FaceletCube(2);
                replay.apply(scramble);
                replay.apply(solution.moves());
                t.check("2x2 scramble " + i + " is solved by replaying the solution",
                        replay.isSolved());

                int turns = solution.steps().get(solution.steps().size() - 1).moveCount();
                worst = Math.max(worst, turns);
            }
            t.check("no solution exceeds God's number (" + worst + " turns)", worst <= 11);

            t.throwsError("refuses a cube of the wrong size",
                    () -> new Solver2x2Optimal().solve(new FaceletCube(3), ""));
        });
    }
}
