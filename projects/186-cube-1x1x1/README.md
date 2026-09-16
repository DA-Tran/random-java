# 186 - Cube 1x1x1

> A single cubie with nothing to turn, so it is solved the moment you pick it up.

| | |
|---|---|
| Category | Big Cubes |
| Difficulty | Beginner |
| Shape | GRID |
| Status | implemented |
| Slug | `cube-1x1x1` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text cube-1x1x1

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/cube-1x1x1/>

## Files

| File | What it is |
|---|---|
| `Cube1x1x1.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

Already implemented. Read the source for how it works.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
