
import java.awt.*;
import javax.swing.*;

public class RatMazeGUI extends JPanel {
    private static final int N = 5; // 5x5 maze
    private int[][] maze = {
        {1, 0, 0, 0, 0},
        {1, 1, 1, 1, 0},
        {0, 0, 0, 0, 0},
        {1, 1, 0, 1, 0},
        {0, 0, 0, 1, 1}
    };
    private boolean[][] solution = new boolean[N][N];
    private JButton solveBtn;

    public RatMazeGUI() {
        setPreferredSize(new Dimension(400, 450));
        setBackground(Color.darkGray);
        solveBtn = new JButton("Solve Maze (Find Path)");
        solveBtn.addActionListener(e -> solveMaze(0, 0));
    }

    private boolean solveMaze(int x, int y) {
        if (x == N-1 && y == N-1) {
            solution[x][y] = true;
            repaint();
            return true;
        }
        if (x >= N || y >= N || x < 0 || y < 0 || maze[x][y] == 1 || solution[x][y]) return false;
        
        solution[x][y] = true;
        repaint();
        
        // Try down, right, up, left
        if (solveMaze(x+1, y) || solveMaze(x, y+1) || solveMaze(x-1, y) || solveMaze(x, y-1)) return true;
        
        solution[x][y] = false;
        repaint();
        try { Thread.sleep(200); } catch (InterruptedException ex) {}
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int size = 70;
        int startX = 30;
        int startY = 50;
        
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (maze[i][j] == 1) {
                    g.setColor(Color.gray);
                } else if (solution[i][j]) {
                    g.setColor(Color.yellow);
                } else {
                    g.setColor(Color.white);
                }
                g.fillRect(startX + j * size, startY + i * size, size - 5, size - 5);
                g.setColor(Color.black);
                g.drawRect(startX + j * size, startY + i * size, size - 5, size - 5);
            }
        }
        
        // Rat start & cheese
        g.setColor(Color.blue);
        g.fillOval(startX - 10, startY - 10, 25, 25); // Rat
        g.setColor(Color.orange);
        g.fillOval(startX + (N-1)*size - 5, startY + (N-1)*size - 5, 30, 30); // Cheese
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Rat in Maze Solver GUI");
        RatMazeGUI panel = new RatMazeGUI();
        frame.add(panel, BorderLayout.CENTER);
        frame.add(panel.solveBtn, BorderLayout.SOUTH);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

