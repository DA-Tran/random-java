# JavaFX/React Upgrade TODO

## 1. JavaFX Setup (JDK25 modules)
```
cd [project]
rm *.class
javafx-app/ - create FXML/App.java
java --module-path "../openJdk-25/jmods/javafx.controls.jmod:../openJdk-25/jmods/javafx.fxml.jmod:../openJdk-25/jmods/javafx.graphics.jmod" --add-modules javafx.controls,javafx.fxml -cp . App
```

## 2. React Projects
```
mkdir react-[name]
cd react-[name]
npm create vite@latest . -- --template react
npm i
npm run dev
```

**Projects:**
- [x] snake-game → JavaFX Snake (javafx/SnakeFXApp.java)
- [x] atm-interface → JavaFX ATM (javafx/AtmFXApp.java)
- [x] rat-in-a-maze → Swing Maze GUI (RatMazeGUI.java)
- [x] chess-game → JavaFX Chess (javafx/chess_interactive.java)
- [ ] random-number-color-generator → React ColorGen
- [ ] brick-breaker-game → React BrickBreaker
- [ ] sorting-visualizer → React Sorting
- [ ] data-visualization-software → React DataViz
- [ ] one-on-one-chat-app → React Chat

**Next: snake-game JavaFX**

