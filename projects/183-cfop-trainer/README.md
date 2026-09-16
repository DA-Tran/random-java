# 183 - CFOP Trainer

> Drill the last-layer cases: recognise the pattern, recall the algorithm.

| | |
|---|---|
| Category | Cube Solving Methods |
| Difficulty | Advanced |
| Shape | LIST |
| Status | implemented |
| Slug | `cfop-trainer` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text cfop-trainer

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/cfop-trainer/>

## Files

| File | What it is |
|---|---|
| `CfopTrainer.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

Already implemented. Read the source for how it works.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
