# 033 - URL Shortener

> Mint short codes for long URLs and count the redirects.

| | |
|---|---|
| Category | Web Development |
| Difficulty | Intermediate |
| Shape | LIST |
| Status | implemented |
| Slug | `url-shortener` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text url-shortener

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/url-shortener/>

## Files

| File | What it is |
|---|---|
| `UrlShortener.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

Already implemented. Read the source for how it works.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
