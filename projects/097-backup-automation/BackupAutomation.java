package com.randomjava.projects.backupautomation;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Backup Automation - works out what an incremental backup would actually copy.
 *
 * <p>The whole value of an incremental backup is skipping files that have not
 * changed, so the interesting logic is the comparison, not the copying. Given a
 * listing of the source and of the destination, this reports four groups: new,
 * changed, unchanged, and present only in the destination.
 *
 * <p>That last group is the one worth being careful about. Deleting it mirrors
 * the source exactly; keeping it means the backup accumulates files that no
 * longer exist. Neither is automatically right, so it is reported separately
 * rather than silently actioned - a backup tool that quietly deletes is a
 * backup tool that eventually loses something.
 */
public final class BackupAutomation implements Project {

    public static final Meta META = new Meta(97, "backup-automation", "Backup Automation", "Automation and Tools", Kind.TOOL,
            Difficulty.BEGINNER, "Copy a source tree to a destination, skipping unchanged files.",
            "", true);

    /** One listed file: name, size in bytes, and a modification stamp. */
    public record Entry(String name, long size, String stamp) { }

    public record Plan(List<String> created, List<String> changed,
                       List<String> unchanged, List<String> orphaned, long bytesToCopy) { }

    @Override public Meta meta() { return META; }

    /** Parses "path size stamp" per line; size and stamp are optional. */
    public static Map<String, Entry> parse(String text) {
        Map<String, Entry> out = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            String row = line.trim();
            if (row.isEmpty() || row.startsWith("#")) { continue; }
            String[] bits = row.split("[\\s,|]+");
            long size = 0;
            String stamp = "";
            if (bits.length >= 2) {
                try { size = Long.parseLong(bits[1]); }
                catch (NumberFormatException e) { stamp = bits[1]; }
            }
            if (bits.length >= 3) { stamp = bits[2]; }
            out.put(bits[0], new Entry(bits[0], size, stamp));
        }
        return out;
    }

    /** A file needs copying when it is new, or its size or stamp differs. */
    public Plan compare(Map<String, Entry> source, Map<String, Entry> destination) {
        List<String> created = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        List<String> orphaned = new ArrayList<>();
        long bytes = 0;

        for (Entry entry : source.values()) {
            Entry existing = destination.get(entry.name());
            if (existing == null) {
                created.add(entry.name());
                bytes += entry.size();
            } else if (existing.size() != entry.size()
                    || !existing.stamp().equals(entry.stamp())) {
                changed.add(entry.name());
                bytes += entry.size();
            } else {
                unchanged.add(entry.name());
            }
        }
        for (String name : destination.keySet()) {
            if (!source.containsKey(name)) { orphaned.add(name); }
        }
        return new Plan(created, changed, unchanged, orphaned, bytes);
    }

    public static String bytes(long value) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double size = value;
        int unit = 0;
        while (size >= 1024 && unit < units.length - 1) { size /= 1024; unit++; }
        return unit == 0 ? value + " B" : String.format("%.1f %s", size, units[unit]);
    }

    private String detail(Plan plan) {
        int total = plan.created().size() + plan.changed().size() + plan.unchanged().size();
        StringBuilder sb = new StringBuilder(String.format(
                "%d files in source. Copying %d, skipping %d.%n"
                + "That is %s to transfer instead of everything.",
                total, plan.created().size() + plan.changed().size(),
                plan.unchanged().size(), bytes(plan.bytesToCopy())));
        append(sb, "new", plan.created());
        append(sb, "changed", plan.changed());
        append(sb, "unchanged, skipped", plan.unchanged());
        if (!plan.orphaned().isEmpty()) {
            append(sb, "only in the destination", plan.orphaned());
            sb.append("\n\nThose exist in the backup but not in the source. Deleting them "
                    + "mirrors the source exactly; keeping them preserves history. This "
                    + "reports them rather than choosing for you.");
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String label, List<String> names) {
        if (names.isEmpty()) { return; }
        sb.append("\n\n").append(label).append(" (").append(names.size()).append("):");
        for (String name : names) { sb.append("\n  ").append(name); }
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Listings as 'path size stamp' per line. A line with only . ends each listing.");
        StringBuilder source = new StringBuilder();
        io.info("Source listing:");
        while (true) {
            String line = io.ask(">");
            if (line.equals(".")) { break; }
            if (line.isEmpty() && source.length() == 0) { return; }
            source.append(line).append('\n');
        }
        StringBuilder destination = new StringBuilder();
        io.info("Destination listing (empty for a first backup):");
        while (true) {
            String line = io.ask(">");
            if (line.equals(".") || line.isEmpty()) { break; }
            destination.append(line).append('\n');
        }
        try {
            Plan plan = compare(parse(source.toString()), parse(destination.toString()));
            io.result(bytes(plan.bytesToCopy()) + " to copy",
                    plan.unchanged().size() + " files skipped");
            io.println("  " + detail(plan).replace("\n", "\n  "));
        } catch (RuntimeException e) { io.error(e.getMessage()); }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            String input = Json.str(body, "input", "");
            String destinationText = Json.str(body, "destination", "");
            if (input.contains("---")) {
                String[] halves = input.split("---+", 2);
                destinationText = halves[1];
                input = halves[0];
            }
            Map<String, Entry> source = parse(input);
            if (source.isEmpty()) {
                return Json.error("List the source files, one per line as: path size stamp");
            }
            Plan plan = compare(source, parse(destinationText));
            return Json.ok("result", (plan.created().size() + plan.changed().size())
                            + " files to copy, " + bytes(plan.bytesToCopy()),
                    "detail", detail(plan));
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
