
public class Maze {
    private char[][] grid;
    private int rows, cols;

    public Maze(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        grid = new char[rows][cols];
        generateMaze();
    }

    private void generateMaze() {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                grid[i][j] = (i == 0 && j == 0) ? 'S' : ((i == rows-1 && j == cols-1) ? 'E' : ((Math.random() < 0.3) ? '#' : ' '));
            }
        }
    }

    public boolean solve(int x, int y, char[][] path) {
        if (x < 0 || y < 0 || x >= rows || y >= cols || grid[x][y] == '#' || path[x][y] != 0) {
            return false;
        }
        path[x][y] = 'R';
        if (grid[x][y] == 'E') {
            return true;
        }
        if (solve(x+1, y, path) || solve(x-1, y, path) || solve(x, y+1, path) || solve(x, y-1, path)) {
            return true;
        }
        path[x][y] = ' ';
        return false;
    }

    public void printMaze(char[][] path) {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                System.out.print(path[i][j] != 0 ? path[i][j] : grid[i][j]);
            }
            System.out.println();
        }
    }

    public char[][] getGrid() { return grid; }
    public int getRows() { return rows; }
    public int getCols() { return cols; }
}

