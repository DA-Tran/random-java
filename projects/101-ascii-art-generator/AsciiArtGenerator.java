package com.randomjava.projects.asciiartgenerator;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ASCII Art Generator - renders text as a banner using a built-in 5x5 block
 * font, with a choice of fill character and optional word wrapping.
 *
 * <p>The font is stored as five slash-separated rows per character, which keeps
 * the table readable in source and trivial to extend: add an entry to
 * {@link #FONT} and it works everywhere immediately.
 */
public final class AsciiArtGenerator implements Project {

    public static final Meta META = new Meta(101, "ascii-art-generator", "ASCII Art Generator", "Creative and Fun", Kind.GRID,
            Difficulty.BEGINNER, "Turn text into a large ASCII banner using a built-in block font.",
            "", true);

    private static final int ROWS = 5;
    private static final Map<Character, String[]> FONT = new LinkedHashMap<>();

    private static void glyph(char symbol, String rows) {
        FONT.put(symbol, rows.split("/"));
    }

    static {
        glyph('A', " ### /#   #/#####/#   #/#   #");
        glyph('B', "#### /#   #/#### /#   #/#### ");
        glyph('C', " ####/#    /#    /#    / ####");
        glyph('D', "#### /#   #/#   #/#   #/#### ");
        glyph('E', "#####/#    /#### /#    /#####");
        glyph('F', "#####/#    /#### /#    /#    ");
        glyph('G', " ####/#    /#  ##/#   #/ ####");
        glyph('H', "#   #/#   #/#####/#   #/#   #");
        glyph('I', "#####/  #  /  #  /  #  /#####");
        glyph('J', "#####/   # /   # /#  # / ##  ");
        glyph('K', "#   #/#  # /###  /#  # /#   #");
        glyph('L', "#    /#    /#    /#    /#####");
        glyph('M', "#   #/## ##/# # #/#   #/#   #");
        glyph('N', "#   #/##  #/# # #/#  ##/#   #");
        glyph('O', " ### /#   #/#   #/#   #/ ### ");
        glyph('P', "#### /#   #/#### /#    /#    ");
        glyph('Q', " ### /#   #/# # #/#  # / ## #");
        glyph('R', "#### /#   #/#### /#  # /#   #");
        glyph('S', " ####/#    / ### /    #/#### ");
        glyph('T', "#####/  #  /  #  /  #  /  #  ");
        glyph('U', "#   #/#   #/#   #/#   #/ ### ");
        glyph('V', "#   #/#   #/#   #/ # # /  #  ");
        glyph('W', "#   #/#   #/# # #/## ##/#   #");
        glyph('X', "#   #/ # # /  #  / # # /#   #");
        glyph('Y', "#   #/ # # /  #  /  #  /  #  ");
        glyph('Z', "#####/   # /  #  / #   /#####");
        glyph('0', " ### /#  ##/# # #/##  #/ ### ");
        glyph('1', "  #  / ##  /  #  /  #  /#####");
        glyph('2', " ### /#   #/   # /  #  /#####");
        glyph('3', "#### /    #/ ### /    #/#### ");
        glyph('4', "#   #/#   #/#####/    #/    #");
        glyph('5', "#####/#    /#### /    #/#### ");
        glyph('6', " ### /#    /#### /#   #/ ### ");
        glyph('7', "#####/    #/   # /  #  /  #  ");
        glyph('8', " ### /#   #/ ### /#   #/ ### ");
        glyph('9', " ### /#   #/ ####/    #/ ### ");
        glyph(' ', "     /     /     /     /     ");
        glyph('!', "  #  /  #  /  #  /     /  #  ");
        glyph('?', " ### /#   #/   # /     /  #  ");
        glyph('.', "     /     /     /     /  #  ");
        glyph(',', "     /     /     /  #  / #   ");
        glyph('-', "     /     /#####/     /     ");
        glyph(':', "     /  #  /     /  #  /     ");
        glyph('\'', "  #  /  #  /     /     /     ");
    }

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Characters this font knows, for showing in the UI. */
    public static String supported() {
        StringBuilder sb = new StringBuilder();
        for (char symbol : FONT.keySet()) {
            sb.append(symbol);
        }
        return sb.toString();
    }

    /**
     * Renders text as a banner.
     *
     * @param text  what to spell out; unknown characters become a space
     * @param fill  the character to draw with
     * @param width wrap when a line would exceed this many columns, 0 for never
     */
    public String render(String text, char fill, int width) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Type something to render.");
        }
        String upper = text.toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        StringBuilder[] lines = freshLines();
        int columns = 0;

        for (int i = 0; i < upper.length(); i++) {
            String[] glyph = FONT.getOrDefault(upper.charAt(i), FONT.get(' '));
            int glyphWidth = glyph[0].length() + 1;
            if (width > 0 && columns > 0 && columns + glyphWidth > width) {
                appendBlock(out, lines);
                lines = freshLines();
                columns = 0;
            }
            for (int row = 0; row < ROWS; row++) {
                lines[row].append(glyph[row].replace('#', fill)).append(' ');
            }
            columns += glyphWidth;
        }
        appendBlock(out, lines);
        return out.toString().stripTrailing();
    }

    private static StringBuilder[] freshLines() {
        StringBuilder[] lines = new StringBuilder[ROWS];
        for (int i = 0; i < ROWS; i++) {
            lines[i] = new StringBuilder();
        }
        return lines;
    }

    private static void appendBlock(StringBuilder out, StringBuilder[] lines) {
        boolean empty = true;
        for (StringBuilder line : lines) {
            if (!line.toString().isBlank()) {
                empty = false;
                break;
            }
        }
        if (empty) {
            return;
        }
        for (StringBuilder line : lines) {
            out.append(line.toString().stripTrailing()).append('\n');
        }
        out.append('\n');
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Knows: " + supported());
        io.muted("Blank line to finish.");
        while (true) {
            String text = io.ask("text:");
            if (text.isEmpty()) {
                return;
            }
            String fill = io.ask("fill character:", "#");
            try {
                io.println();
                io.println(render(text, fill.isEmpty() ? '#' : fill.charAt(0), 76));
            } catch (RuntimeException e) {
                io.error(e.getMessage());
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        if (!List.of("compute", "generate", "render").contains(action)) {
            return Json.error("Unknown action: " + action);
        }
        try {
            String text = Json.str(body, "text", "");
            String fill = Json.str(body, "fill", "#");
            int width = Json.integer(body, "width", 80);
            String banner = render(text, fill.isEmpty() ? '#' : fill.charAt(0), width);
            String escaped = banner.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            return Json.ok(
                    "html", "<pre style=\"font-family:ui-monospace,Consolas,monospace;"
                            + "font-size:12px;line-height:1.1;overflow-x:auto;margin:0;"
                            + "color:var(--accent)\">" + escaped + "</pre>",
                    "detail", banner.lines().count() + " lines rendered.");
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }
}
