
# brick-breaker-game/: Breakout Ball (Swing + JS Canvas)\n\n**Purpose**: Classic brick breaker - paddle bounce ball to break bricks, score, win/gameover.\n\n**Files**:\n- **Swing**: Main.java → Gameplay.java/MapGenerator.java (arrows paddle, ENTER restart)\n- **JS**: index.html/game.js/style.css (mouse paddle)\n\n**Run Swing**:\n```bash\ncd brick-breaker-game\nexport PATH=\"../openJdk-25/bin:$PATH\"\njavac *.java\njava Main\n```\n**Run JS**:\n```bash\nstart index.html\n```

## Java Swing (Video Tutorial)
**Files**:
- Main.java - JFrame (700x600)
- Gameplay.java - JPanel (paddle green ARROW keys, yellow ball, 3x7 white bricks, score, Game Over/Win, ENTER restart)
- MapGenerator.java - Bricks map

**Run** (Git Bash):
```
cd brick-breaker-game
export PATH="../openJdk-25/bin:$PATH"
javac *.java
java Main
```
Arrow L/R paddle, key start ball, break all → Win, ball down → Game Over, ENTER restart.

## Original JS Demo
```
start index.html
```
Mouse paddle, Space pause. Full canvas game.

