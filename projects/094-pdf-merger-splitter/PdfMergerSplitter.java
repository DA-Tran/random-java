package com.randomjava.projects.pdfmergersplitter;

import com.randomjava.lib.*;
import java.util.*;

/**
 * PDF Merger and Splitter - plans the page operations.
 *
 * <p>Writing actual PDF bytes needs a library, and this suite has no
 * dependencies, so what is implemented here is the part that is genuinely
 * fiddly and library-independent: <b>page range arithmetic</b>. Parsing
 * "1-3,7,12-9,-2" correctly, rejecting what is out of bounds, handling reversed
 * ranges as reversed, and working out what a merge or split produces.
 *
 * <p>Ranges are one-based because that is how people number pages, and the
 * off-by-one between that and array indices is exactly the bug this is designed
 * to make impossible.
 */
public final class PdfMergerSplitter implements Project {

    public static final Meta META = new Meta(94, "pdf-merger-splitter", "PDF Merger Splitter", "Automation and Tools", Kind.TOOL,
            Difficulty.BEGINNER, "Merge PDFs, split by page range and reorder pages.",
            "", true);

    public record Document(String name, int pages) { }

    @Override public Meta meta() { return META; }

    /**
     * Expands a range expression into one-based page numbers, in the order
     * given. A reversed range such as 9-5 means those pages backwards, which is
     * how you reverse a section.
     *
     * @param expression for example {@code "1-3,7,12-9"} or {@code "all"}
     * @param pageCount  how many pages the document actually has
     */
    public static List<Integer> expand(String expression, int pageCount) {
        if (pageCount <= 0) { throw new IllegalArgumentException("The document has no pages."); }
        String text = expression == null ? "" : expression.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.equals("all") || text.equals("*")) {
            List<Integer> all = new ArrayList<>();
            for (int p = 1; p <= pageCount; p++) { all.add(p); }
            return all;
        }
        List<Integer> out = new ArrayList<>();
        for (String chunk : text.split(",")) {
            String piece = chunk.trim();
            if (piece.isEmpty()) { continue; }
            // A leading dash means "from the start up to here".
            if (piece.startsWith("-")) { piece = "1" + piece; }
            // A trailing dash means "from here to the end".
            if (piece.endsWith("-")) { piece = piece + pageCount; }

            if (piece.contains("-")) {
                String[] ends = piece.split("-", 2);
                int from = page(ends[0], pageCount);
                int to = page(ends[1], pageCount);
                if (from <= to) {
                    for (int p = from; p <= to; p++) { out.add(p); }
                } else {
                    for (int p = from; p >= to; p--) { out.add(p); }
                }
            } else {
                out.add(page(piece, pageCount));
            }
        }
        if (out.isEmpty()) { throw new IllegalArgumentException("That range selects no pages."); }
        return out;
    }

    private static int page(String text, int pageCount) {
        String clean = text.trim();
        int value;
        if (clean.equals("end") || clean.equals("last")) {
            value = pageCount;
        } else {
            try { value = Integer.parseInt(clean); }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + clean + "' is not a page number.");
            }
        }
        if (value < 1 || value > pageCount) {
            throw new IllegalArgumentException(
                    "Page " + value + " is outside this document, which has " + pageCount + ".");
        }
        return value;
    }

    /** Pages in the document that the expression leaves out. */
    public static List<Integer> omitted(List<Integer> selected, int pageCount) {
        Set<Integer> kept = new HashSet<>(selected);
        List<Integer> out = new ArrayList<>();
        for (int p = 1; p <= pageCount; p++) { if (!kept.contains(p)) { out.add(p); } }
        return out;
    }

    /** Parses "report.pdf 12, appendix.pdf 4". */
    public static List<Document> parseDocuments(String text) {
        List<Document> out = new ArrayList<>();
        for (String chunk : text.split("[,;\\n]+")) {
            String piece = chunk.trim();
            if (piece.isEmpty()) { continue; }
            String[] bits = piece.split("[\\s:=]+");
            if (bits.length < 2) {
                throw new IllegalArgumentException("Write each one as: name pageCount");
            }
            try {
                int pages = Integer.parseInt(bits[bits.length - 1]);
                if (pages <= 0) { throw new IllegalArgumentException("Page counts must be positive."); }
                out.add(new Document(String.join(" ",
                        Arrays.copyOfRange(bits, 0, bits.length - 1)), pages));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + piece + "' needs a page count.");
            }
        }
        if (out.isEmpty()) { throw new IllegalArgumentException("No documents given."); }
        return out;
    }

    /** Where each source document lands in the merged result. */
    public String mergePlan(List<Document> documents) {
        int running = 0;
        StringBuilder sb = new StringBuilder();
        for (Document doc : documents) {
            sb.append(String.format("%n  %-22s pages %d to %d",
                    doc.name(), running + 1, running + doc.pages()));
            running += doc.pages();
        }
        return "Merging " + documents.size() + " documents into " + running + " pages:" + sb;
    }

    private static String compress(List<Integer> pages) {
        if (pages.size() > 40) {
            return pages.size() + " pages, starting " + pages.subList(0, 8) + " ...";
        }
        return pages.toString();
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            int choice = io.menu("PDF tools", List.of("Expand a page range", "Plan a merge"));
            if (choice < 0) { return; }
            try {
                if (choice == 0) {
                    int pages = io.askInt("how many pages:", 1, 100000, 20);
                    String range = io.ask("range (e.g. 1-3,7,12-9 or all):");
                    List<Integer> selected = expand(range, pages);
                    io.result(selected.size() + " pages", compress(selected));
                    io.muted("Left out: " + compress(omitted(selected, pages)));
                } else {
                    io.println();
                    io.println("  " + mergePlan(parseDocuments(
                            io.ask("documents as name pages, comma separated:"))).replace("\n", "\n  "));
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            String input = Json.str(body, "input", "");
            if (input.isBlank()) {
                return Json.error("Give a range and a page count, as in:  1-3,7,12-9 of 20"
                        + "   or documents to merge, as:  merge report.pdf 12, notes.pdf 4");
            }
            if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }

            if (input.toLowerCase(Locale.ROOT).startsWith("merge")) {
                List<Document> documents = parseDocuments(input.substring(5));
                int total = documents.stream().mapToInt(Document::pages).sum();
                return Json.ok("result", total + " pages from " + documents.size() + " documents",
                        "detail", mergePlan(documents));
            }
            String[] halves = input.split("(?i)\\s+of\\s+|\\s*/\\s*");
            if (halves.length < 2) {
                return Json.error("Say how many pages the document has, as in:  1-3,7 of 20");
            }
            int pageCount = Integer.parseInt(halves[1].trim());
            List<Integer> selected = expand(halves[0], pageCount);
            List<Integer> left = omitted(selected, pageCount);
            return Json.ok("result", selected.size() + " of " + pageCount + " pages selected",
                    "detail", "Selected, in order: " + compress(selected)
                            + "\nLeft out: " + (left.isEmpty() ? "nothing" : compress(left))
                            + "\n\nPage numbers are one-based throughout, which is where the "
                            + "off-by-one in this kind of tool normally lives.");
        } catch (NumberFormatException e) {
            return Json.error("The page count must be a whole number.");
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
