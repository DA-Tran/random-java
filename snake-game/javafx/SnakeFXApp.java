import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.input.KeyCode;
import javafx.animation.AnimationTimer;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.Random;

public class SnakeFXApp extends Application {
    private static final int SCALE = 20;
    private static final int WIDTH = 30 * SCALE;
    private static final int HEIGHT = 20 * SCALE;
    private static final int DELAY = 150_000_000; // ns

    private ArrayList<Point> snake = new ArrayList<>();
    private Point food;
    private char direction = 'R';
    private boolean running = true;
    private int score = 0;
    private Random random = new Random();
    private long lastUpdate = 0;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        
        snake.add(new Point(5 * SCALE, 5 * SCALE));
        generateFood();

        Scene scene = new Scene(new StackPane(canvas));
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.LEFT && direction != 'R') direction = 'L';
            else if (e.getCode() == KeyCode.RIGHT && direction != 'L') direction = 'R';
            else if (e.getCode() == KeyCode.UP && direction != 'D') direction = 'U';
            else if (e.getCode() == KeyCode.DOWN && direction != 'U') direction = 'D';
        });

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastUpdate > DELAY) {
                    if (running) {
                        update();
                    }
                    draw(gc);
                    lastUpdate = now;
                }
            }
        }.start();

        stage.setScene(scene);
        stage.setTitle("Snake Game - JavaFX");
        stage.setResizable(false);
        stage.show();
        scene.requestFocus();
    }

    private void update() {
Point head = new Point(snake.get(0).x, snake.get(0).y);
        switch (direction) {
            case 'U': head.y -= SCALE; break;
            case 'D': head.y += SCALE; break;
            case 'L': head.x -= SCALE; break;
            case 'R': head.x += SCALE; break;
        }

        if (head.x < 0 || head.x >= WIDTH || head.y < 0 || head.y >= HEIGHT || snake.stream().skip(1).anyMatch(p -> p.equals(head))) {
            running = false;
javafx.application.HostServices.getHostServices().showDocument("data:," + score);
            return;
        }

        snake.add(0, head);
        if (!head.equals(food)) {
            snake.remove(snake.size() - 1);
        } else {
            score += 10;
            generateFood();
        }
    }

    private void generateFood() {
        food = new Point(random.nextInt(WIDTH / SCALE) * SCALE, random.nextInt(HEIGHT / SCALE) * SCALE);
    }

    private void draw(GraphicsContext gc) {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        // Grid
        gc.setFill(Color.rgb(40, 40, 40, 0.3));
        for (int i = 0; i <= WIDTH; i += SCALE) gc.fillRect(i, 0, 1, HEIGHT);
        for (int i = 0; i <= HEIGHT; i += SCALE) gc.fillRect(0, i, WIDTH, 1);

        // Food
        gc.setFill(Color.RED);
        gc.fillOval(food.x, food.y, SCALE, SCALE);

        // Snake
        for (Point p : snake) {
            gc.setFill(Color.GREEN);
            gc.fillRect(p.x + 2, p.y + 2, SCALE - 4, SCALE - 4);
        }

        // Score
        gc.setFill(Color.WHITE);
        gc.fillText("Score: " + score, 10, 25);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

