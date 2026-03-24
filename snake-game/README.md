
# snake-game/: Snake Game (Console + Swing GUI + New GameFrame/GamePanel)\n\n**Purpose**: Classic Snake - grow by eating apples, avoid walls/self.\n\n**Files**:\n- **Console**: SnakeGame.java + Snake/Food/Point (WASD/Q)\n- **Swing GUI**: SnakeGUI.java (grid, arrows, popup gameover)\n- **Video Guide**: GameFrame.java → GamePanel.java (1300x750 unit50, arrows, red apple/green snake, score, Game Over)\n\n**Run Video Swing**:\n```bash\ncd snake-game\nexport PATH=\"../openJdk-25/bin:$PATH\"\njavac SnakeGame.java GameFrame.java GamePanel.java && java SnakeGame\n```\n**Run Original GUI**:\njavac SnakeGUI.java Point.java && java SnakeGUI\n**Console**:\njavac SnakeGame.java Snake.java Point.java Food.java && java SnakeGame\n

## Files
- SnakeGame.java - Main game
- Snake.java - Snake logic
- Food.java - Food spawning
- Point.java - Position class

## Run Console
```bash
export PATH="./openJdk-25/bin:$PATH"
javac SnakeGame.java Snake.java Point.java Food.java
java SnakeGame
```

## Run Swing GUI (Recommended - Native Java)
```bash
export PATH="./openJdk-25/bin:$PATH"
javac SnakeGUI.java Point.java
java SnakeGUI
```

**Note**: JavaFX requires separate SDK (OpenJDK25 has no javafx jmods). Use Swing for native graphics.**

(Removed JavaFX section - incompatible with current JDK)
```bash
export PATH="./openJdk-25/bin:$PATH"
cd javafx
javac --module-path "../../openJdk-25/jmods" --add-modules javafx.controls,javafx.graphics,javafx.base *.java
java --module-path "../../openJdk-25/jmods" --add-modules javafx.controls,javafx.graphics,javafx.base SnakeFXApp
```

**JavaFX**: Canvas animation, smooth 60fps, WASD/arrow keys.

Controls: WASD move, Q quit.

