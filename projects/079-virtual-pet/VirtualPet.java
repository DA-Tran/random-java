package com.randomjava.projects.virtualpet;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Virtual Pet - feed, play with and rest a creature whose needs decay over time.
 *
 * <p>The design problem is that a pet with independent decaying stats is either
 * trivial or impossible, and which one depends on numbers that are easy to get
 * wrong in both directions.
 *
 * <ul>
 *   <li><b>Actions must cost something.</b> If feeding only raises fullness,
 *       the winning strategy is to feed forever. Each action here trades: food
 *       costs energy, play costs both food and energy and buys happiness.
 *       There is no move that is good for everything.</li>
 *   <li><b>Decay must outpace any single action.</b> Otherwise one button held
 *       down keeps the pet perfect and time stops mattering.</li>
 *   <li><b>Neglect has to be recoverable until it is not.</b> Health only falls
 *       while a need is actually at zero, so a bad hour is survivable and a bad
 *       day is not. Draining health whenever stats are merely low makes early
 *       mistakes unrecoverable, which reads as the game being unfair.</li>
 * </ul>
 *
 * <p>Time is an explicit tick count rather than the wall clock, so a test can
 * run a week through it and the result does not depend on when it ran.
 */
public final class VirtualPet implements Project {

    public static final Meta META = new Meta(79, "virtual-pet", "Virtual Pet", "Game Development", Kind.GAME,
            Difficulty.BEGINNER, "Feed, play with and rest a pet whose stats decay over time.",
            "", true);

    public static final int MAX = 100;

    /** Per-tick decay. Chosen so no single action outruns the clock. */
    private static final int FOOD_DECAY = 4;
    private static final int ENERGY_DECAY = 3;
    private static final int FUN_DECAY = 5;

    public enum Mood { CONTENT, HUNGRY, TIRED, BORED, MISERABLE, GONE }

    private String name;
    private int food = 70;
    private int energy = 70;
    private int fun = 70;
    private int health = 100;
    private int age;
    private final List<String> diary = new ArrayList<>();

    public VirtualPet() { this("Pip"); }

    public VirtualPet(String name) {
        this.name = name == null || name.isBlank() ? "Pip" : name.trim();
    }

    @Override public Meta meta() { return META; }

    public String name() { return name; }
    public int food() { return food; }
    public int energy() { return energy; }
    public int fun() { return fun; }
    public int health() { return health; }
    public int age() { return age; }
    public boolean alive() { return health > 0; }
    public List<String> diary() { return List.copyOf(diary); }

    private static int clamp(int value) { return Math.max(0, Math.min(MAX, value)); }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /** Every action costs something, so none of them is strictly best. */
    public String feed() {
        return act("fed", () -> {
            food = clamp(food + 30);
            energy = clamp(energy - 5);
        });
    }

    public String play() {
        if (energy < 10) {
            return name + " is too tired to play.";
        }
        return act("played", () -> {
            fun = clamp(fun + 30);
            energy = clamp(energy - 15);
            food = clamp(food - 10);
        });
    }

    public String rest() {
        return act("rested", () -> {
            energy = clamp(energy + 35);
            fun = clamp(fun - 10);
            food = clamp(food - 5);
        });
    }

    private String act(String what, Runnable effect) {
        if (!alive()) { return name + " is gone."; }
        effect.run();
        tick();
        diary.add("tick " + age + ": " + what);
        return name + " " + what + ". " + describe();
    }

    /**
     * One unit of time. Health only moves while a need is actually empty, so a
     * dip is survivable and sustained neglect is not.
     */
    public void tick() {
        if (!alive()) { return; }
        age++;
        food = clamp(food - FOOD_DECAY);
        energy = clamp(energy - ENERGY_DECAY);
        fun = clamp(fun - FUN_DECAY);

        int starving = 0;
        if (food == 0) { starving++; }
        if (energy == 0) { starving++; }
        if (fun == 0) { starving++; }
        if (starving > 0) {
            health = clamp(health - starving * 5);
        } else if (food > 50 && energy > 50 && fun > 50) {
            // Looked after well, so a little of the damage comes back.
            health = clamp(health + 2);
        }
    }

    public void tick(int times) {
        for (int i = 0; i < times && alive(); i++) { tick(); }
    }

    public Mood mood() {
        if (!alive()) { return Mood.GONE; }
        if (health < 30) { return Mood.MISERABLE; }
        int lowest = Math.min(food, Math.min(energy, fun));
        if (lowest > 40) { return Mood.CONTENT; }
        if (lowest == food) { return Mood.HUNGRY; }
        if (lowest == energy) { return Mood.TIRED; }
        return Mood.BORED;
    }

    public String describe() {
        if (!alive()) {
            return name + " did not make it. Age " + age + ".";
        }
        return String.format("%s is %s. food %d, energy %d, fun %d, health %d, age %d",
                name, mood().toString().toLowerCase(Locale.ROOT), food, energy, fun, health, age);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            io.muted(describe());
            if (!alive()) { return; }
            int choice = io.menu("Virtual Pet", List.of(
                    "Feed", "Play", "Rest", "Wait an hour", "Rename"));
            if (choice < 0) { return; }
            switch (choice) {
                case 0 -> io.println("  " + feed());
                case 1 -> io.println("  " + play());
                case 2 -> io.println("  " + rest());
                case 3 -> { tick(); io.muted("Time passes."); }
                default -> name = io.ask("name:", name);
            }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            String note = switch (action) {
                case "feed" -> feed();
                case "play" -> play();
                case "rest" -> rest();
                case "wait", "step" -> { tick(); yield "Time passes."; }
                case "start", "new" -> {
                    name = Json.str(body, "name", name);
                    food = 70; energy = 70; fun = 70; health = 100; age = 0;
                    diary.clear();
                    yield "A new pet called " + name + ".";
                }
                case "status", "play_round", "compute" -> "";
                default -> null;
            };
            if (note == null) { return Json.error("Unknown action: " + action); }
            return Json.ok("result", mood().toString(),
                    "detail", (note.isEmpty() ? "" : note + "\n  ") + describe(),
                    "bars", List.of(food, energy, fun, health));
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
