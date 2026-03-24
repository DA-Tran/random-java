
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

public class SnakeGame {
    private static final int WIDTH = 20;
    private static final int HEIGHT = 20;
    private static final int DELAY = 150; // ms

    private Snake snake;
    private Food food;
    private Scanner scanner;
    private boolean running;
    private char currentDir = 'R';

    public SnakeGame() {
        snake = new Snake(WIDTH / 2, HEIGHT / 2);
        food = new Food(WIDTH, HEIGHT);
        scanner = new Scanner(System.in);
        running = true;
    }

    public void start() {
        System.out.println("Snake Game! Use WASD to move, Q to quit.");
        System.out.println("Size: " + WIDTH + "x" + HEIGHT);

        // Timer for game loop
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!running) {
                    timer.cancel();
                    return;
                }
                update();
                render();
                checkInput();
            }
        }, 0, DELAY);
    }

    private void update() {
        snake.setDirection(currentDir);
        snake.move();
        if (snake.getHead().equals(food.getLocation())) {
            snake.grow();
            food.spawn(WIDTH, HEIGHT);
        }
        if (snake.checkCollision(WIDTH, HEIGHT)) {
            running = false;
            System.out.println("\nGame Over!");
        }
    }

    private void render() {
        // ANSI clear screen
        System.out.print("\033[H\033[2J");
        char[][] board = new char[HEIGHT][WIDTH];
        // Fill empty
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                board[y][x] = ' ';
            }
        }
        // Snake
        for (Point p : snake.getBody()) {
            board[p.y][p.x] = 'O';
        }
        // Head different
        board[snake.getHead().y][snake.getHead().x] = '@';
        // Food
        board[food.getLocation().y][food.getLocation().x] = '*';
        // Print
        for (char[] row : board) {
            System.out.println(new String(row));
        }
        System.out.println("Score: " + (snake.getBody().size() - 1));
    }

    private void checkInput() {
        if (scanner.hasNextLine()) {
            String input = scanner.nextLine().toUpperCase();
            if (input.length() > 0) {
                char dir = input.charAt(0);
                if (dir == 'Q') running = false;
                else if ("WASD".indexOf(dir) != -1) currentDir = dir;
            }
        }
    }

    public static void main(String[] args) {
        new SnakeGame().start();
    }
}

