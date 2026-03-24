
# Chess Game (Java Console)

## Files
- ChessGame.java - Board, moves, print (basic, no full rules validation)

## Run Console
```bash
export PATH="../openJdk-25/bin:$PATH"
cd chess-game
javac ChessGame.java
java ChessGame
```

## Run Swing GUI (Legacy)
```bash
export PATH="../openJdk-25/bin:$PATH"
cd chess-game
javac ChessBoardGUI.java
java ChessBoardGUI
```

## Run JavaFX Interactive (Recommended)
```bash
export PATH="../openJdk-25/bin:$PATH"
cd chess-game/javafx
javac --module-path "../../openJdk-25/jmods" --add-modules javafx.controls,javafx.graphics,javafx.base *.java
java --module-path "../../openJdk-25/jmods" --add-modules javafx.controls,javafx.graphics,javafx.base chess_interactive
```

**chess_interactive.java**: Click-to-select/move, turn indicator, Unicode pieces, smooth board.

**Console**: Move format: e2 e4 (basic demo).



