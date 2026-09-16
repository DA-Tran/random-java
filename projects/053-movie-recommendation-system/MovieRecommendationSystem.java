package com.randomjava.projects.movierecommendationsystem;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Recommender - user-based collaborative filtering with cosine similarity.
 *
 * <p>Ratings are <b>mean-centred</b> before anything is compared, and that is
 * the step most naive implementations skip. People use the scale differently:
 * one person's 3 is another's 5, and raw cosine similarity mostly measures how
 * generous someone is rather than what they like. Subtracting each person's own
 * average first makes the comparison about relative preference.
 *
 * <p>A prediction is the weighted deviation of similar users, added back onto
 * the target's own mean, so a harsh rater gets harsh predictions.
 */
public final class MovieRecommendationSystem implements Project {

    public static final Meta META = new Meta(53, "movie-recommendation-system", "Movie Recommendation System", "AI and Machine Learning", Kind.TOOL,
            Difficulty.ADVANCED, "Recommend titles from ratings with collaborative filtering.",
            "", true);

    /** user -> (film -> rating) */
    private final Map<String, Map<String, Double>> ratings = new LinkedHashMap<>();

    public MovieRecommendationSystem() { seed(); }

    @Override public Meta meta() { return META; }

    private void seed() {
        rate("ana", "Inception", 5); rate("ana", "Arrival", 5); rate("ana", "Heat", 4);
        rate("ana", "Paddington", 2);
        rate("ben", "Inception", 4); rate("ben", "Arrival", 5); rate("ben", "Heat", 3);
        rate("ben", "Amelie", 5);
        rate("cal", "Inception", 2); rate("cal", "Paddington", 5); rate("cal", "Amelie", 4);
        rate("cal", "Heat", 1);
        rate("dee", "Arrival", 4); rate("dee", "Amelie", 5); rate("dee", "Paddington", 4);
    }

    public void rate(String user, String film, double score) {
        if (user == null || user.isBlank() || film == null || film.isBlank()) {
            throw new IllegalArgumentException("Give a person and a film.");
        }
        if (score < 0 || score > 5) { throw new IllegalArgumentException("Ratings run 0 to 5."); }
        ratings.computeIfAbsent(user.trim().toLowerCase(Locale.ROOT), k -> new LinkedHashMap<>())
                .put(film.trim(), score);
    }

    public Set<String> users() { return ratings.keySet(); }

    public Set<String> films() {
        Set<String> out = new TreeSet<>();
        for (Map<String, Double> row : ratings.values()) { out.addAll(row.keySet()); }
        return out;
    }

    public double mean(String user) {
        Map<String, Double> row = ratings.get(user);
        if (row == null || row.isEmpty()) { return 0; }
        return row.values().stream().mapToDouble(Double::doubleValue).sum() / row.size();
    }

    /**
     * Cosine similarity on mean-centred ratings, over films both have seen.
     * Returns 0 when they share fewer than two films, since one shared film
     * gives a similarity of exactly 1 or -1 and means nothing.
     */
    public double similarity(String a, String b) {
        Map<String, Double> first = ratings.getOrDefault(a, Map.of());
        Map<String, Double> second = ratings.getOrDefault(b, Map.of());
        List<String> shared = new ArrayList<>();
        for (String film : first.keySet()) { if (second.containsKey(film)) { shared.add(film); } }
        if (shared.size() < 2) { return 0; }

        double meanA = mean(a);
        double meanB = mean(b);
        double dot = 0, normA = 0, normB = 0;
        for (String film : shared) {
            double x = first.get(film) - meanA;
            double y = second.get(film) - meanB;
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }
        if (normA == 0 || normB == 0) { return 0; }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** Predicted rating: the target's mean plus the weighted deviation of neighbours. */
    public OptionalDouble predict(String user, String film) {
        double weighted = 0;
        double weightSum = 0;
        for (String other : ratings.keySet()) {
            if (other.equals(user)) { continue; }
            Double theirs = ratings.get(other).get(film);
            if (theirs == null) { continue; }
            double sim = similarity(user, other);
            if (sim <= 0) { continue; }
            weighted += sim * (theirs - mean(other));
            weightSum += sim;
        }
        if (weightSum == 0) { return OptionalDouble.empty(); }
        return OptionalDouble.of(Math.max(0, Math.min(5, mean(user) + weighted / weightSum)));
    }

    public record Suggestion(String film, double predicted) { }

    public List<Suggestion> recommend(String user, int limit) {
        String key = user.trim().toLowerCase(Locale.ROOT);
        if (!ratings.containsKey(key)) {
            throw new IllegalArgumentException("Nobody called '" + user + "' has rated anything. "
                    + "Known: " + String.join(", ", users()));
        }
        List<Suggestion> out = new ArrayList<>();
        for (String film : films()) {
            if (ratings.get(key).containsKey(film)) { continue; }
            predict(key, film).ifPresent(value -> out.add(new Suggestion(film, value)));
        }
        out.sort((x, y) -> Double.compare(y.predicted(), x.predicted()));
        return out.subList(0, Math.min(limit, out.size()));
    }

    private String detail(String user) {
        String key = user.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(String.format(
                "%s rates %.2f on average, over %d films.%n%nHow alike others are "
                + "(on mean-centred ratings, so generosity does not count as taste):",
                key, mean(key), ratings.get(key).size()));
        for (String other : users()) {
            if (other.equals(key)) { continue; }
            sb.append(String.format("%n  %-6s %+.3f", other, similarity(key, other)));
        }
        sb.append("\n\nSuggestions:");
        List<Suggestion> picks = recommend(key, 5);
        if (picks.isEmpty()) {
            sb.append("\n  nothing left to suggest from this data");
        }
        for (Suggestion s : picks) {
            sb.append(String.format("%n  %-12s predicted %.2f", s.film(), s.predicted()));
        }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Known viewers: " + String.join(", ", users()));
        while (true) {
            int choice = io.menu("Recommender", List.of("Recommend for someone", "Add a rating"));
            if (choice < 0) { return; }
            try {
                if (choice == 0) {
                    String user = io.ask("who:", "ana");
                    List<Suggestion> picks = recommend(user, 5);
                    io.result(picks.isEmpty() ? "nothing to suggest" : picks.get(0).film(),
                            picks.isEmpty() ? "" : String.format("predicted %.2f", picks.get(0).predicted()));
                    io.println("  " + detail(user).replace("\n", "\n  "));
                } else {
                    rate(io.ask("who:"), io.ask("film:"), io.askDouble("rating 0-5:", 4));
                    io.ok("Recorded.");
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "compute", "recommend" -> {
                    String user = Json.str(body, "input", "").trim();
                    if (user.isEmpty()) {
                        return Json.error("Whose recommendations? Known: " + String.join(", ", users()));
                    }
                    List<Suggestion> picks = recommend(user, 5);
                    List<Double> bars = new ArrayList<>();
                    for (Suggestion s : picks) { bars.add(s.predicted()); }
                    return Json.ok("result", picks.isEmpty() ? "Nothing left to suggest"
                                    : picks.get(0).film() + String.format("  (predicted %.2f)",
                                            picks.get(0).predicted()),
                            "detail", detail(user), "bars", bars);
                }
                case "rate" -> {
                    rate(Json.str(body, "user", ""), Json.str(body, "film", ""),
                            Json.num(body, "score", 4));
                    return Json.ok("result", "Recorded", "detail", users().size() + " viewers, "
                            + films().size() + " films.");
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
