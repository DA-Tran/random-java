# 235 - Slitherlink

> Draw a single closed loop so each number has that many edges around it.

| | |
|---|---|
| Category | Logic and Puzzle Games |
| Difficulty | Intermediate |
| Shape | GRID |
| Status | scaffold |
| Slug | `slitherlink` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text slitherlink

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/slitherlink/>

## Files

| File | What it is |
|---|---|
| `Slitherlink.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

`generate(int size)`, `step()` and `solve()` own the board. The renderers are generic.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
