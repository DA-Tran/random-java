
# Rat in a Maze (Java Console)

## Files
- RatInMaze.java - Main solver
- Maze.java - Maze gen & solve

Random maze generation, DFS solve, visual path.

## Run Console
```bash
export PATH="../openJdk-25/bin:$PATH"
cd rat-in-a-maze
javac *.java
java RatInMaze
```

## Run Swing GUI (Recommended - Native)
```bash
export PATH="../openJdk-25/bin:$PATH"
javac RatMazeGUI.java
java RatMazeGUI
```

**RatMazeGUI.java**: Click "Solve Maze" for yellow path animation (rat blue, cheese orange).

**JavaFX version exists but incompatible** (no javafx modules in JDK25 - use Swing).

