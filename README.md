# random-java: Collection of Visual Interactive Projects (Java/JS/Node)\n\nThis folder contains multiple game/visualizer projects following **console_text + interactive_Swing** pattern.\n\n**Root-level**: Main README (project list), TODO.md (progress), .gitignore\n**openJdk-25/**: Local JDK25 for Java builds (export PATH="../openJdk-25/bin:$PATH")\n\n## Java Game Folders\n

## Overview
Portfolio of 10 visual projects: Java console→GUI upgrades (Swing native), vanilla JS games/visualizers, Node Slack bot.

**OpenJDK25 setup**: All Java projects use local `./openJdk-25/bin` (export PATH).

## Java Console → GUI Projects (4)
| Project | Console | GUI | Run |
|---------|---------|-----|-----|
| [Snake](snake-game/) | SnakeGame.java | SnakeGUI.java | `cd snake-game && export PATH="./openJdk-25/bin:$PATH" && javac SnakeGUI.java Point.java && java SnakeGUI` |
| [ATM](atm-interface/) | AtmInterface.java | AtmGUI.java | `cd atm-interface && export PATH="./openJdk-25/bin:$PATH" && javac *.java && java AtmGUI` |
| [Rat Maze](rat-in-a-maze/) | RatInMaze.java | RatMazeGUI.java | `
