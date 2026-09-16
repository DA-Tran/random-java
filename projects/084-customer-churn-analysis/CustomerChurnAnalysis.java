package com.randomjava.projects.customerchurnanalysis;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Customer Churn - scores who is likely to leave, and says why.
 *
 * <p>Built on RFM, the standard retail model: <b>recency</b> of the last
 * purchase, <b>frequency</b> of purchases, and <b>monetary</b> value. Recency
 * carries the most weight because it is the strongest single signal - a
 * customer who bought last week is engaged whatever their history, and one who
 * has not bought in a year is usually gone regardless of how much they once
 * spent.
 *
 * <p>Each score comes with the reasons behind it. A churn number with no
 * explanation cannot be acted on: knowing someone is at 0.8 is useless unless
 * you know whether to send a discount or fix a support problem.
 */
public final class CustomerChurnAnalysis implements Project {

    public static final Meta META = new Meta(84, "customer-churn-analysis", "Customer Churn Analysis", "Data Science", Kind.TOOL,
            Difficulty.INTERMEDIATE, "Score which customers are likely to leave and why.",
            "", true);

    public record Customer(String name, int daysSinceLastOrder, int ordersPerYear,
                           double yearlySpend, int supportTickets) { }

    public record Risk(Customer customer, double score, String band, List<String> reasons) { }

    @Override public Meta meta() { return META; }

    /** Weighted risk from 0 (safe) to 1 (almost certainly gone). */
    public Risk score(Customer c) {
        List<String> reasons = new ArrayList<>();

        // Recency: the strongest signal, so the heaviest weight.
        double recency = Math.min(1.0, c.daysSinceLastOrder() / 365.0);
        if (c.daysSinceLastOrder() > 180) {
            reasons.add("No order in " + c.daysSinceLastOrder() + " days");
        }

        // Frequency: rare buyers are easier to lose.
        double frequency = c.ordersPerYear() >= 12 ? 0
                : 1 - c.ordersPerYear() / 12.0;
        if (c.ordersPerYear() <= 2) {
            reasons.add("Only " + c.ordersPerYear() + " orders a year");
        }

        // Monetary: low spend correlates with low commitment.
        double monetary = c.yearlySpend() >= 1000 ? 0
                : 1 - c.yearlySpend() / 1000.0;
        if (c.yearlySpend() < 100) {
            reasons.add(String.format("Spends only %.0f a year", c.yearlySpend()));
        }

        // Support friction: a real churn driver that RFM alone misses.
        double friction = Math.min(1.0, c.supportTickets() / 6.0);
        if (c.supportTickets() >= 3) {
            reasons.add(c.supportTickets() + " support tickets suggests unresolved friction");
        }

        double score = 0.45 * recency + 0.25 * frequency + 0.15 * monetary + 0.15 * friction;
        if (reasons.isEmpty()) { reasons.add("Nothing concerning: recent, regular and settled"); }
        return new Risk(c, score, band(score), reasons);
    }

    public static String band(double score) {
        if (score < 0.25) { return "safe"; }
        if (score < 0.45) { return "watch"; }
        if (score < 0.65) { return "at risk"; }
        return "likely lost";
    }

    public static String action(double score) {
        if (score < 0.25) { return "Leave them alone; contact would be noise."; }
        if (score < 0.45) { return "Keep an eye on the next order gap."; }
        if (score < 0.65) { return "Worth a targeted offer or a check-in call."; }
        return "Win-back campaign, or accept the loss and stop spending on them.";
    }

    /** Parses "name daysSince ordersPerYear yearlySpend [tickets]" per line. */
    public List<Customer> parse(String text) {
        List<Customer> out = new ArrayList<>();
        for (String line : text.split("[\\n;]+")) {
            String row = line.trim();
            if (row.isEmpty()) { continue; }
            String[] bits = row.split("[\\s,]+");
            if (bits.length < 4) {
                throw new IllegalArgumentException("Each line needs: "
                        + "name daysSinceLastOrder ordersPerYear yearlySpend [supportTickets]");
            }
            try {
                out.add(new Customer(bits[0], Integer.parseInt(bits[1]), Integer.parseInt(bits[2]),
                        Double.parseDouble(bits[3]),
                        bits.length >= 5 ? Integer.parseInt(bits[4]) : 0));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + row + "' has a value that is not a number.");
            }
        }
        if (out.isEmpty()) { throw new IllegalArgumentException("No customers given."); }
        return out;
    }

    public List<Risk> ranked(List<Customer> customers) {
        List<Risk> risks = new ArrayList<>();
        for (Customer c : customers) { risks.add(score(c)); }
        risks.sort((a, b) -> Double.compare(b.score(), a.score()));
        return risks;
    }

    private String detail(List<Customer> customers) {
        StringBuilder sb = new StringBuilder(
                "Weighted RFM: recency 45%, frequency 25%, spend 15%, support friction 15%.");
        for (Risk risk : ranked(customers)) {
            sb.append(String.format("%n%n  %-10s %.2f  %s%n    %s",
                    risk.customer().name(), risk.score(), risk.band(), action(risk.score())));
            for (String reason : risk.reasons()) { sb.append("\n    - ").append(reason); }
        }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("One per line: name daysSinceLastOrder ordersPerYear yearlySpend [tickets]");
        io.muted("A line with only . ends the input.");
        StringBuilder buffer = new StringBuilder();
        while (true) {
            String line = io.ask(">");
            if (line.equals(".")) { break; }
            if (line.isEmpty() && buffer.length() == 0) { return; }
            buffer.append(line).append('\n');
        }
        try {
            List<Customer> customers = parse(buffer.toString());
            Risk worst = ranked(customers).get(0);
            io.result(worst.customer().name(), worst.band() + String.format("  (%.2f)", worst.score()));
            io.println("  " + detail(customers).replace("\n", "\n  "));
        } catch (RuntimeException e) { io.error(e.getMessage()); }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            List<Customer> customers = parse(Json.str(body, "input", ""));
            List<Risk> risks = ranked(customers);
            List<Double> bars = new ArrayList<>();
            for (Risk r : risks) { bars.add(r.score() * 100); }
            long atRisk = risks.stream().filter(r -> r.score() >= 0.45).count();
            return Json.ok("result", atRisk + " of " + risks.size() + " at risk or worse",
                    "detail", detail(customers), "bars", bars);
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
