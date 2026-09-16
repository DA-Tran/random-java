package com.randomjava.projects.fileorganizer;

import com.randomjava.lib.*;
import java.util.*;

/**
 * File Organiser - works out which folder each file belongs in.
 *
 * <p>It plans rather than moves. Given a list of names it reports where each one
 * would go, which is the part worth getting right; actually moving files is a
 * few lines of {@code Files.move} once you trust the plan. Showing the plan
 * first is also how you avoid a script that quietly reorganises the wrong
 * directory.
 */
public final class FileOrganizer implements Project {

    public static final Meta META = new Meta(91, "file-organizer", "File Organizer", "Automation and Tools", Kind.TOOL,
            Difficulty.BEGINNER, "Sort a folder into subfolders by type, date or rules.",
            "", true);

    private static final Map<String, String> FOLDERS = new LinkedHashMap<>();

    private static void kind(String folder, String... extensions) {
        for (String ext : extensions) { FOLDERS.put(ext, folder); }
    }

    static {
        kind("Images", "jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "heic", "tiff", "ico");
        kind("Documents", "pdf", "doc", "docx", "txt", "rtf", "odt", "md", "tex", "pages");
        kind("Spreadsheets", "xls", "xlsx", "csv", "tsv", "ods", "numbers");
        kind("Presentations", "ppt", "pptx", "odp", "key");
        kind("Audio", "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma");
        kind("Video", "mp4", "mkv", "avi", "mov", "wmv", "webm", "m4v");
        kind("Archives", "zip", "tar", "gz", "bz2", "7z", "rar", "xz", "jar");
        kind("Code", "java", "py", "js", "ts", "c", "cpp", "h", "cs", "go", "rs", "rb", "php", "sh");
        kind("Web", "html", "htm", "css", "scss", "json", "xml", "yaml", "yml");
        kind("Fonts", "ttf", "otf", "woff", "woff2");
        kind("Installers", "exe", "msi", "dmg", "deb", "rpm", "appimage", "pkg");
    }

    @Override public Meta meta() { return META; }

    /** The extension, lower-cased, or empty when there is none. */
    public static String extension(String name) {
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        int dot = base.lastIndexOf('.');
        // A leading dot means a hidden file, not an extension.
        if (dot <= 0 || dot == base.length() - 1) { return ""; }
        return base.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public String folderFor(String name) {
        String ext = extension(name);
        if (ext.isEmpty()) { return "No extension"; }
        return FOLDERS.getOrDefault(ext, "Other");
    }

    /** Groups the given names into the folders they would move to. */
    public LinkedHashMap<String, List<String>> plan(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("List some filenames, one per line or comma separated.");
        }
        Map<String, List<String>> groups = new TreeMap<>();
        for (String raw : text.split("[\\n,]+")) {
            String name = raw.trim();
            if (name.isEmpty()) { continue; }
            groups.computeIfAbsent(folderFor(name), k -> new ArrayList<>()).add(name);
        }
        if (groups.isEmpty()) { throw new IllegalArgumentException("No filenames found."); }
        LinkedHashMap<String, List<String>> sorted = new LinkedHashMap<>();
        groups.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    public List<String> knownFolders() { return new ArrayList<>(new LinkedHashSet<>(FOLDERS.values())); }

    private String detail(LinkedHashMap<String, List<String>> plan) {
        int total = plan.values().stream().mapToInt(List::size).sum();
        StringBuilder sb = new StringBuilder(total + " files into " + plan.size() + " folders:");
        for (Map.Entry<String, List<String>> e : plan.entrySet()) {
            sb.append("\n  ").append(e.getKey()).append("/  (").append(e.getValue().size()).append(")");
            for (String name : e.getValue()) { sb.append("\n      ").append(name); }
        }
        return sb.append("\nThis is a plan only; nothing on disk is touched.").toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Paste filenames, one per line. A line with only . ends the input.");
        StringBuilder buffer = new StringBuilder();
        while (true) {
            String line = io.ask(">");
            if (line.equals(".") || (line.isEmpty() && buffer.length() > 0)) { break; }
            if (line.isEmpty()) { return; }
            buffer.append(line).append('\n');
        }
        try {
            LinkedHashMap<String, List<String>> plan = plan(buffer.toString());
            io.result("Plan", plan.size() + " folders");
            io.println("  " + detail(plan).replace("\n", "\n  "));
        } catch (RuntimeException e) { io.error(e.getMessage()); }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            LinkedHashMap<String, List<String>> plan = plan(Json.str(body, "input", ""));
            int total = plan.values().stream().mapToInt(List::size).sum();
            return Json.ok("result", total + " files into " + plan.size() + " folders",
                    "detail", detail(plan));
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
