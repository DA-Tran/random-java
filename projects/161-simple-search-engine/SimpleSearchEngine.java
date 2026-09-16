package com.randomjava.projects.simplesearchengine;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.Map;

/**
 * Simple Search Engine - Index documents and rank results for a query by term frequency.
 *
 * <p>Scaffold. Both front ends already work and talk to this class; what is
 * missing is the real logic. Fill in the marked method and the terminal and
 * the browser both start behaving properly, with nothing written twice.
 */
public final class SimpleSearchEngine implements Project {

    public static final Meta META = new Meta(
            161, "simple-search-engine", "Simple Search Engine", "Travel, City and Communication",
            Kind.TOOL, Difficulty.INTERMEDIATE, "Index documents and rank results for a query by term frequency.",
            "Spring Boot, MySQL, Apache Lucene, Thymeleaf, Spring Security", false);

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // The one method to implement
    // ------------------------------------------------------------------

    /**
     * Turns the user's input into an answer.
     *
     * @param input raw text from the terminal prompt or the web form
     * @return the headline answer to display
     */
    private String compute(String input) {
        // TODO: Index documents and rank results for a query by term frequency.
        if (input.isBlank()) {
            return "(no input)";
        }
        return "Read " + input.length() + " characters. Implement compute() for the real answer.";
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Enter a value to run compute(). Blank line to finish.");
        while (true) {
            String input = io.ask("input:");
            if (input.isEmpty()) {
                return;
            }
            io.result("result", compute(input));
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) {
            return Json.error("Unknown action: " + action);
        }
        return Json.ok(
                "result", compute(Json.str(body, "input", "")),
                "detail", "compute() is still a scaffold in SimpleSearchEngine.java");
    }
}
