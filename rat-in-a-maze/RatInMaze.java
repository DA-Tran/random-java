
public class RatInMaze {
    public static void main(String[] args) {
        Maze maze = new Maze(10, 10);
        char[][] path = new char[maze.getRows()][maze.getCols()];
        System.out.println("Original Maze:");
        maze.printMaze(path);
        if (maze.solve(0, 0, path)) {
            System.out.println("\nSolution:");
            maze.printMaze(path);
        } else {
            System.out.println("\nNo solution!");
        }
    }
}

