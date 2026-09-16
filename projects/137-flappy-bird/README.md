# 137 - Flappy Bird

> Tap to keep a bird airborne through gaps in scrolling pipes.

| | |
|---|---|
| Category | Core Java and Games |
| Difficulty | Intermediate |
| Shape | GRID |
| Status | scaffold |
| Slug | `flappy-bird` |

**Recommended stack for a production build:** Core Java, JavaFX, animation and collision detection

That is what the original brief called for. This suite is deliberately dependency-free — plain JDK, no build tool, no jars — so the version here uses in-memory storage and the shared web hub instead. The note is recorded so the intent is not lost if you later rebuild it for real.


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text flappy-bird

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/flappy-bird/>

## Files

| File | What it is |
|---|---|
| `FlappyBird.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

`generate(int size)`, `step()` and `solve()` own the board. The renderers are generic.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
