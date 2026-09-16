# random-java

Two things live here.

1. **[The 251-project suite](#the-251-project-suite)** — one launcher, 251 projects,
   each playable in the terminal *and* in a browser, sharing the same Java.
2. **[The original 10 projects](#the-original-10-projects)** — standalone folders,
   each built and run on its own.

Both use the local JDK in `openJdk-25/`, so there is nothing to install.

---

## The 251-project suite

Every project implements one interface with two entry points: `runText(ConsoleUI)`
for the terminal and `api(action, body)` for the browser. The logic sits in
ordinary methods that both call, so nothing is written twice and the two front
ends cannot drift apart.

### Run it

```bash
./build.sh            # compile all 251 + the shared lib     (.\build.ps1 on Windows)
./run.sh              # interactive terminal menu            (.\run.ps1)
./run.sh web          # browser hub on http://localhost:8080
./run.sh text 1       # one project in the terminal
./run.sh text maze    # ...by name instead of number
./run.sh list sort    # search the catalogue
```

`run.sh` builds first if you have not already. The web hub serves all 251 from a
single JVM on a single port, using only the JDK's built-in HTTP server — no
servlet container, no npm, no external jars anywhere in the project.

### What state it is in

132 projects are fully implemented. The other 119 are **runnable scaffolds**: they
compile, they run in the terminal, their web page loads and talks to the Java
behind it — what they do not have yet is the real logic. Each one has a single
clearly marked method to fill in.

[`projects/INDEX.md`](projects/INDEX.md) is the checklist of all 251 and tracks
which are which.

| Implemented | | |
|---|---|---|
| [001 Simple Calculator](projects/001-simple-calculator/) | [002 To-Do List](projects/002-todo-list/) | [004 BMI Calculator](projects/004-bmi-calculator/) |
| [008 Unit Converter](projects/008-unit-converter/) | [009 Rock Paper Scissors](projects/009-rock-paper-scissors/) | [012 Markdown to HTML](projects/012-markdown-to-html/) |
| [021 Sudoku Solver](projects/021-sudoku-solver/) | [022 Maze Generator](projects/022-maze-generator/) | [024 Sorting Visualizer](projects/024-sorting-visualizer/) |
| [028 Palindrome Checker](projects/028-palindrome-checker/) | [029 Prime Generator](projects/029-prime-number-generator/) | [061 Password Strength](projects/061-password-strength-checker/) |
| [101 ASCII Art Generator](projects/101-ascii-art-generator/) | [130 Tic-Tac-Toe](projects/130-tic-tac-toe/) | [131 Word Counter](projects/131-word-counter/) |
| **20 cube projects** | 3x3 and 2x2 plus every shape mod that shares their mechanics | see the table below |

### Tests

```bash
./test.sh             # compile and run everything   (.\test.ps1 on Windows)
```

**7,281 checks across 26 groups, in about 25 seconds.** No JUnit, no jars — the
same rule as the rest of the repo. Tests live in [`tests/`](tests) and compile
into `build/test-classes`, so they never ship inside `build/classes`. The runner
exits non-zero on any failure, so it can gate a commit.

| Group | What it proves |
|---|---|
| Json, Move notation | Round trips, number types, escaping, and every parse error being rejected rather than silently accepted |
| Cube engine | 315 checks folded in, including that the order of `R U` is exactly 105 |
| Cube validator | Accepts 25 real scrambles, rejects a twisted corner, a flipped edge and a swapped pair |
| 3x3 and 2x2 solvers | 40 and 150 random scrambles, each **replayed onto a fresh cube** rather than trusting the solver's own state |
| Each implemented project | Real answers, not smoke: `2^3^2 = 512`, `1 mi = 1.609344 km`, `360 = 2·2·2·3·3·5`, every sort ending sorted, the Sudoku solution checked row, column and box |
| Catalogue integrity | 251 ids contiguous, slugs unique and URL-safe, every project's metadata matching the catalogue |
| Every project behaves | All 251 construct, serve a page, and answer an unknown action with an error instead of an exception |
| Files on disk | Every project folder, README and Java file present |

The solver tests deliberately replay the solution onto a *newly scrambled*
cube. A solver that quietly mutated its input would still look correct if you
only checked its own copy.

### Twisty puzzles

Projects 165 to 221 are a Rubik's cube station built on a shared engine in
[`lib/com/randomjava/cube`](lib/com/randomjava/cube). Every puzzle can be
scrambled, turned in standard notation, and stepped through; the ones with a
solver can also explain themselves move by move, give a single hint, or solve
the cube you type in from your desk.

**Every cube from 1×1×1 to 100×100×100** is in the catalogue, because the move
engine is generic in N — a 100×100 is 60,000 stickers and a few hundred
kilobytes. Turning, scrambling and cube entry work at every size; only the
solver is size-limited.

**Shape modifications solve for free.** Fisher, Windmill, Ghost, Mastermorphix,
Bump, Camouflage, Truncated, Cylindrical, X-Cube, Dreidel, Void, Mirror and Axis
are all mechanically a 3×3, so the 3×3 solver drives them directly. Pyramorphix
and the Junior Cube are mechanically a 2×2, so they get the optimal solver. Each
one says so on its page rather than pretending to be a separate engine.

**What genuinely solves today:**

| Puzzle | Method | Verified by |
|---|---|---|
| [165 Rubik's Cube 3x3](projects/165-rubiks-cube-3x3/) | **Two methods:** beginner layer-by-layer (7 explained stages) and CFOP (cross, F2L, 2-look OLL, 1-look PLL with all 21 cases) | 300 scrambles layer-by-layer, 40 CFOP, each replayed onto a fresh cube |
| [166 Pocket Cube 2x2](projects/166-pocket-cube-2x2/) | Optimal, IDA* under an exact bound | 1,000 scrambles, never above God's number of 11 |
| 13 shape mods — [Void](projects/171-void-cube/), [Mirror](projects/172-mirror-cube/), [Axis](projects/173-axis-cube/), [Fisher](projects/209-fisher-cube/), [Windmill](projects/210-windmill-cube/), [Ghost](projects/211-ghost-cube/), [Mastermorphix](projects/212-mastermorphix/) and more | The same two 3x3 methods — all are 3x3 mechanics in a different shell | shares the 3x3 verification |
| [179 Pyramorphix](projects/179-pyramorphix/), [221 Junior Cube](projects/221-junior-cube/) | The optimal 2x2 solver — both are a 2x2 underneath | shares the 2x2 verification |
| [186 Cube 1x1x1](projects/186-cube-1x1x1/) | Nothing to do; it is solved when you pick it up | — |

CFOP averages **77 moves against layer-by-layer's 100** on the same scrambles,
which is the whole point of it. Where an F2L pair has no short insertion — the
last slot is the most constrained — it finishes layer by layer and says so in
the step text rather than failing.

**Reduction, stage by stage.** Big cubes are solved by *reduction*: build the
centres, pair the edges, then finish as a 3x3. **Stage three is implemented and
verified** — any cube that is already reduced, including one scrambled with
outer turns only, solves at every size from 4x4 to 100x100, in about a quarter
of a second even at 60,000 stickers. Stages one and two are not written, and a
slice-scrambled cube says so rather than being attempted.

Why those two stages are not a search problem, which is the thing worth knowing
if you pick this up: placing one centre is easy, but placing the sixth face's
centres without disturbing the first five is not, because every move that
reaches an unsolved piece also passes through solved ones. Real methods use
*commutators* — sequences shaped `A B A' B'` that move three pieces and leave
everything else exactly as it was. Those have to be written out; a naive search
cannot find them at any useful depth. That is the whole reason this stage
resisted the approach that worked everywhere else in the suite. The eight non-cube puzzles
(Pyraminx, Skewb, Megaminx, Square-1, Clock, Gear, Dino, Floppy) are scaffolds:
each needs its own piece model, and guessing at orientation conventions I cannot
verify would produce something that solves but is not actually that puzzle.

**Three supporting tools, all working.**

The [CFOP trainer](projects/183-cfop-trainer/) drills all 21 PLL cases and a
two-look OLL set. Cases are not stored as pictures: each is generated by running
its own algorithm *backwards* from solved, so the puzzle and the answer can
never disagree. Your answer is graded by applying it to the cube, not by
matching text, so any correct solution passes. A `selfCheck()` confirms all 30
algorithms solve the case they claim to.

The [method comparison](projects/184-method-comparison/) runs both solvers on one
scramble and replays each solution onto a fresh cube before reporting, so no
method is ever credited with a solve that does not work.

The [speedcube timer](projects/185-speedcube-timer/) implements averages the way
competitions judge them: an average of 5 drops the fastest *and* the slowest and
means the middle three, so `12.0, 9.5, 11.2, 30.0, 10.1` gives **11.10**, not the
naive mean of 14.56. A +2 adds two seconds; one DNF is the discard, two make the
whole average a DNF.

**Typing in your own cube.** Read each face left to right and top to bottom in
the order U R F D L B, writing the letter of the face each sticker belongs to.
Impossible cubes are rejected with the reason rather than handed to a solver
that would search forever: a single twisted corner, a single flipped edge, and
two swapped pieces are each caught by their own check.

**The engine is tested, not assumed.** `CubeSelfTest` runs 315 checks, the
sharpest being that the order of `R U` on a 3x3 is exactly 105 — any error in
any turn changes that number.

```bash
java -cp build/classes com.randomjava.cube.CubeSelfTest
```

### Logic and puzzle games

Projects 222 to 251 are puzzles that are not twisty: sliding tiles, Minesweeper,
2048, Sokoban, Nonogram, Lights Out, Kakuro, Futoshiki, KenKen, Slitherlink,
Nurikabe, Hitori, Skyscrapers, Shikaku, Mastermind, cryptarithms, the zebra
puzzle, river crossings, Rush Hour, knight's tours and more.

Two are implemented and verified against known answers:

| Puzzle | Verified by |
|---|---|
| [222 Tower of Hanoi](projects/222-tower-of-hanoi/) | Solved for 1 to 16 discs, each in exactly 2^n - 1 moves, which is provably the minimum |
| [223 N Queens](projects/223-n-queens/) | Solved and validated for N = 1 to 12; solution counts match the known values (8 queens = 92, 6 = 4, 10 = 724), and N = 2 and 3 correctly report no solution |

### Difficulty and recommended stacks

Every project carries a **difficulty** — beginner, intermediate or advanced —
shown as a badge on the hub and in the launcher's list, and matched by the
filter box, so `advanced` or `beginner` narrows the grid.

Some projects also carry a **recommended stack**, recorded from the brief they
came from: Spring Boot, MySQL, Thymeleaf, Hibernate, iText, Lucene and so on.
That is a note about how you would build the thing for real — **it is not what
is in this repo**. The suite stays dependency-free on purpose: plain JDK, no
build tool, no jars, one command to run. Those projects use in-memory storage
and the shared web hub instead, and the note is there so the original intent is
not lost if you later rebuild one properly.

### Layout

```
lib/com/randomjava/
  Launcher.java          the single entry point for everything
  Catalog.java           GENERATED - every project's metadata and constructor
  lib/
    Project.java         the interface all 251 implement
    Meta.java            id, name, category, shape, difficulty, stack, status
    Kind.java            scaffold shape: TOOL, LIST, GAME, GRID
    Difficulty.java      beginner, intermediate, advanced
    ConsoleUI.java       terminal prompts, menus, tables, colour
    WebHub.java          the HTTP server, one port for all 128
    Shell.java           page chrome, stylesheet, RJ client helper
    Json.java            dependency-free JSON reader/writer

projects/NNN-slug/
  ClassName.java         logic + runText() + api()
  ui.html                just the page fragment; chrome comes from the shell
  README.md              GENERATED - how to run it, what to implement

tools/
  catalog.py             the 251 projects - the single source of truth
  generate.py            emits scaffolds, READMEs, INDEX.md and Catalog.java
  catalog.json           GENERATED - the same catalogue as plain data
```

### Adding or changing a project

Edit [`tools/catalog.py`](tools/catalog.py), then:

```bash
python3 tools/generate.py && ./build.sh
```

The generator never overwrites a project marked `done`, so hand-written work is
safe. Everything else — READMEs, the index, the Java catalogue — is refreshed
every run.

### How the browser side works

Each `ui.html` is a fragment, not a document. The shell supplies the stylesheet
and a small `RJ` helper, which auto-wires the markup:

```html
<input data-field="input">
<button data-action="compute">Run</button>
<div id="out" class="out"></div>
```

Clicking any `[data-action]` posts every `[data-field]` on the page to
`api(action, body)`. Whatever comes back is rendered by convention:
`result` and `detail` fill `#out`, `board` draws a grid, `bars` draws a bar
chart, `items` draws a list, `html` is injected as-is. That convention is why a
typical project's UI file is a dozen lines.

---

## The original 10 projects

Standalone folders predating the suite, each compiled and run on its own. These
follow a **console → GUI** pattern: a text version plus a Swing or JavaFX window,
or for the web ones a plain `index.html`.

All Java commands below assume the local JDK:

```bash
export PATH="./openJdk-25/bin:$PATH"
```

### Java, console → GUI

| Project | Console | GUI | Run |
|---|---|---|---|
| [Snake](snake-game/) | `SnakeGame.java` | `SnakeGUI.java`, `javafx/SnakeFXApp.java` | `cd snake-game && javac SnakeGUI.java Point.java && java SnakeGUI` |
| [ATM Interface](atm-interface/) | `AtmInterface.java` | `AtmGUI.java`, `javafx/AtmFXApp.java` | `cd atm-interface && javac *.java && java AtmGUI` |
| [Rat in a Maze](rat-in-a-maze/) | `RatInMaze.java` | `RatMazeGUI.java`, `javafx/RatMazeFX.java` | `cd rat-in-a-maze && javac *.java && java RatMazeGUI` |
| [Chess](chess-game/) | `ChessGame_text.java` | `ChessBoardGUI.java`, `ChessGame_interactive.java` | `cd chess-game && javac *.java && java ChessGame_interactive` |
| [Brick Breaker](brick-breaker-game/) | — | `Main.java` (Swing) + `index.html` | `cd brick-breaker-game && javac *.java && java Main` |
| [One-on-One Chat](one-on-one-chat-app/) | `ChatClient_text.java` | `ChatClient_interactive.java` + `index.html` | `cd one-on-one-chat-app && javac *.java && java ChatServer` |

### Browser only

| Project | Open |
|---|---|
| [Sorting Visualizer](sorting-visualizer/) | `index.html` |
| [Data Visualization](data-visualization-software/) | `index.html` |
| [Random Number & Colour Generator](random-number-color-generator/) | `index.html` |

### Node

| Project | Run |
|---|---|
| [Two Truths and a Lie Slack Bot](two-truths-lie-slack-bot/) | `cd two-truths-lie-slack-bot && npm i && node bot.js` (copy `.env.example` first) |

---

## Requirements

JDK 17 or newer. The bundled `openJdk-25/` is used automatically when present;
otherwise the scripts fall back to whatever `javac` and `java` are on your PATH.
The suite compiles with `--release 17`, so it runs on anything from 17 up.
`tools/generate.py` needs Python 3.10 or newer, and is only needed if you change
the catalogue.
