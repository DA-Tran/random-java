package com.randomjava.projects.urlshortener;

import com.randomjava.lib.*;
import java.util.*;

/**
 * URL Shortener - mints short codes and counts the redirects.
 *
 * <p>Codes are base62 encodings of a counter, which guarantees no collisions
 * without needing to check for them: every id maps to exactly one code and back.
 * Shortening the same URL twice returns the same code rather than minting a
 * second one, so the table does not fill with duplicates.
 */
public final class UrlShortener implements Project {

    public static final Meta META = new Meta(33, "url-shortener", "URL Shortener", "Web Development", Kind.LIST,
            Difficulty.INTERMEDIATE, "Mint short codes for long URLs and count the redirects.",
            "", true);

    private static final String ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final Map<String, String> byCode = new LinkedHashMap<>();
    private final Map<String, String> byUrl = new LinkedHashMap<>();
    private final Map<String, Integer> hits = new LinkedHashMap<>();
    private long counter = 1;

    @Override public Meta meta() { return META; }

    /** Base62 of the id, so codes never collide and stay short. */
    public static String encode(long id) {
        if (id <= 0) { return String.valueOf(ALPHABET.charAt(0)); }
        StringBuilder sb = new StringBuilder();
        long value = id;
        while (value > 0) {
            sb.append(ALPHABET.charAt((int) (value % ALPHABET.length())));
            value /= ALPHABET.length();
        }
        return sb.reverse().toString();
    }

    public String shorten(String url) {
        String clean = url == null ? "" : url.trim();
        if (clean.isEmpty()) { throw new IllegalArgumentException("Give a URL to shorten."); }
        if (!clean.matches("(?i)^https?://\\S+\\.\\S+.*$")) {
            throw new IllegalArgumentException(
                    "That does not look like a URL. It should start http:// or https://");
        }
        String existing = byUrl.get(clean);
        if (existing != null) { return existing; }
        String code = encode(counter++);
        byCode.put(code, clean);
        byUrl.put(clean, code);
        hits.put(code, 0);
        return code;
    }

    /** Follows a code, counting the visit. */
    public String resolve(String code) {
        String url = byCode.get(code == null ? "" : code.trim());
        if (url == null) { throw new IllegalArgumentException("No link with the code '" + code + "'."); }
        hits.merge(code.trim(), 1, Integer::sum);
        return url;
    }

    public boolean remove(String code) {
        String url = byCode.remove(code);
        if (url == null) { return false; }
        byUrl.remove(url);
        hits.remove(code);
        return true;
    }

    private String summary() {
        int total = hits.values().stream().mapToInt(Integer::intValue).sum();
        return byCode.size() + " links, " + total + " redirect" + (total == 1 ? "" : "s") + " served.";
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, String> e : byCode.entrySet()) {
            out.add(Json.map("id", e.getKey(), "label", "/" + e.getKey() + "   " + e.getValue(),
                    "meta", hits.get(e.getKey()) + " hits", "done", false));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            if (!byCode.isEmpty()) {
                List<List<String>> rows = new ArrayList<>();
                for (Map.Entry<String, String> e : byCode.entrySet()) {
                    rows.add(List.of(e.getKey(), String.valueOf(hits.get(e.getKey())), e.getValue()));
                }
                io.table(List.of("code", "hits", "url"), rows);
            }
            io.muted(summary());
            int choice = io.menu("URL shortener", List.of("Shorten a URL", "Follow a code", "Remove one"));
            if (choice < 0) { return; }
            try {
                if (choice == 0) { io.result("Short code", shorten(io.ask("url:"))); }
                else if (choice == 1) { io.result("Goes to", resolve(io.ask("code:"))); }
                else { io.println(remove(io.ask("code:")) ? "  removed" : "  no such code"); }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    String code = shorten(Json.str(body, "label", ""));
                    return Json.ok("items", snapshot(), "message", "Shortened to /" + code,
                            "detail", summary());
                }
                case "toggle" -> {
                    String url = resolve(Json.str(body, "id", ""));
                    return Json.ok("items", snapshot(), "message", "Followed",
                            "detail", "That code points at " + url + "\n" + summary());
                }
                case "remove" -> {
                    remove(Json.str(body, "id", ""));
                    return Json.ok("items", snapshot(), "message", "Removed", "detail", summary());
                }
                case "clear" -> {
                    byCode.clear(); byUrl.clear(); hits.clear(); counter = 1;
                    return Json.ok("items", snapshot(), "message", "Cleared", "detail", summary());
                }
                case "list" -> { return Json.ok("items", snapshot(), "detail", summary()); }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
