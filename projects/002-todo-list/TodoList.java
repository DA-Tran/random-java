package com.randomjava.projects.todolist;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * To-Do List - tasks with a priority and an optional due note, which can be
 * ticked off, filtered and sorted.
 *
 * <p>State lives in memory for the life of the process. The terminal and the
 * browser each get their own instance, so they do not share a list; persisting
 * to a file would be the natural next step.
 */
public final class TodoList implements Project {

    public static final Meta META = new Meta(2, "todo-list", "To-Do List", "Beginner Friendly", Kind.LIST,
            Difficulty.BEGINNER, "Keep a list of tasks you can add to, tick off and clear.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    /** Priority, high to low. The order of the constants is the sort order. */
    public enum Priority {
        HIGH, NORMAL, LOW;

        static Priority parse(String text) {
            if (text == null) {
                return NORMAL;
            }
            return switch (text.trim().toLowerCase(Locale.ROOT)) {
                case "high", "h", "1" -> HIGH;
                case "low", "l", "3" -> LOW;
                default -> NORMAL;
            };
        }

        String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One task. Immutable, so edits replace the record rather than mutate it. */
    public record Task(int id, String title, Priority priority, String note, boolean done) {
    }

    private final List<Task> tasks = new ArrayList<>();
    private int nextId = 1;

    // ------------------------------------------------------------------
    // Core
    // ------------------------------------------------------------------

    public Task add(String title, Priority priority, String note) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A task needs a title.");
        }
        Task task = new Task(nextId++, title.trim(), priority, note == null ? "" : note.trim(), false);
        tasks.add(task);
        return task;
    }

    public boolean toggle(int id) {
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            if (task.id() == id) {
                tasks.set(i, new Task(task.id(), task.title(), task.priority(), task.note(), !task.done()));
                return true;
            }
        }
        return false;
    }

    public boolean remove(int id) {
        return tasks.removeIf(task -> task.id() == id);
    }

    /** Drops everything already ticked off, and reports how many went. */
    public int clearDone() {
        int before = tasks.size();
        tasks.removeIf(Task::done);
        return before - tasks.size();
    }

    public void clearAll() {
        tasks.clear();
    }

    /** Outstanding tasks first, then by priority, then by age. */
    public List<Task> sorted() {
        List<Task> copy = new ArrayList<>(tasks);
        copy.sort(Comparator.comparing(Task::done)
                .thenComparing(Task::priority)
                .thenComparing(Task::id));
        return copy;
    }

    public String summary() {
        long outstanding = tasks.stream().filter(task -> !task.done()).count();
        if (tasks.isEmpty()) {
            return "Nothing on the list.";
        }
        long high = tasks.stream().filter(task -> !task.done() && task.priority() == Priority.HIGH).count();
        String base = outstanding + " outstanding of " + tasks.size();
        return high > 0 ? base + ", " + high + " high priority" : base;
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Task task : sorted()) {
            String meta = task.priority().label();
            if (!task.note().isEmpty()) {
                meta = meta + " - " + task.note();
            }
            out.add(Json.map("id", task.id(), "label", task.title(),
                    "meta", meta, "done", task.done()));
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
            int choice = io.menu("To-do", List.of(
                    "Add a task", "Tick one off", "Remove one", "Clear completed", "Clear everything"));
            if (choice < 0) {
                return;
            }
            switch (choice) {
                case 0 -> {
                    String title = io.ask("task:");
                    if (title.isEmpty()) {
                        io.warn("Nothing added.");
                        break;
                    }
                    Priority priority = Priority.parse(io.ask("priority (high/normal/low):", "normal"));
                    String note = io.ask("note (optional):");
                    add(title, priority, note);
                    io.ok("Added.");
                }
                case 1 -> {
                    if (tasks.isEmpty()) {
                        io.warn("Nothing to tick off.");
                        break;
                    }
                    io.println(toggle(io.askInt("id:", 1, Integer.MAX_VALUE, tasks.get(0).id()))
                            ? "  updated" : "  no task with that id");
                }
                case 2 -> {
                    if (tasks.isEmpty()) {
                        io.warn("Nothing to remove.");
                        break;
                    }
                    io.println(remove(io.askInt("id:", 1, Integer.MAX_VALUE, tasks.get(0).id()))
                            ? "  removed" : "  no task with that id");
                }
                case 3 -> io.ok("Cleared " + clearDone() + " completed tasks.");
                default -> {
                    clearAll();
                    io.ok("List emptied.");
                }
            }
        }
    }

    private void show(ConsoleUI io) {
        io.println();
        List<Task> current = sorted();
        if (current.isEmpty()) {
            io.muted("(nothing on the list yet)");
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        for (Task task : current) {
            rows.add(List.of(
                    String.valueOf(task.id()),
                    task.done() ? "[x]" : "[ ]",
                    task.priority().label(),
                    task.title(),
                    task.note()));
        }
        io.table(List.of("id", "", "priority", "task", "note"), rows);
        io.muted(summary());
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        switch (action) {
            case "add" -> {
                try {
                    add(Json.str(body, "label", ""),
                            Priority.parse(Json.str(body, "priority", "normal")),
                            Json.str(body, "meta", ""));
                } catch (IllegalArgumentException e) {
                    return Json.error(e.getMessage());
                }
                return Json.ok("items", snapshot(), "message", "Task added", "detail", summary());
            }
            case "toggle" -> {
                toggle(Json.integer(body, "id", -1));
                return Json.ok("items", snapshot(), "detail", summary());
            }
            case "remove" -> {
                remove(Json.integer(body, "id", -1));
                return Json.ok("items", snapshot(), "message", "Removed", "detail", summary());
            }
            case "clearDone" -> {
                int cleared = clearDone();
                return Json.ok("items", snapshot(), "message", "Cleared " + cleared,
                        "detail", summary());
            }
            case "clear" -> {
                clearAll();
                return Json.ok("items", snapshot(), "message", "List emptied", "detail", summary());
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
