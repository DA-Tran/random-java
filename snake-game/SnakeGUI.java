
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Random;

public class SnakeGUI extends JPanel implements ActionListener {
    static final int SCALE = 20;
    static final int WIDTH = 30;
    static final int HEIGHT = 20;
    static final int DELAY = 120;
    
    Timer timer;
    ArrayList<Point> snake;
    Point food;
    char direction = 'R';
    boolean running = false;
    int score = 0;
    Random random;

    SnakeGUI() {
        random = new Random();
        this.setPreferredSize(new Dimension(WIDTH * SCALE, HEIGHT * SCALE));
        this.setBackground(Color.black);
        this.setFocusable(true);
        this.addKeyListener(new MyKeyAdapter());
        startGame();
    }

    public void startGame() {
        snake = new ArrayList<Point>();
        snake.add(new Point(5, 5));
        newFood();
        running = true;
        timer = new Timer(DELAY, this);
        timer.start();
    }

    public void newFood() {
        food = new Point(random.nextInt(WIDTH), random.nextInt(HEIGHT));
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    public void draw(Graphics g) {
        // Grid
        for (int i = 0; i < HEIGHT * SCALE; i += SCALE) {
            g.drawLine(i, 0, i, HEIGHT * SCALE);
        }
        for (int i = 0; i < WIDTH * SCALE; i += SCALE) {
            g.drawLine(0, i, WIDTH * SCALE, i);
        }
        
        // Food
        g.setColor(Color.red);
        g.fillOval(food.x * SCALE, food.y * SCALE, SCALE, SCALE);
        
        // Snake
        for (Point p : snake) {
            g.setColor(Color.green);
            g.fillRect(p.x * SCALE, p.y * SCALE, SCALE, SCALE);
        }
        
        // Score
        g.setColor(Color.white);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.drawString("Score: " + score, 10, 25);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (running) {
            move();
            checkFood();
            checkCollision();
        }
        repaint();
    }

    public void move() {
Point head = new Point(snake.get(0).x, snake.get(0).y);
        switch (direction) {
            case 'U': head.y--; break;
            case 'D': head.y++; break;
            case 'L': head.x--; break;
            case 'R': head.x++; break;
        }
        snake.add(0, head);
        if (!head.equals(food)) snake.remove(snake.size() - 1);
    }

    public void checkFood() {
        if (snake.get(0).equals(food)) {
            score += 10;
            newFood();
        }
    }

    public void checkCollision() {
        Point head = snake.get(0);
        if (head.x < 0 || head.x >= WIDTH || head.y < 0 || head.y >= HEIGHT || snake.stream().skip(1).anyMatch(p -> p.equals(head))) {
            running = false;
            JOptionPane.showMessageDialog(this, "Game Over! Score: " + score, "Snake GUI", JOptionPane.PLAIN_MESSAGE);
            System.exit(0);
        }
    }

    public class MyKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                    if (direction != 'R') direction = 'L';
                    break;
                case KeyEvent.VK_RIGHT:
                    if (direction != 'L') direction = 'R';
                    break;
                case KeyEvent.VK_UP:
                    if (direction != 'D') direction = 'U';
                    break;
                case KeyEvent.VK_DOWN:
                    if (direction != 'U') direction = 'D';
                    break;
            }
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Snake Game GUI");
        SnakeGUI game = new SnakeGUI();
        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
        game.requestFocusInWindow();
    }
}

