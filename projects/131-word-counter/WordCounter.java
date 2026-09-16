package com.randomjava.projects.wordcounter;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Word Counter - counts words, characters, sentences and paragraphs in a block
 * of text, and reports the words that appear most often.
 *
 * <p>Counting words is less obvious than it looks. Splitting on whitespace
 * treats "don't" and "twenty-one" as one word each, which is right, but also
 * counts a bare "-" as a word, which is not. So a token has to contain at least
 * one letter or digit to be counted.
 */
public final class WordCounter implements Project {

    public static final Meta META = new Meta(131, "word-counter", "Word Counter", "Core Java and Games", Kind.TOOL,
            Difficulty.BEGINNER, "Count words, characters, sentences, paragraphs and reading time in a block of text.",
            "Core Java, JavaFX, string manipulation", true);

    /** Average adult silent reading speed, words per minute. */
    private static final int READING_SPEED = 238;

    /** Words too common to be interesting in a frequency list. */
    private static final List<String> STOP_WORDS = List.of(
            "the", "a", "an", "and", "or", "but", "if", "of", "to", "in", "on", "at",
            "for", "with", "is", "are", "was", "were", "be", "been", "it", "its",
            "this", "that", "these", "those", "as", "by", "from", "has", "have", "had",
            "not", "no", "so", "than", "then", "there", "their", "they", "you", "your",
            "he", "she", "him", "her", "his", "we", "our", "us", "i", "me", "my");

    @Override
    public Meta meta() {
        return META;
    }

    /** Everything counted in one pass over the text. */
    public record Counts(
            int words, int uniqueWords, int characters, int charactersNoSpaces,
            int sentences, int paragraphs, int lines, double readingMinutes,
            double averageWordLength, List<Map.Entry<String, Integer>> topWords) {
    }

    // ------------------------------------------------------------------
    // Counting
    // ------------------------------------------------------------------

    public Counts analyse(String text) {
        if (text == null) {
            text = "";
        }
        String normalised = text.replace("\r\n", "\n");

        int characters = normalised.length();
        int charactersNoSpaces = 0;
        for (int i = 0; i < normalised.length(); i++) {
            if (!Character.isWhitespace(normalised.charAt(i))) {
                charactersNoSpaces++;
            }
        }

        List<String> words = words(normalised);
        Map<String, Integer> frequency = new LinkedHashMap<>();
        long totalLetters = 0;
        for (String word : words) {
            totalLetters += word.length();
            String key = word.toLowerCase(Locale.ROOT);
            frequency.merge(key, 1, Integer::sum);
        }

        List<Map.Entry<String, Integer>> top = new ArrayList<>();
        frequency.entrySet().stream()
                .filter(entry -> !STOP_WORDS.contains(entry.getKey()))
                .filter(entry -> entry.getKey().length() > 2)
                .sorted((a, b) -> {
                    int byCount = Integer.compare(b.getValue(), a.getValue());
                    return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
                })
                .limit(8)
                .forEach(top::add);

        return new Counts(
                words.size(),
                frequency.size(),
                characters,
                charactersNoSpaces,
                countSentences(normalised),
                countParagraphs(normalised),
                normalised.isEmpty() ? 0 : normalised.split("\n", -1).length,
                words.size() / (double) READING_SPEED,
                words.isEmpty() ? 0 : totalLetters / (double) words.size(),
                top);
    }

    /** Whitespace-separated tokens that contain at least one letter or digit. */
    private static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        for (String token : text.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            boolean meaningful = false;
            for (int i = 0; i < token.length(); i++) {
                if (Character.isLetterOrDigit(token.charAt(i))) {
                    meaningful = true;
                    break;
                }
            }
            if (meaningful) {
                out.add(trimPunctuation(token));
            }
        }
        return out;
    }

    /** Strips leading and trailing punctuation but keeps internal apostrophes and hyphens. */
    private static String trimPunctuation(String token) {
        int start = 0;
        int end = token.length();
        while (start < end && !Character.isLetterOrDigit(token.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isLetterOrDigit(token.charAt(end - 1))) {
            end--;
        }
        return token.substring(start, end);
    }

    /**
     * Counts sentence-ending punctuation, treating runs like "..." or "?!" as
     * one ending rather than three.
     */
    private static int countSentences(String text) {
        int count = 0;
        boolean inRun = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean terminator = c == '.' || c == '!' || c == '?';
            if (terminator && !inRun) {
                count++;
            }
            inRun = terminator;
        }
        // Trailing text with no final full stop is still a sentence.
        String trimmed = text.strip();
        if (!trimmed.isEmpty()) {
            char last = trimmed.charAt(trimmed.length() - 1);
            if (last != '.' && last != '!' && last != '?') {
                count++;
            }
        }
        return count;
    }

    /** Blocks separated by one or more blank lines. */
    private static int countParagraphs(String text) {
        int count = 0;
        for (String block : text.split("\n\\s*\n")) {
            if (!block.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static String readingTime(double minutes) {
        if (minutes <= 0) {
            return "no time at all";
        }
        int seconds = (int) Math.round(minutes * 60);
        if (seconds < 60) {
            return seconds + " sec";
        }
        return (seconds / 60) + " min " + (seconds % 60) + " sec";
    }

    private String detail(Counts counts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Characters:            ").append(counts.characters());
        sb.append("\nCharacters, no spaces: ").append(counts.charactersNoSpaces());
        sb.append("\nWords:                 ").append(counts.words());
        sb.append("\nUnique words:          ").append(counts.uniqueWords());
        sb.append("\nSentences:             ").append(counts.sentences());
        sb.append("\nParagraphs:            ").append(counts.paragraphs());
        sb.append("\nLines:                 ").append(counts.lines());
        sb.append(String.format("%nAverage word length:   %.1f characters", counts.averageWordLength()));
        sb.append("\nReading time:          ").append(readingTime(counts.readingMinutes()));
        if (!counts.topWords().isEmpty()) {
            sb.append("\n\nMost used words:");
            for (Map.Entry<String, Integer> entry : counts.topWords()) {
                sb.append("\n  ").append(entry.getKey()).append("  x").append(entry.getValue());
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Paste or type your text. A line containing only . ends the input.");
        StringBuilder buffer = new StringBuilder();
        while (true) {
            String line = io.ask(">");
            if (line.equals(".")) {
                break;
            }
            if (line.isEmpty() && buffer.length() == 0) {
                io.warn("Nothing to count.");
                return;
            }
            buffer.append(line).append('\n');
        }

        Counts counts = analyse(buffer.toString());
        io.result("Words", String.valueOf(counts.words()));
        io.table(List.of("measure", "count"), List.of(
                List.of("characters", String.valueOf(counts.characters())),
                List.of("characters, no spaces", String.valueOf(counts.charactersNoSpaces())),
                List.of("words", String.valueOf(counts.words())),
                List.of("unique words", String.valueOf(counts.uniqueWords())),
                List.of("sentences", String.valueOf(counts.sentences())),
                List.of("paragraphs", String.valueOf(counts.paragraphs())),
                List.of("reading time", readingTime(counts.readingMinutes()))));
        if (!counts.topWords().isEmpty()) {
            io.println();
            io.muted("Most used words:");
            for (Map.Entry<String, Integer> entry : counts.topWords()) {
                io.muted("  " + entry.getKey() + "  x" + entry.getValue());
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action) && !"count".equals(action)) {
            return Json.error("Unknown action: " + action);
        }
        String text = Json.str(body, "text", "");
        if (text.isBlank()) {
            return Json.error("Type or paste some text first.");
        }
        Counts counts = analyse(text);
        return Json.ok(
                "result", counts.words() + (counts.words() == 1 ? " word" : " words"),
                "detail", detail(counts));
    }
}
