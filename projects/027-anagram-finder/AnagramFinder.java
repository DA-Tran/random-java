package com.randomjava.projects.anagramfinder;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Anagram Finder - groups words that are anagrams of one another.
 *
 * <p>The trick is the signature: sort a word's letters and every anagram of it
 * produces the same string. That turns "compare every word with every other
 * word", which is quadratic, into one pass building a map, which is linear.
 */
public final class AnagramFinder implements Project {

    public static final Meta META = new Meta(27, "anagram-finder", "Anagram Finder", "Algorithms and Data Structures", Kind.TOOL,
            Difficulty.BEGINNER, "Group words that are anagrams and test whether two strings match.",
            "", true);

    @Override public Meta meta() { return META; }

    /** Letters only, lower-cased and sorted. Two words share this exactly when they are anagrams. */
    public static String signature(String word) {
        char[] letters = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").toCharArray();
        Arrays.sort(letters);
        return new String(letters);
    }

    public boolean isAnagram(String a, String b) {
        String sa = signature(a);
        return !sa.isEmpty() && sa.equals(signature(b));
    }

    /** Groups the input words, returning only the groups with more than one member. */
    public List<List<String>> groups(String text) {
        Map<String, List<String>> buckets = new LinkedHashMap<>();
        for (String word : text.split("[\\s,;]+")) {
            if (word.isBlank()) { continue; }
            String key = signature(word);
            if (key.isEmpty()) { continue; }
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(word.trim());
        }
        List<List<String>> out = new ArrayList<>();
        for (List<String> group : buckets.values()) {
            if (group.size() > 1) { out.add(group); }
        }
        out.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return out;
    }

    private String detail(String text) {
        List<List<String>> found = groups(text);
        if (found.isEmpty()) {
            return "No two of those words are anagrams of each other.";
        }
        StringBuilder sb = new StringBuilder(found.size() + " group"
                + (found.size() == 1 ? "" : "s") + " found:");
        for (List<String> group : found) {
            sb.append("\n  ").append(String.join(", ", group));
        }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Enter words separated by spaces. Two words alone are tested against each other.");
        io.muted("Blank line to finish.");
        while (true) {
            String input = io.ask("words:");
            if (input.isEmpty()) { return; }
            String[] words = input.trim().split("[\\s,;]+");
            if (words.length == 2) {
                io.result(isAnagram(words[0], words[1]) ? "Anagrams" : "Not anagrams",
                        signature(words[0]) + "  vs  " + signature(words[1]));
            } else {
                io.println();
                io.println("  " + detail(input).replace("\n", "\n  "));
            }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        String input = Json.str(body, "input", "");
        if (input.isBlank()) { return Json.error("Enter some words to group."); }
        String[] words = input.trim().split("[\\s,;]+");
        if (words.length == 2) {
            boolean yes = isAnagram(words[0], words[1]);
            return Json.ok("result", yes ? "Anagrams" : "Not anagrams",
                    "detail", words[0] + " sorts to " + signature(words[0])
                            + "\n" + words[1] + " sorts to " + signature(words[1]));
        }
        List<List<String>> found = groups(input);
        return Json.ok("result", found.size() + " anagram group" + (found.size() == 1 ? "" : "s"),
                "detail", detail(input));
    }
}
