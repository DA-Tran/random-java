# 113 - Disaster Relief Coordination

> Report incidents, match needs to offers by location and urgency, and track recovery.

| | |
|---|---|
| Category | Community and Social Good |
| Difficulty | Intermediate |
| Shape | LIST |
| Status | scaffold |
| Slug | `disaster-relief-coordination` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text disaster-relief-coordination

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/disaster-relief-coordination/>

## Files

| File | What it is |
|---|---|
| `DisasterReliefCoordination.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

`add(...)` and `summary()` carry the meaning. Toggle, remove and clear are already generic.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
