# 003 - Digital Clock

> Show the current time, with timezone and 12 or 24 hour formatting.

| | |
|---|---|
| Category | Beginner Friendly |
| Difficulty | Beginner |
| Shape | TOOL |
| Status | implemented |
| Slug | `digital-clock` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text digital-clock

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/digital-clock/>

## Files

| File | What it is |
|---|---|
| `DigitalClock.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

Already implemented. Read the source for how it works.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
