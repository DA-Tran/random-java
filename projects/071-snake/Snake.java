package com.randomjava.projects.snake;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Snake - steer a growing snake without hitting the walls or yourself.
 *
 * <p>Simple enough to write in an afternoon and still has three places it
 * usually goes wrong, all of which look fine until someone plays properly.
 *
 * <ul>
 *   <li><b>Turning back on yourself.</b> Pressing left while travelling right
 *       walks the head straight into the neck and ends the game instantly. It
 *       reads as the game being broken, because the player did not ask to die.
 *       A 180 degree turn is rejected, not obeyed.</li>
 *   <li><b>The tail that is about to move.</b> When the snake advances, the
 *       last segment vacates its cell on the same tick. Checking the head
 *       against every body segment therefore reports a collision with a square
 *       that will be empty by the time the head arrives - so following your own
 *       tail, which is legal and constant in real play, kills you.</li>
 *   <li><b>Placing food by guessing.</b> Picking random squares until one is
 *       free is fine on an empty board, takes longer and longer as the snake
 *       grows, and never returns at all once the board is full. Free cells are
 *       enumerated and one is chosen from them, which is also how the game
 *       knows the board has been filled and won.</li>
 * </ul>
 */
public final class Snake implements Project {

    public static final Meta META = new Meta(71, "snake", "Snake", "Game Development", Kind.GRID,
            Difficulty.INTERMEDIATE, "Steer a growing snake around a board without hitting anything.",
            "", true);

    public enum Direction {
        UP(-1, 0), DOWN(1, 0), LEFT(0, -1), RIGHT(0, 1);
        final int dRow;
        final int dCol;
        Direction(int dRow, int dCol) { this.dRow = dRow; this.dCol = dCol; }
        /** True when turning to {@code other} would be a 180 degree reversal. */
        public boolean opposes(Direction other) {
            return dRow + other.dRow == 0 && dCol + other.dCol == 0;
        }
    }

    public enum Status { PLAYING, HIT_WALL, HIT_SELF, FILLED_THE_BOARD }

    public record Cell(int row, int col) { }

    private int size;
    private Deque<Cell> body;
    private Direction heading;
    private Direction queued;
    private Cell food;
    private Status status;
    private int score;
    private int ticks;
    private final Random random;

    public Snake() { this(12, new Random()); }

    public Snake(int size, Random random) {
        this.random = random;
        reset(size);
    }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Setting up
    // ------------------------------------------------------------------

    public void reset(int newSize) {
        if (newSize < 5 || newSize > 40) {
            throw new IllegalArgumentException("The board must be between 5 and 40 cells.");
        }
        size = newSize;
        body = new ArrayDeque<>();
        int middle = size / 2;
        // Head first, so the head is always peekFirst and the tail peekLast.
        body.addLast(new Cell(middle, middle));
        body.addLast(new Cell(middle, middle - 1));
        heading = Direction.RIGHT;
        queued = Direction.RIGHT;
        status = Status.PLAYING;
        score = 0;
        ticks = 0;
        placeFood();
    }

    public int size() { return size; }
    public int score() { return score; }
    public int ticks() { return ticks; }
    public Status status() { return status; }
    public boolean playing() { return status == Status.PLAYING; }
    public int length() { return body.size(); }
    public Cell head() { return body.peekFirst(); }
    public Cell food() { return food; }
    public List<Cell> body() { return List.copyOf(body); }
    public Direction heading() { return heading; }

    /**
     * Chooses uniformly from the free cells rather than guessing until a guess
     * lands. On a nearly full board guessing can take arbitrarily long, and on
     * a full one it never finishes.
     */
    private void placeFood() {
        Set<Cell> taken = new HashSet<>(body);
        List<Cell> free = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Cell cell = new Cell(row, col);
                if (!taken.contains(cell)) { free.add(cell); }
            }
        }
        if (free.isEmpty()) {
            food = null;
            status = Status.FILLED_THE_BOARD;
            return;
        }
        food = free.get(random.nextInt(free.size()));
    }

    // ------------------------------------------------------------------
    // Playing
    // ------------------------------------------------------------------

    /**
     * Queues a turn for the next tick, ignoring a reversal.
     *
     * <p>The check is against {@link #heading} - where the snake is actually
     * travelling - and not against a turn already queued this tick. That
     * distinction matters. Travelling right, a player who presses up and then
     * down before the next tick ends up going down, which is an ordinary
     * quarter turn from right and must be allowed. Validating against the
     * queued direction instead would see down opposing the pending up and
     * reject a perfectly legal input, which feels like dropped keypresses.
     *
     * <p>Only the last turn before a tick has any effect, so the sequence can
     * never produce a direction the snake did not legally reach from the one it
     * was travelling in.
     */
    public boolean turn(Direction wanted) {
        if (wanted == null || !playing()) { return false; }
        if (body.size() > 1 && heading.opposes(wanted)) { return false; }
        queued = wanted;
        return true;
    }

    public Status step() {
        if (!playing()) { return status; }
        heading = queued;
        ticks++;

        Cell current = body.peekFirst();
        Cell next = new Cell(current.row() + heading.dRow, current.col() + heading.dCol);

        if (next.row() < 0 || next.row() >= size || next.col() < 0 || next.col() >= size) {
            status = Status.HIT_WALL;
            return status;
        }

        boolean eating = next.equals(food);
        // The tail leaves its cell this tick unless the snake is growing, so it
        // is not an obstacle. Without this, following your own tail is fatal.
        Cell vacating = eating ? null : body.peekLast();
        for (Cell segment : body) {
            if (segment.equals(next) && !segment.equals(vacating)) {
                status = Status.HIT_SELF;
                return status;
            }
        }

        body.addFirst(next);
        if (eating) {
            score++;
            placeFood();
        } else {
            body.removeLast();
        }
        return status;
    }

    /**
     * One step of a cautious autopilot: move toward the food when that square
     * is survivable, otherwise take any surviving move.
     *
     * <p>Greedy and not optimal. It will happily coil itself into a pocket it
     * cannot escape, because avoiding that needs a reachability check on every
     * candidate move rather than a look at the next square.
     */
    public boolean autoStep() {
        if (!playing() || food == null) { return false; }
        Cell current = body.peekFirst();
        List<Direction> preferred = new ArrayList<>();
        if (food.row() < current.row()) { preferred.add(Direction.UP); }
        if (food.row() > current.row()) { preferred.add(Direction.DOWN); }
        if (food.col() < current.col()) { preferred.add(Direction.LEFT); }
        if (food.col() > current.col()) { preferred.add(Direction.RIGHT); }
        for (Direction direction : Direction.values()) {
            if (!preferred.contains(direction)) { preferred.add(direction); }
        }
        for (Direction direction : preferred) {
            if (!heading.opposes(direction) && survives(direction)) {
                turn(direction);
                step();
                return true;
            }
        }
        step();
        return false;
    }

    /** Would moving this way leave the head somewhere legal? */
    public boolean survives(Direction direction) {
        Cell current = body.peekFirst();
        Cell next = new Cell(current.row() + direction.dRow, current.col() + direction.dCol);
        if (next.row() < 0 || next.row() >= size || next.col() < 0 || next.col() >= size) {
            return false;
        }
        Cell vacating = next.equals(food) ? null : body.peekLast();
        for (Cell segment : body) {
            if (segment.equals(next) && !segment.equals(vacating)) { return false; }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public char[][] cells() {
        char[][] grid = new char[size][size];
        for (char[] row : grid) { Arrays.fill(row, '.'); }
        for (Cell segment : body) { grid[segment.row()][segment.col()] = 'o'; }
        Cell headCell = body.peekFirst();
        grid[headCell.row()][headCell.col()] = '@';
        if (food != null) { grid[food.row()][food.col()] = '*'; }
        return grid;
    }

    private String detail() {
        return switch (status) {
            case PLAYING -> String.format("score %d, length %d, heading %s, tick %d",
                    score, body.size(), heading, ticks);
            case HIT_WALL -> String.format("Into the wall on tick %d. Score %d.", ticks, score);
            case HIT_SELF -> String.format("Into itself on tick %d. Score %d.", ticks, score);
            case FILLED_THE_BOARD -> String.format(
                    "The board is full - there is nowhere left to put food. Score %d.", score);
        };
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("w a s d to steer, blank to hold course, q to quit.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            if (!playing()) {
                if (!io.askYesNo("Play again?", true)) { return; }
                reset(size);
                continue;
            }
            String key = io.ask("move:").trim().toLowerCase(Locale.ROOT);
            if (key.equals("q")) { return; }
            switch (key) {
                case "w" -> turn(Direction.UP);
                case "s" -> turn(Direction.DOWN);
                case "a" -> turn(Direction.LEFT);
                case "d" -> turn(Direction.RIGHT);
                default -> { }
            }
            step();
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    reset(Math.min(Math.max(Json.integer(body, "size", size), 5), 40));
                    return board("New game. Steer with the arrow keys or w a s d.");
                }
                case "turn" -> {
                    String wanted = Json.str(body, "direction", "").toUpperCase(Locale.ROOT);
                    Direction direction;
                    try {
                        direction = Direction.valueOf(wanted);
                    } catch (IllegalArgumentException e) {
                        return Json.error("Unknown direction: " + wanted);
                    }
                    boolean accepted = turn(direction);
                    step();
                    return board(accepted ? "" : "A turn straight back on itself is ignored.");
                }
                case "step" -> { step(); return board(""); }
                case "solve" -> {
                    // Greedy autopilot, bounded so a bad board cannot spin here.
                    int limit = size * size * 4;
                    while (playing() && limit-- > 0) { autoStep(); }
                    return board("Autopilot stopped: " + detail()
                            + " It walks toward the food and will trap itself eventually, "
                            + "because it only looks one square ahead.");
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }

    private Map<String, Object> board(String note) {
        return Json.ok("board", Json.grid(cells()),
                "detail", note.isEmpty() ? detail() : note + "\n  " + detail());
    }
}
