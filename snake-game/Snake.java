
import java.util.ArrayList;
import java.util.List;

public class Snake {
    private List<Point> body;
    private char direction = 'R'; // R, L, U, D

    public Snake(int startX, int startY) {
        body = new ArrayList<>();
        body.add(new Point(startX, startY));
    }

    public void move() {
        Point head = body.get(0);
        Point newHead = new Point(head.x, head.y);
        switch (direction) {
            case 'U': newHead.y--; break;
            case 'D': newHead.y++; break;
            case 'L': newHead.x--; break;
            case 'R': newHead.x++; break;
        }
        body.add(0, newHead);
        body.remove(body.size() - 1);
    }

    public void grow() {
        Point tail = body.get(body.size() - 1);
        body.add(new Point(tail.x, tail.y));
    }

    public boolean checkCollision(int width, int height) {
        Point head = body.get(0);
        if (head.x < 0 || head.x >= width || head.y < 0 || head.y >= height) return true;
        for (int i = 1; i < body.size(); i++) {
            if (head.equals(body.get(i))) return true;
        }
        return false;
    }

    // Getters/setters
    public List<Point> getBody() { return body; }
    public void setDirection(char dir) { direction = dir; }
    public Point getHead() { return body.get(0); }
}

