package com.randomjava.projects.plantcarereminder;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plant Care Reminder - Track watering schedules and flag plants that are overdue.
 *
 * <p>Scaffold. Both front ends already work and talk to this class; what is
 * missing is the real logic. Fill in the marked method and the terminal and
 * the browser both start behaving properly, with nothing written twice.
 */
public final class PlantCareReminder implements Project {

    public static final Meta META = new Meta(
            47, "plant-care-reminder", "Plant Care Reminder", "Mobile App Ideas",
            Kind.LIST, Difficulty.BEGINNER, "Track watering schedules and flag plants that are overdue.",
            "", false);

    @Override
    public Meta meta() {
        return META;
    }

    private record Row(int id, String label, String note, boolean done) {
    }

    private final List<Row> rows = new ArrayList<>();
    private int nextId = 1;

    // ------------------------------------------------------------------
    // The methods to implement
    // ------------------------------------------------------------------

    /** Adds an entry. Validate or enrich it here. */
    private Row add(String label, String note) {
        // TODO: Track watering schedules and flag plants that are overdue.
        Row row = new Row(nextId++, label, note, false);
        rows.add(row);
        return row;
    }

    /** A one line summary shown under the list. */
    private String summary() {
        long done = rows.stream().filter(Row::done).count();
        return rows.size() + " entries, " + done + " marked done";
    }

    // ------------------------------------------------------------------
    // Shared operations
    // ------------------------------------------------------------------

    private boolean toggle(int id) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.id() == id) {
                rows.set(i, new Row(row.id(), row.label(), row.note(), !row.done()));
                return true;
            }
        }
        return false;
    }

    private boolean remove(int id) {
        return rows.removeIf(row -> row.id() == id);
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row row : rows) {
            out.add(Json.map("id", row.id(), "label", row.label(),
                    "meta", row.note(), "done", row.done()));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        while (true) {
            show(io);
            int choice = io.menu("What next?", List.of("Add", "Toggle done", "Remove", "Clear all"));
            if (choice < 0) {
                return;
            }
            switch (choice) {
                case 0 -> {
                    String label = io.ask("label:");
                    if (label.isEmpty()) {
                        io.warn("Nothing added.");
                    } else {
                        add(label, io.ask("note (optional):"));
                        io.ok("Added.");
                    }
                }
                case 1 -> io.println(toggle(io.askInt("id to toggle:", 1, Integer.MAX_VALUE, 1))
                        ? "  toggled" : "  no entry with that id");
                case 2 -> io.println(remove(io.askInt("id to remove:", 1, Integer.MAX_VALUE, 1))
                        ? "  removed" : "  no entry with that id");
                default -> {
                    rows.clear();
                    io.ok("Cleared.");
                }
            }
        }
    }

    private void show(ConsoleUI io) {
        io.println();
        if (rows.isEmpty()) {
            io.muted("(empty)");
            return;
        }
        List<List<String>> table = new ArrayList<>();
        for (Row row : rows) {
            table.add(List.of(String.valueOf(row.id()), row.done() ? "x" : " ",
                    row.label(), row.note() == null ? "" : row.note()));
        }
        io.table(List.of("id", "done", "label", "note"), table);
        io.muted(summary());
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        switch (action) {
            case "add" -> {
                String label = Json.str(body, "label", "").trim();
                if (label.isEmpty()) {
                    return Json.error("Give the entry a label first.");
                }
                add(label, Json.str(body, "meta", ""));
                return Json.ok("items", snapshot(), "message", "Added", "detail", summary());
            }
            case "toggle" -> {
                toggle(Json.integer(body, "id", -1));
                return Json.ok("items", snapshot(), "detail", summary());
            }
            case "remove" -> {
                remove(Json.integer(body, "id", -1));
                return Json.ok("items", snapshot(), "message", "Removed", "detail", summary());
            }
            case "clear" -> {
                rows.clear();
                return Json.ok("items", snapshot(), "message", "Cleared", "detail", summary());
            }
            case "list" -> {
                return Json.ok("items", snapshot(), "detail", summary());
            }
            default -> {
                return Json.error("Unknown action: " + action);
            }
        }
    }
}
