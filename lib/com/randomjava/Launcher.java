package com.randomjava;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;
import com.randomjava.lib.WebHub;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The single entry point for the whole suite.
 *
 * <pre>
 *   java -cp build/classes com.randomjava.Launcher                 interactive terminal menu
 *   java -cp build/classes com.randomjava.Launcher text 4          run project 4 in the terminal
 *   java -cp build/classes com.randomjava.Launcher text bmi-calculator
 *   java -cp build/classes com.randomjava.Launcher web             open the browser hub
 *   java -cp build/classes com.randomjava.Launcher web 8099        ...on a specific port
 *   java -cp build/classes com.randomjava.Launcher list maze       search the catalogue
 * </pre>
 */
public final class Launcher {

    public static void main(String[] args) throws Exception {
        ConsoleUI io = new ConsoleUI();
        String command = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "menu";

        switch (command) {
            case "web" -> {
                int port = args.length > 1 ? parsePort(args[1]) : 8080;
                boolean open = !hasFlag(args, "--no-open");
                new WebHub(Catalog.all(), Catalog.factories()).start(port, open);
            }
            case "text", "run" -> {
                if (args.length < 2) {
                    io.error("Usage: Launcher text <id or slug>");
                    System.exit(2);
                }
                Meta meta = find(args[1]);
                if (meta == null) {
                    io.error("No project matches '" + args[1] + "'. Try: Launcher list " + args[1]);
                    System.exit(2);
                }
                run(meta, io);
            }
            case "list" -> printList(io, args.length > 1 ? args[1] : null);
            case "menu" -> interactiveMenu(io);
            case "help", "-h", "--help" -> printHelp(io);
            default -> {
                // Allow "Launcher 12" and "Launcher snake-game" as shorthands.
                Meta meta = find(command);
                if (meta != null) {
                    run(meta, io);
                } else {
                    printHelp(io);
                    System.exit(2);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    private static void run(Meta meta, ConsoleUI io) throws Exception {
        Project project = Catalog.factories().get(meta.slug()).get();
        io.title(String.format("%03d  %s", meta.id(), meta.name()), meta.description());
        if (!meta.done()) {
            io.warn("This one is still a scaffold - the logic below is a placeholder.");
        }
        project.runText(io);
        io.println();
        io.muted("Done. Browser version: java -cp build/classes com.randomjava.Launcher web");
    }

    private static void interactiveMenu(ConsoleUI io) throws Exception {
        io.title("random-java", "128 projects, each playable in this terminal or in a browser");
        io.muted("Tip: 'Launcher web' opens all of these as web pages instead.");

        Map<String, List<Meta>> byCategory = grouped();
        List<String> categories = new ArrayList<>(byCategory.keySet());

        while (true) {
            List<String> labels = new ArrayList<>();
            for (String category : categories) {
                labels.add(category + "  (" + byCategory.get(category).size() + ")");
            }
            int categoryChoice = io.menu("Pick a category", labels);
            if (categoryChoice < 0) {
                io.muted("Bye.");
                return;
            }
            List<Meta> projects = byCategory.get(categories.get(categoryChoice));

            List<String> projectLabels = new ArrayList<>();
            for (Meta meta : projects) {
                projectLabels.add(String.format("%03d  %-34s %-13s %s",
                        meta.id(), meta.name(), meta.difficulty().label(),
                        meta.done() ? "" : "(scaffold)"));
            }
            int projectChoice = io.menu(categories.get(categoryChoice), projectLabels);
            if (projectChoice < 0) {
                continue;
            }
            run(projects.get(projectChoice), io);
            io.pause();
        }
    }

    private static void printList(ConsoleUI io, String filter) {
        String needle = filter == null ? null : filter.toLowerCase(Locale.ROOT);
        List<List<String>> rows = new ArrayList<>();
        for (Meta meta : Catalog.all()) {
            String haystack = (meta.name() + " " + meta.description() + " " + meta.category()
                    + " " + meta.slug() + " " + meta.difficulty().label()
                    + " " + meta.kind().label() + " " + meta.stack()).toLowerCase(Locale.ROOT);
            if (needle != null && !haystack.contains(needle)) {
                continue;
            }
            rows.add(List.of(String.format("%03d", meta.id()), meta.slug(),
                    meta.difficulty().label(), meta.category(),
                    meta.done() ? "done" : "scaffold"));
        }
        io.println();
        if (rows.isEmpty()) {
            io.warn("Nothing matches '" + filter + "'.");
            return;
        }
        io.table(List.of("#", "slug", "level", "category", "status"), rows);
        io.println();
        io.muted(rows.size() + " of " + Catalog.all().size() + " projects");
    }

    private static void printHelp(ConsoleUI io) {
        io.title("random-java launcher", "one command, 128 projects, two front ends");
        io.println();
        io.info("Launcher                     interactive terminal menu");
        io.info("Launcher text <id|slug>      run one project in the terminal");
        io.info("Launcher web [port]          serve every project in a browser");
        io.info("Launcher list [search]       show the catalogue");
        io.println();
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    private static Meta find(String token) {
        String needle = token.trim().toLowerCase(Locale.ROOT);
        try {
            int id = Integer.parseInt(needle);
            for (Meta meta : Catalog.all()) {
                if (meta.id() == id) {
                    return meta;
                }
            }
            return null;
        } catch (NumberFormatException ignored) {
            // not an id, fall through to slug and name matching
        }
        for (Meta meta : Catalog.all()) {
            if (meta.slug().equals(needle)) {
                return meta;
            }
        }
        for (Meta meta : Catalog.all()) {
            if (meta.slug().contains(needle) || meta.name().toLowerCase(Locale.ROOT).contains(needle)) {
                return meta;
            }
        }
        return null;
    }

    private static Map<String, List<Meta>> grouped() {
        Map<String, List<Meta>> byCategory = new LinkedHashMap<>();
        for (Meta meta : Catalog.all()) {
            byCategory.computeIfAbsent(meta.category(), key -> new ArrayList<>()).add(meta);
        }
        return byCategory;
    }

    private static int parsePort(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equalsIgnoreCase(flag)) {
                return true;
            }
        }
        return false;
    }
}
