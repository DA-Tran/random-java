package com.randomjava.projects.socialpostscheduler;

import com.randomjava.lib.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * Social Post Scheduler - queues posts per platform and refuses to double-book.
 *
 * <p>The useful behaviour is the conflict check. Two posts to the same platform
 * minutes apart compete with each other and look automated, so a minimum gap is
 * enforced per platform, and the scheduler suggests the next free slot rather
 * than just rejecting the request.
 *
 * <p>Platforms are kept separate: posting to two networks at the same moment is
 * fine and often intended, so the gap only applies within one platform.
 */
public final class SocialPostScheduler implements Project {

    public static final Meta META = new Meta(98, "social-post-scheduler", "Social Post Scheduler", "Automation and Tools", Kind.LIST,
            Difficulty.BEGINNER, "Queue posts per platform and dispatch them at the right time.",
            "", true);

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Character limits, which are the other thing that silently breaks a post. */
    private static final Map<String, Integer> LIMITS = Map.of(
            "x", 280, "twitter", 280, "bluesky", 300, "mastodon", 500,
            "linkedin", 3000, "facebook", 63206, "instagram", 2200);

    public record Post(int id, String platform, String text, LocalDateTime when) { }

    private final List<Post> posts = new ArrayList<>();
    private int nextId = 1;
    private int minimumGapMinutes = 30;

    @Override public Meta meta() { return META; }

    public static LocalDateTime parseWhen(String text) {
        if (text == null || text.isBlank()) { return LocalDateTime.now().plusHours(1); }
        String clean = text.trim();
        try { return LocalDateTime.parse(clean, STAMP); }
        catch (DateTimeParseException ignored) { /* try the next shape */ }
        try { return LocalDateTime.parse(clean); }
        catch (DateTimeParseException ignored) { /* try relative */ }
        if (clean.matches("(?i)\\+?\\d+\\s*[hm].*")) {
            long amount = Long.parseLong(clean.replaceAll("[^0-9]", ""));
            return clean.toLowerCase(Locale.ROOT).contains("h")
                    ? LocalDateTime.now().plusHours(amount)
                    : LocalDateTime.now().plusMinutes(amount);
        }
        throw new IllegalArgumentException(
                "Write the time as 2026-03-05 14:30, or as +2h or +45m.");
    }

    /** The first post on this platform that sits too close to the given time. */
    public Optional<Post> conflict(String platform, LocalDateTime when, int ignoreId) {
        for (Post post : posts) {
            if (post.id() == ignoreId) { continue; }
            if (!post.platform().equalsIgnoreCase(platform)) { continue; }
            long gap = Math.abs(Duration.between(post.when(), when).toMinutes());
            if (gap < minimumGapMinutes) { return Optional.of(post); }
        }
        return Optional.empty();
    }

    /** The earliest time at or after {@code from} with no conflict. */
    public LocalDateTime nextFreeSlot(String platform, LocalDateTime from) {
        LocalDateTime candidate = from;
        for (int attempt = 0; attempt < 500; attempt++) {
            Optional<Post> clash = conflict(platform, candidate, -1);
            if (clash.isEmpty()) { return candidate; }
            candidate = clash.get().when().plusMinutes(minimumGapMinutes);
        }
        return candidate;
    }

    public Post schedule(String platform, String text, String when) {
        if (platform == null || platform.isBlank()) {
            throw new IllegalArgumentException("Say which platform.");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("The post has no text.");
        }
        String key = platform.trim().toLowerCase(Locale.ROOT);
        Integer limit = LIMITS.get(key);
        if (limit != null && text.length() > limit) {
            throw new IllegalArgumentException(text.length() + " characters is over "
                    + platform + "'s limit of " + limit + ".");
        }
        LocalDateTime at = parseWhen(when);
        Optional<Post> clash = conflict(key, at, -1);
        if (clash.isPresent()) {
            throw new IllegalArgumentException(String.format(
                    "That is within %d minutes of the post at %s. The next free slot is %s.",
                    minimumGapMinutes, clash.get().when().format(STAMP),
                    nextFreeSlot(key, at).format(STAMP)));
        }
        Post post = new Post(nextId++, key, text.trim(), at);
        posts.add(post);
        posts.sort(Comparator.comparing(Post::when));
        return post;
    }

    public boolean remove(int id) { return posts.removeIf(p -> p.id() == id); }
    public void clear() { posts.clear(); }

    public void setGap(int minutes) {
        if (minutes < 0 || minutes > 1440) {
            throw new IllegalArgumentException("The gap must be between 0 and 1440 minutes.");
        }
        minimumGapMinutes = minutes;
    }

    private String summary() {
        if (posts.isEmpty()) { return "Nothing queued."; }
        Map<String, Integer> perPlatform = new TreeMap<>();
        for (Post post : posts) { perPlatform.merge(post.platform(), 1, Integer::sum); }
        StringBuilder sb = new StringBuilder(posts.size() + " queued, minimum "
                + minimumGapMinutes + " minutes apart on any one platform.");
        for (Map.Entry<String, Integer> e : perPlatform.entrySet()) {
            sb.append("\n  ").append(e.getKey()).append(": ").append(e.getValue());
        }
        Post next = posts.get(0);
        sb.append("\nNext out: ").append(next.platform()).append(" at ")
          .append(next.when().format(STAMP));
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Post post : posts) {
            out.add(Json.map("id", post.id(),
                    "label", post.when().format(STAMP) + "  [" + post.platform() + "]  "
                            + (post.text().length() > 60
                                    ? post.text().substring(0, 60) + "..." : post.text()),
                    "meta", post.text().length() + " characters",
                    "done", post.when().isBefore(LocalDateTime.now())));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            io.muted(summary().replace("\n", "\n  "));
            int choice = io.menu("Scheduler", List.of(
                    "Queue a post", "Remove one", "Set the minimum gap", "Clear"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> { schedule(io.ask("platform:"), io.ask("text:"),
                                    io.ask("when (yyyy-MM-dd HH:mm or +2h):")); io.ok("Queued."); }
                    case 1 -> io.println(remove(io.askInt("id:", 1, Integer.MAX_VALUE, 1))
                            ? "  removed" : "  no such post");
                    case 2 -> setGap(io.askInt("minutes:", 0, 1440, minimumGapMinutes));
                    default -> { clear(); io.ok("Cleared."); }
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    String[] bits = Json.str(body, "meta", "").trim().split("\\s+", 2);
                    schedule(bits.length > 0 && !bits[0].isEmpty() ? bits[0] : "x",
                            Json.str(body, "label", ""),
                            bits.length > 1 ? bits[1] : "");
                    return Json.ok("items", snapshot(), "message", "Queued", "detail", summary());
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
