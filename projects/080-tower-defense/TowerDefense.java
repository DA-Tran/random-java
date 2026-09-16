package com.randomjava.projects.towerdefense;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Tower Defense - place towers along a path and stop waves of creeps.
 *
 * <p>Two rules make this a game rather than an arithmetic exercise.
 *
 * <p><b>Damage is dealt to one creep at a time, in range order.</b> Spreading a
 * tower's damage across everything in range means a wave of many weak creeps is
 * identical to one strong creep with the same total health, and the player's
 * only decision - where to concentrate fire - stops existing. Each tower picks
 * the creep furthest along the path and hits that, so leaks are decided by
 * position rather than by a sum.
 *
 * <p><b>Towers cannot be placed on the path.</b> Not a cosmetic rule: without
 * it the optimal play is to wall the path off entirely, and the game is over.
 *
 * <p>The path is generated as a connected walk from the left edge to the right,
 * so creeps always have a route and "block the maze" is never a strategy.
 */
public final class TowerDefense implements Project {

    public static final Meta META = new Meta(80, "tower-defense", "Tower Defense", "Game Development", Kind.GRID,
            Difficulty.ADVANCED, "Place towers along a path and stop waves of creeps.",
            "", true);

    public record Cell(int row, int col) { }
    public record Tower(Cell at, int damage, int range) { }

    /** A creep, tracked by how far along the path it has walked. */
    public static final class Creep {
        private final int id;
        private int step;
        private int health;
        private final int maxHealth;

        Creep(int id, int health) {
            this.id = id;
            this.health = health;
            this.maxHealth = health;
        }
        public int id() { return id; }
        public int step() { return step; }
        public int health() { return health; }
        public int maxHealth() { return maxHealth; }
        public boolean alive() { return health > 0; }
    }

    private final int rows;
    private final int columns;
    private final List<Cell> path = new ArrayList<>();
    private final Set<Cell> pathCells = new HashSet<>();
    private final Map<Cell, Tower> towers = new LinkedHashMap<>();
    private final List<Creep> creeps = new ArrayList<>();

    public static final int TOWER_COST = 50;
    private int money = 150;
    private int lives = 10;
    private int wave;
    private int nextCreepId = 1;
    private boolean waveRunning;

    public TowerDefense() { this(9, 14, new Random()); }

    public TowerDefense(int rows, int columns, Random random) {
        if (rows < 5 || rows > 20 || columns < 6 || columns > 30) {
            throw new IllegalArgumentException("Use 5 to 20 rows and 6 to 30 columns.");
        }
        this.rows = rows;
        this.columns = columns;
        buildPath(random);
    }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // The map
    // ------------------------------------------------------------------

    /** A connected walk from the left edge to the right, so a route always exists. */
    private void buildPath(Random random) {
        path.clear();
        pathCells.clear();
        int row = rows / 2;
        for (int col = 0; col < columns; col++) {
            add(new Cell(row, col));
            if (col < columns - 1 && random.nextInt(3) == 0) {
                int wanted = Math.max(1, Math.min(rows - 2, row + (random.nextBoolean() ? 1 : -1)));
                // Walk vertically one cell at a time so the path stays joined.
                while (row != wanted) {
                    row += Integer.compare(wanted, row);
                    add(new Cell(row, col));
                }
            }
        }
    }

    private void add(Cell cell) {
        if (pathCells.add(cell)) { path.add(cell); }
    }

    public List<Cell> path() { return List.copyOf(path); }
    public boolean onPath(Cell cell) { return pathCells.contains(cell); }
    public int money() { return money; }
    public int lives() { return lives; }
    public int wave() { return wave; }
    public boolean over() { return lives <= 0; }
    public Collection<Tower> towers() { return List.copyOf(towers.values()); }
    public List<Creep> creeps() { return List.copyOf(creeps); }
    public boolean waveRunning() { return waveRunning && creeps.stream().anyMatch(Creep::alive); }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    public Tower place(int row, int col) {
        Cell cell = new Cell(row, col);
        if (row < 0 || row >= rows || col < 0 || col >= columns) {
            throw new IllegalArgumentException("That square is off the map.");
        }
        // Without this, walling the path off is the whole game.
        if (onPath(cell)) {
            throw new IllegalArgumentException("Towers cannot be built on the path.");
        }
        if (towers.containsKey(cell)) {
            throw new IllegalArgumentException("There is already a tower there.");
        }
        if (money < TOWER_COST) {
            throw new IllegalArgumentException(
                    "A tower costs " + TOWER_COST + " and you have " + money + ".");
        }
        money -= TOWER_COST;
        Tower tower = new Tower(cell, 10, 2);
        towers.put(cell, tower);
        return tower;
    }

    public void startWave() {
        if (over()) { return; }
        wave++;
        creeps.clear();
        int count = 3 + wave;
        int health = 20 + wave * 10;
        for (int i = 0; i < count; i++) {
            Creep creep = new Creep(nextCreepId++, health);
            // Spaced out along the path so they arrive in single file.
            creep.step = -i * 2;
            creeps.add(creep);
        }
        waveRunning = true;
    }

    // ------------------------------------------------------------------
    // The tick
    // ------------------------------------------------------------------

    /**
     * Creeps advance, then every tower fires once at the creep nearest the end
     * of its path within range.
     */
    public void step() {
        if (over() || !waveRunning) { return; }

        for (Creep creep : creeps) {
            if (creep.alive()) { creep.step++; }
        }

        // Anything past the end of the path got through.
        for (Creep creep : creeps) {
            if (creep.alive() && creep.step >= path.size()) {
                creep.health = 0;
                lives--;
            }
        }

        for (Tower tower : towers.values()) {
            Creep target = null;
            for (Creep creep : creeps) {
                if (!creep.alive() || creep.step < 0 || creep.step >= path.size()) { continue; }
                Cell at = path.get(creep.step);
                int distance = Math.max(Math.abs(at.row() - tower.at().row()),
                        Math.abs(at.col() - tower.at().col()));
                if (distance > tower.range()) { continue; }
                // Furthest along wins: concentrate fire on what is about to leak.
                if (target == null || creep.step > target.step) { target = creep; }
            }
            if (target != null) {
                target.health -= tower.damage();
                if (!target.alive()) { money += 15; }
            }
        }

        if (creeps.stream().noneMatch(Creep::alive)) {
            waveRunning = false;
            money += 25;
        }
        if (lives < 0) { lives = 0; }
    }

    public void step(int times) {
        for (int i = 0; i < times && !over(); i++) { step(); }
    }

    /** Runs the current wave to its end. */
    public void runWave() {
        int guard = path.size() * 4 + 40;
        while (waveRunning() && guard-- > 0) { step(); }
        if (creeps.stream().noneMatch(Creep::alive)) { waveRunning = false; }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public char[][] cells() {
        char[][] grid = new char[rows][columns];
        for (char[] row : grid) { Arrays.fill(row, '.'); }
        for (Cell cell : path) { grid[cell.row()][cell.col()] = '#'; }
        for (Tower tower : towers.values()) { grid[tower.at().row()][tower.at().col()] = 'T'; }
        for (Creep creep : creeps) {
            if (creep.alive() && creep.step >= 0 && creep.step < path.size()) {
                Cell at = path.get(creep.step);
                grid[at.row()][at.col()] = '*';
            }
        }
        return grid;
    }

    private String detail() {
        if (over()) { return "Overrun on wave " + wave + "."; }
        long alive = creeps.stream().filter(Creep::alive).count();
        return String.format("wave %d, money %d, lives %d, %d towers, %d creeps on the map",
                wave, money, lives, towers.size(), alive);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("# is the path, T a tower, * a creep. Towers cost " + TOWER_COST + ".");
        while (true) {
            io.println();
            io.grid(cells());
            io.muted(detail());
            if (over()) { return; }
            int choice = io.menu("Tower Defense", List.of(
                    "Place a tower", "Start the next wave", "Run the wave", "One tick"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> {
                        place(io.askInt("row:", 0, rows - 1, 0),
                                io.askInt("column:", 0, columns - 1, 0));
                        io.ok("Built. " + money + " left.");
                    }
                    case 1 -> { startWave(); io.ok("Wave " + wave + " incoming."); }
                    case 2 -> runWave();
                    default -> step();
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "generate" -> {
                    towers.clear();
                    creeps.clear();
                    money = 150;
                    lives = 10;
                    wave = 0;
                    waveRunning = false;
                    return board("New map. Build towers beside the path, not on it.");
                }
                case "place" -> {
                    place(Json.integer(body, "row", -1), Json.integer(body, "col", -1));
                    return board("Built. " + money + " left.");
                }
                case "wave" -> { startWave(); return board("Wave " + wave + " incoming."); }
                case "step" -> { step(); return board(""); }
                case "solve" -> { runWave(); return board("Wave resolved."); }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }

    private Map<String, Object> board(String note) {
        return Json.ok("board", Json.grid(cells()),
                "detail", note.isEmpty() ? detail() : note + "\n  " + detail());
    }
}
