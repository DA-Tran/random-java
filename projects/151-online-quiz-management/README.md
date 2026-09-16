# 151 - Online Quiz Management

> Build quizzes, sit them under a time limit and review the marked answers.

| | |
|---|---|
| Category | Business and Records Systems |
| Difficulty | Beginner |
| Shape | LIST |
| Status | scaffold |
| Slug | `online-quiz-management` |

**Recommended stack for a production build:** Spring Boot, MySQL, Thymeleaf, Spring Security, Hibernate

That is what the original brief called for. This suite is deliberately dependency-free — plain JDK, no build tool, no jars — so the version here uses in-memory storage and the shared web hub instead. The note is recorded so the intent is not lost if you later rebuild it for real.


## Run it

From the repository root, after `./build.sh` (or `.\build.ps1`):

```bash
# terminal
java -cp build/classes com.randomjava.Launcher text online-quiz-management

# browser: starts the hub, then open the project from the index
java -cp build/classes com.randomjava.Launcher web
```

Direct link once the hub is up: <http://localhost:8080/p/online-quiz-management/>

## Files

| File | What it is |
|---|---|
| `OnlineQuizManagement.java` | All the logic, plus `runText` for the terminal and `api` for the browser |
| `ui.html` | The browser fragment. The page chrome, styles and the `RJ` helper come from the shell |

## What to implement

`add(...)` and `summary()` carry the meaning. Toggle, remove and clear are already generic.

Both front ends call the same methods, so there is nothing to keep in sync:
implement once and the terminal and the browser both pick it up.
