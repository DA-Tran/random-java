
import java.util.Random;

public class Food {
    private Point location;
    private Random random;

    public Food(int width, int height) {
        random = new Random();
        spawn(width, height);
    }

    public void spawn(int width, int height) {
        location = new Point(random.nextInt(width), random.nextInt(height));
    }

    public Point getLocation() {
        return location;
    }
}

