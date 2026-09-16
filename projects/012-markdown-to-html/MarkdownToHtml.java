package com.randomjava.projects.markdowntohtml;

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
 * Markdown to HTML - a line-based converter covering the parts of Markdown
 * people actually type.
 *
 * <p>Supported: ATX headings, fenced and indented code, unordered and ordered
 * lists, blockquotes, horizontal rules, paragraphs, and the inline run of bold,
 * italic, inline code, links and images.
 *
 * <p>Deliberately not supported: nested lists, tables, reference links, raw
 * HTML passthrough. Those are where a hand-rolled converter stops being worth
 * it and a real parser earns its place.
 *
 * <p>Everything is HTML-escaped before any markup is added, so input cannot
 * inject tags of its own.
 */
public final class MarkdownToHtml implements Project {

    public static final Meta META = new Meta(12, "markdown-to-html", "Markdown To HTML", "Intermediate", Kind.TOOL,
            Difficulty.INTERMEDIATE, "Convert Markdown text into HTML: headings, emphasis, lists, code, links.",
            "", true);

    public static final String SAMPLE = """
            # Shopping notes

            A short paragraph with **bold**, *italic* and `inline code`.

            ## Things to buy

            - Bread
            - Coffee beans
            - Something with [a link](https://example.com)

            ## Steps

            1. Walk to the shop
            2. Buy the things
            3. Walk back

            > Remember the tote bag.

            ---

            ```
            code blocks pass through untouched
            ```
            """;

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // Conversion
    // ------------------------------------------------------------------

    public String convert(String markdown) {
        if (markdown == null) {
            return "";
        }
        List<String> out = new ArrayList<>();
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);

        boolean inCode = false;
        boolean inParagraph = false;
        String listTag = null;

        for (String raw : lines) {
            String line = raw.stripTrailing();
            String trimmed = line.strip();

            // Fenced code: everything inside is escaped and otherwise untouched.
            if (trimmed.startsWith("```")) {
                if (inCode) {
                    out.add("</code></pre>");
                    inCode = false;
                } else {
                    closeParagraph(out, inParagraph);
                    inParagraph = false;
                    listTag = closeList(out, listTag);
                    out.add("<pre><code>");
                    inCode = true;
                }
                continue;
            }
            if (inCode) {
                out.add(escape(line));
                continue;
            }

            if (trimmed.isEmpty()) {
                closeParagraph(out, inParagraph);
                inParagraph = false;
                listTag = closeList(out, listTag);
                continue;
            }

            // Horizontal rule.
            if (trimmed.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
                closeParagraph(out, inParagraph);
                inParagraph = false;
                listTag = closeList(out, listTag);
                out.add("<hr>");
                continue;
            }

            // ATX heading.
            if (trimmed.startsWith("#")) {
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                    level++;
                }
                if (level <= 6 && level < trimmed.length() && trimmed.charAt(level) == ' ') {
                    closeParagraph(out, inParagraph);
                    inParagraph = false;
                    listTag = closeList(out, listTag);
                    out.add("<h" + level + ">" + inline(trimmed.substring(level + 1).strip())
                            + "</h" + level + ">");
                    continue;
                }
            }

            // Blockquote.
            if (trimmed.startsWith("> ") || trimmed.equals(">")) {
                closeParagraph(out, inParagraph);
                inParagraph = false;
                listTag = closeList(out, listTag);
                out.add("<blockquote>" + inline(trimmed.substring(1).strip()) + "</blockquote>");
                continue;
            }

            // Unordered list item.
            if (trimmed.matches("^[-*+] .*")) {
                closeParagraph(out, inParagraph);
                inParagraph = false;
                if (!"ul".equals(listTag)) {
                    listTag = closeList(out, listTag);
                    out.add("<ul>");
                    listTag = "ul";
                }
                out.add("<li>" + inline(trimmed.substring(2).strip()) + "</li>");
                continue;
            }

            // Ordered list item.
            if (trimmed.matches("^\\d+[.)] .*")) {
                closeParagraph(out, inParagraph);
                inParagraph = false;
                if (!"ol".equals(listTag)) {
                    listTag = closeList(out, listTag);
                    out.add("<ol>");
                    listTag = "ol";
                }
                out.add("<li>" + inline(trimmed.replaceFirst("^\\d+[.)] ", "").strip()) + "</li>");
                continue;
            }

            // Anything else is paragraph text.
            listTag = closeList(out, listTag);
            if (!inParagraph) {
                out.add("<p>" + inline(trimmed));
                inParagraph = true;
            } else {
                out.add("<br>" + inline(trimmed));
            }
        }

        if (inCode) {
            out.add("</code></pre>");
        }
        closeParagraph(out, inParagraph);
        closeList(out, listTag);
        return String.join("\n", out);
    }

    private static void closeParagraph(List<String> out, boolean inParagraph) {
        if (inParagraph) {
            out.add("</p>");
        }
    }

    private static String closeList(List<String> out, String listTag) {
        if (listTag != null) {
            out.add("</" + listTag + ">");
        }
        return null;
    }

    /** Escapes first, then applies the inline markup, so input cannot inject tags. */
    private String inline(String text) {
        String html = escape(text);
        // Images before links, since the syntax only differs by a leading !
        html = html.replaceAll("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)",
                "<img src=\"$2\" alt=\"$1\">");
        html = html.replaceAll("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)",
                "<a href=\"$2\" rel=\"noopener noreferrer\">$1</a>");
        html = html.replaceAll("`([^`]+)`", "<code>$1</code>");
        html = html.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("__([^_]+)__", "<strong>$1</strong>");
        html = html.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "<em>$1</em>");
        html = html.replaceAll("(?<!_)_([^_]+)_(?!_)", "<em>$1</em>");
        return html;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Paste Markdown. A line containing only . ends the input.");
        io.muted("Press . straight away to convert the built-in sample.");
        StringBuilder buffer = new StringBuilder();
        while (true) {
            String line = io.ask(">");
            if (line.equals(".")) {
                break;
            }
            if (line.isEmpty() && buffer.length() == 0) {
                break;
            }
            buffer.append(line).append('\n');
        }
        String markdown = buffer.length() == 0 ? SAMPLE : buffer.toString();
        io.println();
        io.rule();
        io.println(convert(markdown));
        io.rule();
        io.muted(markdown.lines().count() + " lines of Markdown converted.");
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        switch (action) {
            case "sample" -> {
                return Json.ok("markdown", SAMPLE, "html", preview(SAMPLE),
                        "detail", "Loaded the sample document.");
            }
            case "compute", "convert" -> {
                String markdown = Json.str(body, "markdown", "");
                if (markdown.isBlank()) {
                    return Json.error("Type or paste some Markdown first.");
                }
                String html = convert(markdown);
                return Json.ok("html", preview(markdown),
                        "source", html,
                        "detail", markdown.lines().count() + " lines converted.");
            }
            default -> {
                return Json.error("Unknown action: " + action);
            }
        }
    }

    /** Rendered output plus the generated source, side by side. */
    private String preview(String markdown) {
        String html = convert(markdown);
        return "<h3 style=\"margin:0 0 10px;font-size:13px;text-transform:uppercase;"
                + "letter-spacing:.06em;color:var(--muted)\">Rendered</h3>"
                + "<div class=\"rendered\">" + html + "</div>"
                + "<h3 style=\"margin:24px 0 10px;font-size:13px;text-transform:uppercase;"
                + "letter-spacing:.06em;color:var(--muted)\">Generated HTML</h3>"
                + "<pre style=\"white-space:pre-wrap;background:var(--bg);padding:14px;"
                + "border-radius:8px;border:1px solid var(--line);font-size:13px\">"
                + escape(html) + "</pre>";
    }
}
