package com.randomjava.projects.datacleaningscript;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Data Cleaning - trims, de-duplicates, fixes types and reports what it changed.
 *
 * <p>The report matters as much as the cleaning. A script that silently drops
 * 40% of the rows looks like it worked; one that says it dropped 40% tells you
 * the input was wrong. Every transformation here is counted and named.
 *
 * <p>De-duplication is case- and whitespace-insensitive but keeps the first
 * spelling it saw, so the output stays readable rather than lower-cased.
 */
public final class DataCleaningScript implements Project {

    public static final Meta META = new Meta(99, "data-cleaning-script", "Data Cleaning Script", "Automation and Tools", Kind.TOOL,
            Difficulty.BEGINNER, "Trim, de-duplicate, fix types and fill gaps in a table.",
            "", true);

    public record Report(List<String> rows, Map<String, Integer> changes) { }

    @Override public Meta meta() { return META; }

    public Report clean(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Paste some rows to clean.");
        }
        Map<String, Integer> changes = new LinkedHashMap<>();
        changes.put("rows read", 0);
        changes.put("blank rows dropped", 0);
        changes.put("whitespace trimmed", 0);
        changes.put("inner spacing collapsed", 0);
        changes.put("duplicates removed", 0);
        changes.put("numbers normalised", 0);
        changes.put("placeholders emptied", 0);

        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        Set<String> placeholders = Set.of("n/a", "na", "null", "none", "-", "--", "?", "nil", "tbd");

        for (String raw : text.split("\\R")) {
            bump(changes, "rows read");
            String row = raw;

            if (!row.equals(row.strip())) { bump(changes, "whitespace trimmed"); }
            row = row.strip();

            if (row.isEmpty()) { bump(changes, "blank rows dropped"); continue; }

            String collapsed = row.replaceAll("[ \\t]{2,}", " ").replaceAll("\\s*,\\s*", ", ");
            if (!collapsed.equals(row)) { bump(changes, "inner spacing collapsed"); }
            row = collapsed;

            if (placeholders.contains(row.toLowerCase(Locale.ROOT))) {
                bump(changes, "placeholders emptied");
                continue;
            }

            String normalised = normaliseNumbers(row);
            if (!normalised.equals(row)) { bump(changes, "numbers normalised"); }
            row = normalised;

            String key = row.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (!seen.add(key)) { bump(changes, "duplicates removed"); continue; }
            out.add(row);
        }
        changes.put("rows kept", out.size());
        return new Report(out, changes);
    }

    /** Strips thousands separators and stray currency marks from numeric fields. */
    private static String normaliseNumbers(String row) {
        StringBuilder sb = new StringBuilder();
        String[] fields = row.split(",", -1);
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i].trim();
            String candidate = field.replaceAll("[£$€,\\s]", "");
            if (!candidate.isEmpty() && candidate.matches("-?\\d+(\\.\\d+)?")) {
                field = candidate;
            }
            sb.append(field);
            if (i < fields.length - 1) { sb.append(", "); }
        }
        return sb.toString();
    }

    private static void bump(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }

    private String detail(Report report) {
        StringBuilder sb = new StringBuilder("What changed:");
        for (Map.Entry<String, Integer> e : report.changes().entrySet()) {
            if (e.getValue() > 0 || e.getKey().startsWith("rows")) {
                sb.append("\n  ").append(e.getKey()).append(": ").append(e.getValue());
            }
        }
        sb.append("\n\nCleaned rows:");
        for (String row : report.rows()) { sb.append("\n  ").append(row); }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Paste rows. A line with only . ends the input.");
        StringBuilder buffer = new StringBuilder();
        while (true) {
            String line = io.ask(">");
            if (line.equals(".")) { break; }
            if (line.isEmpty() && buffer.length() == 0) { return; }
            buffer.append(line).append('\n');
        }
        try {
            Report report = clean(buffer.toString());
            io.result(report.rows().size() + " rows kept",
                    report.changes().get("rows read") + " read");
            io.println("  " + detail(report).replace("\n", "\n  "));
        } catch (RuntimeException e) { io.error(e.getMessage()); }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            Report report = clean(Json.str(body, "input", ""));
            return Json.ok("result", report.rows().size() + " of "
                            + report.changes().get("rows read") + " rows kept",
                    "detail", detail(report));
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
