package com.randomjava.projects.faqchatbot;

import com.randomjava.lib.*;
import java.util.*;

/**
 * FAQ Chatbot - answers a question by retrieving the closest known entry.
 *
 * <p>The matching is <b>TF-IDF weighted cosine similarity</b>, and both halves
 * of that name are doing real work.
 *
 * <p><b>IDF</b> is what stops the bot being confidently wrong. Counting shared
 * words treats every word as equally informative, so in a help desk where every
 * single entry mentions "account", the word "account" appears to be strong
 * evidence for all of them. Inverse document frequency weights a term by how
 * rare it is across the FAQ: a word in every entry gets a weight of exactly
 * zero and is ignored, while a word in one entry carries nearly all the signal.
 * A question made only of words common to everything therefore scores zero
 * against everything, which is the correct answer - it genuinely does not say
 * which entry is wanted.
 *
 * <p><b>Cosine</b> divides by the length of each entry, so a long entry that
 * mentions the topic in passing does not outrank a short entry that is about
 * it. Without that, the longest entry wins most questions simply by having more
 * words available to match.
 *
 * <p>Two separate checks decide whether to answer at all. Below
 * {@link #threshold} nothing matched well enough. Within {@link #margin} of the
 * runner-up, something matched well but so did its neighbour, and the question
 * does not choose between them - that one is reported as a tie rather than
 * resolved by list order. A retrieval bot that always answers is worse than
 * useless, because the caller cannot tell a real hit from the nearest of
 * several bad ones.
 *
 * <p><b>What this cannot do:</b> synonyms. "Delete my account" and "close my
 * account" mean the same thing and share not one significant word, so no amount
 * of weighting will connect them - bag-of-words models compare spellings, not
 * meanings. That is the specific gap word embeddings exist to fill. Here the
 * failure is at least an honest one: the tie check notices that the question
 * points nowhere in particular and says so, instead of picking whichever near
 * miss happened to sort first.
 */
public final class FaqChatbot implements Project {

    public static final Meta META = new Meta(55, "faq-chatbot", "FAQ Chatbot", "AI and Machine Learning", Kind.LIST,
            Difficulty.ADVANCED, "Answer questions by matching them to the closest known FAQ entry.",
            "", true);

    private static final Set<String> STOP = Set.of(
            "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at", "for",
            "with", "is", "are", "was", "were", "be", "am", "do", "does", "did", "can",
            "could", "would", "should", "will", "my", "me", "i", "you", "your", "it",
            "its", "this", "that", "how", "what", "when", "where", "why", "who", "if",
            "from", "by", "as", "so", "there", "here", "get", "got", "have", "has");

    /** One question and its answer. */
    public record Entry(int id, String question, String answer) { }

    /** Why the bot said what it said. */
    public enum Outcome {
        /** One entry matched well and beat the rest clearly. */
        ANSWERED,
        /** Nothing came close enough to be worth saying. */
        LOW_CONFIDENCE,
        /** Two or more entries matched equally well, so the question does not pick one. */
        AMBIGUOUS,
        /** There is nothing to search. */
        EMPTY
    }

    /** What the bot decided, including why. */
    public record Reply(Outcome outcome, Entry match, double confidence,
                        String text, List<String> alsoConsidered) {
        public boolean answered() { return outcome == Outcome.ANSWERED; }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int nextId = 1;

    /**
     * Below this the bot declines. 0.20 is low in absolute terms because IDF
     * weighting concentrates the score into a few rare words, so a genuine hit
     * on one distinctive term is worth more than it looks.
     */
    private double threshold = 0.20;

    /**
     * How close the runner-up may get before the bot admits it cannot tell.
     *
     * <p>A score threshold alone is not enough, and the gap is not a small one.
     * "Delete my account for good" shares exactly one word, "account", with
     * both the reset-password entry and the close-account entry. Those two
     * entries have the same number of terms carrying the same weights, so they
     * score <em>identically</em>, and picking the higher one means picking
     * whichever happens to sit earlier in the list. The score looks respectable
     * the whole time. Ranking cannot detect that; comparing the top two can.
     *
     * <p>So a near-tie is reported as a near-tie. Answering a coin flip in a
     * confident voice is the worst thing a retrieval bot can do, because the
     * caller has no way to tell it apart from a real hit.
     */
    private double margin = 0.90;

    public FaqChatbot() {
        // A starter desk, deliberately all about one product so that the shared
        // vocabulary ("account", "password") has to be discounted to work.
        teach("How do I reset my account password?",
                "Open Settings, choose Security, then Reset password. A link is emailed to you.");
        teach("How do I close my account permanently?",
                "Settings, then Close account. Closure is final after 30 days.");
        teach("How do I upgrade my account to the paid plan?",
                "Settings, then Billing, then Choose plan. The change applies immediately.");
        teach("How do I get a refund for a charge?",
                "Refunds are available within 14 days. Contact billing support with the receipt.");
        teach("Why is the app running slowly?",
                "Clear the cache under Settings, Storage. If it persists, reinstall the app.");
    }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Text handling
    // ------------------------------------------------------------------

    /** Significant terms: lower-cased, punctuation dropped, stop words removed, stemmed. */
    public static List<String> terms(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) { return out; }
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9']+")) {
            String clean = word.replace("'", "");
            if (clean.length() > 1 && !STOP.contains(clean)) { out.add(stem(clean)); }
        }
        return out;
    }

    /**
     * Light suffix stripping so "refund" and "refunds", "charge" and "charges"
     * count as the same term. Anything more aggressive starts merging words
     * that are genuinely different, which on a small FAQ is the worse error.
     */
    public static String stem(String word) {
        if (word.length() <= 4) { return word; }
        for (String suffix : new String[]{"ing", "ed", "es", "s"}) {
            if (word.endsWith(suffix) && word.length() - suffix.length() >= 4) {
                return word.substring(0, word.length() - suffix.length());
            }
        }
        return word;
    }

    /** Term counts for one piece of text. */
    private static Map<String, Integer> counts(String text) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String term : terms(text)) { out.merge(term, 1, Integer::sum); }
        return out;
    }

    /**
     * Inverse document frequency across the current FAQ.
     *
     * <p>Plain {@code log(N / df)} on purpose, with no smoothing added to the
     * numerator, so a term present in every entry lands on {@code log(1) = 0}
     * and drops out entirely. Smoothed variants leave it slightly positive,
     * which is exactly the behaviour being avoided here.
     */
    public Map<String, Double> idf() {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Entry entry : entries) {
            for (String term : new HashSet<>(terms(entry.question()))) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        Map<String, Double> out = new HashMap<>();
        int total = entries.size();
        for (Map.Entry<String, Integer> e : documentFrequency.entrySet()) {
            out.put(e.getKey(), Math.log((double) total / e.getValue()));
        }
        return out;
    }

    /** TF-IDF vector: term frequency times rarity. */
    private static Map<String, Double> vector(String text, Map<String, Double> idf) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : counts(text).entrySet()) {
            double weight = idf.getOrDefault(e.getKey(), 0.0);
            if (weight > 0) { out.put(e.getKey(), e.getValue() * weight); }
        }
        return out;
    }

    /** Cosine of the angle between two sparse vectors: 0 apart, 1 aligned. */
    public static double cosine(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            Double other = b.get(e.getKey());
            if (other != null) { dot += e.getValue() * other; }
        }
        if (dot == 0) { return 0; }
        double normA = 0;
        for (double v : a.values()) { normA += v * v; }
        double normB = 0;
        for (double v : b.values()) { normB += v * v; }
        if (normA == 0 || normB == 0) { return 0; }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ------------------------------------------------------------------
    // Asking and teaching
    // ------------------------------------------------------------------

    /** Scores every entry against the question, best first. */
    public List<Map.Entry<Entry, Double>> rank(String question) {
        Map<String, Double> weights = idf();
        Map<String, Double> query = vector(question, weights);
        List<Map.Entry<Entry, Double>> scored = new ArrayList<>();
        for (Entry entry : entries) {
            scored.add(Map.entry(entry, cosine(query, vector(entry.question(), weights))));
        }
        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return scored;
    }

    public Reply ask(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Ask something.");
        }
        if (entries.isEmpty()) {
            return new Reply(Outcome.EMPTY, null, 0, "There is nothing in the FAQ yet.", List.of());
        }
        List<Map.Entry<Entry, Double>> scored = rank(question);
        Map.Entry<Entry, Double> best = scored.get(0);

        if (best.getValue() < threshold) {
            String reason = vector(question, idf()).isEmpty()
                    ? "Every word in that question appears in every entry, or in none of them, "
                      + "so it does not point at any one of them."
                    : String.format("The closest entry only scored %.2f, under the %.2f I need.",
                            best.getValue(), threshold);
            return new Reply(Outcome.LOW_CONFIDENCE, null, best.getValue(),
                    "I do not know that one. " + reason, runnersUp(scored));
        }

        // Scored well enough, but is it a clear winner or a tie?
        if (scored.size() > 1) {
            double runnerUp = scored.get(1).getValue();
            if (runnerUp >= margin * best.getValue()) {
                List<Entry> tied = new ArrayList<>();
                for (Map.Entry<Entry, Double> candidate : scored) {
                    if (candidate.getValue() >= margin * best.getValue()) {
                        tied.add(candidate.getKey());
                    }
                }
                StringBuilder sb = new StringBuilder(
                        "That could be any of these, and nothing in the question separates them:");
                for (Entry entry : tied) { sb.append("\n    - ").append(entry.question()); }
                sb.append("\n  Ask again with a word that only one of them would use.");
                return new Reply(Outcome.AMBIGUOUS, null, best.getValue(), sb.toString(), List.of());
            }
        }
        return new Reply(Outcome.ANSWERED, best.getKey(), best.getValue(),
                best.getKey().answer(), runnersUp(scored));
    }

    private static List<String> runnersUp(List<Map.Entry<Entry, Double>> scored) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i < scored.size() && i < 3; i++) {
            if (scored.get(i).getValue() > 0) {
                out.add(String.format("%s (%.2f)",
                        scored.get(i).getKey().question(), scored.get(i).getValue()));
            }
        }
        return out;
    }

    public Entry teach(String question, String answer) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("An entry needs a question.");
        }
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("An entry needs an answer.");
        }
        Entry entry = new Entry(nextId++, question.trim(), answer.trim());
        entries.add(entry);
        return entry;
    }

    public List<Entry> entries() { return List.copyOf(entries); }
    public boolean remove(int id) { return entries.removeIf(e -> e.id() == id); }
    public void clear() { entries.clear(); }
    public double threshold() { return threshold; }
    public double margin() { return margin; }

    public void setThreshold(double value) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException("The threshold must be between 0 and 1.");
        }
        threshold = value;
    }

    public void setMargin(double value) {
        if (value <= 0 || value > 1) {
            throw new IllegalArgumentException("The margin must be above 0 and at most 1.");
        }
        margin = value;
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private String describe(Reply reply) {
        StringBuilder sb = new StringBuilder(reply.text());
        if (reply.answered()) {
            sb.append(String.format("%n%n  matched: %s (confidence %.2f)",
                    reply.match().question(), reply.confidence()));
        }
        if (!reply.alsoConsidered().isEmpty()) {
            sb.append("\n  also considered: ").append(String.join("; ", reply.alsoConsidered()));
        }
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Entry entry : entries) {
            out.add(Json.map("id", entry.id(), "label", entry.question(),
                    "meta", entry.answer(), "done", false));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Leave the answer blank to ask a question. Fill it in to teach a new one.");
        while (true) {
            io.println();
            int choice = io.menu("FAQ Chatbot (" + entries.size() + " entries)", List.of(
                    "Ask a question", "Teach a new entry", "Show entries", "Set threshold", "Clear"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> io.println(describe(ask(io.ask("question:"))));
                    case 1 -> {
                        teach(io.ask("question:"), io.ask("answer:"));
                        io.ok("Learned it.");
                    }
                    case 2 -> {
                        for (Entry entry : entries) {
                            io.println("  [" + entry.id() + "] " + entry.question());
                            io.muted("       " + entry.answer());
                        }
                    }
                    case 3 -> setThreshold(io.askDouble("confidence needed 0 to 1:", threshold));
                    default -> { clear(); io.ok("Cleared."); }
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    String question = Json.str(body, "label", "");
                    String answer = Json.str(body, "meta", "");
                    // An answer means you are teaching; no answer means you are asking.
                    if (answer.isBlank()) {
                        Reply reply = ask(question);
                        String note = switch (reply.outcome()) {
                            case ANSWERED -> String.format("Answered (%.2f)", reply.confidence());
                            case AMBIGUOUS -> "Several entries match equally well";
                            case LOW_CONFIDENCE -> "No confident match";
                            case EMPTY -> "The FAQ is empty";
                        };
                        return Json.ok("items", snapshot(), "detail", describe(reply),
                                "message", note);
                    }
                    teach(question, answer);
                    return Json.ok("items", snapshot(), "message", "Learned it",
                            "detail", entries.size() + " entries. Ask by leaving the answer blank.");
                }
                case "remove" -> {
                    remove(Json.integer(body, "id", -1));
                    return Json.ok("items", snapshot(), "message", "Removed",
                            "detail", entries.size() + " entries.");
                }
                case "clear" -> {
                    clear();
                    return Json.ok("items", snapshot(), "message", "Cleared",
                            "detail", "The FAQ is empty. Teach it something.");
                }
                case "list", "toggle" -> {
                    return Json.ok("items", snapshot(), "detail", entries.size()
                            + " entries. Type a question and leave the answer blank to ask it.");
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
