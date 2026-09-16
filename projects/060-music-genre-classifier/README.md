# 060 - Music Genre Classifier

> Predict a genre from tempo, energy and spectral features.

| | |
|---|---|
| Category | AI and Machine Learning |
| Difficulty | Advanced |
| Shape | TOOL |
| Status | scaffold |
| Slug | `music-genre-classifier` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text music-genre-classifier

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/music-genre-classifier/>

## Files

| File | What it is |
|---|---|
| `MusicGenreClassifier.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

`compute(String input)` is the only method that matters. Everything else is wiring.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
