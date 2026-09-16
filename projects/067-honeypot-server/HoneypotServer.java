package com.randomjava.projects.honeypotserver;

import com.randomjava.lib.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Honeypot Server - a decoy service that records what gets thrown at it.
 *
 * <p>A honeypot has no real users, and that is its whole advantage. On a live
 * service, telling attacks apart from unusual-but-legitimate traffic is the
 * hard problem and false positives are expensive. On a decoy nobody has any
 * business talking to, every request is unsolicited by definition, so the
 * question changes from "is this an attack" to the far easier "what kind of
 * attack is this".
 *
 * <p>Classification is by signature, which is worth being honest about: it
 * recognises the shapes of attacks it has been told about and nothing else.
 * Novel techniques land in {@link Category#UNCLASSIFIED}, which is logged just
 * as carefully - on a honeypot an unrecognised request is interesting rather
 * than noise, and quietly dropping it would throw away the only thing here a
 * signature list cannot already do.
 *
 * <p>This records and classifies. It does not listen on a socket, and it does
 * not respond to anything - a decoy that answers is a decoy that can be
 * conscripted into attacking somebody else.
 */
public final class HoneypotServer implements Project {

    public static final Meta META = new Meta(67, "honeypot-server", "Honeypot Server", "Cybersecurity", Kind.LIST,
            Difficulty.ADVANCED, "Fake service that logs everything an intruder tries.",
            "", true);

    public enum Category {
        SQL_INJECTION("SQL injection", "Trying to break out of a query into the database."),
        PATH_TRAVERSAL("Path traversal", "Trying to read files outside the web root."),
        COMMAND_INJECTION("Command injection", "Trying to run shell commands through an input."),
        SCRIPT_INJECTION("Script injection", "Trying to plant script that another user would run."),
        CREDENTIAL_GUESS("Credential guessing", "Trying common username and password pairs."),
        SCANNER("Automated scanner", "A tool sweeping for known software and admin panels."),
        UNCLASSIFIED("Unclassified", "Unsolicited, but not a shape this list recognises.");

        private final String label;
        private final String meaning;
        Category(String label, String meaning) { this.label = label; this.meaning = meaning; }
        public String label() { return label; }
        public String meaning() { return meaning; }
    }

    private record Signature(Category category, Pattern pattern) { }

    /**
     * Ordered by vector, not by target, because several requests are honestly
     * two things at once and the first match wins.
     *
     * <p>"/ping?h=127.0.0.1;cat /etc/shadow" contains a traversal-ish path and
     * a shell metacharacter. Calling it path traversal is not wrong exactly,
     * but it buries the part that matters: user input is reaching a shell.
     * /etc/shadow is only what this particular attacker chose to read with the
     * hole; the hole is the command injection. So command injection is tested
     * first, and a request with no shell metacharacter - "/../../etc/passwd" -
     * still lands on path traversal.
     */
    private static final List<Signature> SIGNATURES = List.of(
            sig(Category.SQL_INJECTION,
                    "('\\s*(or|and)\\s*'?\\d|\\bunion\\s+select\\b|\\bdrop\\s+table\\b"
                    + "|--\\s*$|;\\s*drop\\b|\\bor\\s+1\\s*=\\s*1\\b|\\bsleep\\s*\\()"),
            sig(Category.COMMAND_INJECTION,
                    "([;|`&]\\s*(cat|ls|whoami|id|curl|wget|nc|bash|sh|rm|ping)\\b"
                    + "|\\$\\(|\\|\\s*sh\\b|`[^`]+`)"),
            sig(Category.PATH_TRAVERSAL,
                    "(\\.\\./|\\.\\.\\\\|%2e%2e[/\\\\%]|/etc/(passwd|shadow)|\\bboot\\.ini\\b"
                    + "|\\bwin\\.ini\\b)"),
            sig(Category.SCRIPT_INJECTION,
                    "(<script\\b|javascript:|onerror\\s*=|onload\\s*=|<img[^>]+src\\s*=\\s*[\"']?j)"),
            sig(Category.CREDENTIAL_GUESS,
                    "\\b(admin|root|administrator|test|guest|user)\\s*[:/=]\\s*"
                    + "(admin|root|password|123456|letmein|changeme|toor|test|guest|1234)\\b"),
            sig(Category.SCANNER,
                    "(/wp-(admin|login|content)|/phpmyadmin|/\\.env\\b|/\\.git/|/admin\\.php"
                    + "|/cgi-bin/|\\bnikto\\b|\\bsqlmap\\b|\\bnmap\\b|/xmlrpc\\.php"
                    + "|/vendor/phpunit|/actuator\\b|/solr/)"));

    private static Signature sig(Category category, String regex) {
        return new Signature(category, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    /** One recorded probe. */
    public record Hit(int id, String source, String request, Category category, long at) { }

    private final List<Hit> hits = new ArrayList<>();
    private int nextId = 1;
    private long clock = 0;

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Classifying
    // ------------------------------------------------------------------

    public static Category classify(String request) {
        if (request == null || request.isBlank()) { return Category.UNCLASSIFIED; }
        String decoded = decode(request);
        for (Signature signature : SIGNATURES) {
            if (signature.pattern().matcher(decoded).find()) { return signature.category(); }
        }
        return Category.UNCLASSIFIED;
    }

    /**
     * Percent-decoding, twice.
     *
     * <p>Matching signatures against the raw request misses almost everything
     * real, because "../" travels as "%2e%2e%2f" and sometimes as "%252e" -
     * double-encoded specifically to get past a filter that decodes once. Two
     * passes catches both. Decoding indefinitely would be worse than either:
     * it invents payloads that were never in the request.
     */
    public static String decode(String request) {
        String text = request;
        for (int pass = 0; pass < 2; pass++) {
            StringBuilder sb = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '%' && i + 2 < text.length()) {
                    try {
                        sb.append((char) Integer.parseInt(text.substring(i + 1, i + 3), 16));
                        i += 2;
                        continue;
                    } catch (NumberFormatException ignored) {
                        // Not a real escape. Keep the percent sign as typed.
                    }
                }
                sb.append(c == '+' ? ' ' : c);
            }
            text = sb.toString();
        }
        return text;
    }

    public Hit record(String source, String request) {
        if (request == null || request.isBlank()) {
            throw new IllegalArgumentException("There is no request to log.");
        }
        Hit hit = new Hit(nextId++,
                source == null || source.isBlank() ? "unknown" : source.trim(),
                request.trim(), classify(request), clock++);
        hits.add(hit);
        return hit;
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    public Map<Category, Integer> byCategory() {
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        for (Hit hit : hits) { counts.merge(hit.category(), 1, Integer::sum); }
        return counts;
    }

    /** Sources ordered by how much they probed, busiest first. */
    public List<Map.Entry<String, Integer>> bySource() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Hit hit : hits) { counts.merge(hit.source(), 1, Integer::sum); }
        List<Map.Entry<String, Integer>> out = new ArrayList<>(counts.entrySet());
        out.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return out;
    }

    /**
     * Sources that tried several distinct attack types. One SQL probe is a
     * scanner going through its list; four different categories from the same
     * place is somebody working through the surface by hand.
     */
    public List<String> persistent(int distinctCategories) {
        Map<String, Set<Category>> seen = new LinkedHashMap<>();
        for (Hit hit : hits) {
            seen.computeIfAbsent(hit.source(), k -> EnumSet.noneOf(Category.class))
                .add(hit.category());
        }
        List<String> out = new ArrayList<>();
        seen.forEach((source, categories) -> {
            if (categories.size() >= distinctCategories) {
                out.add(source + " (" + categories.size() + " kinds)");
            }
        });
        return out;
    }

    public List<Hit> hits() { return List.copyOf(hits); }
    public boolean remove(int id) { return hits.removeIf(h -> h.id() == id); }
    public void clear() { hits.clear(); }

    private String summary() {
        if (hits.isEmpty()) {
            return "Nothing logged. Every request that arrives here is unsolicited: "
                    + "the service is a decoy with no legitimate users.";
        }
        StringBuilder sb = new StringBuilder(hits.size() + " probes from "
                + bySource().size() + " sources.");
        byCategory().forEach((category, count) ->
                sb.append(String.format("%n  %-22s %d", category.label(), count)));
        List<String> repeat = persistent(3);
        if (!repeat.isEmpty()) {
            sb.append("\n\n  Working through the surface: ").append(String.join(", ", repeat));
        }
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Hit hit : hits) {
            out.add(Json.map("id", hit.id(), "label", hit.request(),
                    "meta", hit.source() + " - " + hit.category().label(),
                    "done", hit.category() != Category.UNCLASSIFIED));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("Paste requests as they would arrive. Everything here is unsolicited.");
        while (true) {
            io.println();
            io.muted(summary().replace("\n", "\n  "));
            int choice = io.menu("Honeypot", List.of(
                    "Log a request", "Classify without logging", "Load a sample burst", "Clear"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> {
                        Hit hit = record(io.ask("source:", "203.0.113.9"), io.ask("request:"));
                        io.result(hit.category().label(), hit.category().meaning());
                    }
                    case 1 -> {
                        Category category = classify(io.ask("request:"));
                        io.result(category.label(), category.meaning());
                    }
                    case 2 -> { sample(); io.ok("Loaded a burst."); }
                    default -> { clear(); io.ok("Cleared."); }
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    /** A plausible hour on a decoy, for showing the report with something in it. */
    public void sample() {
        record("203.0.113.9", "GET /wp-login.php");
        record("203.0.113.9", "POST /login  admin:admin");
        record("203.0.113.9", "GET /index.php?id=1' OR 1=1--");
        record("203.0.113.9", "GET /..%2f..%2f..%2fetc%2fpasswd");
        record("198.51.100.4", "GET /.env");
        record("198.51.100.4", "GET /phpmyadmin/");
        record("192.0.2.77", "GET /search?q=<script>alert(1)</script>");
        record("192.0.2.77", "GET /ping?host=127.0.0.1;cat /etc/shadow");
        record("203.0.113.200", "GET /api/telemetry?build=42");
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    Hit hit = record(Json.str(body, "meta", "203.0.113.9"),
                            Json.str(body, "label", ""));
                    return Json.ok("items", snapshot(),
                            "message", hit.category().label(),
                            "detail", hit.category().meaning() + "\n\n" + summary());
                }
                case "remove" -> {
                    remove(Json.integer(body, "id", -1));
                    return Json.ok("items", snapshot(), "message", "Removed",
                            "detail", summary());
                }
                case "clear" -> {
                    clear();
                    return Json.ok("items", snapshot(), "message", "Cleared",
                            "detail", summary());
                }
                case "toggle" -> {
                    if (hits.isEmpty()) { sample(); }
                    return Json.ok("items", snapshot(), "message", "Sample burst loaded",
                            "detail", summary());
                }
                case "list" -> { return Json.ok("items", snapshot(), "detail", summary()); }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
