import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

public class RatMazeFX extends Application {
    private static final int N = 5;
    private static final int SIZE = 70;
    private static final int WIDTH = SIZE * N + 40;
    private static final int HEIGHT = SIZE * N + 100;
    private int[][] maze = {
        {1, 0, 0, 0, 0},
        {1, 1, 1, 1, 0},
        {0, 0, 0, 0, 0},
        {1, 1, 0, 1, 0},
        {0, 0, 0, 1, 1}
    };
    private boolean[][] solution = new boolean[N][N];
    private boolean solving = false;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawMaze(gc);

        canvas.setOnMouseClicked(e -> {
            if (!solving) {
                solveMaze(0, 0, gc);
            }
        });

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Rat Maze Solver - JavaFX");
        stage.show();

        // Auto solve button style
        gc.setFill(Color.rgb(255, 255, 255, 0.9));
        gc.fillRect(20, HEIGHT - 70, 200, 40);
        gc.setFill(Color.BLACK);
        gc.fillText("Click anywhere to solve!", 25, HEIGHT - 40);
    }

    private void solveMaze(int x, int y, GraphicsContext gc) {
        if (solving) return;
        solving = true;

        PauseTransition pause = new PauseTransition(Duration.millis(300));
        pause.setOnFinished(e -> {
            if (x == N-1 && y == N-1) {
                solution[x][y] = true;
                drawMaze(gc);
                showComplete(gc);
                solving = false;
                return;
            }

            if (x >= N || y >= N || x < 0 || y < 0 || maze[x][y] == 1 || solution[x][y]) {
                if (solution.length > 0 && solution[x][y]) solution[x][y] = false;
                solving = false;
                return;
            }

            solution[x][y] = true;
            drawMaze(gc);
            pause.setOnFinished(null);
            pause.playFromStart();

            if (solveMaze(x+1, y, gc) || solveMaze(x, y+1, gc) || solveMaze(x-1, y, gc) || solveMaze(x, y-1, gc)) return;

            solution[x][y] = false;
            drawMaze(gc);
            solving = false;
        });
        pause.play();
    }

    private void drawMaze(GraphicsContext gc) {
        gc.setFill(Color.DARKGRAY);
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (maze[i][j] == 1) {
                    gc.setFill(Color.GRAY);
                } else if (solution[i][j]) {
                    gc.setFill(Color.YELLOW);
                } else {
                    gc.setFill(Color.WHITE);
                }
                gc.fillRect(30 + j * SIZE, 30 + i * SIZE, SIZE - 4, SIZE - 4);
                gc.setStroke(Color.BLACK);
                gc.setLineWidth(2);
                gc.strokeRect(30 + j * SIZE, 30 + i * SIZE, SIZE - 4, SIZE - 4);
            }
        }

        // Rat & Cheese
        gc.setFill(Color.BLUE);
        gc.fillOval(20, 20, 30, 30);
        gc.setFill(Color.ORANGE);
        gc.fillOval(30 + (N-1)*SIZE, 30 + (N-1)*SIZE, 30, 30);

        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font(16));
        gc.fillText("Click to animate path!", 20, HEIGHT - 20);
    }

    private void showComplete(GraphicsContext gc) {
        gc.setFill(Color.GREEN);
        gc.setFont(javafx.scene.text.Font.font(24));
        gc.fillText("Maze Solved!", WIDTH / 2 - 80, HEIGHT - 20);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

