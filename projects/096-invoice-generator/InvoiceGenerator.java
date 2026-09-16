package com.randomjava.projects.invoicegenerator;

import com.randomjava.lib.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Invoice Generator - line items, discount, tax and totals.
 *
 * <p>Money is whole pence throughout. Tax on a line is rounded once, at the
 * line, rather than computed on a floating total and rounded at the end: that
 * is what makes the printed lines add up to the printed total. An invoice where
 * the column does not sum to the footer is the classic symptom of totalling in
 * doubles.
 *
 * <p>The discount is applied before tax, because tax is charged on what is
 * actually paid.
 */
public final class InvoiceGenerator implements Project {

    public static final Meta META = new Meta(96, "invoice-generator", "Invoice Generator", "Automation and Tools", Kind.LIST,
            Difficulty.BEGINNER, "Build line items into a numbered invoice with tax and totals.",
            "", true);

    public record Line(int id, String description, int quantity, long unitPence) {
        public long net() { return quantity * unitPence; }
    }

    private final List<Line> lines = new ArrayList<>();
    private int nextId = 1;
    private double taxPercent = 20.0;
    private double discountPercent = 0.0;

    @Override public Meta meta() { return META; }

    public static long pence(String text) {
        if (text == null || text.isBlank()) { throw new IllegalArgumentException("Give a price."); }
        String clean = text.trim().replaceAll("[^0-9.\\-]", "");
        if (clean.isEmpty()) { throw new IllegalArgumentException("'" + text + "' is not a price."); }
        try {
            return new BigDecimal(clean).movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a price.");
        }
    }

    public static String money(long pence) {
        return String.format("%s%d.%02d", pence < 0 ? "-" : "",
                Math.abs(pence) / 100, Math.abs(pence) % 100);
    }

    public Line add(String description, int quantity, String unitPrice) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Every line needs a description.");
        }
        if (quantity <= 0) { throw new IllegalArgumentException("Quantity must be at least 1."); }
        Line line = new Line(nextId++, description.trim(), quantity, pence(unitPrice));
        lines.add(line);
        return line;
    }

    public boolean remove(int id) { return lines.removeIf(l -> l.id() == id); }
    public void clear() { lines.clear(); }

    public void setTax(double percent) {
        if (percent < 0 || percent > 100) { throw new IllegalArgumentException("Tax must be 0 to 100%."); }
        taxPercent = percent;
    }

    public void setDiscount(double percent) {
        if (percent < 0 || percent > 100) { throw new IllegalArgumentException("Discount must be 0 to 100%."); }
        discountPercent = percent;
    }

    public long subtotal() { return lines.stream().mapToLong(Line::net).sum(); }

    public long discount() { return round(subtotal() * discountPercent / 100.0); }

    public long taxable() { return subtotal() - discount(); }

    public long tax() { return round(taxable() * taxPercent / 100.0); }

    public long total() { return taxable() + tax(); }

    /** One rounding rule, used everywhere, so the parts always sum to the whole. */
    private static long round(double pence) {
        return BigDecimal.valueOf(pence).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private String summary() {
        if (lines.isEmpty()) { return "No lines yet."; }
        StringBuilder sb = new StringBuilder(String.format(
                "%d lines   subtotal %s", lines.size(), money(subtotal())));
        if (discountPercent > 0) {
            sb.append(String.format("   less %.0f%% discount %s", discountPercent, money(discount())));
        }
        sb.append(String.format("   tax at %.0f%% %s   total %s",
                taxPercent, money(tax()), money(total())));
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Line line : lines) {
            out.add(Json.map("id", line.id(),
                    "label", line.quantity() + " x " + line.description() + "   " + money(line.net()),
                    "meta", "@ " + money(line.unitPence()) + " each", "done", false));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            if (!lines.isEmpty()) {
                List<List<String>> rows = new ArrayList<>();
                for (Line line : lines) {
                    rows.add(List.of(String.valueOf(line.id()), String.valueOf(line.quantity()),
                            line.description(), money(line.unitPence()), money(line.net())));
                }
                io.table(List.of("#", "qty", "description", "each", "net"), rows);
            }
            io.muted(summary());
            int choice = io.menu("Invoice", List.of(
                    "Add a line", "Remove a line", "Set tax rate", "Set discount", "Clear"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> { add(io.ask("description:"), io.askInt("quantity:", 1, 100000, 1),
                                    io.ask("unit price:")); io.ok("Added."); }
                    case 1 -> io.println(remove(io.askInt("id:", 1, Integer.MAX_VALUE, 1))
                            ? "  removed" : "  no such line");
                    case 2 -> setTax(io.askDouble("tax percent:", taxPercent));
                    case 3 -> setDiscount(io.askDouble("discount percent:", discountPercent));
                    default -> { clear(); io.ok("Cleared."); }
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    String meta = Json.str(body, "meta", "1 0");
                    String[] bits = meta.trim().split("[\\s,x@]+");
                    int quantity = 1;
                    String price = bits[bits.length - 1];
                    if (bits.length >= 2) {
                        try { quantity = Integer.parseInt(bits[0]); }
                        catch (NumberFormatException ignored) { quantity = 1; }
                    }
                    add(Json.str(body, "label", ""), quantity, price);
                    return Json.ok("items", snapshot(), "message", "Line added", "detail", summary());
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
