package com.randomjava.projects.emailautomationbot;

import com.randomjava.lib.*;
import java.util.*;
import java.util.regex.*;

/**
 * Email Automation - merges recipients into a template and queues the results.
 *
 * <p>The failure mode worth designing against is the mail-merge that goes out
 * saying "Dear {name}". So a merge with any placeholder left unfilled is
 * rejected outright rather than sent with a gap, and the missing field is
 * named. Checking after substitution catches it however the data went wrong.
 *
 * <p>Addresses are validated before queuing for the same reason: a bounced
 * batch is discovered hours later, a rejected row immediately.
 */
public final class EmailAutomationBot implements Project {

    public static final Meta META = new Meta(92, "email-automation-bot", "Email Automation Bot", "Automation and Tools", Kind.LIST,
            Difficulty.BEGINNER, "Queue templated emails and send them on a schedule.",
            "", true);

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{?\\s*([a-zA-Z0-9_]+)\\s*\\}?\\}");
    private static final Pattern ADDRESS = Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$");

    public record Queued(int id, String to, String subject, String body) { }

    private String template = "Hi {name},\n\nYour order {order} is on its way.\n\nThanks.";
    private String subject = "Order {order}";
    private final List<Queued> queue = new ArrayList<>();
    private int nextId = 1;

    @Override public Meta meta() { return META; }

    public void setTemplate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The template cannot be empty.");
        }
        template = value;
    }

    public void setSubject(String value) { subject = value == null ? "" : value; }
    public String template() { return template; }

    /** Every placeholder the template expects. */
    public static Set<String> fields(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) { out.add(matcher.group(1)); }
        return out;
    }

    public static boolean validAddress(String address) {
        return address != null && ADDRESS.matcher(address.trim()).matches();
    }

    /**
     * Substitutes values into a template, refusing to return anything with a
     * placeholder still in it.
     */
    public static String merge(String text, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "No value for '" + key + "', so this would have gone out with a gap in it.");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        String merged = out.toString();
        if (PLACEHOLDER.matcher(merged).find()) {
            throw new IllegalArgumentException("A placeholder survived the merge: " + merged);
        }
        return merged;
    }

    /** Parses "alice@example.com name=Alice order=1234". */
    public Queued queue(String row) {
        String[] bits = row.trim().split("\\s+");
        if (bits.length == 0 || bits[0].isEmpty()) {
            throw new IllegalArgumentException("Give an address and the field values.");
        }
        String to = bits[0];
        if (!validAddress(to)) {
            throw new IllegalArgumentException("'" + to + "' is not a usable email address.");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 1; i < bits.length; i++) {
            String[] pair = bits[i].split("=", 2);
            if (pair.length == 2) { values.put(pair[0], pair[1].replace('_', ' ')); }
        }
        Queued queued = new Queued(nextId++, to, merge(subject, values), merge(template, values));
        queue.add(queued);
        return queued;
    }

    public boolean remove(int id) { return queue.removeIf(q -> q.id() == id); }
    public void clear() { queue.clear(); }
    public List<Queued> queued() { return queue; }

    private String summary() {
        StringBuilder sb = new StringBuilder("Template expects: "
                + String.join(", ", fields(template + " " + subject)));
        sb.append("\n").append(queue.size()).append(" queued.");
        if (!queue.isEmpty()) {
            Queued first = queue.get(0);
            sb.append("\n\nFirst one:\n  To: ").append(first.to())
              .append("\n  Subject: ").append(first.subject())
              .append("\n  ").append(first.body().replace("\n", "\n  "));
        }
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Queued q : queue) {
            out.add(Json.map("id", q.id(), "label", q.to() + "   " + q.subject(),
                    "meta", q.body().replace("\n", " ").substring(0,
                            Math.min(70, q.body().replace("\n", " ").length())),
                    "done", false));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            io.muted(summary().replace("\n", "\n  "));
            int choice = io.menu("Email bot", List.of(
                    "Queue a recipient", "Change the template", "Change the subject", "Clear"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> { queue(io.ask("address field=value ...:")); io.ok("Queued."); }
                    case 1 -> { setTemplate(io.ask("template:")); io.ok("Template set."); }
                    case 2 -> { setSubject(io.ask("subject:")); io.ok("Subject set."); }
                    default -> { clear(); io.ok("Cleared."); }
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    String meta = Json.str(body, "meta", "");
                    queue((Json.str(body, "label", "") + " " + meta).trim());
                    return Json.ok("items", snapshot(), "message", "Queued", "detail", summary());
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
