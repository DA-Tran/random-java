# 044 - Language Flashcards

> Spaced-repetition flashcards that resurface what you get wrong.

| | |
|---|---|
| Category | Mobile App Ideas |
| Difficulty | Beginner |
| Shape | GAME |
| Status | scaffold |
| Slug | `language-flashcards` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text language-flashcards

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/language-flashcards/>

## Files

| File | What it is |
|---|---|
| `LanguageFlashcards.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

`playRound(String move)` decides the outcome and updates the score.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
