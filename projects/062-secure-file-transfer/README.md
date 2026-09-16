# 062 - Secure File Transfer

> Move a file between hosts with encryption and an integrity check.

| | |
|---|---|
| Category | Cybersecurity |
| Difficulty | Advanced |
| Shape | TOOL |
| Status | implemented |
| Slug | `secure-file-transfer` |


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text secure-file-transfer

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/secure-file-transfer/>

## Files

| File | What it is |
|---|---|
| `SecureFileTransfer.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

Already implemented. Read the source for how it works.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
