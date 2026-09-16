package com.randomjava.lib;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Every project in the suite implements this one interface, which is what lets
 * a single launcher offer all 128 in both a terminal and a browser.
 *
 * <p>There are exactly two entry points:
 * <ul>
 *   <li>{@link #runText(ConsoleUI)} drives the project from the terminal.</li>
 *   <li>{@link #api(String, Map)} answers a single request from the browser UI
 *       and returns any JSON-able value (usually a {@code Map}).</li>
 * </ul>
 *
 * <p>The logic itself should live in neither of those methods. Keep it in plain
 * helper methods so both front ends call the same code and nothing is written
 * twice.
 */
public interface Project {

    /** Title, category and description. Usually returns a generated constant. */
    Meta meta();

    /** Runs the project interactively in the terminal. */
    void runText(ConsoleUI io) throws Exception;

    /**
     * Handles one browser action.
     *
     * @param action the {@code action} field posted by the page
     * @param body   the rest of the posted JSON object
     * @return any JSON-able value; use {@link Json#ok} and {@link Json#error}
     */
    default Object api(String action, Map<String, Object> body) throws Exception {
        return Json.error("This project has no web action named '" + action + "' yet.");
    }

    /**
     * The HTML fragment for this project's page, loaded from {@code ui.html}
     * sitting beside the class on the classpath. The surrounding page chrome,
     * stylesheet and {@code RJ} javascript helper are supplied by the shell.
     */
    default String uiFragment() {
        try (InputStream in = getClass().getResourceAsStream("ui.html")) {
            if (in == null) {
                return "<p class=\"empty\">No <code>ui.html</code> found for this project.</p>";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "<p class=\"empty\">Could not load ui.html: " + e.getMessage() + "</p>";
        }
    }
}
