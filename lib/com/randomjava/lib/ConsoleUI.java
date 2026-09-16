package com.randomjava.lib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Terminal input/output helpers shared by every project's text mode.
 *
 * <p>Colour is emitted with plain ANSI escapes, which Windows Terminal,
 * PowerShell 7 and every Unix shell understand. Set {@code NO_COLOR=1} to get
 * plain text, which is also what happens automatically when output is piped.
 */
public final class ConsoleUI {

    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String CYAN = "\033[36m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String RED = "\033[31m";
    private static final String MAGENTA = "\033[35m";

    private final BufferedReader reader;
    private final PrintStream out;
    private final boolean colour;

    public ConsoleUI() {
        this(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                new PrintStream(System.out, true, StandardCharsets.UTF_8),
                System.getenv("NO_COLOR") == null);
    }

    public ConsoleUI(BufferedReader reader, PrintStream out, boolean colour) {
        this.reader = reader;
        this.out = out;
        this.colour = colour;
    }

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------

    private String paint(String code, String text) {
        return colour ? code + text + RESET : text;
    }

    public void print(String text) {
        out.print(text);
    }

    public void println() {
        out.println();
    }

    public void println(String text) {
        out.println(text);
    }

    /** A boxed heading used at the start of every project's text mode. */
    public void title(String heading, String subtitle) {
        String bar = "=".repeat(Math.max(heading.length(), 44));
        println();
        println(paint(CYAN + BOLD, bar));
        println(paint(CYAN + BOLD, heading));
        if (subtitle != null && !subtitle.isBlank()) {
            println(paint(DIM, subtitle));
        }
        println(paint(CYAN + BOLD, bar));
    }

    public void rule() {
        println(paint(DIM, "-".repeat(44)));
    }

    public void info(String text) {
        println(paint(CYAN, "  " + text));
    }

    public void ok(String text) {
        println(paint(GREEN, "  " + text));
    }

    public void warn(String text) {
        println(paint(YELLOW, "  ! " + text));
    }

    public void error(String text) {
        println(paint(RED, "  x " + text));
    }

    public void muted(String text) {
        println(paint(DIM, "  " + text));
    }

    /** Highlights the headline answer so it stands out in a wall of terminal text. */
    public void result(String label, String value) {
        println();
        println("  " + paint(DIM, label));
        println("  " + paint(MAGENTA + BOLD, value));
        println();
    }

    /** Prints a simple aligned table. */
    public void table(List<String> headers, List<List<String>> rows) {
        int columns = headers.size();
        int[] widths = new int[columns];
        for (int i = 0; i < columns; i++) {
            widths[i] = headers.get(i).length();
        }
        for (List<String> row : rows) {
            for (int i = 0; i < columns && i < row.size(); i++) {
                widths[i] = Math.max(widths[i], String.valueOf(row.get(i)).length());
            }
        }
        StringBuilder head = new StringBuilder("  ");
        for (int i = 0; i < columns; i++) {
            head.append(pad(headers.get(i), widths[i])).append("  ");
        }
        println(paint(BOLD, head.toString()));
        StringBuilder sep = new StringBuilder("  ");
        for (int i = 0; i < columns; i++) {
            sep.append("-".repeat(widths[i])).append("  ");
        }
        println(paint(DIM, sep.toString()));
        for (List<String> row : rows) {
            StringBuilder line = new StringBuilder("  ");
            for (int i = 0; i < columns; i++) {
                String cell = i < row.size() ? String.valueOf(row.get(i)) : "";
                line.append(pad(cell, widths[i])).append("  ");
            }
            println(line.toString());
        }
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    /** Renders a character grid, used by maze / board / puzzle projects. */
    public void grid(char[][] cells) {
        for (char[] row : cells) {
            StringBuilder line = new StringBuilder("  ");
            for (char cell : row) {
                line.append(cell).append(' ');
            }
            println(line.toString());
        }
    }

    public void clear() {
        if (colour) {
            out.print("\033[2J\033[H");
            out.flush();
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /** Reads a line. Returns an empty string at end of input so piped runs terminate. */
    public String ask(String prompt) {
        out.print(paint(CYAN, "  " + prompt + " "));
        out.flush();
        try {
            String line = reader.readLine();
            if (line == null) {
                println();
                return "";
            }
            return line.trim();
        } catch (IOException e) {
            return "";
        }
    }

    public String ask(String prompt, String fallback) {
        String answer = ask(prompt + " [" + fallback + "]");
        return answer.isEmpty() ? fallback : answer;
    }

    public int askInt(String prompt, int min, int max, int fallback) {
        while (true) {
            String answer = ask(prompt + " [" + fallback + "]");
            if (answer.isEmpty()) {
                return fallback;
            }
            try {
                int value = Integer.parseInt(answer);
                if (value < min || value > max) {
                    warn("Enter a whole number between " + min + " and " + max + ".");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                warn("That is not a whole number.");
            }
        }
    }

    public double askDouble(String prompt, double fallback) {
        while (true) {
            String answer = ask(prompt + " [" + trim(fallback) + "]");
            if (answer.isEmpty()) {
                return fallback;
            }
            try {
                return Double.parseDouble(answer);
            } catch (NumberFormatException e) {
                warn("That is not a number.");
            }
        }
    }

    public boolean askYesNo(String prompt, boolean fallback) {
        String answer = ask(prompt + (fallback ? " [Y/n]" : " [y/N]"));
        if (answer.isEmpty()) {
            return fallback;
        }
        return answer.toLowerCase().startsWith("y");
    }

    /**
     * Shows a numbered menu and returns the chosen index, or -1 if the user
     * asked to go back / input ended.
     */
    public int menu(String heading, List<String> options) {
        println();
        println(paint(BOLD, "  " + heading));
        for (int i = 0; i < options.size(); i++) {
            println("   " + paint(YELLOW, String.valueOf(i + 1)) + ". " + options.get(i));
        }
        println("   " + paint(YELLOW, "0") + ". " + paint(DIM, "back"));
        while (true) {
            String answer = ask("choice:");
            if (answer.isEmpty() || answer.equals("0") || answer.equalsIgnoreCase("q")) {
                return -1;
            }
            try {
                int choice = Integer.parseInt(answer);
                if (choice >= 1 && choice <= options.size()) {
                    return choice - 1;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the warning
            }
            warn("Pick a number from the list.");
        }
    }

    public void pause() {
        ask(paint(DIM, "press Enter to continue"));
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
