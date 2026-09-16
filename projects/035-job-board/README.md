# 035 - Job Board

> Recruiters post openings, candidates apply and search, and applications get screened.

| | |
|---|---|
| Category | Web Development |
| Difficulty | Intermediate |
| Shape | LIST |
| Status | scaffold |
| Slug | `job-board` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text job-board

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/job-board/>

## Files

| File | What it is |
|---|---|
| `JobBoard.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

`add(...)` and `summary()` carry the meaning. Toggle, remove and clear are already generic.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
