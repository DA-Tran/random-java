package com.randomjava.test;

import com.randomjava.Catalog;
import com.randomjava.lib.Json;
import com.randomjava.lib.Project;
import com.randomjava.projects.faqchatbot.FaqChatbot;
import com.randomjava.projects.resumescreener.ResumeScreener;
import com.randomjava.projects.handwrittendigitrecognizer.HandwrittenDigitRecognizer;
import com.randomjava.projects.stockpricepredictor.StockPricePredictor;
import com.randomjava.projects.imagecaptiongenerator.ImageCaptionGenerator;
import com.randomjava.projects.voicecommandassistant.VoiceCommandAssistant;
import com.randomjava.projects.digitalsignaturegenerator.DigitalSignatureGenerator;
import com.randomjava.projects.securenotetakingapp.SecureNoteTakingApp;
import com.randomjava.projects.securefiletransfer.SecureFileTransfer;
import com.randomjava.projects.honeypotserver.HoneypotServer;
import com.randomjava.projects.encryptedchatapp.EncryptedChatApp;
import com.randomjava.projects.keyloggerdetector.KeyloggerDetector;
import com.randomjava.projects.keyloggerdetector.KeyloggerDetector.Signal;
import com.randomjava.projects.keyloggerdetector.KeyloggerDetector.Verdict;
import com.randomjava.projects.portscanner.PortScanner;
import com.randomjava.projects.snake.Snake;
import com.randomjava.projects.tetris.Tetris;
import com.randomjava.projects.tetris.Tetris.Shape;
import com.randomjava.projects.memorymatchinggame.MemoryMatchingGame;
import com.randomjava.projects.wordscramble.WordScramble;
import com.randomjava.projects.virtualpet.VirtualPet;
import com.randomjava.projects.spaceshooter.SpaceShooter;
import com.randomjava.projects.towerdefense.TowerDefense;
import com.randomjava.projects.chessengine.ChessEngine;
import com.randomjava.projects.platformer2d.Platformer2d;
import com.randomjava.projects.blooddonationfinder.BloodDonationFinder;
import com.randomjava.projects.blooddonationfinder.BloodDonationFinder.BloodType;
import com.randomjava.projects.blockchainvoting.BlockchainVoting;
import com.randomjava.projects.multiplayertrivia.MultiplayerTrivia;
import com.randomjava.projects.decentralizedfilestorage.DecentralizedFileStorage;
import com.randomjava.projects.sliding15puzzle.Sliding15Puzzle;
import com.randomjava.projects.minesweeper.Minesweeper;
import com.randomjava.projects.game2048.Game2048;
import com.randomjava.projects.lightsout.LightsOut;
import com.randomjava.projects.magicsquare.MagicSquare;
import com.randomjava.projects.binarypuzzle.BinaryPuzzle;
import com.randomjava.projects.futoshiki.Futoshiki;
import com.randomjava.projects.hitori.Hitori;
import com.randomjava.projects.nonogram.Nonogram;
import com.randomjava.projects.pegsolitaire.PegSolitaire;
import com.randomjava.projects.shikaku.Shikaku;
import com.randomjava.projects.skyscrapers.Skyscrapers;
import com.randomjava.projects.sokoban.Sokoban;
import com.randomjava.projects.mastermind.Mastermind;
import com.randomjava.projects.mastermind.Mastermind.Score;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Tests the projects that are actually implemented, through the same
 * {@code api} the browser uses. Going through that contract rather than calling
 * internals means these tests break if the wiring breaks, not only if the maths
 * does.
 */
final class ProjectTests {

    private ProjectTests() {
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Project make(String slug) {
        return Catalog.factories().get(slug).get();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> call(Project project, String action, Object... keyValues) {
        Map<String, Object> body = Json.map(keyValues);
        body.put("action", action);
        try {
            return (Map<String, Object>) project.api(action, body);
        } catch (Exception e) {
            throw new RuntimeException(action + " threw " + e, e);
        }
    }

    private static Map<String, Object> call(String slug, String action, Object... keyValues) {
        return call(make(slug), action, keyValues);
    }

    private static boolean ok(Map<String, Object> response) {
        return Boolean.TRUE.equals(response.get("ok"));
    }

    private static String text(Map<String, Object> response, String key) {
        Object value = response.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    // ------------------------------------------------------------------

    static void run(Harness h) {
        calculator(h);
        converters(h);
        wordTools(h);
        numbers(h);
        security(h);
        markdown(h);
        puzzles(h);
        games(h);
        lists(h);
        cubes(h);
        retrieval(h);
        prediction(h);
        vision(h);
        language(h);
        crypto(h);
        defence(h);
        keyAgreement(h);
        scanning(h);
        arcade(h);
        boardGames(h);
        liveGames(h);
        strategy(h);
        chess(h);
        physics(h);
        systems(h);
        gridPuzzles(h);
        algebraPuzzles(h);
        deduction(h);
    }

    private static void calculator(Harness h) {
        h.group("001 Simple Calculator", t -> {
            Project p = make("simple-calculator");
            t.equal("respects precedence", "14", text(call(p, "compute", "input", "2 + 3 * 4"), "result"));
            t.equal("handles parentheses and powers", "25",
                    text(call(p, "compute", "input", "(8 - 3) ^ 2"), "result"));
            t.equal("handles remainder", "1", text(call(p, "compute", "input", "22 % 7"), "result"));
            t.equal("handles unary minus", "5", text(call(p, "compute", "input", "-5 + 10"), "result"));
            t.equal("powers are right associative", "512",
                    text(call(p, "compute", "input", "2 ^ 3 ^ 2"), "result"));
            t.equal("keeps fractions", "2.5", text(call(p, "compute", "input", "5 / 2"), "result"));
            t.check("rejects division by zero", !ok(call(p, "compute", "input", "1 / 0")));
            t.check("rejects malformed input", !ok(call(p, "compute", "input", "2 +* 3")));
            t.check("rejects an unclosed bracket", !ok(call(p, "compute", "input", "(1 + 2")));
            t.check("rejects empty input", !ok(call(p, "compute", "input", "")));
            t.check("rejects an unknown action", !ok(call(p, "nonsense")));
        });
    }

    private static void converters(Harness h) {
        h.group("004 BMI Calculator", t -> {
            Project p = make("bmi-calculator");
            Map<String, Object> metric = call(p, "compute", "units", "metric", "height", 180, "weight", 75);
            t.contains("computes a known BMI", text(metric, "result"), "23.1");
            t.contains("names the band", text(metric, "result"), "healthy");
            t.contains("gives the healthy range", text(metric, "detail"), "Healthy range");
            t.check("rejects zero height", !ok(call(p, "compute", "height", 0, "weight", 70)));
            t.check("rejects an impossible height", !ok(call(p, "compute", "height", 900, "weight", 70)));
            Map<String, Object> imperial = call(p, "compute", "units", "imperial",
                    "height", 70.87, "weight", 165.3);
            t.contains("imperial agrees with metric", text(imperial, "result"), "23.1");
        });

        h.group("008 Unit Converter", t -> {
            Project p = make("unit-converter");
            t.equal("boiling point", "212 F",
                    text(call(p, "convert", "category", "temperature", "from", "C", "to", "F",
                            "value", 100), "result"));
            t.equal("freezing point", "32 F",
                    text(call(p, "convert", "category", "temperature", "from", "C", "to", "F",
                            "value", 0), "result"));
            t.equal("absolute zero", "0 K",
                    text(call(p, "convert", "category", "temperature", "from", "C", "to", "K",
                            "value", -273.15), "result"));
            t.equal("a mile in kilometres", "1.609344 km",
                    text(call(p, "convert", "category", "length", "from", "mi", "to", "km",
                            "value", 1), "result"));
            t.equal("a kilogram in grams", "1000 g",
                    text(call(p, "convert", "category", "mass", "from", "kg", "to", "g",
                            "value", 1), "result"));
            t.equal("converting to itself changes nothing", "5 m",
                    text(call(p, "convert", "category", "length", "from", "m", "to", "m",
                            "value", 5), "result"));
            t.check("rejects an unknown unit",
                    !ok(call(p, "convert", "category", "length", "from", "furlong", "to", "m",
                            "value", 1)));
            t.check("rejects an unknown category",
                    !ok(call(p, "convert", "category", "bananas", "from", "a", "to", "b",
                            "value", 1)));
            t.check("lists the units for a category",
                    ok(call(p, "units", "category", "mass")));
        });
    }

    private static void wordTools(Harness h) {
        h.group("028 Palindrome Checker", t -> {
            Project p = make("palindrome-checker");
            t.contains("accepts a phrase with punctuation",
                    text(call(p, "compute", "input", "A man, a plan, a canal: Panama"), "result"), "Yes");
            t.contains("accepts a single word",
                    text(call(p, "compute", "input", "racecar"), "result"), "Yes");
            t.contains("accepts digits",
                    text(call(p, "compute", "input", "12321"), "result"), "Yes");
            t.contains("rejects a non-palindrome",
                    text(call(p, "compute", "input", "hello world"), "result"), "No");
            t.contains("reports the longest run",
                    text(call(p, "compute", "input", "hello world"), "detail"), "Longest");
            t.check("rejects empty input", !ok(call(p, "compute", "input", "")));
        });

        h.group("131 Word Counter", t -> {
            Project p = make("word-counter");
            Map<String, Object> r = call(p, "count", "text",
                    "The quick brown fox jumps over the lazy dog.");
            t.equal("counts words", "9 words", text(r, "result"));
            t.contains("counts characters", text(r, "detail"), "Characters:            44");
            t.contains("counts unique words", text(r, "detail"), "Unique words:          8");
            t.contains("counts one sentence", text(r, "detail"), "Sentences:             1");

            Map<String, Object> two = call(p, "count", "text", "One. Two! Three?");
            t.contains("counts three sentences", text(two, "detail"), "Sentences:             3");
            Map<String, Object> runs = call(p, "count", "text", "Wait... really?!");
            t.contains("treats runs of punctuation as one ending",
                    text(runs, "detail"), "Sentences:             2");
            Map<String, Object> paras = call(p, "count", "text", "One para.\n\nTwo para.");
            t.contains("counts paragraphs", text(paras, "detail"), "Paragraphs:            2");
            t.check("rejects empty text", !ok(call(p, "count", "text", "   ")));
        });

        h.group("101 ASCII Art Generator", t -> {
            Project p = make("ascii-art-generator");
            Map<String, Object> r = call(p, "render", "text", "HI", "fill", "#", "width", 80);
            t.check("renders something", ok(r));
            t.contains("wraps the art in a pre block", text(r, "html"), "<pre");
            t.contains("uses the fill character", text(r, "html"), "#");
            Map<String, Object> star = call(p, "render", "text", "A", "fill", "*", "width", 80);
            t.contains("honours a different fill", text(star, "html"), "*");
            t.check("rejects empty text", !ok(call(p, "render", "text", "")));
        });
    }

    private static void numbers(Harness h) {
        h.group("029 Prime Generator", t -> {
            Project p = make("prime-number-generator");
            Map<String, Object> sieve = call(p, "sieve", "limit", 30);
            t.equal("counts the primes under 30", "10 primes up to 30", text(sieve, "result"));
            t.contains("lists them", text(sieve, "detail"), "2, 3, 5, 7, 11, 13, 17, 19, 23, 29");
            t.equal("knows 1 is not prime", "0 primes up to 1",
                    text(call(p, "sieve", "limit", 1), "result"));
            t.contains("tests a known prime", text(call(p, "test", "value", 97), "result"), "is prime");
            t.contains("tests a known composite",
                    text(call(p, "test", "value", 91), "result"), "not prime");
            t.contains("factorises correctly",
                    text(call(p, "factorise", "value", 360), "result"), "2, 2, 2, 3, 3, 5");
            t.contains("a prime factorises to itself",
                    text(call(p, "factorise", "value", 97), "result"), "97");
            t.check("refuses a silly limit", !ok(call(p, "sieve", "limit", 99999999)));
        });
    }

    private static void security(Harness h) {
        h.group("061 Password Strength", t -> {
            Project p = make("password-strength-checker");
            t.contains("calls a famous password very weak",
                    text(call(p, "compute", "input", "password"), "result"), "very weak");
            t.contains("spots an embedded common word",
                    text(call(p, "compute", "input", "password123"), "detail"), "common word");
            t.contains("spots a keyboard run",
                    text(call(p, "compute", "input", "qwertyXK9!"), "detail"), "keyboard run");
            t.contains("spots a repeated character",
                    text(call(p, "compute", "input", "aaabcdefXY9!"), "detail"), "repeated");
            t.contains("spots a year",
                    text(call(p, "compute", "input", "Tulip1998Zx!"), "detail"), "year");
            t.contains("rates a long passphrase well",
                    text(call(p, "compute", "input", "correct-horse-battery-staple-9"), "result"),
                    "strong");
            t.contains("reports entropy in bits",
                    text(call(p, "compute", "input", "Zq7!vLm2"), "detail"), "bits");
            t.check("rejects empty input", !ok(call(p, "compute", "input", "")));
        });
    }

    private static void markdown(Harness h) {
        h.group("012 Markdown to HTML", t -> {
            Project p = make("markdown-to-html");
            String html = text(call(p, "convert", "markdown",
                    "# Title\n\nSome **bold** and *italic* and `code`.\n\n- one\n- two\n\n"
                            + "1. first\n2. second\n\n> quoted\n\n---\n"), "html");
            t.contains("makes a heading", html, "<h1>Title</h1>");
            t.contains("makes bold", html, "<strong>bold</strong>");
            t.contains("makes italic", html, "<em>italic</em>");
            t.contains("makes inline code", html, "<code>code</code>");
            t.contains("makes an unordered list", html, "<ul>");
            t.contains("makes an ordered list", html, "<ol>");
            t.contains("makes a blockquote", html, "<blockquote>");
            t.contains("makes a rule", html, "<hr>");

            String link = text(call(p, "convert", "markdown", "[x](https://example.com)"), "html");
            t.contains("makes a link", link, "href=\"https://example.com\"");

            // Input must never become live markup.
            String injected = text(call(p, "convert", "markdown",
                    "<script>alert(1)</script>"), "html");
            t.absent("does not pass a script tag through", injected, "<script>alert");
            t.contains("escapes it into text instead", injected, "&lt;script&gt;");

            String fence = text(call(p, "convert", "markdown", "```\nraw <b>text</b>\n```"), "html");
            t.contains("keeps fenced code as code", fence, "<pre><code>");
            t.absent("escapes markup inside a fence", fence, "<b>text</b>");

            t.check("rejects empty input", !ok(call(p, "convert", "markdown", "")));
            t.check("offers a sample", ok(call(p, "sample")));
        });
    }

    private static void puzzles(Harness h) {
        h.group("021 Sudoku Solver", t -> {
            Project p = make("sudoku-solver");
            Map<String, Object> solved = call(p, "solve");
            t.check("solves the built-in puzzle", ok(solved));
            t.contains("says how it went", text(solved, "result"), "Solved");

            @SuppressWarnings("unchecked")
            List<List<String>> board = (List<List<String>>) solved.get("board");
            t.equal("returns nine rows", 9, board.size());
            boolean valid = true;
            for (int i = 0; i < 9; i++) {
                boolean[] row = new boolean[10];
                boolean[] col = new boolean[10];
                boolean[] box = new boolean[10];
                for (int j = 0; j < 9; j++) {
                    int r = Integer.parseInt(board.get(i).get(j));
                    int c = Integer.parseInt(board.get(j).get(i));
                    int b = Integer.parseInt(board.get((i / 3) * 3 + j / 3).get((i % 3) * 3 + j % 3));
                    valid &= !row[r] && !col[c] && !box[b];
                    row[r] = col[c] = box[b] = true;
                }
            }
            t.check("every row, column and box holds 1-9 exactly once", valid);

            t.check("solves the hard puzzle too", ok(call(p, "hard")));
            t.check("rejects a short grid", !ok(call(p, "load", "puzzle", "123")));
            t.check("rejects a contradictory grid",
                    !ok(call(p, "load", "puzzle", "11" + ".".repeat(79))));
        });

        h.group("022 Maze Generator", t -> {
            Project p = make("maze-generator");
            Map<String, Object> made = call(p, "generate", "size", 8);
            t.check("generates a maze", ok(made));
            @SuppressWarnings("unchecked")
            List<List<String>> grid = (List<List<String>>) made.get("board");
            t.equal("renders at twice the cell count plus one", 17, grid.size());

            Map<String, Object> solved = call(p, "solve");
            t.contains("finds a route", text(solved, "result"), "Shortest route");
            @SuppressWarnings("unchecked")
            List<List<String>> routed = (List<List<String>>) solved.get("board");
            int marks = 0;
            boolean start = false;
            boolean exit = false;
            for (List<String> row : routed) {
                for (String cell : row) {
                    if (cell.equals("*")) {
                        marks++;
                    }
                    start |= cell.equals("S");
                    exit |= cell.equals("E");
                }
            }
            t.check("marks a path", marks > 0);
            t.check("keeps the start marker", start);
            t.check("keeps the exit marker", exit);
            t.check("clearing removes the route", ok(call(p, "clear")));
        });

        h.group("024 Sorting Visualizer", t -> {
            for (String algorithm : List.of("bubble", "insertion", "selection", "merge", "quick")) {
                Project p = make("sorting-visualizer");
                call(p, "generate", "size", 20, "algorithm", algorithm);
                Map<String, Object> done = call(p, "run");
                @SuppressWarnings("unchecked")
                List<Integer> bars = (List<Integer>) done.get("bars");
                boolean sorted = true;
                for (int i = 1; i < bars.size(); i++) {
                    sorted &= bars.get(i - 1) <= bars.get(i);
                }
                t.check(algorithm + " sort finishes sorted", sorted);
                t.equal(algorithm + " keeps every value", 20, bars.size());
                t.contains(algorithm + " reports its work", text(done, "detail"), "comparisons");
            }
            Project p = make("sorting-visualizer");
            call(p, "generate", "size", 10, "algorithm", "bubble");
            t.check("stepping works", ok(call(p, "step")));
            t.check("rewinding works", ok(call(p, "rewind")));
            t.check("rejects an unknown algorithm",
                    !ok(call(p, "algorithm", "algorithm", "bogosort")));
        });
    }

    private static void games(Harness h) {
        h.group("009 Rock Paper Scissors", t -> {
            Project p = make("rock-paper-scissors");
            t.check("starts a match", ok(call(p, "new", "bestOf", 3)));
            Map<String, Object> played = call(p, "play", "move", "rock");
            t.check("plays a round", ok(played));
            t.contains("says what was thrown", text(played, "detail"), "You played Rock");
            t.check("rejects a nonsense move", !ok(call(p, "play", "move", "banana")));
            t.check("rejects an empty move", !ok(call(p, "play", "move", "")));

            // A best of one must end after a decisive round.
            Project quick = make("rock-paper-scissors");
            call(quick, "new", "bestOf", 1);
            boolean ended = false;
            for (int i = 0; i < 30 && !ended; i++) {
                String detail = text(call(quick, "play", "move", "rock"), "detail");
                ended = detail.contains("New match") || detail.contains("play again");
                if (!ok(call(quick, "play", "move", "rock"))) {
                    ended = true;
                }
            }
            t.check("a best of one finishes", ended);
        });

        h.group("130 Tic-Tac-Toe", t -> {
            Project p = make("tic-tac-toe");
            call(p, "new", "mode", "computer");
            Map<String, Object> played = call(p, "play", "cell", 4);
            @SuppressWarnings("unchecked")
            List<String> cells = (List<String>) played.get("cells");
            t.equal("keeps nine squares", 9, cells.size());
            t.equal("plays your move where you asked", "X", cells.get(4));
            int taken = (int) cells.stream().filter(c -> !c.isEmpty()).count();
            t.equal("the computer replies immediately", 2, taken);
            t.contains("rejects an occupied square",
                    text(call(p, "play", "cell", 4), "detail"), "already taken");
            t.contains("rejects a square off the board",
                    text(call(p, "play", "cell", 99), "detail"), "1 to 9");

            Project two = make("tic-tac-toe");
            call(two, "new", "mode", "two-player");
            call(two, "play", "cell", 0);
            @SuppressWarnings("unchecked")
            List<String> shared = (List<String>) call(two, "state").get("cells");
            t.equal("two-player mode does not auto-reply", 1,
                    (int) shared.stream().filter(c -> !c.isEmpty()).count());
        });
    }

    private static void lists(Harness h) {
        h.group("002 To-Do List", t -> {
            Project p = make("todo-list");
            t.check("starts empty", listSize(call(p, "list")) == 0);
            call(p, "add", "label", "Buy milk", "priority", "high", "meta", "before 6pm");
            call(p, "add", "label", "Write tests", "priority", "low");
            Map<String, Object> listed = call(p, "list");
            t.equal("keeps both tasks", 2, listSize(listed));
            t.contains("summarises outstanding work", text(listed, "detail"), "2 outstanding");
            t.contains("counts high priority", text(listed, "detail"), "high priority");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) listed.get("items");
            t.equal("sorts high priority first", "Buy milk", items.get(0).get("label"));

            Object id = items.get(0).get("id");
            call(p, "toggle", "id", id);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> afterToggle =
                    (List<Map<String, Object>>) call(p, "list").get("items");
            boolean anyDone = afterToggle.stream().anyMatch(i -> Boolean.TRUE.equals(i.get("done")));
            t.check("ticking a task marks it done", anyDone);
            t.check("done tasks sort to the bottom",
                    Boolean.TRUE.equals(afterToggle.get(afterToggle.size() - 1).get("done")));

            call(p, "clearDone");
            t.equal("clearing completed leaves the rest", 1, listSize(call(p, "list")));
            call(p, "remove", "id", listId(call(p, "list"), 0));
            t.equal("removing works", 0, listSize(call(p, "list")));
            t.check("rejects a task with no label", !ok(call(p, "add", "label", "  ")));
        });
    }

    private static void cubes(Harness h) {
        h.group("165-173 Cube projects", t -> {
            for (String slug : List.of("rubiks-cube-3x3", "void-cube", "mirror-cube", "axis-cube")) {
                Project p = make(slug);
                call(p, "scramble");
                Map<String, Object> solved = call(p, "solve");
                t.check(slug + " solves a scramble", ok(solved));
                @SuppressWarnings("unchecked")
                Map<String, Object> solution = (Map<String, Object>) solved.get("solution");
                t.check(slug + " reports its stages",
                        ((List<?>) solution.get("steps")).size() >= 5);
                Map<String, Object> hinted = call(p, "hint");
                t.check(slug + " gives a hint", hinted.get("hint") != null);
            }

            Project two = make("pocket-cube-2x2");
            call(two, "scramble");
            t.check("2x2 solves a scramble", ok(call(two, "solve")));

            // A slice-scrambled big cube is not reduced, so stage three must
            // decline it with a reason rather than attempt it.
            Project big = make("revenge-cube-4x4");
            t.check("4x4 scrambles", ok(call(big, "scramble")));
            Map<String, Object> noSolver = call(big, "solve");
            t.check("4x4 declines a cube that is not reduced", !ok(noSolver));
            t.contains("and names the missing stage", text(noSolver, "error"), "centres");

            // Reduction stage three: outer turns keep a cube reduced, so every
            // size from 4x4 up must solve, including the very large ones.
            for (int n : new int[]{4, 5, 7, 10, 33}) {
                com.randomjava.cube.FaceletCube cube = new com.randomjava.cube.FaceletCube(n);
                List<com.randomjava.cube.Move> scramble = new java.util.ArrayList<>();
                java.util.Random random = new java.util.Random(n);
                char[] faces = {'U', 'R', 'F', 'D', 'L', 'B'};
                for (int i = 0; i < 25; i++) {
                    com.randomjava.cube.Move move = new com.randomjava.cube.Move(
                            faces[random.nextInt(6)], 1, 0, 1 + random.nextInt(3));
                    scramble.add(move);
                    cube.apply(move);
                }
                t.check(n + "x" + n + " stays reduced under outer turns",
                        com.randomjava.cube.SolverNxNReduction.isReduced(cube));
                com.randomjava.cube.Solution solved =
                        new com.randomjava.cube.SolverNxNReduction().solve(cube, "");
                com.randomjava.cube.FaceletCube replay = new com.randomjava.cube.FaceletCube(n);
                replay.apply(scramble);
                replay.apply(solved.moves());
                t.check(n + "x" + n + " reduced cube is solved by replaying the solution",
                        replay.isSolved());
            }

            Project cube = make("rubiks-cube-3x3");
            t.check("rejects an impossible cube",
                    !ok(call(cube, "load", "facelets", "U".repeat(54))));
            Map<String, Object> reset = call(cube, "reset");
            t.check("resets to solved", Boolean.TRUE.equals(reset.get("solved")));
            t.check("applies typed turns", ok(call(cube, "move", "moves", "R U R' U'")));
            t.check("rejects nonsense turns", !ok(call(cube, "move", "moves", "Q7")));
        });
    }

    @SuppressWarnings("unchecked")
    private static int listSize(Map<String, Object> response) {
        Object items = response.get("items");
        return items instanceof List ? ((List<Object>) items).size() : -1;
    }

    @SuppressWarnings("unchecked")
    private static Object listId(Map<String, Object> response, int index) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        return items.get(index).get("id");
    }

    /**
     * The two retrieval projects, tested on the properties that separate them
     * from the naive version of the same idea. Both of the naive versions
     * "work" on a happy-path example, which is exactly why the happy path is
     * not what is checked here.
     */
    private static void retrieval(Harness h) {
        h.group("055 FAQ Chatbot", t -> {
            FaqChatbot bot = new FaqChatbot();

            FaqChatbot.Reply hit = bot.ask("I forgot my password, how do I reset it?");
            t.check("a well-worded question is answered", hit.answered());
            t.contains("and answered from the right entry",
                    hit.match().question(), "reset");
            t.check("with a high confidence", hit.confidence() > 0.8);

            // Inverse document frequency has to reach exactly zero for a term
            // that is in every entry. Smoothed idf leaves it slightly positive,
            // and then a question made only of that term looks like evidence.
            FaqChatbot flat = new FaqChatbot();
            flat.clear();
            flat.teach("How do I reset my invoice password?", "a");
            flat.teach("How do I export my invoice history?", "b");
            flat.teach("How do I cancel my invoice subscription?", "c");
            t.check("a term in every entry weighs exactly nothing",
                    Math.abs(flat.idf().getOrDefault("invoice", -1.0)) < 1e-9);
            t.check("a term in one entry weighs a lot",
                    flat.idf().getOrDefault("export", 0.0) > 1.0);
            FaqChatbot.Reply noSignal = flat.ask("invoice");
            t.check("so a question of only that term scores zero",
                    noSignal.confidence() == 0.0);
            t.check("and is declined", !noSignal.answered());

            // The tie check. "Delete" and "close" are synonyms that share no
            // letters, so this question really only says "account" - which
            // three entries say equally. Ranking alone would return whichever
            // sorted first, at a score that looks perfectly respectable.
            FaqChatbot.Reply tied = bot.ask("I want to delete my account for good");
            t.check("a question that fits several entries is not answered", !tied.answered());
            t.equal("and is reported as a tie",
                    FaqChatbot.Outcome.AMBIGUOUS, tied.outcome());
            t.contains("naming the entries it could not choose between",
                    tied.text(), "close my account");
            t.check("the tie is not a low score, it is a near-equal one",
                    tied.confidence() >= 0.20);

            // Using the entry's own word resolves the same question at once.
            t.check("the entry's own wording finds it",
                    bot.ask("how do I close my account").answered());

            // Cosine divides by entry length, so an entry about the topic beats
            // one that merely name-drops it in a long list.
            FaqChatbot lengths = new FaqChatbot();
            lengths.clear();
            lengths.teach("How do I request a refund?", "Contact billing.");
            lengths.teach("Our shipping, returns, warranty, refund, exchange, delivery, "
                    + "packaging and customs policies explained in full", "Long page.");
            lengths.teach("Where do I find my order number?", "Top of the receipt.");
            t.contains("a focused entry outranks one that name-drops the term",
                    lengths.rank("refund").get(0).getKey().question(), "request a refund");

            t.check("an unrelated question is declined",
                    !bot.ask("what is the airspeed velocity of a swallow").answered());

            int before = bot.entries().size();
            bot.teach("How do I change my email?", "Settings, Account, Email.");
            t.equal("teaching adds an entry", before + 1, bot.entries().size());
            t.check("and the new entry is immediately answerable",
                    bot.ask("change my email address").answered());

            // Through the browser contract: a blank answer means ask, a filled
            // one means teach.
            Project p = make("faq-chatbot");
            t.check("asking over the api works",
                    ok(call(p, "add", "label", "how do I get a refund", "meta", "")));
            t.check("teaching over the api works",
                    ok(call(p, "add", "label", "Where is my receipt?", "meta", "In your inbox.")));
        });

        h.group("059 Resume Screener", t -> {
            ResumeScreener rs = new ResumeScreener();

            var good = rs.screen(ResumeScreener.SAMPLE_JOB, ResumeScreener.SAMPLE_CV);
            t.check("a real candidate scores like one", good.score() >= 0.40);
            t.equal("years are read from the advert", 5, good.yearsWanted());
            t.equal("years are read from the CV", 7, good.yearsFound());

            // The keyword-stuffing property. Counting occurrences is the
            // obvious implementation and it ranks a CV that says one word forty
            // times above a CV that has every skill on the list.
            String stuffed = "Java Java Java Java Java Java Java Java Java Java Java "
                    + "Java Java Java Java Java Java Java Java Java. 9 years of Java.";
            String broad = "6 years. Java services, SQL on a relational database, "
                    + "Docker and Kubernetes in production, AWS hosting.";
            double stuffedScore = rs.screen(ResumeScreener.SAMPLE_JOB, stuffed).score();
            double broadScore = rs.screen(ResumeScreener.SAMPLE_JOB, broad).score();
            t.check("covering the requirements beats repeating one of them",
                    broadScore > stuffedScore);
            t.check("repetition is worth exactly nothing",
                    Math.abs(rs.screen(ResumeScreener.SAMPLE_JOB, "9 years. Java.").score()
                            - stuffedScore) < 1e-9);

            double core = rs.screen(ResumeScreener.SAMPLE_JOB,
                    "6 years Java, SQL, relational database design, Docker, Kubernetes, AWS.")
                    .score();
            double bonus = rs.screen(ResumeScreener.SAMPLE_JOB,
                    "6 years Kafka and Terraform.").score();
            t.check("required skills count for more than nice-to-haves", core > bonus * 2);

            // Experience is a gate, not another keyword, so no amount of skill
            // matching turns two years into five.
            var junior = rs.screen(ResumeScreener.SAMPLE_JOB,
                    "2 years. Java, SQL, relational database design, Docker, Kubernetes, "
                    + "AWS, Kafka, Terraform.");
            t.check("a perfect skill match short on years is capped", junior.score() <= 0.60);
            t.contains("and the shortfall is stated", junior.verdict(), "Short on experience");

            t.check("an unrelated CV scores near zero",
                    rs.screen(ResumeScreener.SAMPLE_JOB,
                            "12 years running restaurant kitchens. Menu design.").score() < 0.15);

            t.equal("a range reads as its lower bound", 3,
                    ResumeScreener.years("we want 3-5 years of this", true));
            t.equal("a plus reads as the number", 5,
                    ResumeScreener.years("5+ years required", true));
            t.equal("spelled-out numbers are read", 5,
                    ResumeScreener.years("five years of Java", true));
            t.equal("no years stated reads as zero", 0,
                    ResumeScreener.years("Java and SQL", true));

            t.check("an empty advert is refused",
                    !ok(call("resume-screener", "compute", "jd", "", "cv", "x")));
            t.check("the api scores a pasted pair",
                    ok(call("resume-screener", "compute",
                            "jd", ResumeScreener.SAMPLE_JOB, "cv", ResumeScreener.SAMPLE_CV)));
        });
    }

    private static void prediction(Harness h) {
        h.group("057 Stock Price Predictor", t -> {
            StockPricePredictor sp = new StockPricePredictor();

            java.util.List<Double> line = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) { line.add(100 + 2.5 * i); }
            var exact = StockPricePredictor.fit(line);
            t.check("slope recovered exactly", Math.abs(exact.slope() - 2.5) < 1e-9);
            t.check("intercept recovered exactly", Math.abs(exact.intercept() - 100) < 1e-9);
            t.check("R2 is 1 on a perfect line", Math.abs(exact.rSquared() - 1.0) < 1e-9);
            t.check("residuals are 0 on a perfect line", exact.residualError() < 1e-9);

            // The interval has to widen with the horizon. A band of constant
            // width claims a guess ten steps out is as good as one step out,
            // which is the single most misleading thing this could do.
            java.util.List<Double> noisy = new java.util.ArrayList<>();
            java.util.Random rng = new java.util.Random(7);
            double price = 100;
            for (int i = 0; i < 40; i++) { price += rng.nextGaussian() * 1.5; noisy.add(price); }
            var out = sp.project(noisy, 10, 5);
            double previous = -1;
            boolean widening = true;
            for (var p : out.projections()) {
                double width = p.high() - p.low();
                if (width <= previous) { widening = false; }
                previous = width;
            }
            t.check("the interval widens at every step", widening);
            t.check("and never inverts",
                    out.projections().stream().allMatch(p -> p.low() < p.high()));

            // A hard-coded 1.96 is a third too narrow on the short histories
            // people actually paste in.
            t.check("t is much wider than 1.96 on a tiny sample",
                    StockPricePredictor.tValue(3) > 3.0);
            t.check("and settles to the normal value on a large one",
                    Math.abs(StockPricePredictor.tValue(500) - 1.96) < 1e-9);

            // The backtest must be able to report losing, or it measures nothing.
            java.util.List<Double> walk = new java.util.ArrayList<>();
            java.util.Random rw = new java.util.Random(11);
            double w = 50;
            for (int i = 0; i < 60; i++) { w += rw.nextGaussian(); walk.add(w); }
            var walked = sp.project(walk, 5, 5);
            t.check("a backtest is run", walked.backtest() != null);
            t.check("and its verdict matches its own numbers",
                    walked.backtest().trendWon()
                            == (walked.backtest().trendError() < walked.backtest().naiveError()));
            t.contains("the caveat always names the random walk",
                    walked.caveat(), "random walk");

            java.util.List<Double> trending = new java.util.ArrayList<>();
            for (int i = 0; i < 40; i++) { trending.add(20 + 3.0 * i + rng.nextGaussian() * 0.5); }
            t.check("on a real trend the line beats the naive guess",
                    sp.project(trending, 5, 5).backtest().trendWon());

            // A flat series has no variance to explain, so both 0 and 1 are lies.
            var flat = StockPricePredictor.fit(java.util.List.of(5.0, 5.0, 5.0, 5.0, 5.0));
            t.check("a flat series has zero slope", Math.abs(flat.slope()) < 1e-9);
            t.check("and R2 is not invented", Double.isNaN(flat.rSquared()));

            t.equal("prices parse from mixed separators", 5,
                    StockPricePredictor.prices("1, 2 3\n4;5").size());
            t.check("two points are refused, having no residuals to speak of",
                    !ok(call("stock-price-predictor", "compute", "prices", "1 2")));
            t.check("junk is refused",
                    !ok(call("stock-price-predictor", "compute", "prices", "1, banana")));
            t.check("a real history is projected",
                    ok(call("stock-price-predictor", "compute",
                            "prices", "101 100.8 102.4 103.1 102.7 104 105.2", "horizon", 5)));
        });
    }

    private static void vision(Harness h) {
        h.group("052 Digit Recognizer", t -> {
            // Every digit, at every size from below the template resolution to
            // well above it, in each corner and the centre. This sweep is the
            // point of the project: cropping and rescaling before comparing is
            // what makes position and size stop mattering. It also catches a
            // renderer that drops rows while scaling down, which is how a small
            // 3 turns into a small 9 before the classifier ever sees it.
            int checked = 0;
            int correct = 0;
            StringBuilder wrong = new StringBuilder();
            for (int height : new int[]{6, 7, 9, 12, 18}) {
                for (int[] corner : new int[][]{{0, 0}, {0, 8}, {6, 0}, {4, 5}}) {
                    for (int digit = 0; digit <= 9; digit++) {
                        HandwrittenDigitRecognizer dr = new HandwrittenDigitRecognizer();
                        dr.resize(26);
                        dr.clear();
                        dr.drawDigit(digit, corner[0], corner[1], height);
                        var guess = dr.classify();
                        checked++;
                        if (guess.decided() && guess.digit() == digit) { correct++; }
                        else if (wrong.length() < 60) {
                            wrong.append(' ').append(digit).append('@').append(height)
                                 .append("->").append(guess.decided() ? guess.digit() : "?");
                        }
                    }
                }
            }
            t.equal("every digit at every size and position" + wrong, checked, correct);

            // The same digit big and small is a completely different picture in
            // raw pixels and nearly the same one after normalising. That gap is
            // the whole argument for the normalising step.
            HandwrittenDigitRecognizer helper = new HandwrittenDigitRecognizer();
            helper.resize(20);
            helper.clear();
            helper.drawDigit(3, 2, 2, 16);
            boolean[][] big = helper.canvas();
            helper.clear();
            helper.drawDigit(3, 0, 0, 7);
            boolean[][] small = helper.canvas();
            double normalised = HandwrittenDigitRecognizer.similarity(
                    HandwrittenDigitRecognizer.normalise(big),
                    HandwrittenDigitRecognizer.normalise(small));
            double raw = HandwrittenDigitRecognizer.similarity(grey(big), grey(small));
            t.check("normalising makes big and small the same picture", normalised > 0.9);
            t.check("while raw pixels see two unrelated shapes", raw < normalised - 0.2);

            // 0 and 8 are the pair that sets the template resolution.
            HandwrittenDigitRecognizer pair = new HandwrittenDigitRecognizer();
            pair.resize(16);
            pair.clear();
            pair.drawDigit(8, 1, 1, 14);
            var eight = pair.classify();
            t.equal("an 8 is an 8, not a 0", 8, eight.digit());
            t.check("and clearly so, not by a hair", eight.margin() > 0.05);

            t.check("a blank canvas is not classified",
                    !new HandwrittenDigitRecognizer().classify().decided());

            HandwrittenDigitRecognizer scribble = new HandwrittenDigitRecognizer();
            scribble.resize(12);
            scribble.paint(0, 0, true);
            scribble.paint(11, 11, true);
            t.check("a scribble is refused rather than guessed", !scribble.classify().decided());

            Project p = make("handwritten-digit-recognizer");
            t.check("the canvas generates", ok(call(p, "generate", "size", 14)));
            t.check("a cell can be painted", ok(call(p, "paint", "row", 3, "col", 4)));
            t.check("off-canvas painting is refused", !ok(call(p, "paint", "row", 99, "col", 0)));
            t.check("the demo draws a digit", ok(call(p, "step")));
            Map<String, Object> classified = call(p, "solve");
            t.check("and the demo digit is then recognised", ok(classified));
            t.contains("with the normalised grid shown as evidence",
                    text(classified, "detail"), "normalised to");
        });
    }

    private static double[][] grey(boolean[][] ink) {
        double[][] out = new double[ink.length][ink[0].length];
        for (int row = 0; row < ink.length; row++) {
            for (int col = 0; col < ink[row].length; col++) {
                out[row][col] = ink[row][col] ? 1 : 0;
            }
        }
        return out;
    }

    private static void language(Harness h) {
        h.group("056 Image Caption Generator", t -> {
            ImageCaptionGenerator ic = new ImageCaptionGenerator();
            var sample = ic.caption(ImageCaptionGenerator.boxes(ImageCaptionGenerator.SAMPLE));
            t.check("the caption is a sentence", sample.text().endsWith("."));
            t.check("a 0.31 detection is not asserted as fact",
                    !sample.text().contains("umbrella"));
            t.check("but is reported as dropped, not silently lost",
                    sample.dropped().stream().anyMatch(d -> d.startsWith("umbrella")));
            t.contains("a middling detection is hedged", sample.text(), "what looks like");

            // Articles follow sound, not spelling. The vowel-letter rule that
            // everyone writes first gets both of these backwards.
            t.equal("a unicorn, not an unicorn", "a", ImageCaptionGenerator.article("unicorn"));
            t.equal("an hour, not a hour", "an", ImageCaptionGenerator.article("hour"));
            t.equal("a user", "a", ImageCaptionGenerator.article("user"));
            t.equal("an heir", "an", ImageCaptionGenerator.article("heir"));
            t.equal("an apple still works", "an", ImageCaptionGenerator.article("apple"));
            t.equal("a dog still works", "a", ImageCaptionGenerator.article("dog"));

            t.equal("people, not persons", "people", ImageCaptionGenerator.plural("person"));
            t.equal("sheep, not sheeps", "sheep", ImageCaptionGenerator.plural("sheep"));
            t.equal("boxes", "boxes", ImageCaptionGenerator.plural("box"));
            t.equal("babies", "babies", ImageCaptionGenerator.plural("baby"));
            t.equal("leaves", "leaves", ImageCaptionGenerator.plural("leaf"));
            t.equal("three people", "three people", ImageCaptionGenerator.count("person", 3));
            t.equal("large counts stay numeric", "40 cats",
                    ImageCaptionGenerator.count("cat", 40));

            // Area times confidence, not arrival order.
            t.check("the caption leads with the salient object",
                    ic.caption(ImageCaptionGenerator.boxes(
                            "ant 0.99 0 0 2 2\nelephant 0.95 10 10 200 200"))
                      .text().toLowerCase().startsWith("an elephant"));
            // Screen y runs downwards, so a smaller y is higher up.
            t.contains("a smaller y reads as above",
                    ic.caption(ImageCaptionGenerator.boxes(
                            "bird 0.9 50 0 40 40\ntree 0.9 50 100 40 40")).text(), "above");
            t.check("repeats are grouped and pluralised",
                    ic.caption(ImageCaptionGenerator.boxes(
                            "person 0.9 0 0 30 30\nperson 0.9 40 0 30 30\n"
                            + "person 0.9 80 0 30 30"))
                      .text().toLowerCase().contains("three people"));

            t.check("a short line is refused",
                    !ok(call("image-caption-generator", "compute", "detections", "cat 0.9 1 2 3")));
            t.check("a confidence above 1 is refused",
                    !ok(call("image-caption-generator", "compute",
                            "detections", "cat 1.8 1 2 3 4")));
            t.check("the sample captions over the api",
                    ok(call("image-caption-generator", "compute", "detections", "")));
        });

        h.group("058 Voice Command Assistant", t -> {
            VoiceCommandAssistant va = new VoiceCommandAssistant();

            var timer = va.handle("set a timer for five minutes");
            t.equal("a spoken duration is understood",
                    VoiceCommandAssistant.Status.DONE, timer.status());
            t.contains("and converted", timer.reply(), "5m 00s");
            t.contains("digits work too",
                    va.handle("set a timer for 90 seconds").reply(), "1m 30s");
            t.contains("mixed units add up",
                    va.handle("set a timer for 1 hour 30 minutes").reply(), "1h 30m");
            t.contains("an hour is one hour",
                    va.handle("set a timer for an hour").reply(), "1h 00m");

            // The property the whole design turns on: an intent that is
            // recognised but under-specified must ask for the missing slot.
            // Firing the handler anyway is how a zero-second timer gets set.
            var bare = va.handle("set a timer");
            t.equal("a timer with no duration is not fired",
                    VoiceCommandAssistant.Status.NEEDS_DETAIL, bare.status());
            t.equal("the intent is still recognised", "timer", bare.intent());
            t.check("and it asks for exactly what is missing",
                    bare.reply().toLowerCase().contains("how long"));
            t.equal("an unparseable duration counts as missing",
                    VoiceCommandAssistant.Status.NEEDS_DETAIL,
                    va.handle("set a timer for bananas").status());

            var light = va.handle("turn off the kitchen light");
            t.equal("both slots fill", "off", light.slots().get("state"));
            t.equal("including the multi-word one", "kitchen light",
                    light.slots().get("device"));
            t.equal("turn on what? is asked", VoiceCommandAssistant.Status.NEEDS_DETAIL,
                    va.handle("turn on").status());

            var remind = va.handle("remind me to call mum at six");
            t.equal("a reminder splits task from time", "call mum",
                    remind.slots().get("task"));
            // Discarding the preposition gives "remind you to call mum six",
            // and loses the difference between "in ten minutes" and "at ten".
            t.contains("the preposition survives", remind.reply(), "call mum at six");
            t.contains("in and at are told apart",
                    va.handle("remind me to stretch in ten minutes").reply(),
                    "stretch in ten minutes");
            t.equal("a reminder with no time asks for one",
                    VoiceCommandAssistant.Status.NEEDS_DETAIL,
                    va.handle("remind me to call mum").status());

            t.equal("an unknown command is not forced into an intent",
                    VoiceCommandAssistant.Status.UNKNOWN,
                    va.handle("make me a sandwich").status());
            t.equal("trailing punctuation is tolerated", VoiceCommandAssistant.Status.DONE,
                    va.handle("play jazz.").status());
            t.equal("case is tolerated", VoiceCommandAssistant.Status.DONE,
                    va.handle("TURN OFF THE LAMP").status());

            // Absent and zero have to stay different: one means "not spoken",
            // the other would mean "a timer for no time at all".
            t.equal("a unit with no number reads as absent", -1,
                    VoiceCommandAssistant.seconds("minutes"));
            t.equal("nothing at all reads as absent", -1, VoiceCommandAssistant.seconds(""));
            t.equal("clock formats hours", "1h 02m 05s", VoiceCommandAssistant.clock(3725));

            t.check("an empty utterance is refused",
                    !ok(call("voice-command-assistant", "add", "label", "")));
            t.check("a real command works over the api",
                    ok(call("voice-command-assistant", "add",
                            "label", "set a timer for two minutes")));
        });
    }

    private static char[] nonogramBlank(int length) {
        char[] line = new char[length];
        java.util.Arrays.fill(line, Nonogram.UNKNOWN);
        return line;
    }

    private static String nonogramShow(char[] line) {
        return line == null ? "impossible" : String.valueOf(line);
    }

    private static int countBlanks(MagicSquare board) {
        int blanks = 0;
        for (int row = 0; row < board.size(); row++) {
            for (int column = 0; column < board.size(); column++) {
                if (board.cell(row, column) == 0) {
                    blanks++;
                }
            }
        }
        return blanks;
    }

    private static boolean refused(Runnable action) {
        try {
            action.run();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static void crypto(Harness h) {
        h.group("070 Digital Signatures", t -> {
            var alice = DigitalSignatureGenerator.generate("alice");
            var bob = DigitalSignatureGenerator.generate("bob");
            String message = "Transfer 500 to account 12345.";
            String signature = DigitalSignatureGenerator.sign(message, alice.privateKey());

            t.check("a genuine signature verifies",
                    DigitalSignatureGenerator.verify(message, signature,
                            alice.publicKey()).valid());
            // The two claims a signature makes: unchanged, and from this key.
            t.check("changing the amount breaks it",
                    !DigitalSignatureGenerator.verify("Transfer 900 to account 12345.",
                            signature, alice.publicKey()).valid());
            t.check("even a trailing space breaks it",
                    !DigitalSignatureGenerator.verify(message + " ", signature,
                            alice.publicKey()).valid());
            t.check("another key does not verify it",
                    !DigitalSignatureGenerator.verify(message, signature,
                            bob.publicKey()).valid());

            // What is signed is the digest, so the signature is a fixed size
            // regardless of the message - which is why RSA can sign at all.
            String tiny = DigitalSignatureGenerator.sign("hi", alice.privateKey());
            String huge = DigitalSignatureGenerator.sign("x".repeat(100_000),
                    alice.privateKey());
            t.equal("signature size is independent of message size",
                    tiny.length(), huge.length());
            t.check("and a 100k message still verifies",
                    DigitalSignatureGenerator.verify("x".repeat(100_000), huge,
                            alice.publicKey()).valid());

            t.equal("the digest is 64 hex characters", 64,
                    DigitalSignatureGenerator.digest("anything").length());
            t.check("junk fails verification rather than throwing",
                    !DigitalSignatureGenerator.verify(message, "not-a-signature",
                            alice.publicKey()).valid());
            t.check("an empty message will not be signed",
                    refused(() -> DigitalSignatureGenerator.sign("", alice.privateKey())));
            t.check("a bogus key is refused",
                    refused(() -> DigitalSignatureGenerator.sign("hi", "not-a-key")));

            Project p = make("digital-signature-generator");
            Map<String, Object> signed = call(p, "sign", "message", "hello", "identity", "alice");
            t.check("signing works over the api", ok(signed));
            t.contains("and shows the digest that was signed",
                    text(signed, "detail"), DigitalSignatureGenerator.digest("hello"));
            t.check("verifying the same message passes",
                    text(call(p, "verify", "message", "hello", "identity", "alice"),
                            "result").equals("VALID"));
            t.check("verifying an altered message fails",
                    text(call(p, "verify", "message", "hell0",
                            "identity", "alice"), "result").equals("INVALID"));
        });

        h.group("069 Secure Notes", t -> {
            SecureNoteTakingApp vault = new SecureNoteTakingApp();
            String master = "correct horse battery";

            t.check("a locked vault refuses writes", refused(() -> vault.add("x", "y")));
            t.check("a short master password is refused", refused(() -> vault.unlock("abc")));
            vault.unlock(master);
            var note = vault.add("wifi", "hunter2-is-not-a-good-password");
            t.equal("the note round-trips", "hunter2-is-not-a-good-password",
                    vault.read(note.id()));
            t.check("the body is not sitting in the blob",
                    !note.sealed().contains("hunter2"));

            // A fresh salt and IV each time. Identical ciphertext for identical
            // plaintext would tell an observer which notes match without
            // decrypting anything.
            String once = SecureNoteTakingApp.seal("same text", master);
            String twice = SecureNoteTakingApp.seal("same text", master);
            t.check("the same text seals to different bytes", !once.equals(twice));
            t.equal("but both decrypt to the same thing",
                    SecureNoteTakingApp.unseal(once, master),
                    SecureNoteTakingApp.unseal(twice, master));
            t.check("the wrong password is refused",
                    refused(() -> SecureNoteTakingApp.unseal(once, "wrong password here")));

            // The property plain AES-CBC would fail. Without authentication a
            // flipped bit decrypts to plausible garbage, and an attacker can
            // choose which bits of the plaintext change.
            byte[] blob = java.util.Base64.getDecoder().decode(once);
            blob[blob.length - 5] ^= 0x01;
            String flipped = java.util.Base64.getEncoder().encodeToString(blob);
            t.check("a single flipped bit is detected, not decrypted",
                    refused(() -> SecureNoteTakingApp.unseal(flipped, master)));

            byte[] saltHit = java.util.Base64.getDecoder().decode(once);
            saltHit[0] ^= 0x01;
            t.check("altering the salt is detected", refused(() -> SecureNoteTakingApp.unseal(
                    java.util.Base64.getEncoder().encodeToString(saltHit), master)));
            byte[] ivHit = java.util.Base64.getDecoder().decode(once);
            ivHit[SecureNoteTakingApp.SALT_BYTES] ^= 0x01;
            t.check("altering the IV is detected", refused(() -> SecureNoteTakingApp.unseal(
                    java.util.Base64.getEncoder().encodeToString(ivHit), master)));
            t.check("a truncated blob is refused",
                    refused(() -> SecureNoteTakingApp.unseal("AAAA", master)));

            vault.lock();
            t.check("locking makes the notes unreadable",
                    refused(() -> vault.read(note.id())));
            t.equal("but does not lose them", 1, vault.notes().size());
            vault.unlock(master);
            t.equal("and the right password brings them back",
                    "hunter2-is-not-a-good-password", vault.read(note.id()));

            Project p = make("secure-note-taking-app");
            t.check("the api refuses a weak master password",
                    !ok(call(p, "unlock", "password", "abc")));
            t.check("and accepts a reasonable one",
                    ok(call(p, "unlock", "password", "a longer passphrase")));
            t.check("then seals a note", ok(call(p, "add", "label", "n", "meta", "secret")));
        });
    }

    private static void defence(Harness h) {
        h.group("062 Secure File Transfer", t -> {
            String secret = "shared-secret-value";
            var parcel = SecureFileTransfer.send("report.txt", "quarterly numbers", secret);
            var arrival = SecureFileTransfer.receive(parcel, secret);
            t.check("an untouched transfer arrives intact", arrival.trustworthy());
            t.equal("and the contents survive", "quarterly numbers", arrival.contents());
            t.check("the plaintext is not in the payload",
                    !parcel.payload().contains("quarterly"));

            // The whole point. An attacker alters the file and recomputes the
            // checksum, which needs no secret, so only the keyed tag notices.
            var forged = SecureFileTransfer.tamper(parcel);
            var rejected = SecureFileTransfer.receive(forged, secret);
            t.check("the recomputed checksum happily passes", rejected.checksumOk());
            t.check("but the HMAC catches it", !rejected.hmacOk());
            t.check("so the transfer is rejected", !rejected.trustworthy());
            t.contains("and the reason is explained", rejected.verdict(), "needs no secret");

            var again = SecureFileTransfer.send("report.txt", "quarterly numbers", secret);
            t.check("the same file sent twice looks different on the wire",
                    !again.payload().equals(parcel.payload()));
            t.check("the wrong secret is rejected",
                    !SecureFileTransfer.receive(parcel, "another-secret-x").trustworthy());
            t.check("an hmac depends on its key",
                    !SecureFileTransfer.hmac("x", "key-one-here")
                            .equals(SecureFileTransfer.hmac("x", "key-two-here")));
            // Comparing tags byte by byte with an early return leaks how much
            // of a guess was right, one byte at a time.
            t.check("tag comparison rejects a length mismatch",
                    !SecureFileTransfer.sameTag("abc", "abcd"));
            t.check("and accepts equal tags", SecureFileTransfer.sameTag("abcd", "abcd"));
            t.check("a short shared secret is refused",
                    !ok(call("secure-file-transfer", "send", "contents", "x", "secret", "shrt")));
            t.check("a clean send works over the api",
                    ok(call("secure-file-transfer", "send", "contents", "hello",
                            "secret", "shared-secret-value")));
            t.contains("and the tampered send reports rejection",
                    text(call("secure-file-transfer", "tamper", "contents", "hello",
                            "secret", "shared-secret-value"), "result"), "Rejected");
        });

        h.group("067 Honeypot Server", t -> {
            t.equal("sql injection", HoneypotServer.Category.SQL_INJECTION,
                    HoneypotServer.classify("GET /index.php?id=1' OR 1=1--"));
            t.equal("union select", HoneypotServer.Category.SQL_INJECTION,
                    HoneypotServer.classify("GET /p?q=1 UNION SELECT password FROM users"));
            t.equal("path traversal", HoneypotServer.Category.PATH_TRAVERSAL,
                    HoneypotServer.classify("GET /download?f=../../../etc/passwd"));
            // Both a shell metacharacter and a sensitive path. The vector is
            // reported, because the shell is the hole that needs closing.
            t.equal("a shell metacharacter outranks the path it reads",
                    HoneypotServer.Category.COMMAND_INJECTION,
                    HoneypotServer.classify("GET /ping?h=127.0.0.1;cat /etc/shadow"));
            t.equal("script injection", HoneypotServer.Category.SCRIPT_INJECTION,
                    HoneypotServer.classify("GET /s?q=<script>alert(1)</script>"));
            t.equal("credential guessing", HoneypotServer.Category.CREDENTIAL_GUESS,
                    HoneypotServer.classify("POST /login admin:admin"));
            t.equal("scanner", HoneypotServer.Category.SCANNER,
                    HoneypotServer.classify("GET /wp-login.php"));

            // Encoded payloads are the normal case. Matching the raw request
            // misses "../" whenever it travels as %2e%2e%2f, which is usually.
            t.equal("percent-encoded traversal is decoded",
                    HoneypotServer.Category.PATH_TRAVERSAL,
                    HoneypotServer.classify("GET /..%2f..%2f..%2fetc%2fpasswd"));
            t.equal("double-encoded traversal is decoded",
                    HoneypotServer.Category.PATH_TRAVERSAL,
                    HoneypotServer.classify("GET /%252e%252e%252fetc%252fpasswd"));
            t.equal("but decoding stops at twice, rather than inventing payloads",
                    "%2e", HoneypotServer.decode("%25252e"));

            // On a decoy an unrecognised request is the interesting one.
            t.equal("an unrecognised probe is kept, not dropped",
                    HoneypotServer.Category.UNCLASSIFIED,
                    HoneypotServer.classify("GET /api/telemetry?build=42"));

            HoneypotServer pot = new HoneypotServer();
            pot.sample();
            t.equal("the whole burst is logged", 9, pot.hits().size());
            t.equal("including the unclassified one", 1,
                    (int) pot.byCategory().getOrDefault(
                            HoneypotServer.Category.UNCLASSIFIED, 0));
            t.equal("sources rank by volume", "203.0.113.9",
                    pot.bySource().get(0).getKey());
            // One probe type is a scanner working a list; four is a person.
            t.check("a source trying several attack types is flagged",
                    pot.persistent(3).stream().anyMatch(s -> s.startsWith("203.0.113.9")));
            t.check("a source trying one is not",
                    pot.persistent(3).stream().noneMatch(s -> s.startsWith("198.51.100.4")));
            t.check("an empty request is refused",
                    !ok(call("honeypot-server", "add", "label", "")));
        });
    }

    private static void keyAgreement(Harness h) {
        h.group("066 Encrypted Chat", t -> {
            var alice = EncryptedChatApp.generate("alice");
            var bob = EncryptedChatApp.generate("bob");
            var eve = EncryptedChatApp.generate("eve");

            // The property the whole scheme rests on: two parties who have
            // exchanged only public keys arrive at the same secret, and that
            // secret never crossed the wire in any form.
            byte[] aliceSide = EncryptedChatApp.sharedSecret(alice.privateKey(), bob.publicKey());
            byte[] bobSide = EncryptedChatApp.sharedSecret(bob.privateKey(), alice.publicKey());
            t.check("both sides derive an identical secret",
                    java.util.Arrays.equals(aliceSide, bobSide));
            t.equal("of a full 256 bits", 32, aliceSide.length);
            byte[] eveSide = EncryptedChatApp.sharedSecret(eve.privateKey(), alice.publicKey());
            t.check("a third party derives something else",
                    !java.util.Arrays.equals(aliceSide, eveSide));

            String cipher = EncryptedChatApp.encrypt("meet at six", aliceSide);
            t.equal("the recipient reads it", "meet at six",
                    EncryptedChatApp.decrypt(cipher, bobSide));
            t.check("the plaintext is not in the ciphertext", !cipher.contains("meet"));
            t.check("an eavesdropper cannot read it",
                    refused(() -> EncryptedChatApp.decrypt(cipher, eveSide)));
            t.check("the same message encrypts differently each time",
                    !EncryptedChatApp.encrypt("meet at six", aliceSide).equals(cipher));

            byte[] blob = java.util.Base64.getDecoder().decode(cipher);
            blob[blob.length - 3] ^= 0x01;
            String flipped = java.util.Base64.getEncoder().encodeToString(blob);
            t.check("a tampered message is refused, not decrypted",
                    refused(() -> EncryptedChatApp.decrypt(flipped, bobSide)));

            EncryptedChatApp chat = new EncryptedChatApp();
            var sent = chat.send("alice", "bob", "the package is here");
            t.equal("a sent message round-trips", "the package is here",
                    chat.read(sent.id(), "bob"));
            t.check("the server holds only ciphertext",
                    !sent.cipherText().contains("package"));
            chat.join("mallory");
            t.check("an outsider cannot read it",
                    refused(() -> chat.read(sent.id(), "mallory")));
            t.check("sending to yourself is refused",
                    refused(() -> chat.send("alice", "alice", "hi")));
            t.check("a message sends over the api",
                    ok(call("encrypted-chat-app", "add", "label", "hello",
                            "meta", "alice to bob")));
        });

        h.group("064 Keylogger Detector", t -> {
            KeyloggerDetector kd = new KeyloggerDetector();
            t.equal("a plain process is clean", Verdict.CLEAN,
                    kd.assess(kd.add("notepad.exe", EnumSet.noneOf(Signal.class))).verdict());

            // Every signal here has a legitimate owner. If any one of them
            // alone raised an alert, the tool would flag most of the machine,
            // and an alert list nobody can act on is the same as no alerts.
            for (Signal signal : Signal.values()) {
                Verdict alone = kd.assess(kd.add("thing.exe", EnumSet.of(signal))).verdict();
                t.check("one signal alone is not an alert: " + signal,
                        alone == Verdict.CLEAN || alone == Verdict.WORTH_A_LOOK);
            }

            var real = kd.assess(kd.add("svch0st.exe", EnumSet.of(Signal.KEYBOARD_HOOK,
                    Signal.HIDDEN_WINDOW, Signal.WRITES_LOG, Signal.NETWORK,
                    Signal.AUTOSTART, Signal.UNSIGNED, Signal.TEMP_DIRECTORY)));
            t.equal("but the combination is flagged", Verdict.LIKELY_KEYLOGGER, real.verdict());
            t.check("with its reasoning shown", real.reasons().size() >= 8);
            t.check("including why hooking plus network is the tell",
                    real.reasons().stream().anyMatch(r -> r.contains("sending is not")));

            var manager = kd.assess(kd.add("1password.exe",
                    EnumSet.of(Signal.KEYBOARD_HOOK, Signal.AUTOSTART, Signal.NETWORK)));
            t.check("a password manager is not flagged",
                    manager.verdict() == Verdict.CLEAN
                            || manager.verdict() == Verdict.WORTH_A_LOOK);
            t.check("and is marked as recognised", manager.allowlisted());

            // Allowlisting reduces the score rather than exiting early, so the
            // allowlist cannot be used as a hiding place.
            var hiding = kd.assess(kd.add("1password.exe", EnumSet.of(Signal.KEYBOARD_HOOK,
                    Signal.HIDDEN_WINDOW, Signal.WRITES_LOG, Signal.NETWORK,
                    Signal.AUTOSTART, Signal.UNSIGNED, Signal.TEMP_DIRECTORY)));
            t.check("naming yourself after allowlisted software does not hide you",
                    hiding.verdict() == Verdict.SUSPICIOUS
                            || hiding.verdict() == Verdict.LIKELY_KEYLOGGER);

            t.check("svch0st reads as generated", KeyloggerDetector.looksGenerated("svch0st.exe"));
            t.check("kbdhkstrm reads as generated", KeyloggerDetector.looksGenerated("kbdhkstrm"));
            t.check("notepad does not", !KeyloggerDetector.looksGenerated("notepad.exe"));
            t.check("chrome does not", !KeyloggerDetector.looksGenerated("chrome.exe"));
            t.check("ssh is too short to judge", !KeyloggerDetector.looksGenerated("ssh"));
            t.check("an unknown signal name is refused",
                    !ok(call("keylogger-detector", "add", "label", "x", "meta", "wearing_a_hat")));
        });
    }

    private static void scanning(Harness h) {
        h.group("063 Port Scanner", t -> {
            PortScanner ps = new PortScanner();
            t.equal("ssh is named", "ssh", PortScanner.service(22));
            t.equal("postgres is named", "postgresql", PortScanner.service(5432));
            t.equal("an unlisted port says so", "unknown", PortScanner.service(54321));

            // Pointed at anything but this machine, the same code is
            // reconnaissance. It refuses rather than leaving that to judgement.
            t.equal("localhost resolves", "127.0.0.1", PortScanner.resolve("localhost"));
            t.check("another host is refused",
                    refused(() -> PortScanner.resolve("example.com")));
            t.check("a private address is refused too",
                    refused(() -> PortScanner.resolve("192.168.1.1")));

            // A real listener, so this tests the socket code rather than a mock.
            try (java.net.ServerSocket listener = new java.net.ServerSocket(0)) {
                int port = listener.getLocalPort();
                t.equal("a listening port is reported open", PortScanner.State.OPEN,
                        ps.probe("localhost", port).state());
            } catch (java.io.IOException e) {
                t.check("could not open a test listener: " + e.getMessage(), false);
            }

            // Closed and filtered are different findings. A refusal proves the
            // port is free; a timeout proves nothing, and reporting one as the
            // other claims something that was never observed.
            try {
                int freed;
                try (java.net.ServerSocket temporary = new java.net.ServerSocket(0)) {
                    freed = temporary.getLocalPort();
                }
                var closed = ps.probe("localhost", freed);
                t.equal("a free port is refused, not timed out",
                        PortScanner.State.CLOSED, closed.state());
                t.check("and a refusal comes back fast", closed.millis() < 250);
            } catch (java.io.IOException e) {
                t.check("could not free a test port: " + e.getMessage(), false);
            }

            t.check("port 0 is refused", refused(() -> ps.probe("localhost", 0)));
            t.check("port 70000 is refused", refused(() -> ps.probe("localhost", 70000)));
            t.equal("a range is inclusive", List.of(10, 11, 12), ps.range(10, 12));
            t.check("a backwards range is refused", refused(() -> ps.range(20, 10)));
            t.check("an enormous scan is refused",
                    refused(() -> ps.scan("localhost", ps.range(1, 3000))));
            t.check("a silly timeout is refused", refused(() -> ps.setTimeout(0)));

            // Serial probing of 40 ports at 250 ms each would be 10 seconds.
            try (java.net.ServerSocket listener = new java.net.ServerSocket(0)) {
                int port = listener.getLocalPort();
                List<Integer> ports = new java.util.ArrayList<>();
                for (int p = port; p < port + 40; p++) { ports.add(p); }
                long started = System.currentTimeMillis();
                var results = ps.scan("localhost", ports);
                long elapsed = System.currentTimeMillis() - started;
                t.equal("every port is accounted for", 40, results.size());
                t.check("and the scan is parallel, not serial",
                        elapsed < 40L * ps.timeout() / 2);
                t.check("the listener is found",
                        ps.open().stream().anyMatch(r -> r.port() == port));
            } catch (java.io.IOException e) {
                t.check("could not run the parallel scan: " + e.getMessage(), false);
            }

            t.check("the api scans the common ports",
                    ok(call("port-scanner", "add", "label", "common")));
            t.check("and rejects nonsense",
                    !ok(call("port-scanner", "add", "label", "not-a-port")));
        });
    }

    private static void arcade(Harness h) {
        h.group("071 Snake", t -> {
            Snake s = new Snake(12, new java.util.Random(1));
            t.check("it starts alive", s.playing());
            t.equal("with a length of 2", 2, s.length());

            // Obeying a 180 degree turn walks the head into the neck and ends
            // the game on an input the player did not mean as suicide.
            t.check("turning back on itself is rejected", !s.turn(Snake.Direction.LEFT));
            t.equal("and the heading is unchanged", Snake.Direction.RIGHT, s.heading());
            t.check("a quarter turn is accepted", s.turn(Snake.Direction.UP));

            // Judged against the real heading, not a queued turn: right, then
            // up, then down lands on down, a legal quarter turn from right.
            Snake queued = new Snake(12, new java.util.Random(2));
            queued.turn(Snake.Direction.UP);
            t.check("a quarter turn is allowed even with one already queued",
                    queued.turn(Snake.Direction.DOWN));
            queued.step();
            t.equal("and the last one takes effect", Snake.Direction.DOWN, queued.heading());
            t.check("while a true reversal is still refused",
                    !queued.turn(Snake.Direction.UP));

            Snake wall = new Snake(8, new java.util.Random(3));
            for (int i = 0; i < 20 && wall.playing(); i++) { wall.step(); }
            t.equal("driving straight ends at the wall", Snake.Status.HIT_WALL, wall.status());

            // The tail vacates its square on the same tick, so it is not an
            // obstacle. Checking the head against the whole body makes chasing
            // your own tail fatal - and that is constant in real play.
            Snake tail = new Snake(6, new java.util.Random(4));
            boolean survived = true;
            for (int lap = 0; lap < 3 && survived; lap++) {
                for (Snake.Direction d : new Snake.Direction[]{Snake.Direction.DOWN,
                        Snake.Direction.LEFT, Snake.Direction.UP, Snake.Direction.RIGHT}) {
                    tail.turn(d);
                    tail.step();
                    if (!tail.playing()) { survived = false; break; }
                }
            }
            t.check("a snake can chase its own tail", survived);

            Snake eat = new Snake(9, new java.util.Random(5));
            int before = eat.length();
            int guard = 400;
            while (eat.playing() && eat.score() == 0 && guard-- > 0) { eat.autoStep(); }
            t.equal("eating scores a point", 1, eat.score());
            t.equal("and grows by exactly one", before + 1, eat.length());

            // Guessing squares until one is free slows down as the board fills
            // and never returns once it is full.
            Snake food = new Snake(7, new java.util.Random(6));
            boolean clean = true;
            for (int i = 0; i < 300 && food.playing(); i++) {
                food.autoStep();
                if (food.food() != null && food.body().contains(food.food())) {
                    clean = false;
                    break;
                }
            }
            t.check("food never spawns under the snake", clean);
            t.check("a tiny board is refused",
                    refused(() -> new Snake(3, new java.util.Random())));
            t.check("the api turns and ticks",
                    ok(call("snake", "turn", "direction", "UP")));
            t.check("and rejects a nonsense direction",
                    !ok(call("snake", "turn", "direction", "sideways")));
        });

        h.group("072 Tetris", t -> {
            // O sits in a 2x2 box, so the rotation arithmetic leaves it alone
            // without needing to be special-cased.
            t.check("O is unchanged by rotation", sameCells(Shape.O.at(0), Shape.O.at(1)));
            t.check("T genuinely turns", !sameCells(Shape.T.at(0), Shape.T.at(1)));
            for (Shape shape : Shape.values()) {
                t.check("four turns is the identity: " + shape,
                        sameCells(shape.at(0), shape.at(4)));
                t.equal("and " + shape + " keeps four cells", 4, shape.at(1).length);
            }

            // A rotation that does not fit where it stands is shifted sideways
            // until it does. Refusing outright is playable but reads as broken,
            // because the piece obviously has room a column over.
            Tetris kick = new Tetris(18, 10, new java.util.Random(7));
            kick.spawn(Shape.I);
            kick.rotate();
            while (kick.move(-1)) { /* shove the vertical bar to the wall */ }
            int before = kick.pieceCol();
            boolean turned = kick.rotate();
            t.check("a rotation against the wall succeeds", turned);
            t.check("by kicking sideways rather than refusing", kick.pieceCol() != before);
            t.check("and the result is on the board",
                    kick.fits(kick.pieceRow(), kick.pieceCol(), kick.rotation()));

            // Removing rows in place shifts untested rows into indices the loop
            // has passed, so four adjacent lines clear as three.
            Tetris four = new Tetris(18, 10, new java.util.Random(8));
            for (int row = 14; row <= 17; row++) { four.fillRow(row); }
            t.equal("four full rows all clear", 4, four.clearLines());
            t.equal("and are counted as four", 4, four.lines());
            t.equal("a tetris scores more than four singles", 800, four.score());
            t.check("the board is empty afterwards", allEmpty(four.settled()));

            Tetris split = new Tetris(18, 10, new java.util.Random(9));
            split.fillRow(17);
            split.fillRow(15);
            t.equal("two non-adjacent rows both clear", 2, split.clearLines());

            Tetris keep = new Tetris(18, 10, new java.util.Random(10));
            keep.fillRow(17, 3);
            t.equal("an incomplete row is left alone", 0, keep.clearLines());
            t.equal("with its contents intact", 'X', keep.settled()[17][0]);

            Tetris blocked = new Tetris(8, 10, new java.util.Random(11));
            for (int row = 0; row < 8; row++) { blocked.fillRow(row); }
            blocked.spawn(Shape.T);
            t.check("spawning into a full board ends the game", blocked.over());

            Tetris drop = new Tetris(18, 10, new java.util.Random(12));
            t.check("a hard drop moves the piece down", drop.hardDrop() > 0);
            t.check("and lands it", !allEmpty(drop.settled()));
            t.check("a silly board size is refused",
                    refused(() -> new Tetris(2, 2, new java.util.Random())));
            t.check("a gap outside the board is refused",
                    refused(() -> new Tetris(18, 10, new java.util.Random(13)).fillRow(0, -1)));
            t.check("the api drops a piece", ok(call("tetris", "solve")));
        });
    }

    /** Cell sets, order-independent. */
    private static boolean sameCells(int[][] a, int[][] b) {
        java.util.Set<String> first = new java.util.TreeSet<>();
        java.util.Set<String> second = new java.util.TreeSet<>();
        for (int[] cell : a) { first.add(cell[0] + "," + cell[1]); }
        for (int[] cell : b) { second.add(cell[0] + "," + cell[1]); }
        return first.equals(second);
    }

    private static boolean allEmpty(char[][] board) {
        for (char[] row : board) {
            for (char cell : row) { if (cell != '.') { return false; } }
        }
        return true;
    }

    private static void boardGames(Harness h) {
        h.group("073 Memory Matching", t -> {
            // Measured, not asserted. Three cards have six arrangements, and a
            // fair shuffle should hit each about a sixth of the time. The naive
            // swap-with-anywhere shuffle produces n^n sequences over n!
            // arrangements, and n^n is not divisible by n!, so it cannot be even.
            double fairSpread = spread(true);
            double naiveSpread = spread(false);
            t.check("Fisher-Yates is even across all six arrangements", fairSpread < 3.0);
            t.check("the naive shuffle is measurably biased", naiveSpread > 10.0);
            t.check("and the difference is large, not marginal",
                    naiveSpread > fairSpread * 5);

            MemoryMatchingGame g = new MemoryMatchingGame(4, 4, new java.util.Random(1));
            t.equal("a 4x4 deal makes 8 pairs", 8, g.pairs());
            t.check("an odd board has no pairing and is refused",
                    refused(() -> new MemoryMatchingGame(3, 3, new java.util.Random())));

            java.util.Map<Character, Integer> faces = new java.util.HashMap<>();
            for (int i = 0; i < 16; i++) { faces.merge(g.card(i).face(), 1, Integer::sum); }
            t.check("every face appears exactly twice, so the board is winnable",
                    faces.values().stream().allMatch(v -> v == 2));

            int first = -1;
            int second = -1;
            outer:
            for (int i = 0; i < 16; i++) {
                for (int j = i + 1; j < 16; j++) {
                    if (g.card(i).face() == g.card(j).face()) {
                        first = i;
                        second = j;
                        break outer;
                    }
                }
            }
            g.flip(first);
            t.equal("one card turns face up", 1, g.faceUp().size());
            g.flip(second);
            t.equal("a matching pair is found", 1, g.pairsFound());
            t.check("and stays revealed", g.card(first).matched());

            // A mismatch has to stay visible to be worth anything, then clear
            // when the player moves on - not stack up a third face-up card.
            int x = -1;
            int y = -1;
            for (int i = 0; i < 16 && x < 0; i++) {
                if (g.card(i).matched()) { continue; }
                for (int j = i + 1; j < 16; j++) {
                    if (!g.card(j).matched() && g.card(i).face() != g.card(j).face()) {
                        x = i;
                        y = j;
                        break;
                    }
                }
            }
            g.flip(x);
            g.flip(y);
            t.equal("a mismatch leaves both showing", 2, g.faceUp().size());
            int third = -1;
            for (int i = 0; i < 16; i++) {
                if (!g.card(i).matched() && i != x && i != y) { third = i; break; }
            }
            g.flip(third);
            t.check("and the next flip clears them rather than adding a third",
                    g.faceUp().size() == 1 && g.faceUp().contains(third));
            t.check("an off-board card is refused", refused(() -> g.flip(99)));
            t.check("the api deals and flips", ok(call("memory-matching-game", "generate")));
        });

        h.group("078 Word Scramble", t -> {
            // A scramble that hands back the original is not a puzzle, and on a
            // three-letter word that is one shuffle in six.
            java.util.Random r = new java.util.Random(9);
            int unchanged = 0;
            for (int i = 0; i < 5000; i++) {
                if (WordScramble.scramble("cat", r).equals("cat")) { unchanged++; }
            }
            t.equal("a scramble never returns the original", 0, unchanged);

            // Some words genuinely cannot be rearranged. That is a fact about
            // the word, so it returns rather than looping looking for a change.
            t.equal("a word of one repeated letter comes back as-is", "aaa",
                    WordScramble.scramble("aaa", new java.util.Random()));
            t.check("and is reported as unscrambleable", !WordScramble.canScramble("aaa"));
            t.equal("a single letter is safe", "a",
                    WordScramble.scramble("a", new java.util.Random()));
            t.check("null does not throw",
                    WordScramble.scramble(null, new java.util.Random()) != null);
            t.equal("scrambling preserves the letters",
                    WordScramble.letters("orchestra"),
                    WordScramble.letters(WordScramble.scramble("orchestra",
                            new java.util.Random(3))));

            // "Silent" is a correct unscrambling of the letters in "listen".
            // Comparing against the one word chosen marks a right answer wrong.
            WordScramble ws = new WordScramble(new java.util.Random(5));
            boolean severalAnswers = false;
            for (int i = 0; i < 200; i++) {
                if (ws.accepted().size() > 1) { severalAnswers = true; break; }
                ws.next();
            }
            t.check("some rounds have several valid answers", severalAnswers);
            t.check("and any of them is accepted",
                    ws.guess(ws.accepted().get(ws.accepted().size() - 1)));
            t.check("scoring one is worth points", ws.score() > 0);

            WordScramble strict = new WordScramble(new java.util.Random(11));
            t.check("a wrong word is rejected", !strict.guess("zzzzzz"));
            t.equal("and resets the streak", 0, strict.streak());
            t.check("a non-anagram is identified as such", !strict.isRearrangement("zzzzzz"));
            t.equal("letters() is order independent",
                    WordScramble.letters("listen"), WordScramble.letters("silent"));
            t.check("an empty guess is refused",
                    !ok(call("word-scramble", "guess", "guess", "")));
        });
    }

    /** Percentage spread between the most and least common arrangement of 3 cards. */
    private static double spread(boolean fisherYates) {
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        java.util.Random r = new java.util.Random(42);
        int trials = 60_000;
        for (int i = 0; i < trials; i++) {
            char[] cards = {'A', 'B', 'C'};
            if (fisherYates) { MemoryMatchingGame.shuffle(cards, r); }
            else { MemoryMatchingGame.biasedShuffle(cards, r); }
            counts.merge(new String(cards), 1, Integer::sum);
        }
        int min = java.util.Collections.min(counts.values());
        int max = java.util.Collections.max(counts.values());
        return (max - min) * 100.0 / (trials / 6.0);
    }

    private static void liveGames(Harness h) {
        h.group("079 Virtual Pet", t -> {
            VirtualPet p = new VirtualPet("Pip");
            t.check("starts alive", p.alive());
            // Every action has to cost something, or one button is the whole game.
            int energy = p.energy();
            p.feed();
            t.check("feeding raises food", p.food() > 60);
            t.check("but costs energy", p.energy() < energy);

            VirtualPet tired = new VirtualPet("T");
            tired.tick(25);
            t.contains("an exhausted pet cannot play", tired.play(), "too tired");

            // Decay has to outrun any single action or the clock stops mattering.
            VirtualPet fed = new VirtualPet("F");
            for (int i = 0; i < 12; i++) { fed.feed(); }
            t.check("feeding on repeat does not keep everything perfect",
                    fed.energy() < 40 || fed.fun() < 40);

            // Health only moves while a need is actually empty, so a bad hour
            // is survivable and a bad day is not. Draining on merely-low stats
            // makes an early mistake unrecoverable, which reads as unfair.
            VirtualPet dip = new VirtualPet("D");
            dip.tick(12);
            t.check("a short dip does not kill", dip.alive());
            t.check("but does show in the mood", dip.mood() != VirtualPet.Mood.CONTENT);

            VirtualPet neglected = new VirtualPet("N");
            neglected.tick(200);
            t.check("sustained neglect is fatal", !neglected.alive());
            t.equal("and the mood says so", VirtualPet.Mood.GONE, neglected.mood());
            int age = neglected.age();
            neglected.tick(10);
            t.equal("a dead pet does not age on", age, neglected.age());

            VirtualPet cared = new VirtualPet("C");
            for (int i = 0; i < 60; i++) {
                if (cared.food() < 40) { cared.feed(); }
                else if (cared.energy() < 40) { cared.rest(); }
                else { cared.play(); }
            }
            t.check("a well-kept pet survives an hour", cared.alive());
            t.check("and stays healthy", cared.health() > 50);
            t.check("stats never leave 0..100",
                    cared.food() >= 0 && cared.food() <= VirtualPet.MAX
                    && cared.health() >= 0 && cared.health() <= VirtualPet.MAX);
            t.check("the api feeds", ok(call("virtual-pet", "feed")));
        });

        h.group("077 Space Shooter", t -> {
            SpaceShooter s = new SpaceShooter(14, 12, new java.util.Random(1));
            t.check("a wave spawns", !s.enemies().isEmpty());
            t.equal("three lives", 3, s.lives());

            for (int i = 0; i < 50; i++) { s.moveShip(-1); }
            t.equal("the ship stops at the left edge", 0, s.shipColumn());
            for (int i = 0; i < 50; i++) { s.moveShip(1); }
            t.equal("and at the right", 11, s.shipColumn());

            // One bullet per column, so holding fire is not a win button.
            SpaceShooter f = new SpaceShooter(14, 12, new java.util.Random(3));
            t.check("the first shot fires", f.fire());
            t.check("a second in the same column does not", !f.fire());

            // Everything moves, then the world resolves once. Resolving between
            // moves gives a bullet two chances at the same enemy in one tick.
            SpaceShooter hit = new SpaceShooter(14, 12, new java.util.Random(4));
            for (int i = 0; i < 200 && hit.score() == 0 && !hit.over(); i++) {
                SpaceShooter.Point target = null;
                for (var e : hit.enemies()) {
                    if (target == null || e.row() > target.row()) { target = e; }
                }
                if (target != null) {
                    hit.moveShip(Integer.compare(target.col(), hit.shipColumn()));
                    if (target.col() == hit.shipColumn()) { hit.fire(); }
                }
                hit.step();
            }
            t.check("a tracked enemy is destroyed", hit.score() > 0);

            SpaceShooter ignored = new SpaceShooter(10, 8, new java.util.Random(5));
            ignored.step(400);
            t.check("ignoring the enemies ends the game", ignored.over());
            t.check("and lives never go negative", ignored.lives() >= 0);
            t.check("a silly board is refused",
                    refused(() -> new SpaceShooter(2, 2, new java.util.Random())));
            t.check("the api fires", ok(call("space-shooter", "fire")));
        });
    }

    private static void strategy(Harness h) {
        h.group("080 Tower Defense", t -> {
            TowerDefense td = new TowerDefense(9, 14, new java.util.Random(1));
            var path = td.path();
            t.check("a path exists", !path.isEmpty());
            t.equal("starting at the left edge", 0, path.get(0).col());
            t.equal("and reaching the right", 13, path.get(path.size() - 1).col());

            // Every consecutive pair must be adjacent or creeps teleport, and
            // the tower range check stops meaning anything.
            boolean joined = true;
            for (int i = 1; i < path.size(); i++) {
                int dr = Math.abs(path.get(i).row() - path.get(i - 1).row());
                int dc = Math.abs(path.get(i).col() - path.get(i - 1).col());
                if (dr + dc != 1) { joined = false; break; }
            }
            t.check("the path is connected step by step", joined);

            // Without this rule the best play is to wall the path off entirely.
            t.check("building on the path is refused",
                    refused(() -> td.place(path.get(3).row(), path.get(3).col())));

            int br = -1;
            int bc = -1;
            outer:
            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 14; c++) {
                    if (!td.onPath(new TowerDefense.Cell(r, c))) { br = r; bc = c; break outer; }
                }
            }
            final int row = br;
            final int col = bc;
            td.place(row, col);
            t.equal("a tower off the path is built", 1, td.towers().size());
            t.equal("and costs money", 150 - TowerDefense.TOWER_COST, td.money());
            t.check("the same square twice is refused", refused(() -> td.place(row, col)));
            t.check("off the map is refused", refused(() -> td.place(99, 99)));

            TowerDefense naked = new TowerDefense(9, 14, new java.util.Random(2));
            naked.startWave();
            naked.runWave();
            t.check("undefended creeps get through", naked.lives() < 10);

            // Waves must escalate or the game never ends.
            TowerDefense escalate = new TowerDefense(9, 14, new java.util.Random(4));
            escalate.startWave();
            int firstCount = escalate.creeps().size();
            int firstHealth = escalate.creeps().get(0).maxHealth();
            escalate.runWave();
            escalate.startWave();
            t.check("later waves send more creeps", escalate.creeps().size() > firstCount);
            t.check("and tougher ones",
                    escalate.creeps().get(0).maxHealth() > firstHealth);

            TowerDefense doomed = new TowerDefense(9, 14, new java.util.Random(5));
            for (int i = 0; i < 20 && !doomed.over(); i++) {
                doomed.startWave();
                doomed.runWave();
            }
            t.check("enough leaks ends the game", doomed.over());
            t.check("lives never go negative", doomed.lives() >= 0);
            t.check("a silly map is refused",
                    refused(() -> new TowerDefense(2, 2, new java.util.Random())));
            t.check("the api builds a map", ok(call("tower-defense", "generate")));
        });
    }

    private static void chess(Harness h) {
        h.group("075 Chess Engine", t -> {
            ChessEngine e = new ChessEngine();

            // Perft. These counts are published and exact, so if anything in
            // move generation is wrong - a pawn's double step, a knight's
            // wrap-around, a pinned piece allowed to move - they do not match.
            t.equal("20 legal moves at the start", 20, e.legalMoves().size());
            int perft2 = 0;
            for (var m : e.legalMoves()) {
                e.apply(m);
                perft2 += e.legalMoves().size();
                e.undo(m);
            }
            t.equal("perft(2) is 400", 400, perft2);

            int perft3 = 0;
            for (var m1 : e.legalMoves()) {
                e.apply(m1);
                for (var m2 : e.legalMoves()) {
                    e.apply(m2);
                    perft3 += e.legalMoves().size();
                    e.undo(m2);
                }
                e.undo(m1);
            }
            t.equal("perft(3) is 8902", 8902, perft3);

            // Every perft number above depends on undo being exact.
            ChessEngine u = new ChessEngine();
            String before = render(u);
            for (var m : u.legalMoves()) { u.apply(m); u.undo(m); }
            t.equal("undo restores the position exactly", before, render(u));
            t.check("and whose turn it is", u.whiteToMove());

            // A pinned piece looks perfectly mobile from its own square. The
            // only way to know is to play the move and test the king.
            ChessEngine pin = new ChessEngine();
            pin.setPosition(new String[]{
                "....k...", "........", "........", "........",
                "....r...", "....N...", "....K...", "........"}, true);
            t.check("a pinned piece cannot move",
                    pin.legalMoves().stream()
                       .noneMatch(m -> m.fromRow() == 5 && m.fromCol() == 4));

            ChessEngine chk = new ChessEngine();
            chk.setPosition(new String[]{
                "....k...", "........", "........", "........",
                "........", "........", "....r...", "....K..."}, true);
            t.check("check is recognised", chk.inCheck());
            t.check("and every legal move answers it",
                    chk.legalMoves().stream().allMatch(m -> {
                        chk.apply(m);
                        boolean safe = !chk.kingAttacked(true);
                        chk.undo(m);
                        return safe;
                    }));

            // Back-rank mate: the rook covers g1 as well as h1, and White's own
            // pawns block the escape squares.
            ChessEngine mate = new ChessEngine();
            mate.setPosition(new String[]{
                "....k...", "........", "........", "........",
                "........", "........", "......PP", "r......K"}, true);
            t.check("checkmate is detected", mate.checkmate());
            t.check("and is not called stalemate", !mate.stalemate());

            // h1 is not attacked, but everywhere it could go is. Drawn.
            ChessEngine stale = new ChessEngine();
            stale.setPosition(new String[]{
                "....k...", "........", "........", "........",
                "........", "........", ".....q..", ".......K"}, true);
            t.check("no legal moves", stale.legalMoves().isEmpty());
            t.check("but not in check", !stale.inCheck());
            t.check("so it is stalemate, not mate",
                    stale.stalemate() && !stale.checkmate());

            // Alpha-beta only skips branches that cannot change the result, so
            // it has to agree with the full search. If it disagrees, the bounds
            // are wrong and the pruning is losing real moves.
            ChessEngine s = new ChessEngine();
            var plain = s.search(3, false);
            var pruned = s.search(3, true);
            t.equal("pruning reaches the same evaluation", plain.score(), pruned.score());
            t.check("having searched far fewer positions",
                    pruned.nodes() < plain.nodes() / 2);

            ChessEngine grab = new ChessEngine();
            grab.setPosition(new String[]{
                "....k...", "........", "........", "...q....",
                "....B...", "........", "........", "....K..."}, true);
            var take = grab.search(2, true);
            t.check("the engine takes a hanging queen",
                    take.best().toRow() == 3 && take.best().toCol() == 3);

            t.check("a bad move string is refused", refused(() -> e.parseMove("zz99")));
            t.check("an illegal move is refused", refused(() -> e.parseMove("e2e5")));
            t.check("a silly depth is refused", refused(() -> e.search(9, true)));
            t.check("the api plays a move",
                    ok(call("chess-engine", "move", "move", "e2e4")));
        });
    }

    private static String render(ChessEngine engine) {
        StringBuilder sb = new StringBuilder();
        for (char[] row : engine.cells()) { sb.append(row).append('/'); }
        return sb.toString();
    }

    private static void physics(Harness h) {
        h.group("074 Platformer", t -> {
            Platformer2d fall = new Platformer2d(new String[]{
                "........", "........", "........", "........",
                "........", "........", "........", "########"});
            int startY = fall.y();
            fall.step(0);
            t.check("gravity pulls down", fall.y() > startY || fall.grounded());
            for (int i = 0; i < 10; i++) { fall.step(0); }
            t.check("falling stops at the floor", fall.grounded());
            t.equal("and speed is zeroed on landing", 0, fall.verticalSpeed());

            // Resolving both axes together means a wall cancels the whole move,
            // so running into one freezes the player instead of stopping them.
            Platformer2d wall = new Platformer2d(new String[]{
                "........", "........", "........", "........",
                "...#....", "...#....", "...#....", "########"});
            while (!wall.grounded()) { wall.step(0); }
            for (int i = 0; i < 10; i++) { wall.step(1); }
            t.equal("a wall stops horizontal movement", 2, wall.x());
            t.check("but the player stays grounded, not frozen", wall.grounded());

            Platformer2d j = new Platformer2d(new String[]{
                "........", "........", "........", "........",
                "........", "........", "........", "########"});
            while (!j.grounded()) { j.step(0); }
            t.check("a grounded jump works", j.jump());
            t.check("and leaves the ground", !j.grounded());
            t.check("a second jump in the air is refused", !j.jump());
            for (int i = 0; i < 12; i++) { j.step(0); }
            t.check("and it comes back down", j.grounded());

            // Landing is recorded when a downward move is stopped. Sampling the
            // tile below instead misses a floor the player crossed in one step.
            Platformer2d thin = new Platformer2d(new String[]{
                "........", "........", "........", "........",
                "........", "........", "########", "........"});
            for (int i = 0; i < 12; i++) { thin.step(0); }
            t.check("a fast fall does not tunnel through a one-tile floor",
                    thin.grounded());
            t.check("and does not fall out of the world", !thin.fellOut());

            Platformer2d coins = new Platformer2d(new String[]{
                "........", "..ooo...", "########", "........",
                "........", "........", "........", "########"});
            t.equal("coins are counted", 3, coins.coinsTotal());
            for (int i = 0; i < 20; i++) { coins.step(1); }
            t.check("walking over a coin collects it", coins.coins() > 0);
            t.check("and never more than exist", coins.coins() <= coins.coinsTotal());

            Platformer2d goal = new Platformer2d(new String[]{
                "........", "......G.", "########", "........",
                "........", "........", "........", "########"});
            for (int i = 0; i < 20 && !goal.finished(); i++) { goal.step(1); }
            t.check("reaching the goal finishes the level", goal.finished());

            Platformer2d edge = new Platformer2d(new String[]{
                "........", "........", "........", "........",
                "........", "........", "........", "########"});
            for (int i = 0; i < 30; i++) { edge.step(-1); }
            t.check("the left edge holds the player in", edge.x() >= 0);
            for (int i = 0; i < 40; i++) { edge.step(1); }
            t.check("and the right edge too", edge.x() <= 7);

            t.check("a ragged map is refused",
                    refused(() -> new Platformer2d(new String[]{"###", "##", "###"})));
            t.check("the api runs right", ok(call("platformer-2d", "right")));
        });
    }

    private static void systems(Harness h) {
        h.group("114 Blood Donation", t -> {
            // Compatibility is directional. Stored as an undirected relation -
            // a set of pairs, a symmetric matrix - the universal donor and
            // universal recipient come out backwards, and those are exactly the
            // two types that matter in an emergency.
            t.equal("O- gives to all 8 types", 8,
                    BloodDonationFinder.countCanDonateTo(BloodType.O_NEG));
            t.equal("but receives from only itself", 1,
                    BloodDonationFinder.countCanReceive(BloodType.O_NEG));
            t.equal("AB+ receives from all 8", 8,
                    BloodDonationFinder.countCanReceive(BloodType.AB_POS));
            t.equal("but gives only to itself", 1,
                    BloodDonationFinder.countCanDonateTo(BloodType.AB_POS));
            t.check("O- can give to AB+", BloodType.O_NEG.canDonateTo(BloodType.AB_POS));
            t.check("AB+ cannot give to O-", !BloodType.AB_POS.canDonateTo(BloodType.O_NEG));

            int asymmetric = 0;
            for (BloodType x : BloodType.values()) {
                for (BloodType y : BloodType.values()) {
                    if (x.canDonateTo(y) != y.canDonateTo(x)) { asymmetric++; }
                }
            }
            t.check("direction matters for most ordered pairs", asymmetric >= 20);
            t.check("A- gives to A+", BloodType.A_NEG.canDonateTo(BloodType.A_POS));
            t.check("A+ does not give to A-", !BloodType.A_POS.canDonateTo(BloodType.A_NEG));
            t.check("A and B never cross", !BloodType.A_POS.canDonateTo(BloodType.B_POS));

            BloodDonationFinder f = new BloodDonationFinder();
            t.check("only O- donors match an O- patient",
                    f.donorsFor(BloodType.O_NEG, "").stream()
                     .allMatch(d -> d.type() == BloodType.O_NEG));
            t.check("an AB+ patient matches more donors",
                    f.donorsFor(BloodType.AB_POS, "").size()
                            > f.donorsFor(BloodType.O_NEG, "").size());
            t.check("donors inside the 56-day window are excluded",
                    f.donorsFor(BloodType.AB_POS, "").stream()
                     .allMatch(d -> d.lastDonatedDaysAgo() >= 56));
            t.equal("\"O positive\" parses", BloodType.O_POS, BloodType.parse("O positive"));
            t.check("nonsense is refused", refused(() -> BloodType.parse("purple")));
        });

        h.group("121 Blockchain Voting", t -> {
            BlockchainVoting v = new BlockchainVoting();
            v.cast("ana", "Green");
            v.cast("ben", "Blue");
            v.cast("cara", "Green");
            t.check("the chain verifies", v.verify().valid());
            t.equal("each block links to the one before",
                    v.chain().get(0).hash(), v.chain().get(1).previousHash());
            t.check("nobody votes twice", refused(() -> v.cast("ana", "Blue")));

            // Editing a block without rehashing must be visible, and visible at
            // the block that changed.
            v.tamper(1, "Green");
            var audit = v.verify();
            t.check("tampering is detected", !audit.valid());
            t.equal("and points at the altered block", 1, audit.firstBadBlock());

            // The honest limit. Whoever holds the chain can rewrite it and
            // rehash everything after, and the result verifies perfectly. A
            // hash chain makes edits visible; only consensus makes them costly.
            BlockchainVoting r = new BlockchainVoting();
            r.cast("ana", "Green");
            r.cast("ben", "Blue");
            r.cast("cara", "Green");
            r.rewrite(0, "Blue");
            t.check("a full rewrite still verifies", r.verify().valid());
            t.equal("and the vote really changed", 2, (int) r.tally().get("Blue"));
            t.check("hashes are 64 hex characters",
                    BlockchainVoting.sha256("x").matches("[0-9a-f]{64}"));
        });

        h.group("076 Multiplayer Trivia", t -> {
            MultiplayerTrivia game = new MultiplayerTrivia(new java.util.Random(1));
            t.check("a duplicate player is refused", refused(() -> game.join("Alex")));
            t.equal("answering instantly earns the full bonus",
                    MultiplayerTrivia.MAX_SPEED_BONUS, MultiplayerTrivia.speedBonus(0));
            t.equal("answering at the buzzer earns none", 0,
                    MultiplayerTrivia.speedBonus(MultiplayerTrivia.TIME_LIMIT));
            t.check("the bonus decays with time",
                    MultiplayerTrivia.speedBonus(5) > MultiplayerTrivia.speedBonus(15));
            t.equal("past the limit it is zero, not negative", 0,
                    MultiplayerTrivia.speedBonus(999));

            // First-correct-takes-all means one fast player wins every round
            // and everyone else stops playing. Speed is a bonus, not the rule.
            int right = game.current().answer();
            int wrong = (right + 1) % game.current().options().size();
            var quickWrong = game.answer("Alex", wrong, 0);
            var slowRight = game.answer("Bo", right, 19);
            t.equal("a wrong answer scores nothing", 0, quickWrong.points());
            t.check("a slow correct answer still scores",
                    slowRight.points() >= MultiplayerTrivia.BASE_POINTS);
            t.check("so right beats quick", slowRight.points() > quickWrong.points());
            t.check("everyone has answered", game.roundComplete());
            t.check("answering twice is refused", refused(() -> game.answer("Alex", right, 1)));
            game.nextQuestion();
            t.check("a new round resets who answered", !game.roundComplete());
            t.check("but keeps the scores", game.leaderboard().get(0).score() > 0);
        });

        h.group("122 Decentralized Storage", t -> {
            DecentralizedFileStorage s = new DecentralizedFileStorage();
            String content = "The quick brown fox jumps over the lazy dog, at some length.";
            var file = s.store("notes.txt", content);
            t.equal("the file round-trips", content, s.retrieve("notes.txt"));
            t.check("split into several shards", file.shardIds().size() > 1);
            t.equal("identical content gets an identical address",
                    DecentralizedFileStorage.address("abc"),
                    DecentralizedFileStorage.address("abc"));
            t.check("different content does not",
                    !DecentralizedFileStorage.address("abc")
                            .equals(DecentralizedFileStorage.address("abd")));

            t.check("every shard starts with three copies",
                    file.shardIds().stream().allMatch(id -> s.healthOf(id) == 3));
            s.setOnline("node-1", false);
            s.setOnline("node-2", false);
            t.equal("two nodes down and it still rebuilds", content, s.retrieve("notes.txt"));

            // Returning a file with a hole in it - silently wrong data that
            // looks like success - is the worst available behaviour.
            DecentralizedFileStorage dead = new DecentralizedFileStorage();
            dead.store("doc.txt", content);
            for (int i = 1; i <= 6; i++) { dead.setOnline("node-" + i, false); }
            t.check("an unrecoverable file fails rather than truncating",
                    refused(() -> dead.retrieve("doc.txt")));

            // Rehashing what a node returns is the integrity check that content
            // addressing gives for free.
            DecentralizedFileStorage c = new DecentralizedFileStorage();
            var stored = c.store("data.txt", content);
            String shard = stored.shardIds().get(0);
            int before = c.healthOf(shard);
            for (int i = 1; i <= 6; i++) { if (c.corrupt("node-" + i, shard)) { break; } }
            t.equal("a corrupted replica stops counting", before - 1, c.healthOf(shard));
            t.equal("and retrieval routes around it", content, c.retrieve("data.txt"));
            t.check("an unknown file is refused", refused(() -> s.retrieve("nope.txt")));
            t.check("too many replicas is refused", refused(() -> s.setReplicas(99)));
        });
    }

    private static void gridPuzzles(Harness h) {
        h.group("224 Sliding 15 Puzzle", t -> {
            // Half of all arrangements are unreachable, and shuffling the tiles
            // at random produces one about half the time - with nothing to see.
            int[] impossible = new int[16];
            for (int i = 0; i < 13; i++) { impossible[i] = i + 1; }
            impossible[13] = 15;
            impossible[14] = 14;
            impossible[15] = 0;
            t.check("the classic 14-15 swap is impossible",
                    !Sliding15Puzzle.solvable(impossible, 4));

            int bad = 0;
            java.util.Random r = new java.util.Random(7);
            for (int trial = 0; trial < 400; trial++) {
                List<Integer> values = new java.util.ArrayList<>();
                for (int i = 0; i < 16; i++) { values.add(i); }
                java.util.Collections.shuffle(values, r);
                int[] board = values.stream().mapToInt(Integer::intValue).toArray();
                if (!Sliding15Puzzle.solvable(board, 4)) { bad++; }
            }
            t.check("about half of random arrangements are impossible",
                    bad > 150 && bad < 250);

            // Shuffling by legal moves cannot reach an unreachable state, so no
            // parity check is needed afterwards.
            int unsolvable = 0;
            for (int seed = 0; seed < 200; seed++) {
                Sliding15Puzzle s = new Sliding15Puzzle(4, new java.util.Random(seed));
                s.shuffle(120);
                if (!s.solvable()) { unsolvable++; }
            }
            t.equal("but shuffling by legal moves never is", 0, unsolvable);

            Sliding15Puzzle odd = new Sliding15Puzzle(3, new java.util.Random(5));
            odd.shuffle(200);
            t.check("the odd-width rule works too", odd.solvable());

            Sliding15Puzzle p = new Sliding15Puzzle(4, new java.util.Random(1));
            t.check("starts solved", p.solved());
            t.equal("manhattan distance is 0 when solved", 0, p.manhattan());
            t.check("setting an impossible board is refused",
                    refused(() -> p.setTiles(impossible)));
            t.check("an unknown tile is refused", refused(() -> p.slideTile(99)));
            t.check("the api shuffles", ok(call("sliding-15-puzzle", "generate")));
        });

        h.group("225 Minesweeper", t -> {
            // One click in five ending the game is a coin toss, not a puzzle.
            int hits = 0;
            int lonely = 0;
            for (int seed = 0; seed < 300; seed++) {
                Minesweeper ms = new Minesweeper(9, 9, 10, new java.util.Random(seed));
                ms.reveal(4, 4);
                if (ms.state() == Minesweeper.State.LOST) { hits++; }
                if (ms.revealedCount() < 9) { lonely++; }
            }
            t.equal("the first click is never a mine", 0, hits);
            // A game opening on a lone number often has no deducible move.
            t.equal("and always opens a region", 0, lonely);

            Minesweeper ms = new Minesweeper(9, 9, 10, new java.util.Random(2));
            t.check("mines wait for the first click", !ms.placed());
            ms.reveal(4, 4);
            t.check("then are laid", ms.placed());
            int count = 0;
            for (int mr = 0; mr < 9; mr++) {
                for (int mc = 0; mc < 9; mc++) { if (ms.isMine(mr, mc)) { count++; } }
            }
            t.equal("exactly the requested number", 10, count);
            t.check("flagging works", ms.flag(0, 0) && ms.isFlagged(0, 0));
            t.check("a flagged square will not reveal", !ms.reveal(0, 0));
            t.check("off the board is refused", refused(() -> ms.reveal(99, 99)));

            // A recursive flood fill overflows exactly here - the big open board
            // where the cascade is the point.
            Minesweeper big = new Minesweeper(24, 30, 1, new java.util.Random(4));
            big.reveal(12, 15);
            t.check("a 720-square cascade does not overflow", big.revealedCount() > 700);
            t.equal("and wins outright", Minesweeper.State.WON, big.state());
            t.check("too many mines is refused",
                    refused(() -> new Minesweeper(5, 5, 999, new java.util.Random())));
        });

        h.group("226 Game 2048", t -> {
            // The clause everybody forgets: a tile formed this move is finished.
            // Without it [2,2,4] collapses to 8 instead of 4 4.
            int[] gained = {0};
            int[] merged = Game2048.slideRow(new int[]{2, 2, 4, 0}, gained);
            t.check("a merged tile cannot merge again this move",
                    merged[0] == 4 && merged[1] == 4 && merged[2] == 0);
            t.equal("and scores only what merged", 4, gained[0]);

            int[] four = Game2048.slideRow(new int[]{2, 2, 2, 2}, null);
            t.check("four equal tiles make two pairs",
                    four[0] == 4 && four[1] == 4 && four[2] == 0 && four[3] == 0);
            int[] mixed = Game2048.slideRow(new int[]{4, 2, 2, 0}, null);
            t.check("only equal neighbours merge", mixed[0] == 4 && mixed[1] == 4);
            int[] gaps = Game2048.slideRow(new int[]{0, 2, 0, 2}, null);
            t.check("gaps close before merging", gaps[0] == 4 && gaps[1] == 0);

            // Spawning on a no-op lets a player fill the board with a dead key.
            Game2048 g = new Game2048(4, new java.util.Random(1));
            g.setGrid(new int[][]{{2, 4, 8, 16}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}});
            int before = tiles(g);
            t.check("a no-op move reports as such", !g.slide(0));
            t.equal("and spawns nothing", before, tiles(g));
            t.equal("nor counts as a move", 0, g.moves());

            Game2048 s = new Game2048(4, new java.util.Random(2));
            s.setGrid(new int[][]{{0, 0, 0, 2}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}});
            int had = tiles(s);
            t.check("a real move happens", s.slide(0));
            t.equal("and spawns exactly one", had + 1, tiles(s));

            // All four directions go through one merge, via rotation.
            for (int d = 0; d < 4; d++) {
                Game2048 dir = new Game2048(4, new java.util.Random(3));
                dir.setGrid(new int[][]{{2, 2, 0, 0}, {2, 2, 0, 0},
                                        {0, 0, 0, 0}, {0, 0, 0, 0}});
                dir.slide(d);
                t.check("direction " + d + " merges and scores", dir.score() > 0);
            }

            Game2048 win = new Game2048(4, new java.util.Random(4));
            win.setGrid(new int[][]{{1024, 1024, 0, 0}, {0, 0, 0, 0},
                                    {0, 0, 0, 0}, {0, 0, 0, 0}});
            win.slide(0);
            t.check("reaching 2048 is recognised", win.won());

            Game2048 dead = new Game2048(2, new java.util.Random(5));
            dead.setGrid(new int[][]{{2, 4}, {8, 16}});
            t.check("a full board with no matches is stuck", dead.stuck());
            Game2048 alive = new Game2048(2, new java.util.Random(6));
            alive.setGrid(new int[][]{{2, 2}, {8, 16}});
            t.check("but not when neighbours match", !alive.stuck());
            t.check("a silly board is refused",
                    refused(() -> new Game2048(99, new java.util.Random())));
        });
    }

    private static int tiles(Game2048 g) {
        int n = 0;
        for (int[] row : g.grid()) {
            for (int v : row) { if (v != 0) { n++; } }
        }
        return n;
    }

    private static void algebraPuzzles(Harness h) {
        h.group("229 Lights Out", t -> {
            // Two facts turn this from a search into linear algebra: pressing
            // twice cancels, and order does not matter. So a solution is a set
            // of buttons - a vector over GF(2) - not a sequence.
            LightsOut l = new LightsOut(5, 5, new java.util.Random(1));
            boolean[] before = l.lights();
            l.press(12);
            l.press(12);
            t.check("pressing twice is the identity",
                    java.util.Arrays.equals(before, l.lights()));

            LightsOut x = new LightsOut(5, 5, new java.util.Random(2));
            LightsOut y = new LightsOut(5, 5, new java.util.Random(2));
            x.press(3); x.press(11); x.press(20);
            y.press(20); y.press(3); y.press(11);
            t.check("order of presses does not matter",
                    java.util.Arrays.equals(x.lights(), y.lights()));

            // The toggle matrix is singular on 5x5 with a 2-dimensional null
            // space, so it does not reach every configuration.
            t.equal("the 5x5 has two quiet patterns", 2,
                    new LightsOut(5, 5, new java.util.Random(3)).quietPatterns());
            t.equal("the 3x3 has none, so every board solves", 0,
                    new LightsOut(3, 3, new java.util.Random(4)).quietPatterns());

            // Which means roughly one board in four is solvable. A solver that
            // always returns an answer is wrong on the other three.
            int solvable = 0;
            java.util.Random r = new java.util.Random(9);
            for (int trial = 0; trial < 400; trial++) {
                LightsOut board = new LightsOut(5, 5, r);
                boolean[] state = new boolean[25];
                for (int i = 0; i < 25; i++) { state[i] = r.nextBoolean(); }
                board.setLights(state);
                if (board.solvable()) { solvable++; }
            }
            t.check("about a quarter of random boards are solvable",
                    solvable > 70 && solvable < 130);

            // Scrambling by pressing can only reach reachable states, exactly
            // as shuffling a sliding puzzle by legal moves does.
            int unsolvable = 0;
            int solved = 0;
            for (int seed = 0; seed < 100; seed++) {
                LightsOut board = new LightsOut(5, 5, new java.util.Random(seed));
                board.scramble(40);
                if (!board.solvable()) { unsolvable++; }
                board.applySolution();
                if (board.dark()) { solved++; }
            }
            t.equal("scrambling by pressing is always solvable", 0, unsolvable);
            t.equal("and elimination turns every one of them off", 100, solved);

            LightsOut corner = new LightsOut(5, 5, new java.util.Random(5));
            boolean[] one = new boolean[25];
            one[0] = true;
            corner.setLights(one);
            t.check("an unreachable board is refused rather than guessed",
                    corner.solvable() || refused(corner::applySolution));
            t.check("an off-board press is refused", refused(() -> l.press(999)));
        });

        h.group("242 Magic Square", t -> {
            // The constant is forced by the numbers, not chosen: 1..n*n sums
            // to n*n(n*n+1)/2 and the rows split that into n equal shares.
            t.equal("a 3x3 must use 15", 15, MagicSquare.magicConstant(3));
            t.equal("a 4x4 must use 34", 34, MagicSquare.magicConstant(4));
            t.equal("a 5x5 must use 65", 65, MagicSquare.magicConstant(5));

            // Three unrelated methods, chosen by n mod 4, and each has to
            // actually produce a magic square across its whole family.
            int notMagic = 0;
            StringBuilder methods = new StringBuilder();
            for (int n = 3; n <= 10; n++) {
                if (!MagicSquare.isMagic(MagicSquare.construct(n))) {
                    notMagic++;
                }
                methods.append(n).append('=').append(MagicSquare.methodFor(n)).append(' ');
            }
            t.equal("every side from 3 to 10 builds a genuine magic square", 0, notMagic);
            t.equal("odd sides go to Siamese", "Siamese", MagicSquare.methodFor(7));
            t.equal("multiples of four to the lattice",
                    "complement lattice", MagicSquare.methodFor(8));
            t.equal("and the awkward family to LUX", "LUX", MagicSquare.methodFor(6));

            // 2x2 is impossible, and the argument is arithmetic rather than a
            // search that ran out of patience.
            t.check("a 2x2 is refused outright",
                    refused(() -> MagicSquare.construct(2)));

            // isMagic has to be strict about more than the sums.
            t.check("the classic 3x3 passes", MagicSquare.isMagic(new int[][] {
                {2, 7, 6}, {9, 5, 1}, {4, 3, 8}}));
            t.check("a square with the right sums but a repeat is refused",
                    !MagicSquare.isMagic(new int[][] {{5, 5, 5}, {5, 5, 5}, {5, 5, 5}}));
            t.check("so is one whose diagonals miss", !MagicSquare.isMagic(new int[][] {
                {2, 7, 6}, {9, 5, 1}, {4, 8, 3}}));
            t.check("and one using numbers out of range",
                    !MagicSquare.isMagic(new int[][] {{0, 7, 8}, {9, 5, 1}, {6, 3, 6}}));

            // Rotating a magic square leaves it magic, which is why completion
            // is judged by the rules rather than against one stored answer.
            int[][] original = MagicSquare.construct(5);
            int[][] turned = new int[5][5];
            for (int row = 0; row < 5; row++) {
                for (int column = 0; column < 5; column++) {
                    turned[column][4 - row] = original[row][column];
                }
            }
            t.check("a quarter turn of a magic square is still magic",
                    MagicSquare.isMagic(turned));
            t.check("and it is a different arrangement",
                    !java.util.Arrays.deepEquals(original, turned));

            MagicSquare board = new MagicSquare(new java.util.Random(5));
            board.generate(4);
            t.check("some cells are hidden", !board.missing().isEmpty());
            t.equal("and the hidden ones are exactly what is missing",
                    board.missing().size(), countBlanks(board));
            board.solve();
            t.check("solving fills a magic square", board.complete());
            t.check("an out-of-range number is refused",
                    refused(() -> board.place(0, 0, 99)));
            t.check("an unplayable size is refused", refused(() -> board.generate(2)));
            t.check("the api generates", ok(call("magic-square", "generate", "size", 5)));
            t.check("the api solves", ok(call("magic-square", "solve")));
        });

        h.group("241 Shikaku", t -> {
            Shikaku puzzle = new Shikaku(new java.util.Random(2));
            puzzle.generate(7);

            // Every candidate rectangle has to be a legal home for its clue:
            // the right area, covering its own number, and covering no other.
            // That last filter is what keeps the candidate lists small.
            int badArea = 0;
            int missesOwnClue = 0;
            int swallowsAnother = 0;
            int candidates = 0;
            for (int row = 0; row < puzzle.size(); row++) {
                for (int column = 0; column < puzzle.size(); column++) {
                    int clue = puzzle.clue(row, column);
                    if (clue == 0) {
                        continue;
                    }
                    for (int[] rectangle : puzzle.candidatesFor(row, column)) {
                        candidates++;
                        if (rectangle[2] * rectangle[3] != clue) {
                            badArea++;
                        }
                        if (row < rectangle[0] || row >= rectangle[0] + rectangle[2]
                                || column < rectangle[1]
                                || column >= rectangle[1] + rectangle[3]) {
                            missesOwnClue++;
                        }
                        for (int r = rectangle[0]; r < rectangle[0] + rectangle[2]; r++) {
                            for (int c = rectangle[1]; c < rectangle[1] + rectangle[3]; c++) {
                                if (puzzle.clue(r, c) != 0 && (r != row || c != column)) {
                                    swallowsAnother++;
                                }
                            }
                        }
                    }
                }
            }
            t.check("there are candidate rectangles to choose from", candidates > 0);
            t.equal("every candidate has the area its clue demands", 0, badArea);
            t.equal("and covers the clue it belongs to", 0, missesOwnClue);
            t.equal("and never swallows a second clue", 0, swallowsAnother);

            // The answer has to be a genuine partition: areas summing to the
            // board, one clue per rectangle, and no cell claimed twice.
            int notUnique = 0;
            int partitionFaults = 0;
            for (int side = 4; side <= 8; side += 2) {
                for (int seed = 0; seed < 3; seed++) {
                    Shikaku board = new Shikaku(new java.util.Random(seed));
                    board.generate(side);
                    if (board.countSolutions(2) != 1) {
                        notUnique++;
                    }
                    int[][] owner = new int[side][side];
                    for (int[] line : owner) {
                        java.util.Arrays.fill(line, -1);
                    }
                    int index = 0;
                    int area = 0;
                    for (int[] rectangle : board.answer()) {
                        int clues = 0;
                        for (int r = rectangle[0]; r < rectangle[0] + rectangle[2]; r++) {
                            for (int c = rectangle[1]; c < rectangle[1] + rectangle[3]; c++) {
                                if (owner[r][c] != -1) {
                                    partitionFaults++;   // two rectangles, one cell
                                }
                                owner[r][c] = index;
                                if (board.clue(r, c) != 0) {
                                    clues++;
                                    if (board.clue(r, c) != rectangle[2] * rectangle[3]) {
                                        partitionFaults++;
                                    }
                                }
                            }
                        }
                        if (clues != 1) {
                            partitionFaults++;
                        }
                        area += rectangle[2] * rectangle[3];
                        index++;
                    }
                    if (area != side * side) {
                        partitionFaults++;
                    }
                    for (int[] line : owner) {
                        for (int cell : line) {
                            if (cell < 0) {
                                partitionFaults++;   // a cell nobody claimed
                            }
                        }
                    }
                }
            }
            t.equal("every generated puzzle has exactly one cut", 0, notUnique);
            t.equal("and its answer really does partition the board", 0, partitionFaults);

            t.check("an unplayable size is refused", refused(() -> puzzle.generate(2)));
            t.check("the api generates", ok(call("shikaku", "generate", "size", 5)));
            t.check("the api reveals one rectangle", ok(call("shikaku", "hint")));
            t.check("the api solves", ok(call("shikaku", "solve")));
        });

        h.group("238 Hitori", t -> {
            // The sandwich rule, which the solver is never told. Shade the
            // middle of x y x and the no-touching rule forces both neighbours
            // white, leaving two identical whites in one row. So the middle is
            // white - and that falls out of trying it, not out of a rule list.
            Hitori sandwich = new Hitori(new java.util.Random(1));
            sandwich.setPuzzle(new int[][] {
                {1, 2, 1, 3, 4},
                {2, 3, 4, 5, 1},
                {3, 4, 5, 1, 2},
                {4, 5, 1, 2, 3},
                {5, 1, 2, 3, 4}});
            t.equal("the middle of x y x is deduced white without searching",
                    Hitori.WHITE, sandwich.deduced(0, 1));
            t.equal("while its neighbours stay open",
                    Hitori.UNKNOWN, sandwich.deduced(0, 0));

            // Connectivity is the one rule no local reasoning sees coming.
            char[][] pinched = new char[5][5];
            for (char[] line : pinched) {
                java.util.Arrays.fill(line, Hitori.WHITE);
            }
            t.check("an unshaded board is connected", sandwich.connected(pinched));
            pinched[0][1] = Hitori.BLACK;
            pinched[1][0] = Hitori.BLACK;
            t.check("but shading a corner off is spotted", !sandwich.connected(pinched));

            // Generated puzzles are unique, and their answers really do obey
            // all three rules at once.
            int notUnique = 0;
            int notReproduced = 0;
            int ruleFaults = 0;
            for (int side = 4; side <= 7; side++) {
                for (int seed = 0; seed < 3; seed++) {
                    Hitori puzzle = new Hitori(new java.util.Random(seed));
                    puzzle.generate(side);
                    if (puzzle.countSolutions(2) != 1) {
                        notUnique++;
                    }
                    puzzle.solve();
                    if (!puzzle.complete()) {
                        notReproduced++;
                    }
                    char[][] shaded = new char[side][side];
                    for (int row = 0; row < side; row++) {
                        for (int column = 0; column < side; column++) {
                            shaded[row][column] = puzzle.shade(row, column) == Hitori.BLACK
                                    ? Hitori.BLACK : Hitori.WHITE;
                        }
                    }
                    if (!puzzle.connected(shaded)) {
                        ruleFaults++;
                    }
                    for (int row = 0; row < side; row++) {
                        for (int column = 0; column < side; column++) {
                            if (shaded[row][column] != Hitori.BLACK) {
                                continue;
                            }
                            if (row + 1 < side && shaded[row + 1][column] == Hitori.BLACK) {
                                ruleFaults++;
                            }
                            if (column + 1 < side && shaded[row][column + 1] == Hitori.BLACK) {
                                ruleFaults++;
                            }
                        }
                    }
                    for (int line = 0; line < side; line++) {
                        for (int a = 0; a < side; a++) {
                            for (int b = a + 1; b < side; b++) {
                                if (shaded[line][a] == Hitori.WHITE
                                        && shaded[line][b] == Hitori.WHITE
                                        && puzzle.number(line, a) == puzzle.number(line, b)) {
                                    ruleFaults++;
                                }
                                if (shaded[a][line] == Hitori.WHITE
                                        && shaded[b][line] == Hitori.WHITE
                                        && puzzle.number(a, line) == puzzle.number(b, line)) {
                                    ruleFaults++;
                                }
                            }
                        }
                    }
                }
            }
            t.equal("every generated puzzle has exactly one shading", 0, notUnique);
            t.equal("and solving reproduces it", 0, notReproduced);
            t.equal("and the answer breaks none of the three rules", 0, ruleFaults);

            Hitori board = new Hitori(new java.util.Random(4));
            board.generate(6);
            t.equal("a click shades", Hitori.BLACK, board.toggle(0, 0));
            t.equal("and clicking again clears", Hitori.UNKNOWN, board.toggle(0, 0));
            t.check("an off-board cell is refused", refused(() -> board.toggle(99, 0)));
            t.check("an unplayable size is refused", refused(() -> board.generate(2)));
            t.check("the api generates", ok(call("hitori", "generate", "size", 5)));
            t.check("the api shades", ok(call("hitori", "toggle", "row", 0, "col", 0)));
            t.check("the api solves", ok(call("hitori", "solve")));
        });

        h.group("240 Skyscrapers", t -> {
            t.equal("a rising street shows every tower", 5,
                    Skyscrapers.visible(new int[] {1, 2, 3, 4, 5}));
            t.equal("a falling one shows only the first", 1,
                    Skyscrapers.visible(new int[] {5, 4, 3, 2, 1}));
            t.equal("the tallest in front hides the rest", 1,
                    Skyscrapers.visible(new int[] {5, 1, 3, 2, 4}));
            t.equal("and anything taller than all before it counts", 3,
                    Skyscrapers.visible(new int[] {2, 1, 3, 5, 4}));

            // A tower is visible exactly when it is a left-to-right maximum,
            // so the clue distribution is the unsigned Stirling numbers of the
            // first kind. That is not a flat distribution, and the shape of it
            // is why some clues are worth so much more than others.
            int[] histogram = new int[6];
            for (int[] candidate : Skyscrapers.permutations(5)) {
                histogram[candidate[5]]++;
            }
            t.equal("of 120 lines of five, 24 show just one tower", 24, histogram[1]);
            t.equal("50 show two - the least informative clue there is", 50, histogram[2]);
            t.equal("35 show three", 35, histogram[3]);
            t.equal("10 show four", 10, histogram[4]);
            t.equal("and exactly one shows all five", 1, histogram[5]);

            int[] sixes = new int[7];
            for (int[] candidate : Skyscrapers.permutations(6)) {
                sixes[candidate[6]]++;
            }
            t.equal("the same pattern at size six", "[0, 120, 274, 225, 85, 15, 1]",
                    java.util.Arrays.toString(sixes));

            // Which cashes out directly: the rarest clue settles a line alone.
            int[] blank = new int[5];
            java.util.Arrays.fill(blank, 0b11111);
            int[] rising = Skyscrapers.refineLine(blank.clone(), 5, 0);
            StringBuilder settled = new StringBuilder();
            int decided = 0;
            for (int mask : rising) {
                settled.append(Integer.numberOfTrailingZeros(mask) + 1);
                if (Integer.bitCount(mask) == 1) {
                    decided++;
                }
            }
            t.equal("a clue of five settles all five cells", 5, decided);
            t.equal("as the only line that shows five towers", "12345", settled.toString());

            int[] tallestFirst = Skyscrapers.refineLine(blank.clone(), 1, 0);
            t.equal("a clue of one pins the tallest to the front",
                    1, Integer.bitCount(tallestFirst[0]));
            t.equal("and that tower is the tallest there is",
                    5, Integer.numberOfTrailingZeros(tallestFirst[0]) + 1);
            t.check("but says nothing certain about the rest",
                    Integer.bitCount(tallestFirst[2]) > 1);

            // Five from both ends at once cannot happen: the only line showing
            // five from the left shows one from the right.
            t.check("clues that contradict each other are refused",
                    Skyscrapers.refineLine(blank.clone(), 5, 5) == null);

            // Generation has to check the square it starts from, not just the
            // clues it removes: at size six there are Latin squares whose full
            // clue set is satisfied by more than one skyline.
            int notUnique = 0;
            int notReproduced = 0;
            for (int side = 4; side <= 6; side++) {
                for (int seed = 0; seed < 3; seed++) {
                    Skyscrapers city = new Skyscrapers(new java.util.Random(seed));
                    city.generate(side);
                    if (city.countSolutions(2) != 1) {
                        notUnique++;
                    }
                    city.solve();
                    if (!city.complete()) {
                        notReproduced++;
                    }
                }
            }
            t.equal("every generated city has exactly one skyline", 0, notUnique);
            t.equal("and solving reproduces it", 0, notReproduced);

            Skyscrapers board = new Skyscrapers(new java.util.Random(6));
            board.generate(5);
            board.solve();
            int latinFaults = 0;
            for (int line = 0; line < 5; line++) {
                boolean[] inRow = new boolean[6];
                boolean[] inColumn = new boolean[6];
                for (int other = 0; other < 5; other++) {
                    if (inRow[board.height(line, other)] || inColumn[board.height(other, line)]) {
                        latinFaults++;
                    }
                    inRow[board.height(line, other)] = true;
                    inColumn[board.height(other, line)] = true;
                }
            }
            t.equal("the answer is a Latin square", 0, latinFaults);

            int clueFaults = 0;
            for (int row = 0; row < 5; row++) {
                int[] line = new int[5];
                for (int column = 0; column < 5; column++) {
                    line[column] = board.height(row, column);
                }
                if (board.leftClue(row) != 0
                        && Skyscrapers.visible(line) != board.leftClue(row)) {
                    clueFaults++;
                }
            }
            t.equal("and the skyline it makes matches every clue kept", 0, clueFaults);
            t.check("an impossible height is refused", refused(() -> board.place(0, 0, 9)));
            t.check("an unplayable size is refused", refused(() -> board.generate(9)));
            t.check("the api generates", ok(call("skyscrapers", "generate", "size", 4)));
            t.check("a click on a clue is ignored rather than refused",
                    ok(call("skyscrapers", "place", "row", 0, "col", 0)));
            t.check("the api solves", ok(call("skyscrapers", "solve")));
        });

        h.group("233 Futoshiki", t -> {
            int[][] empty = new int[5][5];

            // A chain of four rising signs across a row of five fixes all five
            // cells with nothing else to go on. Pairwise narrowing cannot do
            // that; the longest-path bound can, because the cell at the head
            // of a chain of length k is at least k.
            Futoshiki chain = new Futoshiki(new java.util.Random(1));
            chain.setPuzzle(5, empty, new int[][] {
                {0, 0, 0, 1}, {0, 1, 0, 2}, {0, 2, 0, 3}, {0, 3, 0, 4}});
            t.equal("the head of a rising chain of five can only be one",
                    List.of(1), chain.candidates(0, 0));
            t.equal("the second can only be two", List.of(2), chain.candidates(0, 1));
            t.equal("and the tail can only be five", List.of(5), chain.candidates(0, 4));
            // And the consequences carry: pinning the top row to 1..5 puts a
            // five in the last column, which the Latin rule then takes away
            // from every other cell in it.
            t.equal("which costs the rest of that column its five",
                    List.of(1, 2, 3, 4), chain.candidates(4, 4));

            // A shorter chain bounds without fixing, which is the general rule
            // the fixed case above is just the extreme of.
            Futoshiki partial = new Futoshiki(new java.util.Random(1));
            partial.setPuzzle(5, empty, new int[][] {{0, 0, 0, 1}, {0, 1, 0, 2}});
            t.equal("two rising signs put the far end at three or more",
                    List.of(3, 4, 5), partial.candidates(0, 2));
            t.equal("and the near end at three or less",
                    List.of(1, 2, 3), partial.candidates(0, 0));

            // The same graph catches signs that contradict each other, which
            // no amount of searching would ever resolve.
            Futoshiki loop = new Futoshiki(new java.util.Random(1));
            loop.setPuzzle(5, empty, new int[][] {
                {0, 0, 0, 1}, {0, 1, 1, 1}, {1, 1, 0, 0}});
            t.equal("a cycle of signs leaves no candidates at all",
                    0, loop.candidates(0, 0).size());
            t.equal("and no solutions to search for", 0, loop.countSolutions(1));

            // Generated puzzles: unique, and genuinely carried by the signs
            // rather than by a pile of givens.
            int notUnique = 0;
            int notReproduced = 0;
            int signless = 0;
            for (int seed = 0; seed < 4; seed++) {
                Futoshiki puzzle = new Futoshiki(new java.util.Random(seed));
                puzzle.generate(5);
                if (puzzle.countSolutions(2) != 1) {
                    notUnique++;
                }
                if (puzzle.signCount() == 0) {
                    signless++;
                }
                puzzle.solve();
                if (!puzzle.complete()) {
                    notReproduced++;
                }
            }
            t.equal("every generated puzzle has exactly one answer", 0, notUnique);
            t.equal("and solving reproduces it", 0, notReproduced);
            t.equal("and none of them came out as a plain Latin square", 0, signless);

            Futoshiki board = new Futoshiki(new java.util.Random(3));
            board.generate(5);
            int[][] solved = new int[5][5];
            board.solve();
            for (int row = 0; row < 5; row++) {
                for (int column = 0; column < 5; column++) {
                    solved[row][column] = board.digit(row, column);
                }
            }
            int latinFaults = 0;
            for (int line = 0; line < 5; line++) {
                boolean[] inRow = new boolean[6];
                boolean[] inColumn = new boolean[6];
                for (int other = 0; other < 5; other++) {
                    if (inRow[solved[line][other]] || inColumn[solved[other][line]]) {
                        latinFaults++;
                    }
                    inRow[solved[line][other]] = true;
                    inColumn[solved[other][line]] = true;
                }
            }
            t.equal("the answer really is a Latin square", 0, latinFaults);
            int signFaults = 0;
            for (int row = 0; row < 5; row++) {
                for (int column = 0; column < 5; column++) {
                    if (column + 1 < 5 && board.lessThan(row, column, row, column + 1)
                            && solved[row][column] > solved[row][column + 1]) {
                        signFaults++;
                    }
                    if (row + 1 < 5 && board.lessThan(row, column, row + 1, column)
                            && solved[row][column] > solved[row + 1][column]) {
                        signFaults++;
                    }
                }
            }
            t.equal("and it points the right way through every sign", 0, signFaults);

            t.check("a digit outside the range is refused",
                    refused(() -> board.place(0, 0, 9)));
            t.check("an off-board cell is refused", refused(() -> board.place(9, 0, 1)));
            t.check("an unplayable size is refused", refused(() -> board.generate(3)));
            t.check("the api generates", ok(call("futoshiki", "generate", "size", 5)));
            t.check("a click on a sign is ignored rather than refused",
                    ok(call("futoshiki", "place", "row", 1, "col", 0)));
            t.check("the api solves", ok(call("futoshiki", "solve")));
        });

        h.group("239 Binary Puzzle", t -> {
            // The two local rules are savage. Of 4096 ways to fill a line of
            // twelve, 208 survive, which is what makes enumerating them a
            // sensible thing to do rather than a stunt.
            t.equal("six legal lines of length four", 6, BinaryPuzzle.validLines(4).length);
            t.equal("fourteen of length six", 14, BinaryPuzzle.validLines(6).length);
            t.equal("thirty-four of length eight", 34, BinaryPuzzle.validLines(8).length);
            t.equal("two hundred and eight of length twelve",
                    208, BinaryPuzzle.validLines(12).length);

            int malformed = 0;
            for (int candidate : BinaryPuzzle.validLines(10)) {
                if (Integer.bitCount(candidate) != 5) {
                    malformed++;
                }
                for (int i = 0; i + 2 < 10; i++) {
                    int a = candidate >> i & 1;
                    if (a == (candidate >> (i + 1) & 1) && a == (candidate >> (i + 2) & 1)) {
                        malformed++;
                    }
                }
            }
            t.equal("and every one of them is balanced with no run of three", 0, malformed);

            // The named patterns players learn are all just consequences.
            t.equal("a pair forces the opposite symbol after it", BinaryPuzzle.ZERO,
                    BinaryPuzzle.refineLine("##....".toCharArray())[2]);
            t.equal("a gap between two alike forces the opposite", BinaryPuzzle.ZERO,
                    BinaryPuzzle.refineLine("#.#...".toCharArray())[1]);
            char[] counted = BinaryPuzzle.refineLine("#.#.#.".toCharArray());
            t.equal("and once a line has its share, the rest follow", "#*#*#*",
                    String.valueOf(counted));
            t.check("three alike is refused outright",
                    BinaryPuzzle.refineLine("###...".toCharArray()) == null);

            // The claim being made is stronger than any list of patterns: the
            // solver writes down precisely what every surviving line agrees on,
            // no less and no more. Over all 3^6 partial lines, brute force
            // says so too.
            int[] legal = BinaryPuzzle.validLines(6);
            char[] states = {BinaryPuzzle.UNKNOWN, BinaryPuzzle.ONE, BinaryPuzzle.ZERO};
            int mismatches = 0;
            for (int code = 0; code < 729; code++) {
                char[] partial = new char[6];
                int digits = code;
                for (int i = 0; i < 6; i++) {
                    partial[i] = states[digits % 3];
                    digits /= 3;
                }
                java.util.List<Integer> survivors = new java.util.ArrayList<>();
                for (int candidate : legal) {
                    boolean agrees = true;
                    for (int i = 0; i < 6 && agrees; i++) {
                        if (partial[i] == BinaryPuzzle.UNKNOWN) {
                            continue;
                        }
                        agrees = (candidate >> i & 1)
                                == (partial[i] == BinaryPuzzle.ONE ? 1 : 0);
                    }
                    if (agrees) {
                        survivors.add(candidate);
                    }
                }
                char[] refined = BinaryPuzzle.refineLine(partial);
                if (survivors.isEmpty()) {
                    if (refined != null) {
                        mismatches++;
                    }
                    continue;
                }
                if (refined == null) {
                    mismatches++;
                    continue;
                }
                for (int i = 0; i < 6; i++) {
                    boolean allOne = true;
                    boolean allZero = true;
                    for (int candidate : survivors) {
                        if ((candidate >> i & 1) == 1) {
                            allZero = false;
                        } else {
                            allOne = false;
                        }
                    }
                    char expected = allOne ? BinaryPuzzle.ONE
                            : allZero ? BinaryPuzzle.ZERO : BinaryPuzzle.UNKNOWN;
                    if (refined[i] != expected) {
                        mismatches++;
                    }
                }
            }
            t.equal("over all 729 partial lines of six, the solver is exactly the "
                    + "intersection of the survivors", 0, mismatches);

            // Generation runs it backwards: carve givens away while the answer
            // stays unique, so every given left behind is load-bearing.
            int notUnique = 0;
            int notReproduced = 0;
            for (int seed = 0; seed < 4; seed++) {
                BinaryPuzzle puzzle = new BinaryPuzzle(new java.util.Random(seed));
                puzzle.generate(6);
                if (puzzle.countSolutions(2) != 1) {
                    notUnique++;
                }
                puzzle.solve();
                if (!puzzle.complete()) {
                    notReproduced++;
                }
            }
            t.equal("every generated puzzle has exactly one answer", 0, notUnique);
            t.equal("and solving reproduces it", 0, notReproduced);

            BinaryPuzzle board = new BinaryPuzzle(new java.util.Random(5));
            board.generate(6);
            int givenRow = -1;
            int givenColumn = -1;
            int freeRow = -1;
            int freeColumn = -1;
            for (int row = 0; row < board.size(); row++) {
                for (int column = 0; column < board.size(); column++) {
                    if (board.isGiven(row, column)) {
                        givenRow = row;
                        givenColumn = column;
                    } else {
                        freeRow = row;
                        freeColumn = column;
                    }
                }
            }
            final int lockedRow = givenRow;
            final int lockedColumn = givenColumn;
            t.check("a given cannot be changed",
                    refused(() -> board.cycle(lockedRow, lockedColumn)));
            t.equal("a click on a blank cell places the first symbol",
                    BinaryPuzzle.ONE, board.cycle(freeRow, freeColumn));
            t.equal("the next places the second",
                    BinaryPuzzle.ZERO, board.cycle(freeRow, freeColumn));
            t.equal("and the next clears it",
                    BinaryPuzzle.UNKNOWN, board.cycle(freeRow, freeColumn));
            t.check("an off-board cell is refused", refused(() -> board.cycle(99, 0)));
            t.check("an odd size is refused", refused(() -> board.generate(7)));

            t.check("the api generates", ok(call("binary-puzzle", "generate", "size", 6)));
            t.check("the api solves", ok(call("binary-puzzle", "solve")));
        });

        h.group("230 Peg Solitaire", t -> {
            PegSolitaire english = new PegSolitaire(PegSolitaire.ENGLISH);
            t.equal("the English board starts with 32 pegs", 32, english.pegCount());

            // The whole argument is that a jump touches three cells whose
            // exponents are consecutive, and 1 + w + w^2 is zero in GF(4). So
            // play the board out at random and neither sum ever moves.
            PegSolitaire walk = new PegSolitaire(PegSolitaire.ENGLISH);
            int[] before = walk.invariants();
            java.util.Random rng = new java.util.Random(7);
            int drift = 0;
            while (!walk.moves().isEmpty()) {
                List<int[]> options = walk.moves();
                int[] jump = options.get(rng.nextInt(options.size()));
                walk.move(jump[0], jump[1], jump[2], jump[3]);
                if (!java.util.Arrays.equals(before, walk.invariants())) {
                    drift++;
                }
            }
            t.equal("no jump ever changes either sum", 0, drift);
            t.check("and that walk really did play most of the board out",
                    walk.movesMade() > 10);

            // Which prunes hard. From the central vacancy only five of the 33
            // holes survive: the middle and the four tips of the arms.
            t.equal("the colouring rules out 28 of the 33 holes",
                    28, english.impossibleFinishes().size());
            t.check("the centre survives", english.couldFinishAt(3, 3));
            t.check("and so do the four arm tips",
                    english.couldFinishAt(0, 3) && english.couldFinishAt(3, 0)
                            && english.couldFinishAt(3, 6) && english.couldFinishAt(6, 3));
            t.check("a hole beside the centre does not", !english.couldFinishAt(3, 4));

            // Necessary is not sufficient, so the centre still has to be found.
            List<int[]> plan = english.solve();
            t.check("the central game does have a solution", plan != null);
            t.equal("each jump removes one peg, so it takes 31 of them",
                    31, plan == null ? -1 : plan.size());
            t.equal("and searching leaves the board where it found it",
                    32, english.pegCount());
            english.solveAndPlay();
            t.check("playing that line out finishes in the middle", english.solved());

            // The European board is where the colouring does something no
            // search can: it rules out every finishing hole there is.
            PegSolitaire european = new PegSolitaire(PegSolitaire.EUROPEAN);
            t.equal("the European board starts with 36 pegs", 36, european.pegCount());
            t.equal("and the colouring eliminates all 37 of its holes",
                    37, european.impossibleFinishes().size());
            t.check("so its central game has no solution to look for",
                    european.solve() == null);

            PegSolitaire fresh = new PegSolitaire(PegSolitaire.ENGLISH);
            t.check("the classic opening jump is legal", fresh.legal(3, 1, 0, 1));
            t.check("landing on an occupied hole is not", !fresh.legal(2, 0, 0, 1));
            t.check("nor is jumping diagonally", !fresh.legal(2, 2, 1, 1));
            fresh.move(3, 1, 0, 1);
            t.equal("a jump removes exactly one peg", 31, fresh.pegCount());
            t.check("jumping over an empty hole is refused",
                    refused(() -> fresh.move(3, 0, 0, 1)));
            t.check("undo puts the peg back", fresh.undo() && fresh.pegCount() == 32);

            // Two clicks - the peg, then the hole two along - are one jump.
            PegSolitaire clicks = new PegSolitaire(PegSolitaire.ENGLISH);
            clicks.touch(3, 1);
            clicks.touch(3, 3);
            t.equal("a peg then a hole two along is a jump", 31, clicks.pegCount());
            clicks.touch(0, 0);
            t.equal("a click off the board does nothing", 31, clicks.pegCount());

            t.check("the api plays", ok(call("peg-solitaire", "touch", "row", 3, "col", 1)));
            t.check("the api switches boards",
                    ok(call("peg-solitaire", "generate", "board", "european")));
        });

        h.group("228 Nonogram", t -> {
            t.equal("a clue is the run lengths in order", "[2, 3]",
                    java.util.Arrays.toString(Nonogram.cluesFor(
                            new boolean[] {true, true, false, true, true, true})));
            t.equal("an empty line has no runs at all", 0,
                    Nonogram.cluesFor(new boolean[] {false, false, false}).length);

            // The overlap rule arrives as a consequence, not a special case.
            // Eight in a row of ten has three placements and all three cover
            // the middle six, so the solver writes those and leaves the ends.
            t.equal("eight in ten forces the middle six", "..######..",
                    nonogramShow(Nonogram.refineLine(nonogramBlank(10), new int[] {8})));
            t.equal("five in five forces everything", "#####",
                    nonogramShow(Nonogram.refineLine(nonogramBlank(5), new int[] {5})));
            t.equal("no runs means the whole line is blank", "****",
                    nonogramShow(Nonogram.refineLine(nonogramBlank(4), new int[] {})));

            // A cell already known drags the rest with it: with one run of two
            // and the first cell filled, the run can only start where it is.
            char[] anchored = "#....".toCharArray();
            t.equal("a known cell pins the only run that can reach it", "##***",
                    nonogramShow(Nonogram.refineLine(anchored, new int[] {2})));

            // An unsatisfiable line is reported, not guessed at. This is what
            // makes the line solver usable as the test inside a search.
            t.equal("a run too long for the line is impossible", "impossible",
                    nonogramShow(Nonogram.refineLine(nonogramBlank(4), new int[] {5})));
            t.equal("and so are two runs that cannot both fit", "impossible",
                    nonogramShow(Nonogram.refineLine(nonogramBlank(4), new int[] {2, 2})));

            // The claim the dynamic program actually makes is that it computes
            // the intersection of every valid placement - no less, so it never
            // misses a deduction, and no more, so it never invents one. Over
            // every clue that exists on a line of eight, brute force agrees.
            java.util.Map<String, java.util.List<boolean[]>> families =
                    new java.util.LinkedHashMap<>();
            for (int bits = 0; bits < (1 << 8); bits++) {
                boolean[] line = new boolean[8];
                for (int i = 0; i < 8; i++) {
                    line[i] = (bits >> i & 1) == 1;
                }
                families.computeIfAbsent(
                        java.util.Arrays.toString(Nonogram.cluesFor(line)),
                        key -> new java.util.ArrayList<>()).add(line);
            }
            int mismatches = 0;
            for (java.util.List<boolean[]> family : families.values()) {
                boolean[] alwaysFilled = new boolean[8];
                boolean[] alwaysBlank = new boolean[8];
                java.util.Arrays.fill(alwaysFilled, true);
                java.util.Arrays.fill(alwaysBlank, true);
                for (boolean[] line : family) {
                    for (int i = 0; i < 8; i++) {
                        if (line[i]) {
                            alwaysBlank[i] = false;
                        } else {
                            alwaysFilled[i] = false;
                        }
                    }
                }
                char[] refined = Nonogram.refineLine(
                        nonogramBlank(8), Nonogram.cluesFor(family.get(0)));
                for (int i = 0; i < 8; i++) {
                    char expected = alwaysFilled[i] ? Nonogram.FILLED
                            : alwaysBlank[i] ? Nonogram.EMPTY : Nonogram.UNKNOWN;
                    if (refined == null || refined[i] != expected) {
                        mismatches++;
                    }
                }
            }
            t.equal("across all " + families.size() + " clues on a line of eight, the "
                    + "solver is exactly the intersection of every placement", 0, mismatches);

            // A clue need not describe one picture. Either diagonal of a 2x2
            // gives all four clues as a single one, and nothing distinguishes
            // them - so a generator has to check uniqueness rather than assume
            // that the drawing it made is the only answer to its own clues.
            Nonogram ambiguous = new Nonogram(new java.util.Random(1));
            ambiguous.setPicture(new boolean[][] {{true, false}, {false, true}});
            t.equal("the 2x2 diagonal has two solutions, not one",
                    2, ambiguous.countSolutions(5));

            // Which is exactly what generation rules out.
            int notUnique = 0;
            int notReproduced = 0;
            for (int seed = 0; seed < 8; seed++) {
                Nonogram puzzle = new Nonogram(new java.util.Random(seed));
                if (puzzle.countSolutions(2) != 1) {
                    notUnique++;
                }
                puzzle.solve();
                if (!puzzle.complete()) {
                    notReproduced++;
                }
            }
            t.equal("every generated puzzle has exactly one solution", 0, notUnique);
            t.equal("and solving from the clues reproduces the picture", 0, notReproduced);

            Nonogram board = new Nonogram(new java.util.Random(11));
            t.equal("a fresh board knows nothing", Nonogram.UNKNOWN, board.cell(0, 0));
            t.equal("a click fills", Nonogram.FILLED, board.cycle(0, 0));
            t.equal("the next crosses off", Nonogram.EMPTY, board.cycle(0, 0));
            t.equal("and the next clears", Nonogram.UNKNOWN, board.cycle(0, 0));
            // Hints write the answer one cell at a time, so enough of them
            // finish the board and never more than there are cells.
            int hints = 0;
            while (!board.complete() && hints <= board.rows() * board.columns()) {
                board.hint();
                hints++;
            }
            t.check("hints fill the board and stop", board.complete());
            t.check("and take no more hints than there are cells",
                    hints <= board.rows() * board.columns());
            t.check("an off-board cell is refused", refused(() -> board.cycle(99, 0)));
            t.check("an unplayable size is refused", refused(() -> board.generate(3)));

            // The margins are part of the picture the browser draws, so a
            // click can land on a clue. That is filtered, not an error.
            Nonogram live = new Nonogram(new java.util.Random(3));
            t.check("the api generates", ok(call("nonogram", "generate", "size", 6)));
            t.check("a click in the clue margin is ignored rather than refused",
                    ok(call(live, "cycle", "row", 0, "col", 0)));
            t.check("the api solves", ok(call(live, "solve")));
        });

        h.group("227 Sokoban", t -> {
            Sokoban s = new Sokoban();
            t.equal("the default level has two crates", 2, s.crateCount());
            t.check("not solved yet", !s.solved());

            // A push needs the far side clear, and moves one crate, not a train.
            Sokoban room = new Sokoban(new String[]{
                "#####", "#@$ #", "#  .#", "#####"});
            t.check("a crate with room behind it pushes", room.move(0, 1));
            Sokoban wall = new Sokoban(new String[]{
                "#####", "#@$##", "#  .#", "#####"});
            t.check("a crate against a wall does not", !wall.move(0, 1));
            Sokoban train = new Sokoban(new String[]{
                "######", "#@$$ #", "#  ..#", "######"});
            t.check("and two crates in a row do not", !train.move(0, 1));

            // A crate in a non-goal corner can never move again, so the level
            // is over. Saying so beats letting the player shuffle for an hour.
            Sokoban dead = new Sokoban(new String[]{
                "#####", "#@$ #", "#   #", "#  .#", "#####"});
            dead.move(0, 1);
            dead.move(0, 1);
            t.check("a corner deadlock is detected", dead.deadlocked());

            // But a crate home in a corner is the goal, not a deadlock.
            Sokoban fine = new Sokoban(new String[]{
                "#####", "#@$.#", "#   #", "#####"});
            fine.move(0, 1);
            t.check("a crate home in a corner is not a deadlock", !fine.deadlocked());
            t.check("and the level is solved", fine.solved());

            t.check("a level with no player is refused",
                    refused(() -> new Sokoban(new String[]{"###", "#$#", "#.#", "###"})));
            // Unequal counts make a level impossible or trivially incomplete.
            t.check("mismatched crates and goals are refused",
                    refused(() -> new Sokoban(new String[]{
                        "#####", "#@$$#", "#  .#", "#####"})));
            t.check("a diagonal move is refused", refused(() -> s.move(1, 1)));
            t.check("the api moves", ok(call("sokoban", "right")));
        });
    }

    private static void deduction(Harness h) {
        h.group("231 Mastermind", t -> {
            t.equal("an exact match is four black", new Score(4, 0),
                    Mastermind.score("RGBY", "RGBY"));
            t.equal("a full permutation is four white", new Score(0, 4),
                    Mastermind.score("RGBY", "GRYB"));
            t.equal("nothing in common scores nothing", new Score(0, 0),
                    Mastermind.score("RRRR", "GGGG"));

            // Duplicates are where the naive rule breaks. Asking "does this
            // colour appear in the code" once per guess position counts one
            // coded R against all four guessed Rs. Every peg must consume
            // something, so one R in the code is worth exactly one peg.
            t.equal("four guessed Rs against one coded R give one peg",
                    new Score(1, 0), Mastermind.score("RGGG", "RRRR"));
            t.equal("and one guessed R against four coded Rs likewise",
                    new Score(1, 0), Mastermind.score("RRRR", "RGGG"));
            t.equal("duplicates still score correctly when displaced",
                    new Score(0, 4), Mastermind.score("RRGB", "GBRR"));

            // The invariant, over every pair of codes there is. A double-count
            // shows up as pegs exceeding the code length.
            String colours = Mastermind.COLOURS;
            int total = (int) Math.pow(colours.length(), Mastermind.LENGTH);
            int overCounted = 0;
            int blackWrong = 0;
            int selfWrong = 0;
            for (int i = 0; i < total; i++) {
                String code = mastermindCode(i, colours);
                if (!Mastermind.score(code, code).solved()) { selfWrong++; }
                for (int j = 0; j < total; j++) {
                    String guess = mastermindCode(j, colours);
                    Score s = Mastermind.score(code, guess);
                    if (s.black() + s.white() > Mastermind.LENGTH) { overCounted++; }
                    int exact = 0;
                    for (int k = 0; k < Mastermind.LENGTH; k++) {
                        if (code.charAt(k) == guess.charAt(k)) { exact++; }
                    }
                    if (s.black() != exact) { blackWrong++; }
                }
            }
            t.equal("over all 1296x1296 pairs, pegs never exceed the code length",
                    0, overCounted);
            t.equal("and black always equals the matching positions", 0, blackWrong);
            t.equal("every code scores four black against itself", 0, selfWrong);

            // The deduction itself: candidates must shrink and must never drop
            // the real code, or the feedback is lying somewhere.
            Mastermind m = new Mastermind(new java.util.Random(1));
            m.setSecret("RGBY");
            t.equal("all codes fit before any guess", total, m.consistent().size());
            m.guess("RRGG");
            int afterOne = m.consistent().size();
            m.guess("BYRG");
            int afterTwo = m.consistent().size();
            t.check("each guess narrows the field",
                    afterOne < total && afterTwo < afterOne);
            t.check("and the real code survives every round",
                    m.consistent().contains("RGBY"));

            m.guess("RGBY");
            t.check("guessing right wins", m.won());
            t.check("guessing after the end is refused", refused(() -> m.guess("RGBY")));

            Mastermind spent = new Mastermind(new java.util.Random(2));
            spent.setSecret("RRRR");
            for (int i = 0; i < Mastermind.MAX_GUESSES; i++) { spent.guess("GGGG"); }
            t.check("running out of guesses ends it", spent.over() && !spent.won());

            t.check("a short code is refused",
                    refused(() -> Mastermind.score("RGB", "RGBY")));
            t.check("an unknown colour is refused",
                    refused(() -> Mastermind.score("RGBZ", "RGBY")));
            t.check("lowercase is accepted", Mastermind.score("rgby", "RGBY").solved());
        });
    }

    private static String mastermindCode(int n, String colours) {
        StringBuilder sb = new StringBuilder();
        int value = n;
        for (int i = 0; i < Mastermind.LENGTH; i++) {
            sb.append(colours.charAt(value % colours.length()));
            value /= colours.length();
        }
        return sb.toString();
    }
}
