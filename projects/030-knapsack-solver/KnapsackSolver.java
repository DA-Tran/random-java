package com.randomjava.projects.knapsacksolver;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Knapsack Solver - the 0/1 knapsack, solved by dynamic programming.
 *
 * <p>Greedily taking the best value-per-weight item is the obvious approach and
 * it is wrong: it can miss the optimum entirely. The DP builds a table of the
 * best value achievable for every capacity up to the limit, which is O(n*W) and
 * provably optimal. The chosen items are then recovered by walking the table
 * backwards, which is why the table is kept rather than just the running best.
 */
public final class KnapsackSolver implements Project {

    public static final Meta META = new Meta(30, "knapsack-solver", "Knapsack Solver", "Algorithms and Data Structures", Kind.TOOL,
            Difficulty.INTERMEDIATE, "Maximise value under a weight limit with dynamic programming.",
            "", true);

    public record Item(String name, int weight, int value) { }
    public record Result(int value, int weight, List<Item> chosen) { }

    @Override public Meta meta() { return META; }

    /** Best total value within the capacity, plus which items make it up. */
    public Result solve(List<Item> items, int capacity) {
        if (capacity < 0) { throw new IllegalArgumentException("Capacity cannot be negative."); }
        int n = items.size();
        int[][] best = new int[n + 1][capacity + 1];
        for (int i = 1; i <= n; i++) {
            Item item = items.get(i - 1);
            for (int c = 0; c <= capacity; c++) {
                best[i][c] = best[i - 1][c];
                if (item.weight() <= c) {
                    best[i][c] = Math.max(best[i][c],
                            best[i - 1][c - item.weight()] + item.value());
                }
            }
        }
        List<Item> chosen = new ArrayList<>();
        int c = capacity;
        for (int i = n; i > 0; i--) {
            if (best[i][c] != best[i - 1][c]) {
                Item item = items.get(i - 1);
                chosen.add(item);
                c -= item.weight();
            }
        }
        Collections.reverse(chosen);
        int weight = chosen.stream().mapToInt(Item::weight).sum();
        return new Result(best[n][capacity], weight, chosen);
    }

    /** Parses "15 ; gold 4 10, silver 3 7" or "15; 4:10, 3:7". */
    public static List<Item> parseItems(String text) {
        List<Item> items = new ArrayList<>();
        int index = 1;
        for (String chunk : text.split(",")) {
            String piece = chunk.trim();
            if (piece.isEmpty()) { continue; }
            String[] bits = piece.split("[\\s:]+");
            try {
                if (bits.length >= 3) {
                    items.add(new Item(bits[0], Integer.parseInt(bits[1]), Integer.parseInt(bits[2])));
                } else if (bits.length == 2) {
                    items.add(new Item("item " + index,
                            Integer.parseInt(bits[0]), Integer.parseInt(bits[1])));
                } else {
                    throw new IllegalArgumentException("Each item needs a weight and a value: " + piece);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + piece + "' is not weight and value.");
            }
            index++;
        }
        if (items.isEmpty()) { throw new IllegalArgumentException("No items given."); }
        return items;
    }

    private String detail(Result result, int capacity) {
        StringBuilder sb = new StringBuilder("Capacity " + capacity
                + ", packed " + result.weight() + ", value " + result.value());
        for (Item item : result.chosen()) {
            sb.append("\n  ").append(item.name()).append("  weight ").append(item.weight())
                    .append(", value ").append(item.value());
        }
        if (result.chosen().isEmpty()) { sb.append("\n  nothing fits"); }
        return sb.append("\nThis is the provable optimum, not a greedy guess.").toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Items as: name weight value, separated by commas. Blank line to finish.");
        while (true) {
            String line = io.ask("items:");
            if (line.isEmpty()) { return; }
            try {
                List<Item> items = parseItems(line);
                int capacity = io.askInt("capacity:", 0, 100000, 10);
                Result result = solve(items, capacity);
                io.result("Best value", String.valueOf(result.value()));
                io.println("  " + detail(result, capacity).replace("\n", "\n  "));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            String input = Json.str(body, "input", "");
            int capacity = Json.integer(body, "capacity", -1);
            String itemText = input;
            if (input.contains(";")) {
                String[] halves = input.split(";", 2);
                capacity = Integer.parseInt(halves[0].trim());
                itemText = halves[1];
            }
            if (capacity < 0) {
                return Json.error("Give a capacity, as in:  15 ; gold 4 10, silver 3 7");
            }
            if (capacity > 100000) { return Json.error("Keep the capacity at or below 100000."); }
            Result result = solve(parseItems(itemText), capacity);
            return Json.ok("result", "Best value " + result.value(),
                    "detail", detail(result, capacity));
        } catch (NumberFormatException e) {
            return Json.error("The capacity must be a whole number.");
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
