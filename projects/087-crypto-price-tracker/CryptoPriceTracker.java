package com.randomjava.projects.cryptopricetracker;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Crypto Price Tracker - a watchlist with percentage moves and alerts.
 *
 * <p>Percentage change is asymmetric and it catches people out constantly: a
 * 50% fall needs a 100% rise to get back to where it started, not another 50%.
 * The recovery figure is reported alongside the move for exactly that reason.
 *
 * <p>Alerts fire on absolute movement in either direction, since a holder cares
 * about a 20% drop as much as a 20% gain.
 */
public final class CryptoPriceTracker implements Project {

    public static final Meta META = new Meta(87, "crypto-price-tracker", "Crypto Price Tracker", "Data Science", Kind.LIST,
            Difficulty.INTERMEDIATE, "Watch a basket of coins and alert on percentage moves.",
            "", true);

    public record Holding(int id, String symbol, double entry, double current, double alertAt) {

        /** Percentage change from entry to current. */
        public double move() { return entry == 0 ? 0 : (current - entry) / entry * 100; }

        /** What a recovery to the entry price would take from here. */
        public double recoveryNeeded() {
            return current == 0 ? Double.POSITIVE_INFINITY : (entry - current) / current * 100;
        }

        public boolean triggered() { return Math.abs(move()) >= alertAt; }
    }

    private final List<Holding> holdings = new ArrayList<>();
    private int nextId = 1;

    @Override public Meta meta() { return META; }

    public Holding add(String symbol, double entry, double current, double alertAt) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Give the coin a symbol.");
        }
        if (entry <= 0 || current < 0) {
            throw new IllegalArgumentException("Entry must be positive and current cannot be negative.");
        }
        Holding holding = new Holding(nextId++, symbol.trim().toUpperCase(Locale.ROOT),
                entry, current, alertAt <= 0 ? 10 : alertAt);
        holdings.add(holding);
        return holding;
    }

    public boolean remove(int id) { return holdings.removeIf(h -> h.id() == id); }
    public void clear() { holdings.clear(); }

    public List<Holding> triggered() {
        List<Holding> out = new ArrayList<>();
        for (Holding h : holdings) { if (h.triggered()) { out.add(h); } }
        return out;
    }

    /** Portfolio move, weighted by what each position is worth, not a plain mean. */
    public double portfolioMove() {
        double entryTotal = holdings.stream().mapToDouble(Holding::entry).sum();
        double currentTotal = holdings.stream().mapToDouble(Holding::current).sum();
        return entryTotal == 0 ? 0 : (currentTotal - entryTotal) / entryTotal * 100;
    }

    private static String pct(double value) {
        if (Double.isInfinite(value)) { return "unrecoverable"; }
        return String.format("%+.2f%%", value);
    }

    private String summary() {
        if (holdings.isEmpty()) { return "Nothing on the watchlist."; }
        StringBuilder sb = new StringBuilder(String.format(
                "%d holdings, portfolio %s (weighted by position size, not an average of percentages)",
                holdings.size(), pct(portfolioMove())));
        List<Holding> alerts = triggered();
        if (!alerts.isEmpty()) {
            sb.append("\nAlerts:");
            for (Holding h : alerts) {
                sb.append(String.format("%n  %-6s %s past its %.0f%% threshold",
                        h.symbol(), pct(h.move()), h.alertAt()));
            }
        }
        for (Holding h : holdings) {
            if (h.move() < 0) {
                sb.append(String.format("%n  %-6s is down %s and needs %s to get back to entry",
                        h.symbol(), pct(h.move()), pct(h.recoveryNeeded())));
            }
        }
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Holding h : holdings) {
            out.add(Json.map("id", h.id(),
                    "label", h.symbol() + "   " + pct(h.move()),
                    "meta", String.format("%.4f -> %.4f, alert at %.0f%%",
                            h.entry(), h.current(), h.alertAt()),
                    "done", h.triggered()));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            if (!holdings.isEmpty()) {
                List<List<String>> rows = new ArrayList<>();
                for (Holding h : holdings) {
                    rows.add(List.of(String.valueOf(h.id()), h.symbol(),
                            String.format("%.4f", h.entry()), String.format("%.4f", h.current()),
                            pct(h.move()), h.triggered() ? "ALERT" : ""));
                }
                io.table(List.of("id", "coin", "entry", "now", "move", ""), rows);
            }
            io.muted(summary().replace("\n", "  "));
            int choice = io.menu("Watchlist", List.of("Add a holding", "Remove one", "Clear"));
            if (choice < 0) { return; }
            try {
                if (choice == 0) {
                    add(io.ask("symbol:"), io.askDouble("entry price:", 1),
                            io.askDouble("current price:", 1), io.askDouble("alert at %:", 10));
                    io.ok("Added.");
                } else if (choice == 1) {
                    io.println(remove(io.askInt("id:", 1, Integer.MAX_VALUE, 1))
                            ? "  removed" : "  no such id");
                } else { clear(); io.ok("Cleared."); }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    String[] bits = Json.str(body, "meta", "").trim().split("[\\s,]+");
                    if (bits.length < 2) {
                        return Json.error("Put entry and current price in the note, "
                                + "as in:  100 85   (optionally a third number for the alert %)");
                    }
                    add(Json.str(body, "label", ""), Double.parseDouble(bits[0]),
                            Double.parseDouble(bits[1]),
                            bits.length > 2 ? Double.parseDouble(bits[2]) : 10);
                    return Json.ok("items", snapshot(), "message", "Added", "detail", summary());
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
        } catch (NumberFormatException e) {
            return Json.error("Entry and current price must be numbers.");
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
