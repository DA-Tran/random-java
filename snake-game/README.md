
# Snake Game (Java Console)

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

