# 041 - Fitness Tracker

> Log workouts, sets and distance, and total the week.

| | |
|---|---|
| Category | Mobile App Ideas |
| Difficulty | Beginner |
| Shape | LIST |
| Status | scaffold |
| Slug | `fitness-tracker` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text fitness-tracker

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/fitness-tracker/>

## Files

| File | What it is |
|---|---|
| `FitnessTracker.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

`add(...)` and `summary()` carry the meaning. Toggle, remove and clear are already generic.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
