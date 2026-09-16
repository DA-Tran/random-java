# 218 - Truncated Cube

> A 3x3 with its corners sliced off, changing the look and nothing else.

| | |
|---|---|
| Category | Shape Modifications |
| Difficulty | Advanced |
| Shape | GRID |
| Status | implemented |
| Slug | `truncated-cube` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text truncated-cube

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/truncated-cube/>

## Files

| File | What it is |
|---|---|
| `TruncatedCube.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

Already implemented. Read the source for how it works.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
