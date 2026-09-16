package com.randomjava.projects.randomquotegenerator;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Random Quote Generator - a quote at random, optionally filtered by topic or
 * author.
 *
 * <p>Picking purely at random repeats itself more than people expect, so the
 * last few picks are remembered and avoided. That is the difference between
 * "random" and "feels random".
 */
public final class RandomQuoteGenerator implements Project {

    public static final Meta META = new Meta(6, "random-quote-generator", "Random Quote Generator", "Beginner Friendly", Kind.TOOL,
            Difficulty.BEGINNER, "Pull a random quote, optionally filtered by topic or author.",
            "", true);

    public record Quote(String text, String author, String topic) { }

    private static final List<Quote> QUOTES = List.of(
        new Quote("The only way to do great work is to love what you do.", "Steve Jobs", "work"),
        new Quote("Simplicity is the soul of efficiency.", "Austin Freeman", "design"),
        new Quote("Premature optimisation is the root of all evil.", "Donald Knuth", "code"),
        new Quote("Talk is cheap. Show me the code.", "Linus Torvalds", "code"),
        new Quote("Programs must be written for people to read.", "Harold Abelson", "code"),
        new Quote("The best time to plant a tree was twenty years ago. The second best is now.",
                  "Chinese proverb", "time"),
        new Quote("It always seems impossible until it is done.", "Nelson Mandela", "persistence"),
        new Quote("Perfection is achieved when there is nothing left to take away.",
                  "Antoine de Saint-Exupery", "design"),
        new Quote("A person who never made a mistake never tried anything new.",
                  "Albert Einstein", "persistence"),
        new Quote("Make it work, make it right, make it fast.", "Kent Beck", "code"),
        new Quote("The function of good software is to make the complex appear simple.",
                  "Grady Booch", "design"),
        new Quote("Time is what we want most and what we use worst.", "William Penn", "time"),
        new Quote("Do not wait to strike till the iron is hot; make it hot by striking.",
                  "William Butler Yeats", "work"),
        new Quote("Fall seven times, stand up eight.", "Japanese proverb", "persistence"),
        new Quote("Any fool can write code a computer understands. Good programmers write "
                  + "code humans understand.", "Martin Fowler", "code"));

    private final Random random = new Random();
    private final Deque<Integer> recent = new ArrayDeque<>();

    @Override public Meta meta() { return META; }

    public List<String> topics() {
        Set<String> out = new TreeSet<>();
        for (Quote q : QUOTES) { out.add(q.topic()); }
        return new ArrayList<>(out);
    }

    public List<Quote> matching(String filter) {
        if (filter == null || filter.isBlank()) { return QUOTES; }
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        List<Quote> out = new ArrayList<>();
        for (Quote q : QUOTES) {
            if (q.topic().toLowerCase(Locale.ROOT).contains(needle)
                    || q.author().toLowerCase(Locale.ROOT).contains(needle)
                    || q.text().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(q);
            }
        }
        return out;
    }

    /** Picks at random but avoids anything shown in the last few calls. */
    public Quote pick(String filter) {
        List<Quote> pool = matching(filter);
        if (pool.isEmpty()) {
            throw new IllegalArgumentException("No quote matches '" + filter
                    + "'. Topics: " + String.join(", ", topics()));
        }
        int memory = Math.min(recent.size(), Math.max(0, pool.size() - 1));
        for (int attempt = 0; attempt < 24; attempt++) {
            int index = random.nextInt(pool.size());
            int id = QUOTES.indexOf(pool.get(index));
            if (memory == 0 || !recent.contains(id)) {
                recent.addLast(id);
                while (recent.size() > Math.min(5, QUOTES.size() - 1)) { recent.removeFirst(); }
                return pool.get(index);
            }
        }
        return pool.get(random.nextInt(pool.size()));
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Topics: " + String.join(", ", topics()) + ". Blank for any, blank line twice to stop.");
        while (true) {
            String filter = io.ask("topic or author:");
            try {
                Quote q = pick(filter);
                io.result("\"" + q.text() + "\"", "- " + q.author());
                io.muted("topic: " + q.topic());
            } catch (RuntimeException e) { io.error(e.getMessage()); }
            if (!io.askYesNo("Another?", true)) { return; }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            Quote q = pick(Json.str(body, "input", ""));
            return Json.ok("result", "“" + q.text() + "”",
                    "detail", "- " + q.author() + "   (" + q.topic() + ")"
                            + "\nTopics available: " + String.join(", ", topics()));
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
