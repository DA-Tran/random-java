# 132 - Text Based Adventure

> Navigate rooms by typing commands, carrying an inventory and saving progress.

| | |
|---|---|
| Category | Core Java and Games |
| Difficulty | Beginner |
| Shape | GAME |
| Status | scaffold |
| Slug | `text-based-adventure` |

**Recommended stack for a production build:** Core Java, Java Collections, file I/O

That is what the original brief called for. This suite is deliberately dependency-free — plain JDK, no build tool, no jars — so the version here uses in-memory storage and the shared web hub instead. The note is recorded so the intent is not lost if you later rebuild it for real.


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text text-based-adventure

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/text-based-adventure/>

## Files

| File | What it is |
|---|---|
| `TextBasedAdventure.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

`playRound(String move)` decides the outcome and updates the score.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
