package com.randomjava.projects.sentimentanalysistool;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Sentiment Analysis - scores text with a weighted lexicon.
 *
 * <p>A plain word count gets "not good" exactly backwards, so two things are
 * handled beyond looking words up: a negator within three words flips the
 * following term's sign, and an intensifier multiplies it. That is a large part
 * of the gap between a toy and something usable, and it costs very little.
 */
public final class SentimentAnalysisTool implements Project {

    public static final Meta META = new Meta(54, "sentiment-analysis-tool", "Sentiment Analysis Tool", "AI and Machine Learning", Kind.TOOL,
            Difficulty.ADVANCED, "Rate text as positive, negative or neutral with a scored lexicon.",
            "", true);

    private static final Map<String, Double> LEXICON = new HashMap<>();
    private static final Set<String> NEGATORS = Set.of(
            "not", "no", "never", "none", "cannot", "cant", "wont", "isnt", "arent",
            "wasnt", "didnt", "doesnt", "dont", "hardly", "barely", "without");
    private static final Map<String, Double> INTENSIFIERS = Map.of(
            "very", 1.6, "really", 1.5, "extremely", 1.9, "incredibly", 1.8,
            "totally", 1.5, "absolutely", 1.8, "slightly", 0.5, "somewhat", 0.6,
            "quite", 1.3, "so", 1.4);

    private static void word(double score, String... words) {
        for (String w : words) { LEXICON.put(w, score); }
    }

    static {
        word(3.0, "excellent", "outstanding", "superb", "brilliant", "perfect", "wonderful");
        word(2.0, "great", "love", "loved", "fantastic", "amazing", "delighted", "beautiful");
        word(1.0, "good", "nice", "happy", "like", "liked", "pleased", "fine", "enjoy", "works");
        word(0.5, "okay", "ok", "decent", "fair", "acceptable");
        word(-0.5, "meh", "bland", "dull", "mediocre");
        word(-1.0, "bad", "poor", "sad", "dislike", "slow", "boring", "annoying", "broken");
        word(-2.0, "terrible", "awful", "hate", "hated", "horrible", "useless", "rubbish");
        word(-3.0, "appalling", "disgusting", "atrocious", "unusable", "catastrophic");
    }

    public record Score(double total, String verdict, int hits, List<String> notes) { }

    @Override public Meta meta() { return META; }

    public Score analyse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Type some text to score.");
        }
        String[] words = text.toLowerCase(Locale.ROOT).split("[^a-z']+");
        double total = 0;
        int hits = 0;
        List<String> notes = new ArrayList<>();

        for (int i = 0; i < words.length; i++) {
            String word = words[i].replace("'", "");
            Double base = LEXICON.get(word);
            if (base == null) { continue; }
            double value = base;
            String why = word + " (" + fmt(base) + ")";

            // Look back a little for a negator or an intensifier.
            for (int back = 1; back <= 3 && i - back >= 0; back++) {
                String prior = words[i - back].replace("'", "");
                if (NEGATORS.contains(prior)) {
                    value = -value * 0.8;
                    why += " flipped by '" + prior + "'";
                    break;
                }
                Double boost = INTENSIFIERS.get(prior);
                if (boost != null) {
                    value *= boost;
                    why += " scaled by '" + prior + "'";
                    break;
                }
            }
            // An exclamation mark anywhere strengthens the whole thing slightly.
            total += value;
            hits++;
            notes.add(why + " -> " + fmt(value));
        }
        if (text.contains("!")) { total *= 1.15; }

        String verdict = total > 1.0 ? "positive" : total < -1.0 ? "negative" : "neutral";
        if (hits == 0) { verdict = "neutral"; }
        return new Score(total, verdict, hits, notes);
    }

    private static String fmt(double v) { return String.format("%+.2f", v); }

    private String detail(Score score) {
        StringBuilder sb = new StringBuilder(String.format("Score %+.2f from %d scored word%s.",
                score.total(), score.hits(), score.hits() == 1 ? "" : "s"));
        if (score.hits() == 0) {
            return sb.append("\nNothing in the lexicon appeared, so this reads as neutral.").toString();
        }
        for (String note : score.notes()) { sb.append("\n  ").append(note); }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Type a sentence. Blank line to finish.");
        while (true) {
            String input = io.ask("text:");
            if (input.isEmpty()) { return; }
            try {
                Score score = analyse(input);
                io.result(score.verdict(), String.format("%+.2f", score.total()));
                io.println("  " + detail(score).replace("\n", "\n  "));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            Score score = analyse(Json.str(body, "input", ""));
            return Json.ok("result", score.verdict() + String.format("  (%+.2f)", score.total()),
                    "detail", detail(score));
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
