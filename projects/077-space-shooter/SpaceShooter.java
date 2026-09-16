package com.randomjava.projects.spaceshooter;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Space Shooter - dodge and shoot waves of descending enemies.
 *
 * <p>The whole game is one update loop, and the order of the steps inside it
 * decides whether it is fair.
 *
 * <p><b>Collisions must be checked after everything has moved, once.</b> The
 * tempting version moves bullets, checks hits, moves enemies, checks hits
 * again - which gives a bullet two chances to hit the same enemy in one tick
 * and lets a fast enemy pass straight through a bullet that was about to be
 * exactly where it is. Everything moves, then the world is resolved.
 *
 * <p><b>Removal happens after iteration, not during it.</b> Deleting from a
 * list while looping over it either throws or silently skips the next element,
 * and the skipped element is an enemy that should have died. Hits are collected
 * and applied afterwards.
 *
 * <p>Fixed ticks rather than wall-clock time, so the same inputs give the same
 * game and a test can play a hundred ticks instantly.
 */
public final class SpaceShooter implements Project {

    public static final Meta META = new Meta(77, "space-shooter", "Space Shooter", "Game Development", Kind.GRID,
            Difficulty.INTERMEDIATE, "Dodge and shoot waves of descending enemies.",
            "", true);

    public record Point(int row, int col) { }

    private final int rows;
    private final int columns;
    private final Random random;

    private int shipColumn;
    private final List<Point> bullets = new ArrayList<>();
    private final List<Point> enemies = new ArrayList<>();
    private int score;
    private int lives = 3;
    private int tick;
    private int wave = 1;
    private boolean over;

    public SpaceShooter() { this(14, 12, new Random()); }

    public SpaceShooter(int rows, int columns, Random random) {
        if (rows < 6 || rows > 30 || columns < 5 || columns > 30) {
            throw new IllegalArgumentException("Use 6 to 30 rows and 5 to 30 columns.");
        }
        this.rows = rows;
        this.columns = columns;
        this.random = random;
        this.shipColumn = columns / 2;
        spawnWave();
    }

    @Override public Meta meta() { return META; }

    public int rows() { return rows; }
    public int columns() { return columns; }
    public int score() { return score; }
    public int lives() { return lives; }
    public int wave() { return wave; }
    public int shipColumn() { return shipColumn; }
    public boolean over() { return over; }
    public List<Point> enemies() { return List.copyOf(enemies); }
    public List<Point> bullets() { return List.copyOf(bullets); }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    public void moveShip(int delta) {
        if (over) { return; }
        shipColumn = Math.max(0, Math.min(columns - 1, shipColumn + delta));
    }

    /** One bullet per column at a time, so holding fire is not a win button. */
    public boolean fire() {
        if (over) { return false; }
        for (Point bullet : bullets) {
            if (bullet.col() == shipColumn) { return false; }
        }
        bullets.add(new Point(rows - 2, shipColumn));
        return true;
    }

    public void spawnWave() {
        enemies.clear();
        int count = Math.min(columns, 3 + wave);
        List<Integer> columnsLeft = new ArrayList<>();
        for (int c = 0; c < columns; c++) { columnsLeft.add(c); }
        Collections.shuffle(columnsLeft, random);
        for (int i = 0; i < count; i++) {
            enemies.add(new Point(i % 2, columnsLeft.get(i)));
        }
    }

    // ------------------------------------------------------------------
    // The update loop
    // ------------------------------------------------------------------

    /**
     * One tick: everything moves, then the world is resolved once.
     */
    public void step() {
        if (over) { return; }
        tick++;

        // 1. Move bullets up. Anything off the top is gone.
        List<Point> movedBullets = new ArrayList<>();
        for (Point bullet : bullets) {
            int row = bullet.row() - 1;
            if (row >= 0) { movedBullets.add(new Point(row, bullet.col())); }
        }
        bullets.clear();
        bullets.addAll(movedBullets);

        // 2. Move enemies down, but only every other tick, so a player can aim.
        if (tick % 2 == 0) {
            List<Point> movedEnemies = new ArrayList<>();
            for (Point enemy : enemies) {
                movedEnemies.add(new Point(enemy.row() + 1, enemy.col()));
            }
            enemies.clear();
            enemies.addAll(movedEnemies);
        }

        // 3. Resolve, once, after everything has moved. Collected first and
        //    removed after, because removing mid-iteration skips elements.
        Set<Point> deadEnemies = new HashSet<>();
        Set<Point> spentBullets = new HashSet<>();
        for (Point bullet : bullets) {
            for (Point enemy : enemies) {
                if (bullet.row() == enemy.row() && bullet.col() == enemy.col()
                        && !deadEnemies.contains(enemy)) {
                    deadEnemies.add(enemy);
                    spentBullets.add(bullet);
                    break;
                }
            }
        }
        enemies.removeAll(deadEnemies);
        bullets.removeAll(spentBullets);
        score += deadEnemies.size() * 10;

        // 4. Anything that reached the bottom row, or the ship, costs a life.
        List<Point> landed = new ArrayList<>();
        for (Point enemy : enemies) {
            if (enemy.row() >= rows - 1
                    || (enemy.row() == rows - 1 && enemy.col() == shipColumn)) {
                landed.add(enemy);
            }
        }
        if (!landed.isEmpty()) {
            enemies.removeAll(landed);
            lives -= landed.size();
            if (lives <= 0) {
                lives = 0;
                over = true;
            }
        }

        // 5. Wave cleared.
        if (enemies.isEmpty() && !over) {
            wave++;
            score += 50;
            spawnWave();
        }
    }

    public void step(int times) {
        for (int i = 0; i < times && !over; i++) { step(); }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public char[][] cells() {
        char[][] grid = new char[rows][columns];
        for (char[] row : grid) { Arrays.fill(row, '.'); }
        for (Point enemy : enemies) {
            if (enemy.row() >= 0 && enemy.row() < rows) { grid[enemy.row()][enemy.col()] = 'v'; }
        }
        for (Point bullet : bullets) {
            if (bullet.row() >= 0 && bullet.row() < rows) { grid[bullet.row()][bullet.col()] = '|'; }
        }
        if (!over) { grid[rows - 1][shipColumn] = 'A'; }
        return grid;
    }

    private String detail() {
        if (over) {
            return String.format("Game over on wave %d. Score %d.", wave, score);
        }
        return String.format("score %d, lives %d, wave %d, %d enemies left",
                score, lives, wave, enemies.size());
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("a and d to move, space to fire, blank to let time pass, q to quit.");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            if (over) { return; }
            String key = io.ask("move:").trim().toLowerCase(Locale.ROOT);
            if (key.equals("q")) { return; }
            switch (key) {
                case "a" -> moveShip(-1);
                case "d" -> moveShip(1);
                case "f", " ", "space" -> fire();
                default -> { }
            }
            step();
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    shipColumn = columns / 2;
                    bullets.clear();
                    score = 0;
                    lives = 3;
                    tick = 0;
                    wave = 1;
                    over = false;
                    spawnWave();
                    return board("New game. a and d to move, space to fire.");
                }
                case "left" -> { moveShip(-1); step(); return board(""); }
                case "right" -> { moveShip(1); step(); return board(""); }
                case "fire" -> { fire(); step(); return board(""); }
                case "step" -> { step(); return board(""); }
                case "solve" -> {
                    // Line the ship up with the lowest enemy and fire.
                    for (int i = 0; i < 60 && !over; i++) {
                        Point target = null;
                        for (Point enemy : enemies) {
                            if (target == null || enemy.row() > target.row()) { target = enemy; }
                        }
                        if (target != null) {
                            moveShip(Integer.compare(target.col(), shipColumn));
                            if (target.col() == shipColumn) { fire(); }
                        }
                        step();
                    }
                    return board("Autopilot tracked the lowest enemy for 60 ticks.");
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
