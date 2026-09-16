# 009 - Rock Paper Scissors

> Play best-of against the computer and keep a running score.

| | |
|---|---|
| Category | Beginner Friendly |
| Difficulty | Beginner |
| Shape | GAME |
| Status | implemented |
| Slug | `rock-paper-scissors` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text rock-paper-scissors

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/rock-paper-scissors/>

## Files

| File | What it is |
|---|---|
| `RockPaperScissors.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

Already implemented. Read the source for how it works.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
