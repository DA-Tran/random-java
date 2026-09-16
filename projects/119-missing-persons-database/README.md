# 119 - Missing Persons Database

> Searchable records with descriptions, last seen and case status.

| | |
|---|---|
| Category | Community and Social Good |
| Difficulty | Intermediate |
| Shape | LIST |
| Status | scaffold |
| Slug | `missing-persons-database` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text missing-persons-database

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/missing-persons-database/>

## Files

| File | What it is |
|---|---|
| `MissingPersonsDatabase.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

`add(...)` and `summary()` carry the meaning. Toggle, remove and clear are already generic.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
