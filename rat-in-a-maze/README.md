
# rat-in-a-maze/: Pathfinding Solver (Console + Swing GUI)\n\n**Purpose**: Generate maze, find shortest path from rat(blue) to cheese(orange) via DFS, animate yellow path.\n\n**Files**:\n- **Console**: RatInMaze.java/Maze.java (print solved maze)\n- **Swing**: RatMazeGUI.java (grid 20x20, 'Solve Maze' button animation)\n\n**Run Console**:\n```bash\ncd rat-in-a-maze\nexport PATH=\"../openJdk-25/bin:$PATH\"\njavac *.java\njava RatInMaze\n```\n**Run GUI**:\njavac RatMazeGUI.java && java RatMazeGUI\n

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

