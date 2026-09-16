package com.randomjava.projects.expensetracker;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Expense Tracker - log spending, tag it, and see where the money went.
 *
 * <p>Money is held in whole pence rather than doubles. Floating point cannot
 * represent 0.10 exactly, so totalling a few hundred amounts as doubles drifts
 * by a penny or two and the books stop balancing. Integers make that impossible.
 */
public final class ExpenseTracker implements Project {

    public static final Meta META = new Meta(45, "expense-tracker", "Expense Tracker", "Mobile App Ideas", Kind.LIST,
            Difficulty.BEGINNER, "Log spending, tag it, and summarise by category and month.",
            "", true);

    public record Expense(int id, String what, long pence, String category) { }

    private final List<Expense> expenses = new ArrayList<>();
    private int nextId = 1;

    @Override public Meta meta() { return META; }

    /** Parses "12.50" or "£12.50" or "1250p" into whole pence. */
    public static long pence(String text) {
        if (text == null || text.isBlank()) { throw new IllegalArgumentException("Give an amount."); }
        String clean = text.trim().replaceAll("[^0-9.\\-]", "");
        if (clean.isEmpty() || clean.equals("-")) {
            throw new IllegalArgumentException("'" + text + "' is not an amount.");
        }
        try {
            java.math.BigDecimal amount = new java.math.BigDecimal(clean);
            return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not an amount.");
        }
    }

    public static String money(long pence) {
        return String.format("%s%d.%02d", pence < 0 ? "-" : "", Math.abs(pence) / 100, Math.abs(pence) % 100);
    }

    public Expense add(String what, String amount, String category) {
        if (what == null || what.isBlank()) {
            throw new IllegalArgumentException("Say what the expense was for.");
        }
        long value = pence(amount);
        String tag = category == null || category.isBlank() ? "uncategorised"
                : category.trim().toLowerCase(Locale.ROOT);
        Expense expense = new Expense(nextId++, what.trim(), value, tag);
        expenses.add(expense);
        return expense;
    }

    public boolean remove(int id) { return expenses.removeIf(e -> e.id() == id); }
    public void clear() { expenses.clear(); }
    public long total() { return expenses.stream().mapToLong(Expense::pence).sum(); }

    /** Totals per category, largest first. */
    public LinkedHashMap<String, Long> byCategory() {
        Map<String, Long> totals = new HashMap<>();
        for (Expense e : expenses) { totals.merge(e.category(), e.pence(), Long::sum); }
        LinkedHashMap<String, Long> sorted = new LinkedHashMap<>();
        totals.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    private String summary() {
        if (expenses.isEmpty()) { return "Nothing logged yet."; }
        StringBuilder sb = new StringBuilder(expenses.size() + " expenses, total " + money(total()));
        long biggest = 0;
        String where = "";
        for (Map.Entry<String, Long> e : byCategory().entrySet()) {
            sb.append("\n  ").append(e.getKey()).append("  ").append(money(e.getValue()));
            if (e.getValue() > biggest) { biggest = e.getValue(); where = e.getKey(); }
        }
        if (!where.isEmpty() && total() > 0) {
            sb.append("\nMost goes on ").append(where)
              .append(String.format(", %.0f%% of the total.", 100.0 * biggest / total()));
        }
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Expense e : expenses) {
            out.add(Json.map("id", e.id(), "label", e.what() + "   " + money(e.pence()),
                    "meta", e.category(), "done", false));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            if (!expenses.isEmpty()) {
                List<List<String>> rows = new ArrayList<>();
                for (Expense e : expenses) {
                    rows.add(List.of(String.valueOf(e.id()), money(e.pence()), e.category(), e.what()));
                }
                io.table(List.of("id", "amount", "category", "what"), rows);
            }
            io.muted(summary().replace("\n", "  "));
            int choice = io.menu("Expenses", List.of("Add one", "Remove one", "Clear everything"));
            if (choice < 0) { return; }
            try {
                if (choice == 0) {
                    add(io.ask("what for:"), io.ask("amount:"), io.ask("category:"));
                    io.ok("Logged.");
                } else if (choice == 1) {
                    io.println(remove(io.askInt("id:", 1, Integer.MAX_VALUE, 1)) ? "  removed" : "  no such id");
                } else { clear(); io.ok("Cleared."); }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    String meta = Json.str(body, "meta", "");
                    String[] bits = meta.split("[\\s,]+", 2);
                    add(Json.str(body, "label", ""), bits[0], bits.length > 1 ? bits[1] : "");
                    return Json.ok("items", snapshot(), "message", "Logged", "detail", summary());
                }
                case "remove" -> {
                    remove(Json.integer(body, "id", -1));
                    return Json.ok("items", snapshot(), "message", "Removed", "detail", summary());
                }
                case "clear" -> {
                    clear();
                    return Json.ok("items", snapshot(), "message", "Cleared", "detail", summary());
                }
                case "list", "toggle" -> { return Json.ok("items", snapshot(), "detail", summary()); }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
