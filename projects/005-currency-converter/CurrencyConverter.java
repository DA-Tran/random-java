package com.randomjava.projects.currencyconverter;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Currency Converter - converts between currencies using a stored rate table.
 *
 * <p>Rates are held against one base currency, so any pair converts by going
 * through it. That keeps the table linear rather than quadratic: 30 currencies
 * need 30 numbers, not 900. The rates are a fixed snapshot, which the output
 * says plainly rather than implying they are live.
 */
public final class CurrencyConverter implements Project {

    public static final Meta META = new Meta(5, "currency-converter", "Currency Converter", "Beginner Friendly", Kind.TOOL,
            Difficulty.BEGINNER, "Convert an amount between currencies using a rate table.",
            "", true);

    /** Units of each currency per 1 USD, as a fixed snapshot. */
    private static final Map<String, Double> PER_USD = new LinkedHashMap<>();
    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    private static void rate(String code, double perUsd, String name) {
        PER_USD.put(code, perUsd);
        NAMES.put(code, name);
    }

    static {
        rate("USD", 1.0, "US dollar");           rate("EUR", 0.92, "euro");
        rate("GBP", 0.79, "pound sterling");     rate("JPY", 157.0, "Japanese yen");
        rate("CHF", 0.89, "Swiss franc");        rate("CAD", 1.37, "Canadian dollar");
        rate("AUD", 1.52, "Australian dollar");  rate("NZD", 1.64, "New Zealand dollar");
        rate("CNY", 7.25, "Chinese yuan");       rate("INR", 83.5, "Indian rupee");
        rate("SGD", 1.35, "Singapore dollar");   rate("HKD", 7.81, "Hong Kong dollar");
        rate("SEK", 10.6, "Swedish krona");      rate("NOK", 10.8, "Norwegian krone");
        rate("DKK", 6.87, "Danish krone");       rate("PLN", 3.97, "Polish zloty");
        rate("ZAR", 18.4, "South African rand"); rate("BRL", 5.45, "Brazilian real");
        rate("MXN", 18.1, "Mexican peso");       rate("KRW", 1370.0, "South Korean won");
    }

    @Override public Meta meta() { return META; }

    public Set<String> currencies() { return PER_USD.keySet(); }

    /** Converts via the base currency, so every pair works from one table. */
    public double convert(double amount, String from, String to) {
        Double fromRate = PER_USD.get(code(from));
        Double toRate = PER_USD.get(code(to));
        if (fromRate == null) { throw new IllegalArgumentException(unknown(from)); }
        if (toRate == null) { throw new IllegalArgumentException(unknown(to)); }
        return amount / fromRate * toRate;
    }

    private static String code(String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
    }

    private static String unknown(String text) {
        return "'" + text + "' is not in the table. Known: " + String.join(", ", PER_USD.keySet());
    }

    /** Parses "100 USD to EUR" or "100 usd eur". */
    public String[] parse(String input) {
        String[] parts = input.trim().replaceAll("(?i)\\bto\\b", " ").split("[\\s,]+");
        List<String> kept = new ArrayList<>();
        for (String part : parts) { if (!part.isBlank()) { kept.add(part); } }
        if (kept.size() < 3) {
            throw new IllegalArgumentException("Write it as: 100 USD to EUR");
        }
        return new String[]{kept.get(0), kept.get(1), kept.get(2)};
    }

    public static String money(double value, String code) {
        return String.format("%,.2f %s", value, code);
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Known: " + String.join(" ", PER_USD.keySet()));
        io.muted("Write it as: 100 USD to EUR. Blank line to finish.");
        while (true) {
            String input = io.ask("convert:");
            if (input.isEmpty()) { return; }
            try {
                String[] p = parse(input);
                double amount = Double.parseDouble(p[0]);
                io.result(money(amount, code(p[1])) + "  =",
                        money(convert(amount, p[1], p[2]), code(p[2])));
            } catch (NumberFormatException e) {
                io.error("That amount is not a number.");
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            String[] p = parse(Json.str(body, "input", ""));
            double amount = Double.parseDouble(p[0]);
            double result = convert(amount, p[1], p[2]);
            return Json.ok("result", money(result, code(p[2])),
                    "detail", money(amount, code(p[1])) + " is " + money(result, code(p[2]))
                            + "\n1 " + code(p[1]) + " = " + String.format("%.4f", convert(1, p[1], p[2]))
                            + " " + code(p[2])
                            + "\nRates are a fixed snapshot, not live market data.");
        } catch (NumberFormatException e) {
            return Json.error("That amount is not a number.");
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
