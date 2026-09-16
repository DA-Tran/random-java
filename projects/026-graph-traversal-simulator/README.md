# 026 - Graph Traversal Simulator

> Run breadth-first and depth-first search over a graph, step by step.

| | |
|---|---|
| Category | Algorithms and Data Structures |
| Difficulty | Intermediate |
| Shape | GRID |
| Status | scaffold |
| Slug | `graph-traversal-simulator` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text graph-traversal-simulator

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/graph-traversal-simulator/>

## Files

| File | What it is |
|---|---|
| `GraphTraversalSimulator.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

`generate(int size)`, `step()` and `solve()` own the board. The renderers are generic.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
