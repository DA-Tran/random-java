# 139 - Digital Steganography

> Hide a message in the low bits of an image and pull it back out again.

| | |
|---|---|
| Category | Core Java and Games |
| Difficulty | Advanced |
| Shape | TOOL |
| Status | scaffold |
| Slug | `digital-steganography` |

**Recommended stack for a production build:** Core Java, JavaFX, Java Image I/O, encryption libraries, MySQL

That is what the original brief called for. This suite is deliberately dependency-free — plain JDK, no build tool, no jars — so the version here uses in-memory storage and the shared web hub instead. The note is recorded so the intent is not lost if you later rebuild it for real.


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text digital-steganography

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/digital-steganography/>

## Files

| File | What it is |
|---|---|
| `DigitalSteganography.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

`compute(String input)` is the only method that matters. Everything else is wiring.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
