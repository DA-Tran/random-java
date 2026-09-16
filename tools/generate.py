#!/usr/bin/env python3
"""Generates the 128 project folders and lib/com/randomjava/Catalog.java.

    python3 tools/generate.py            regenerate scaffolds and the catalogue
    python3 tools/generate.py --force    also overwrite projects marked done

Projects marked `done` in catalog.py keep their hand-written Java and ui.html.
Their README and their entry in Catalog.java are always refreshed, so editing
catalog.py stays the way to rename or re-describe anything.
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import catalog  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
PROJECTS_DIR = ROOT / "projects"
CATALOG_JAVA = ROOT / "lib" / "com" / "randomjava" / "Catalog.java"
CATALOG_JSON = ROOT / "tools" / "catalog.json"

FORCE = "--force" in sys.argv


# ----------------------------------------------------------------------
# Java scaffolds
# ----------------------------------------------------------------------

JAVA_HEADER = """package com.randomjava.projects.__PKG__;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;
__EXTRA_IMPORTS__
/**
 * __NAME__ - __DESCRIPTION__
 *
 * <p>Scaffold. Both front ends already work and talk to this class; what is
 * missing is the real logic. Fill in the marked method and the terminal and
 * the browser both start behaving properly, with nothing written twice.
 */
public final class __CLASS__ implements Project {

    public static final Meta META = new Meta(
            __ID__, "__SLUG__", "__NAME__", "__CATEGORY__",
            Kind.__KIND__, Difficulty.__DIFFICULTY__, "__DESCRIPTION__",
            "__STACK__", false);

    @Override
    public Meta meta() {
        return META;
    }
"""

TOOL_BODY = """
    // ------------------------------------------------------------------
    // The one method to implement
    // ------------------------------------------------------------------

    /**
     * Turns the user's input into an answer.
     *
     * @param input raw text from the terminal prompt or the web form
     * @return the headline answer to display
     */
    private String compute(String input) {
        // TODO: __DESCRIPTION__
        if (input.isBlank()) {
            return "(no input)";
        }
        return "Read " + input.length() + " characters. Implement compute() for the real answer.";
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Enter a value to run compute(). Blank line to finish.");
        while (true) {
            String input = io.ask("input:");
            if (input.isEmpty()) {
                return;
            }
            io.result("result", compute(input));
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) {
            return Json.error("Unknown action: " + action);
        }
        return Json.ok(
                "result", compute(Json.str(body, "input", "")),
                "detail", "compute() is still a scaffold in __CLASS__.java");
    }
}
"""

LIST_BODY = """
    private record Row(int id, String label, String note, boolean done) {
    }

    private final List<Row> rows = new ArrayList<>();
    private int nextId = 1;

    // ------------------------------------------------------------------
    // The methods to implement
    // ------------------------------------------------------------------

    /** Adds an entry. Validate or enrich it here. */
    private Row add(String label, String note) {
        // TODO: __DESCRIPTION__
        Row row = new Row(nextId++, label, note, false);
        rows.add(row);
        return row;
    }

    /** A one line summary shown under the list. */
    private String summary() {
        long done = rows.stream().filter(Row::done).count();
        return rows.size() + " entries, " + done + " marked done";
    }

    // ------------------------------------------------------------------
    // Shared operations
    // ------------------------------------------------------------------

    private boolean toggle(int id) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.id() == id) {
                rows.set(i, new Row(row.id(), row.label(), row.note(), !row.done()));
                return true;
            }
        }
        return false;
    }

    private boolean remove(int id) {
        return rows.removeIf(row -> row.id() == id);
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row row : rows) {
            out.add(Json.map("id", row.id(), "label", row.label(),
                    "meta", row.note(), "done", row.done()));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        while (true) {
            show(io);
            int choice = io.menu("What next?", List.of("Add", "Toggle done", "Remove", "Clear all"));
            if (choice < 0) {
                return;
            }
            switch (choice) {
                case 0 -> {
                    String label = io.ask("label:");
                    if (label.isEmpty()) {
                        io.warn("Nothing added.");
                    } else {
                        add(label, io.ask("note (optional):"));
                        io.ok("Added.");
                    }
                }
                case 1 -> io.println(toggle(io.askInt("id to toggle:", 1, Integer.MAX_VALUE, 1))
                        ? "  toggled" : "  no entry with that id");
                case 2 -> io.println(remove(io.askInt("id to remove:", 1, Integer.MAX_VALUE, 1))
                        ? "  removed" : "  no entry with that id");
                default -> {
                    rows.clear();
                    io.ok("Cleared.");
                }
            }
        }
    }

    private void show(ConsoleUI io) {
        io.println();
        if (rows.isEmpty()) {
            io.muted("(empty)");
            return;
        }
        List<List<String>> table = new ArrayList<>();
        for (Row row : rows) {
            table.add(List.of(String.valueOf(row.id()), row.done() ? "x" : " ",
                    row.label(), row.note() == null ? "" : row.note()));
        }
        io.table(List.of("id", "done", "label", "note"), table);
        io.muted(summary());
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        switch (action) {
            case "add" -> {
                String label = Json.str(body, "label", "").trim();
                if (label.isEmpty()) {
                    return Json.error("Give the entry a label first.");
                }
                add(label, Json.str(body, "meta", ""));
                return Json.ok("items", snapshot(), "message", "Added", "detail", summary());
            }
            case "toggle" -> {
                toggle(Json.integer(body, "id", -1));
                return Json.ok("items", snapshot(), "detail", summary());
            }
            case "remove" -> {
                remove(Json.integer(body, "id", -1));
                return Json.ok("items", snapshot(), "message", "Removed", "detail", summary());
            }
            case "clear" -> {
                rows.clear();
                return Json.ok("items", snapshot(), "message", "Cleared", "detail", summary());
            }
            case "list" -> {
                return Json.ok("items", snapshot(), "detail", summary());
            }
            default -> {
                return Json.error("Unknown action: " + action);
            }
        }
    }
}
"""

GAME_BODY = """
    private final Random random = new Random();
    private int round;
    private int wins;
    private int losses;
    private int draws;

    // ------------------------------------------------------------------
    // The one method to implement
    // ------------------------------------------------------------------

    /**
     * Plays a single round.
     *
     * @param move what the player chose
     * @return a short description of what happened
     */
    private String playRound(String move) {
        // TODO: __DESCRIPTION__
        round++;
        int outcome = random.nextInt(3);
        if (outcome == 0) {
            wins++;
            return "You played '" + move + "' and won this round.";
        }
        if (outcome == 1) {
            losses++;
            return "You played '" + move + "' and lost this round.";
        }
        draws++;
        return "You played '" + move + "' and drew.";
    }

    private void reset() {
        round = 0;
        wins = 0;
        losses = 0;
        draws = 0;
    }

    private String scoreline() {
        return "Round " + round + "  |  " + wins + " won, " + losses + " lost, " + draws + " drawn";
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        reset();
        io.muted("Enter a move each round. Blank line to stop.");
        while (true) {
            String move = io.ask("your move:");
            if (move.isEmpty()) {
                io.result("final score", scoreline());
                return;
            }
            io.println("  " + playRound(move));
            io.muted(scoreline());
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        switch (action) {
            case "new" -> {
                reset();
                return Json.ok("result", "New game", "detail", scoreline());
            }
            case "play" -> {
                String move = Json.str(body, "move", "").trim();
                if (move.isEmpty()) {
                    return Json.error("Pick a move first.");
                }
                String outcome = playRound(move);
                return Json.ok("result", outcome, "detail", scoreline());
            }
            case "state" -> {
                return Json.ok("result", round == 0 ? "Ready" : "In progress", "detail", scoreline());
            }
            default -> {
                return Json.error("Unknown action: " + action);
            }
        }
    }
}
"""

GRID_BODY = """
    private char[][] cells = new char[0][0];
    private int size = 12;
    private int steps;

    // ------------------------------------------------------------------
    // The methods to implement
    // ------------------------------------------------------------------

    /** Builds the starting board. */
    private void generate(int requestedSize) {
        // TODO: __DESCRIPTION__
        size = Math.max(4, Math.min(40, requestedSize));
        steps = 0;
        cells = new char[size][size];
        Random random = new Random();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                boolean edge = row == 0 || column == 0 || row == size - 1 || column == size - 1;
                cells[row][column] = edge || random.nextInt(5) == 0 ? '#' : '.';
            }
        }
        cells[1][1] = '*';
    }

    /** Advances the board one tick. */
    private void step() {
        // TODO: advance the simulation by one step
        if (cells.length == 0) {
            generate(size);
            return;
        }
        steps++;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (cells[row][column] == '*') {
                    cells[row][column] = '.';
                    int nextColumn = column + 1 < size - 1 ? column + 1 : 1;
                    int nextRow = nextColumn == 1 ? (row + 1 < size - 1 ? row + 1 : 1) : row;
                    cells[nextRow][nextColumn] = '*';
                    return;
                }
            }
        }
    }

    /** Runs to completion, if that means anything for this project. */
    private String solve() {
        // TODO: solve or run to completion
        for (int i = 0; i < size * 2; i++) {
            step();
        }
        return "Ran " + (size * 2) + " steps. Implement solve() for the real thing.";
    }

    private String status() {
        return size + "x" + size + " board, " + steps + " steps taken";
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        generate(io.askInt("board size:", 4, 40, 12));
        io.println();
        io.grid(cells);
        io.muted(status());
        while (true) {
            int choice = io.menu("Board", List.of("Step", "Run to completion", "Regenerate"));
            if (choice < 0) {
                return;
            }
            switch (choice) {
                case 0 -> step();
                case 1 -> io.info(solve());
                default -> generate(size);
            }
            io.println();
            io.grid(cells);
            io.muted(status());
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        switch (action) {
            case "generate" -> {
                generate(Json.integer(body, "size", 12));
                return Json.ok("board", Json.grid(cells), "detail", status());
            }
            case "step" -> {
                step();
                return Json.ok("board", Json.grid(cells), "detail", status());
            }
            case "solve" -> {
                String message = solve();
                return Json.ok("board", Json.grid(cells), "result", message, "detail", status());
            }
            default -> {
                return Json.error("Unknown action: " + action);
            }
        }
    }
}
"""

BODIES = {"TOOL": TOOL_BODY, "LIST": LIST_BODY, "GAME": GAME_BODY, "GRID": GRID_BODY}

EXTRA_IMPORTS = {
    "TOOL": "\nimport java.util.Map;\n",
    "LIST": "\nimport java.util.ArrayList;\nimport java.util.List;\nimport java.util.Map;\n",
    "GAME": "\nimport java.util.Map;\nimport java.util.Random;\n",
    "GRID": "\nimport java.util.List;\nimport java.util.Map;\nimport java.util.Random;\n",
}


# ----------------------------------------------------------------------
# HTML scaffolds
# ----------------------------------------------------------------------

TOOL_UI = """<div class="card">
  <h2>Input</h2>
  <p class="hint">__DESCRIPTION__</p>
  <div class="row">
    <label class="field">
      <span>Input</span>
      <input data-field="input" placeholder="type something" autocomplete="off">
    </label>
    <div class="tight"><button data-action="compute" data-primary>Run</button></div>
  </div>
</div>

<div class="card">
  <h2>Result</h2>
  <div id="out" class="out"><p class="empty">Nothing yet.</p></div>
</div>
"""

LIST_UI = """<div class="card">
  <h2>Add an entry</h2>
  <p class="hint">__DESCRIPTION__</p>
  <div class="row">
    <label class="field">
      <span>Label</span>
      <input data-field="label" placeholder="what is it" autocomplete="off">
    </label>
    <label class="field">
      <span>Note</span>
      <input data-field="meta" placeholder="optional detail" autocomplete="off">
    </label>
    <div class="tight"><button data-action="add" data-primary>Add</button></div>
  </div>
  <div class="buttons">
    <button class="quiet" data-action="clear">Clear all</button>
  </div>
</div>

<div class="card" data-boot="list">
  <h2>Entries</h2>
  <div id="items"><p class="empty">Nothing here yet.</p></div>
  <div id="out" class="out"></div>
</div>
"""

GAME_UI = """<div class="card">
  <h2>Play</h2>
  <p class="hint">__DESCRIPTION__</p>
  <div class="row">
    <label class="field">
      <span>Your move</span>
      <input data-field="move" placeholder="your move" autocomplete="off">
    </label>
    <div class="tight"><button data-action="play" data-primary>Play</button></div>
    <div class="tight"><button class="ghost" data-action="new">New game</button></div>
  </div>
</div>

<div class="card" data-boot="state">
  <h2>Result</h2>
  <div id="out" class="out"></div>
</div>
"""

GRID_UI = """<div class="card">
  <h2>Controls</h2>
  <p class="hint">__DESCRIPTION__</p>
  <div class="row">
    <label class="field">
      <span>Board size</span>
      <input data-field="size" type="number" value="12" min="4" max="40">
    </label>
    <div class="tight"><button data-action="generate" data-primary>Generate</button></div>
    <div class="tight"><button class="ghost" data-action="step">Step</button></div>
    <div class="tight"><button class="quiet" data-action="solve">Run</button></div>
  </div>
</div>

<div class="card" data-boot="generate">
  <h2>Board</h2>
  <div id="board"></div>
  <div id="out" class="out"></div>
</div>
"""

UIS = {"TOOL": TOOL_UI, "LIST": LIST_UI, "GAME": GAME_UI, "GRID": GRID_UI}


README = """# __ID3__ - __NAME__

> __DESCRIPTION__

| | |
|---|---|
| Category | __CATEGORY__ |
| Difficulty | __DIFFICULTY_LABEL__ |
| Shape | __KIND__ |
| Status | __STATUS__ |
| Slug | `__SLUG__` |
__STACK_ROW__

## Run it

From the repository root, after `./build.sh` (or `.\\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text __SLUG__

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/__SLUG__/>

## Files

| File | What it is |
|---|---|
| `__CLASS__.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

__TODO_NOTE__

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
"""

TODO_NOTES = {
    "TOOL": "`compute(String input)` is the only method that matters. Everything else is wiring.",
    "LIST": "`add(...)` and `summary()` carry the meaning. Toggle, remove and clear are already generic.",
    "GAME": "`playRound(String move)` decides the outcome and updates the score.",
    "GRID": "`generate(int size)`, `step()` and `solve()` own the board. The renderers are generic.",
}


def fill(template: str, row: dict, extra: dict | None = None) -> str:
    values = {
        "__PKG__": row["package"],
        "__CLASS__": row["klass"],
        "__NAME__": row["name"],
        "__SLUG__": row["slug"],
        "__CATEGORY__": row["category"],
        "__KIND__": row["kind"],
        "__ID__": str(row["id"]),
        "__ID3__": f"{row['id']:03d}",
        "__DESCRIPTION__": row["description"],
        "__DIFFICULTY__": row["difficulty"].upper(),
        "__DIFFICULTY_LABEL__": row["difficulty"].capitalize(),
        "__STACK__": row["stack"],
    }
    if extra:
        values.update(extra)
    out = template
    for key, value in values.items():
        out = out.replace(key, value)
    return out


def write(path: Path, content: str) -> bool:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_text(encoding="utf-8") == content:
        return False
    path.write_text(content, encoding="utf-8", newline="\n")
    return True


def generate_index(rows: list[dict]) -> str:
    """A browsable checklist of all 128, grouped by category."""
    done = sum(1 for row in rows if row["done"])
    out = [
        f"# All {len(rows)} projects",
        "",
        f"{done} implemented, {len(rows) - done} runnable scaffolds. "
        "A scaffold compiles, runs in the terminal and answers its web page; "
        "what it does not yet have is the real logic.",
        "",
        "Tick a box when you graduate one out of scaffold state, and flip its "
        "`done` flag in [`tools/catalog.py`](../tools/catalog.py) so the hub and "
        "the launcher agree.",
        "",
    ]
    current_category = None
    for row in rows:
        if row["category"] != current_category:
            current_category = row["category"]
            out += ["", f"## {current_category}", ""]
        box = "x" if row["done"] else " "
        out.append(
            f"- [{box}] **{row['id']:03d}** [{row['name']}]({row['folder']}/) "
            f"`{row['kind'].lower()}` `{row['difficulty']}` - {row['description']}"
        )
    out.append("")
    return "\n".join(out)


def generate_catalog_java(rows: list[dict]) -> str:
    def java_string(text: str) -> str:
        escaped = text.replace("\\", "\\\\").replace('"', '\\"')
        return f'"{escaped}"'

    metas = ",\n".join(
        "            new Meta({id}, {slug}, {name}, {category}, Kind.{kind}, "
        "Difficulty.{difficulty}, {description}, {stack}, {done})".format(
            id=row["id"],
            slug=java_string(row["slug"]),
            name=java_string(row["name"]),
            category=java_string(row["category"]),
            kind=row["kind"],
            difficulty=row["difficulty"].upper(),
            description=java_string(row["description"]),
            stack=java_string(row.get("stack") or ""),
            done="true" if row["done"] else "false",
        )
        for row in rows
    )
    classes = ",\n".join(
        f'            {{"{row["slug"]}", '
        f'"com.randomjava.projects.{row["package"]}.{row["klass"]}"}}'
        for row in rows
    )
    return f"""package com.randomjava;

import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * GENERATED FILE - do not edit by hand.
 *
 * <p>Regenerate with {{@code python3 tools/generate.py}} after changing
 * {{@code tools/catalog.py}}.
 *
 * <p>Holds every project's metadata and a way to construct it. Both are
 * deliberately arranged so that <b>reading the catalogue does not load the
 * {len(rows)} project classes</b>.
 *
 * <p>The obvious version of this file writes {{@code Foo.META}} for the
 * metadata and {{@code Foo::new}} for the factory. Both touch the class: a
 * static field read initialises it, and a constructor reference has to link the
 * class to bootstrap its call site. Listing every project therefore loaded
 * every project, which cost about 4.7 seconds before the launcher could print
 * its menu or the hub could serve its first page - all to run one of them.
 *
 * <p>So the metadata is written out as literals, and the factories resolve
 * their class by name on first use. Starting up now touches nothing, and
 * visiting a project loads exactly that project.
 *
 * <p>The cost of the literals is that they could drift from the {{@code META}}
 * each project declares for itself. {{@code SuiteTests}} compares the two for
 * every project, which turns a silent inconsistency into a failing test.
 */
public final class Catalog {{

    private Catalog() {{
    }}

    private static final List<Meta> ALL = List.of(
{metas});

    /** Slug to fully-qualified class name. Strings, so nothing is loaded. */
    private static final String[][] CLASS_NAMES = {{
{classes}
    }};

    private static final Map<String, Supplier<Project>> FACTORIES = buildFactories();

    private static Map<String, Supplier<Project>> buildFactories() {{
        Map<String, Supplier<Project>> map = new LinkedHashMap<>();
        for (String[] row : CLASS_NAMES) {{
            // One lambda body for all {len(rows)} entries, closing over a
            // string. Writing Foo::new here instead would be {len(rows)}
            // separate call sites, each needing its class loaded to link.
            String className = row[1];
            map.put(row[0], () -> instantiate(className));
        }}
        return Collections.unmodifiableMap(map);
    }}

    private static Project instantiate(String className) {{
        try {{
            return (Project) Class.forName(className)
                    .getDeclaredConstructor()
                    .newInstance();
        }} catch (ReflectiveOperationException e) {{
            throw new IllegalStateException(
                    "Cannot create " + className + ". The catalogue and the project classes "
                    + "have got out of step; regenerate with tools/generate.py.", e);
        }}
    }}

    /** Every project, in catalogue order. */
    public static List<Meta> all() {{
        return ALL;
    }}

    /** Slug to a factory that loads and constructs the project on demand. */
    public static Map<String, Supplier<Project>> factories() {{
        return FACTORIES;
    }}
}}
"""


def main() -> None:
    rows = list(catalog.rows())
    expected = catalog.CATEGORIES[-1][1]
    if len(rows) != expected:
        raise SystemExit(
            f"catalogue covers ids 1-{expected} but has {len(rows)} projects; "
            "an id is missing or duplicated")

    created, skipped, refreshed = 0, 0, 0

    for row in rows:
        folder = PROJECTS_DIR / row["folder"]
        java_path = folder / f"{row['klass']}.java"
        ui_path = folder / "ui.html"

        protected = row["done"] or row["slug"] in getattr(catalog, "HANDWRITTEN", set())
        if protected and not FORCE:
            if not java_path.exists():
                raise SystemExit(
                    f"{row['slug']} is marked done but {java_path.relative_to(ROOT)} is missing"
                )
            skipped += 1
        else:
            java = fill(JAVA_HEADER, row, {"__EXTRA_IMPORTS__": EXTRA_IMPORTS[row["kind"]]})
            java += fill(BODIES[row["kind"]], row)
            if write(java_path, java):
                created += 1
            write(ui_path, fill(UIS[row["kind"]], row))

        status = "implemented" if row["done"] else "scaffold"
        stack_row = ""
        if row["stack"]:
            stack_row = (
                f"\n**Recommended stack for a production build:** {row['stack']}\n\n"
                "That is what the original brief called for. This suite is "
                "deliberately dependency-free — plain JDK, no build tool, no jars — "
                "so the version here uses in-memory storage and the shared web hub "
                "instead. The note is recorded so the intent is not lost if you "
                "later rebuild it for real.\n"
            )
        readme = fill(README, row, {
            "__STATUS__": status,
            "__STACK_ROW__": stack_row,
            "__TODO_NOTE__": TODO_NOTES[row["kind"]] if not row["done"]
            else "Already implemented. Read the source for how it works.",
        })
        if write(folder / "README.md", readme):
            refreshed += 1

    write(PROJECTS_DIR / "INDEX.md", generate_index(rows))
    write(CATALOG_JAVA, generate_catalog_java(rows))
    write(CATALOG_JSON, json.dumps(
        [{k: row[k] for k in ("id", "slug", "name", "category", "kind",
                              "difficulty", "done", "description", "stack")}
         for row in rows], indent=2) + "\n")

    done = sum(1 for row in rows if row["done"])
    print(f"projects:   {len(rows)}  ({done} implemented, {len(rows) - done} scaffolds)")
    print(f"java:       {created} written, {skipped} hand-written left alone")
    print(f"readmes:    {refreshed} updated")
    print(f"catalogue:  {CATALOG_JAVA.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
