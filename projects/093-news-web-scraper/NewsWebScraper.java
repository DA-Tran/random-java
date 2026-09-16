package com.randomjava.projects.newswebscraper;

import com.randomjava.lib.*;
import java.util.*;

/**
 * News Scraper - collects headlines and folds near-duplicates together.
 *
 * <p>Exact-match de-duplication is useless here, because twenty outlets running
 * the same wire story never phrase it identically. So similarity is measured by
 * <b>Jaccard overlap</b> on the significant words: the size of the intersection
 * over the size of the union. Identical headlines score 1.0, unrelated ones
 * score 0.0, and reworded versions of the same story land high enough to group.
 *
 * <p>Stop words are dropped before comparing, otherwise every headline looks
 * similar to every other simply because they all contain "the" and "of".
 */
public final class NewsWebScraper implements Project {

    public static final Meta META = new Meta(93, "news-web-scraper", "News Web Scraper", "Automation and Tools", Kind.LIST,
            Difficulty.BEGINNER, "Pull headlines from sources and de-duplicate them.",
            "", true);

    private static final Set<String> STOP = Set.of(
            "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at", "for",
            "with", "is", "are", "was", "were", "be", "as", "by", "from", "that", "this",
            "its", "it", "after", "over", "new", "says", "said", "amid", "will",
            "has", "have", "had", "been", "today", "now", "more", "than", "into");

    /** A headline with where it came from. */
    public record Headline(int id, String text, String source) { }

    private final List<Headline> headlines = new ArrayList<>();
    private int nextId = 1;

    /**
     * How much overlap counts as the same story. 0.6 rather than 0.5 because
     * half the words matching is weak evidence on a short headline: "Apple
     * launches iPhone" and "Apple launches iPad" share exactly half their
     * significant words and are plainly different stories. Genuine wire copies
     * of one story score far higher once stemming has been applied.
     */
    private double threshold = 0.6;

    @Override public Meta meta() { return META; }

    /** Significant words, lower-cased, punctuation stripped, stop words removed, stemmed. */
    public static Set<String> tokens(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9']+")) {
            String clean = word.replace("'", "");
            if (clean.length() > 1 && !STOP.contains(clean)) { out.add(stem(clean)); }
        }
        return out;
    }

    /**
     * Crude suffix stripping, which is the difference between this working and
     * not. "Apple launches" and "Apple launched" are the same story, but as raw
     * tokens they have nothing in common, so overlap collapses and the wire
     * copies never group. Reducing both to "launch" fixes that.
     *
     * <p>Deliberately not a full Porter stemmer: headlines are short, and the
     * handful of endings below covers almost all of the variation that matters
     * without the false merges a more aggressive stemmer produces.
     */
    public static String stem(String word) {
        if (word.length() <= 4) { return word; }
        for (String suffix : new String[]{"ing", "edly", "ed", "es", "ly", "s"}) {
            if (word.endsWith(suffix) && word.length() - suffix.length() >= 3) {
                String root = word.substring(0, word.length() - suffix.length());
                // "launches" loses "es" to give "launch"; "makes" loses only "s".
                if (suffix.equals("es") && !root.endsWith("h") && !root.endsWith("s")
                        && !root.endsWith("x") && !root.endsWith("z")) {
                    return word.substring(0, word.length() - 1);
                }
                return root;
            }
        }
        return word;
    }

    /** Intersection over union: 1.0 identical, 0.0 nothing in common. */
    public static double similarity(String a, String b) {
        Set<String> first = tokens(a);
        Set<String> second = tokens(b);
        if (first.isEmpty() && second.isEmpty()) { return 1.0; }
        if (first.isEmpty() || second.isEmpty()) { return 0.0; }
        Set<String> shared = new HashSet<>(first);
        shared.retainAll(second);
        Set<String> all = new HashSet<>(first);
        all.addAll(second);
        return (double) shared.size() / all.size();
    }

    public Headline add(String text, String source) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A headline needs some text.");
        }
        Headline headline = new Headline(nextId++, text.trim(),
                source == null || source.isBlank() ? "unknown" : source.trim());
        headlines.add(headline);
        return headline;
    }

    public boolean remove(int id) { return headlines.removeIf(h -> h.id() == id); }
    public void clear() { headlines.clear(); }

    public void setThreshold(double value) {
        if (value <= 0 || value > 1) {
            throw new IllegalArgumentException("The threshold must be between 0 and 1.");
        }
        threshold = value;
    }

    /** Groups headlines that are the same story, first seen wins as the label. */
    public List<List<Headline>> cluster() {
        List<List<Headline>> groups = new ArrayList<>();
        for (Headline headline : headlines) {
            List<Headline> home = null;
            for (List<Headline> group : groups) {
                if (similarity(group.get(0).text(), headline.text()) >= threshold) {
                    home = group;
                    break;
                }
            }
            if (home == null) {
                home = new ArrayList<>();
                groups.add(home);
            }
            home.add(headline);
        }
        groups.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return groups;
    }

    private String summary() {
        if (headlines.isEmpty()) { return "No headlines collected."; }
        List<List<Headline>> groups = cluster();
        int duplicates = headlines.size() - groups.size();
        StringBuilder sb = new StringBuilder(String.format(
                "%d headlines from %d sources fold into %d stories (%d duplicates) "
                + "at a similarity threshold of %.2f.",
                headlines.size(),
                headlines.stream().map(Headline::source).distinct().count(),
                groups.size(), duplicates, threshold));
        for (List<Headline> group : groups) {
            if (group.size() > 1) {
                sb.append(String.format("%n%n  Covered by %d sources:", group.size()));
                for (Headline h : group) {
                    sb.append(String.format("%n    [%s] %s", h.source(), h.text()));
                }
            }
        }
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (List<Headline> group : cluster()) {
            Headline lead = group.get(0);
            out.add(Json.map("id", lead.id(), "label", lead.text(),
                    "meta", group.size() > 1
                            ? group.size() + " sources: " + joinSources(group)
                            : lead.source(),
                    "done", group.size() > 1));
        }
        return out;
    }

    private static String joinSources(List<Headline> group) {
        List<String> names = new ArrayList<>();
        for (Headline h : group) { if (!names.contains(h.source())) { names.add(h.source()); } }
        return String.join(", ", names);
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            io.muted(summary().replace("\n", "\n  "));
            int choice = io.menu("Headlines", List.of(
                    "Add a headline", "Compare two headlines", "Set threshold", "Clear"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> { add(io.ask("headline:"), io.ask("source:")); io.ok("Added."); }
                    case 1 -> io.result(String.format("%.2f similar",
                            similarity(io.ask("first:"), io.ask("second:"))), "1.00 is identical");
                    case 2 -> setThreshold(io.askDouble("threshold 0 to 1:", threshold));
                    default -> { clear(); io.ok("Cleared."); }
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    add(Json.str(body, "label", ""), Json.str(body, "meta", ""));
                    return Json.ok("items", snapshot(), "message", "Added", "detail", summary());
                }
                case "remove" -> {
                    remove(Json.integer(body, "id", -1));
                    return Json.ok("items", snapshot(), "message", "Removed", "detail", summary());
                }
                case "clear" -> {
                    clear();
                    return Json.ok("items", snapshot(), "message", "Cleared", "detail", summary());
                }
                case "list", "toggle" -> { return Json.ok("items", snapshot(), "detail", summary()); }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
