
# chess-game/: Full Chess (Console Text + Swing GUI)\n\n**Purpose**: Complete chess engine - algebraic moves, piece logic, check/checkmate detection, legal move validation.\n\n**Files**:\n- **Console**: ChessGame_text.java (e2 e4 input/print board)\n- **Swing GUI**: ChessGame_interactive.java + ChessSquareComponent.java (click drag pieces, green legal moves)\n- **OOP**: Piece.java (Pawn/Rook/Knight/Bishop/Queen/King), ChessBoard.java, Position.java\n\n**Run Console**:\n```bash\ncd chess-game\nexport PATH=\"../openJdk-25/bin:$PATH\"\njavac *.java\njava ChessGame_text\n```\n**Run GUI**:\njava ChessGame_interactive\n\n**Features**: Unicode board, check alerts, checkmate, reset.

## Files
- ChessGame_text.java: Console text version (e2 e4 moves, basic)
- ChessGame_interactive.java: Full Swing GUI + logic (pieces, check/checkmate, legal moves highlight)
- ChessSquareComponent.java: Board squares
- Piece* classes, ChessBoard.java, ChessGame.java (logic)

## Run Console Text
```bash
export PATH="../openJdk-25/bin:$PATH"
cd chess-game
javac *.java
java ChessGame_text
```

## Run Interactive Swing GUI (Click pieces)
```bash
export PATH="../openJdk-25/bin:$PATH"
cd chess-game
javac *.java
java ChessGame_interactive
```

**Features**: Unicode pieces, select piece (green highlights legal moves), check alerts, checkmate dialog, reset menu.
**Removed**: Broken JavaFX.

Board rows 0-7 top-black bottom-white, columns a-h left-right.




