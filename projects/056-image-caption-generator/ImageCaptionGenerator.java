package com.randomjava.projects.imagecaptiongenerator;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Image Caption Generator - turns a list of detections into a sentence.
 *
 * <p>The detector itself is out of scope: this takes the labelled boxes a
 * detector would emit and does the part that is actually language work. That
 * part is easy to do badly and the failures are all the same shape - output
 * that is grammatically wrong, or that states things the input never supported.
 *
 * <p>Four things get done properly rather than approximately:
 *
 * <ul>
 *   <li><b>Salience.</b> Objects are ordered by area times confidence, not by
 *       the order they arrived. The caption should be about the thing the
 *       picture is about.</li>
 *   <li><b>Articles by sound, not spelling.</b> "A unicorn" and "an hour" both
 *       break the vowel-letter rule everyone writes first. The rule is about
 *       pronunciation, and the spelling only correlates with it.</li>
 *   <li><b>Plurals with the irregulars.</b> Three of a thing is "three people",
 *       not "three persons"; two "sheep", not "sheeps".</li>
 *   <li><b>Confidence gating.</b> A detection the model is unsure of is hedged
 *       or dropped, never asserted. A caption that says "a dog" when the
 *       detector said 0.31 has invented a fact.</li>
 * </ul>
 */
public final class ImageCaptionGenerator implements Project {

    public static final Meta META = new Meta(56, "image-caption-generator", "Image Caption Generator", "AI and Machine Learning", Kind.TOOL,
            Difficulty.ADVANCED, "Describe an image from detected features and templates.",
            "", true);

    /** Asserted plainly above this. */
    public static final double CONFIDENT = 0.70;
    /** Hedged between this and CONFIDENT. Dropped below it. */
    public static final double MENTIONABLE = 0.45;

    public static final String SAMPLE = """
            person 0.94 120 30 70 190
            dog 0.88 40 150 80 70
            frisbee 0.62 200 60 30 30
            umbrella 0.31 10 10 20 40
            """;

    /** One detected object: a label, how sure the detector was, and where it sat. */
    public record Box(String label, double confidence, int x, int y, int width, int height) {
        public int area() { return width * height; }
        public double centreX() { return x + width / 2.0; }
        public double centreY() { return y + height / 2.0; }
        /** Area times confidence: big and certain beats small and hopeful. */
        public double salience() { return area() * confidence; }
    }

    public record Caption(String text, List<String> mentioned, List<String> dropped) { }

    private static final Map<String, String> IRREGULAR_PLURALS = Map.of(
            "person", "people", "child", "children", "man", "men", "woman", "women",
            "mouse", "mice", "goose", "geese", "foot", "feet", "tooth", "teeth",
            "sheep", "sheep");

    /**
     * Words whose first letter lies about how they sound. "Unicorn" starts with
     * a consonant sound, "hour" with a vowel one, and the article follows the
     * sound. Checking the letter alone gets both wrong.
     */
    private static final Set<String> SOUNDS_LIKE_CONSONANT = Set.of(
            "unicorn", "uniform", "university", "union", "unit", "user", "usb",
            "european", "ewe", "once", "one", "ukulele", "utensil", "eucalyptus");
    private static final Set<String> SOUNDS_LIKE_VOWEL = Set.of(
            "hour", "honest", "honour", "honor", "heir", "heirloom", "herb");

    private static final String[] NUMBER_WORDS = {
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
            "nine", "ten", "eleven", "twelve"};

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Grammar
    // ------------------------------------------------------------------

    /** "a" or "an", decided by sound where spelling would mislead. */
    public static String article(String noun) {
        String word = noun.toLowerCase(Locale.ROOT).split("[^a-z]")[0];
        if (word.isEmpty()) { return "a"; }
        if (SOUNDS_LIKE_VOWEL.contains(word)) { return "an"; }
        if (SOUNDS_LIKE_CONSONANT.contains(word)) { return "a"; }
        return "aeiou".indexOf(word.charAt(0)) >= 0 ? "an" : "a";
    }

    public static String plural(String noun) {
        String lower = noun.toLowerCase(Locale.ROOT);
        String irregular = IRREGULAR_PLURALS.get(lower);
        if (irregular != null) { return irregular; }
        if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z")
                || lower.endsWith("ch") || lower.endsWith("sh")) {
            return noun + "es";
        }
        if (lower.endsWith("y") && lower.length() > 1
                && "aeiou".indexOf(lower.charAt(lower.length() - 2)) < 0) {
            return noun.substring(0, noun.length() - 1) + "ies";
        }
        if (lower.endsWith("f")) { return noun.substring(0, noun.length() - 1) + "ves"; }
        if (lower.endsWith("fe")) { return noun.substring(0, noun.length() - 2) + "ves"; }
        return noun + "s";
    }

    /** "a dog", "two dogs", "15 dogs" - numbers spelled out while they read well. */
    public static String count(String noun, int howMany) {
        if (howMany == 1) { return article(noun) + " " + noun; }
        String number = howMany < NUMBER_WORDS.length
                ? NUMBER_WORDS[howMany] : String.valueOf(howMany);
        return number + " " + plural(noun);
    }

    /** Where the first box sits relative to the second. */
    public static String relation(Box subject, Box other) {
        double dx = subject.centreX() - other.centreX();
        double dy = subject.centreY() - other.centreY();
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx < 0 ? "to the left of" : "to the right of";
        }
        // Screen coordinates run downwards, so a smaller y is higher up.
        return dy < 0 ? "above" : "below";
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /** Reads "label confidence x y width height", one detection per line. */
    public static List<Box> boxes(String text) {
        List<Box> out = new ArrayList<>();
        if (text == null) { return out; }
        int lineNumber = 0;
        for (String line : text.split("\\R")) {
            lineNumber++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) { continue; }
            String[] parts = trimmed.split("[,\\s]+");
            if (parts.length != 6) {
                throw new IllegalArgumentException("Line " + lineNumber + " has " + parts.length
                        + " fields, expected 6: label confidence x y width height");
            }
            try {
                double confidence = Double.parseDouble(parts[1]);
                if (confidence < 0 || confidence > 1) {
                    throw new IllegalArgumentException(
                            "Line " + lineNumber + ": confidence must be between 0 and 1.");
                }
                int width = Integer.parseInt(parts[4]);
                int height = Integer.parseInt(parts[5]);
                if (width <= 0 || height <= 0) {
                    throw new IllegalArgumentException(
                            "Line " + lineNumber + ": a box needs a positive width and height.");
                }
                out.add(new Box(parts[0].toLowerCase(Locale.ROOT), confidence,
                        Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), width, height));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Line " + lineNumber + " is not numbers.");
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Captioning
    // ------------------------------------------------------------------

    public Caption caption(List<Box> detections) {
        if (detections == null || detections.isEmpty()) {
            return new Caption("Nothing was detected in this image.", List.of(), List.of());
        }
        List<String> dropped = new ArrayList<>();
        List<Box> usable = new ArrayList<>();
        for (Box box : detections) {
            if (box.confidence() < MENTIONABLE) {
                dropped.add(String.format("%s (%.2f)", box.label(), box.confidence()));
            } else {
                usable.add(box);
            }
        }
        if (usable.isEmpty()) {
            return new Caption("Nothing was detected clearly enough to describe.",
                    List.of(), dropped);
        }

        // Group repeats, keeping the best example of each label as its stand-in.
        Map<String, List<Box>> byLabel = new LinkedHashMap<>();
        for (Box box : usable) {
            byLabel.computeIfAbsent(box.label(), k -> new ArrayList<>()).add(box);
        }
        List<Map.Entry<String, List<Box>>> groups = new ArrayList<>(byLabel.entrySet());
        groups.sort((a, b) -> Double.compare(total(b.getValue()), total(a.getValue())));

        Map.Entry<String, List<Box>> lead = groups.get(0);
        Box leadBox = best(lead.getValue());
        StringBuilder sb = new StringBuilder();
        sb.append(capitalise(hedge(leadBox, count(lead.getKey(), lead.getValue().size()))));

        if (groups.size() > 1) {
            Map.Entry<String, List<Box>> next = groups.get(1);
            Box nextBox = best(next.getValue());
            sb.append(' ').append(relation(leadBox, nextBox)).append(' ')
              .append(hedge(nextBox, count(next.getKey(), next.getValue().size())));
        }
        if (groups.size() > 2) {
            List<String> rest = new ArrayList<>();
            for (int i = 2; i < groups.size(); i++) {
                Map.Entry<String, List<Box>> group = groups.get(i);
                rest.add(hedge(best(group.getValue()),
                        count(group.getKey(), group.getValue().size())));
            }
            sb.append(", with ").append(join(rest));
        }
        sb.append('.');

        List<String> mentioned = new ArrayList<>();
        for (Map.Entry<String, List<Box>> group : groups) {
            mentioned.add(group.getKey() + " x" + group.getValue().size());
        }
        return new Caption(sb.toString(), mentioned, dropped);
    }

    /**
     * Wraps a phrase in "what looks like" when the detector was not confident.
     * The alternative is a caption that reports a guess as an observation.
     */
    private static String hedge(Box box, String phrase) {
        return box.confidence() >= CONFIDENT ? phrase : "what looks like " + phrase;
    }

    private static double total(List<Box> group) {
        double sum = 0;
        for (Box box : group) { sum += box.salience(); }
        return sum;
    }

    private static Box best(List<Box> group) {
        Box winner = group.get(0);
        for (Box box : group) {
            if (box.salience() > winner.salience()) { winner = box; }
        }
        return winner;
    }

    private static String join(List<String> parts) {
        if (parts.size() == 1) { return parts.get(0); }
        return String.join(", ", parts.subList(0, parts.size() - 1))
                + " and " + parts.get(parts.size() - 1);
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private static String describe(Caption caption) {
        StringBuilder sb = new StringBuilder(caption.text());
        if (!caption.mentioned().isEmpty()) {
            sb.append("\n\n  described: ").append(String.join(", ", caption.mentioned()));
        }
        if (!caption.dropped().isEmpty()) {
            sb.append(String.format("%n  left out below %.2f confidence: %s",
                    MENTIONABLE, String.join(", ", caption.dropped())));
        }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("One detection per line: label confidence x y width height");
        while (true) {
            io.println();
            String first = io.ask("detections (blank for the sample, 'q' to quit):");
            if (first.equalsIgnoreCase("q")) { return; }
            StringBuilder raw = new StringBuilder();
            if (first.isEmpty()) {
                raw.append(SAMPLE);
                io.muted(SAMPLE);
            } else {
                raw.append(first).append('\n');
                while (true) {
                    String line = io.ask(">");
                    if (line.isEmpty()) { break; }
                    raw.append(line).append('\n');
                }
            }
            try {
                io.println();
                io.println(describe(caption(boxes(raw.toString()))));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!List.of("compute", "caption", "input").contains(action)) {
            return Json.error("Unknown action: " + action);
        }
        try {
            String raw = Json.str(body, "detections", "");
            if (raw.isBlank()) { raw = Json.str(body, "input", ""); }
            if (raw.isBlank()) { raw = SAMPLE; }
            Caption caption = caption(boxes(raw));
            return Json.ok("result", caption.text(), "detail", describe(caption));
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
