package com.randomjava.projects.spamemailclassifier;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Spam Classifier - multinomial naive Bayes, trained on whatever you give it.
 *
 * <p>Two details make this work rather than merely run.
 *
 * <p><b>Laplace smoothing.</b> A word seen only in spam has zero probability
 * under ham, and multiplying by zero makes the whole message impossible no
 * matter what else it contains. Adding one to every count removes the zero
 * without meaningfully disturbing words that do appear.
 *
 * <p><b>Log probabilities.</b> Multiplying a hundred probabilities each around
 * 0.001 underflows a double to zero within about forty words, and the classifier
 * silently starts returning the prior for everything. Summing logs instead is
 * arithmetically identical and cannot underflow.
 */
public final class SpamEmailClassifier implements Project {

    public static final Meta META = new Meta(51, "spam-email-classifier", "Spam Email Classifier", "AI and Machine Learning", Kind.TOOL,
            Difficulty.ADVANCED, "Score a message as spam or not with a naive Bayes model.",
            "", true);

    private final Map<String, Integer> spamCounts = new HashMap<>();
    private final Map<String, Integer> hamCounts = new HashMap<>();
    private final Set<String> vocabulary = new HashSet<>();
    private int spamMessages;
    private int hamMessages;
    private int spamWords;
    private int hamWords;

    public SpamEmailClassifier() { trainDefaults(); }

    @Override public Meta meta() { return META; }

    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9$!]+")) {
            if (!word.isBlank() && word.length() > 1) { out.add(word); }
        }
        return out;
    }

    public void train(String text, boolean spam) {
        List<String> words = tokens(text);
        if (words.isEmpty()) { throw new IllegalArgumentException("Nothing to learn from."); }
        if (spam) { spamMessages++; } else { hamMessages++; }
        for (String word : words) {
            vocabulary.add(word);
            if (spam) { spamCounts.merge(word, 1, Integer::sum); spamWords++; }
            else { hamCounts.merge(word, 1, Integer::sum); hamWords++; }
        }
    }

    /** A small starting corpus so the thing is usable before you train it. */
    private void trainDefaults() {
        String[] spam = {
            "WINNER! You have won a FREE prize claim now click here urgent",
            "Cheap meds online no prescription buy now limited offer discount",
            "Congratulations you are selected claim your $1000 gift card today",
            "Urgent business proposal transfer funds account million dollars",
            "Act now! Exclusive offer expires today click this link to claim free money",
            "Lowest rates guaranteed refinance now no credit check approved instantly"};
        String[] ham = {
            "Hi are we still on for lunch tomorrow at the usual place",
            "Please find attached the quarterly report for your review thanks",
            "The meeting has been moved to Thursday afternoon in room four",
            "Thanks for sending the draft I have added some comments in the margin",
            "Can you let me know if the deployment finished successfully last night",
            "Reminder that the team retrospective is on Friday morning as usual"};
        for (String s : spam) { train(s, true); }
        for (String h : ham) { train(h, false); }
    }

    public record Verdict(boolean spam, double confidence, double spamLog, double hamLog,
                          List<String> strongest) { }

    /**
     * Classifies a message. Works in log space throughout, which is what keeps
     * long messages from underflowing to zero.
     */
    public Verdict classify(String text) {
        List<String> words = tokens(text);
        if (words.isEmpty()) { throw new IllegalArgumentException("Type a message to classify."); }
        int total = spamMessages + hamMessages;
        double spamLog = Math.log((double) spamMessages / total);
        double hamLog = Math.log((double) hamMessages / total);

        int vocab = vocabulary.size();
        Map<String, Double> influence = new LinkedHashMap<>();
        for (String word : words) {
            // Laplace: +1 on top, +vocabulary size underneath.
            double pSpam = (spamCounts.getOrDefault(word, 0) + 1.0) / (spamWords + vocab);
            double pHam = (hamCounts.getOrDefault(word, 0) + 1.0) / (hamWords + vocab);
            spamLog += Math.log(pSpam);
            hamLog += Math.log(pHam);
            influence.merge(word, Math.log(pSpam) - Math.log(pHam), Double::sum);
        }

        List<String> strongest = new ArrayList<>();
        influence.entrySet().stream()
                .sorted((x, y) -> Double.compare(Math.abs(y.getValue()), Math.abs(x.getValue())))
                .limit(6)
                .forEach(e -> strongest.add(String.format("%s %s%.2f", e.getKey(),
                        e.getValue() > 0 ? "+" : "", e.getValue())));

        // Convert the log gap into a probability without ever leaving log space.
        double gap = spamLog - hamLog;
        double confidence = 1 / (1 + Math.exp(-Math.abs(gap)));
        return new Verdict(spamLog > hamLog, confidence, spamLog, hamLog, strongest);
    }

    public String corpus() {
        return spamMessages + " spam and " + hamMessages + " legitimate messages learned, "
                + vocabulary.size() + " distinct words.";
    }

    private String detail(Verdict v) {
        StringBuilder sb = new StringBuilder(String.format(
                "Log score: spam %.2f against legitimate %.2f%n"
                + "Confidence %.1f%%%n%n"
                + "Words pulling hardest (+ toward spam, - toward legitimate):",
                v.spamLog(), v.hamLog(), v.confidence() * 100));
        for (String word : v.strongest()) { sb.append("\n  ").append(word); }
        sb.append("\n\n").append(corpus());
        sb.append("\nScores are logs, not probabilities: multiplying a hundred small "
                + "probabilities underflows to zero, adding their logs does not.");
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted(corpus());
        while (true) {
            int choice = io.menu("Spam classifier", List.of(
                    "Classify a message", "Teach it a spam example", "Teach it a legitimate one"));
            if (choice < 0) { return; }
            try {
                String text = io.ask("message:");
                if (choice == 0) {
                    Verdict v = classify(text);
                    io.result(v.spam() ? "Spam" : "Legitimate",
                            String.format("%.1f%% confident", v.confidence() * 100));
                    io.println("  " + detail(v).replace("\n", "\n  "));
                } else {
                    train(text, choice == 1);
                    io.ok("Learned. " + corpus());
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "compute", "classify" -> {
                    Verdict v = classify(Json.str(body, "input", ""));
                    return Json.ok("result", (v.spam() ? "Spam" : "Legitimate")
                                    + String.format("  (%.1f%% confident)", v.confidence() * 100),
                            "detail", detail(v));
                }
                case "trainSpam", "trainHam" -> {
                    train(Json.str(body, "input", ""), action.equals("trainSpam"));
                    return Json.ok("result", "Learned", "detail", corpus());
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
