package com.randomjava.projects.countdowntimer;

import com.randomjava.lib.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/**
 * Countdown Timer - parses a duration and reports what remains.
 *
 * <p>The parser accepts the way people actually write durations: "90s",
 * "1h30m", "2 hours 15 minutes", "01:30:00". All of them reduce to seconds,
 * which is the only number the rest of the class needs.
 */
public final class CountdownTimer implements Project {

    public static final Meta META = new Meta(10, "countdown-timer", "Countdown Timer", "Beginner Friendly", Kind.TOOL,
            Difficulty.BEGINNER, "Count down from a duration and report the time remaining.",
            "", true);

    private static final Pattern UNIT = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(h(?:ours?|rs?)?|m(?:in(?:ute)?s?)?|s(?:ec(?:ond)?s?)?|d(?:ays?)?)",
            Pattern.CASE_INSENSITIVE);

    private Instant deadline;

    @Override public Meta meta() { return META; }

    /** Seconds represented by a duration string, however it is written. */
    public long parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Enter a duration, such as 1h30m or 90s.");
        }
        String input = text.trim();

        // Clock form: mm:ss or hh:mm:ss
        if (input.matches("\\d{1,3}(:\\d{1,2}){1,2}")) {
            String[] parts = input.split(":");
            long total = 0;
            for (String part : parts) { total = total * 60 + Long.parseLong(part); }
            return total;
        }

        Matcher matcher = UNIT.matcher(input);
        double seconds = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            double value = Double.parseDouble(matcher.group(1));
            char unit = Character.toLowerCase(matcher.group(2).charAt(0));
            seconds += switch (unit) {
                case 'd' -> value * 86400;
                case 'h' -> value * 3600;
                case 'm' -> value * 60;
                default -> value;
            };
        }
        if (!found) {
            if (input.matches("\\d+")) { return Long.parseLong(input); }
            throw new IllegalArgumentException(
                    "Could not read '" + text + "'. Try 90s, 1h30m, 2 hours, or 01:30:00.");
        }
        long total = Math.round(seconds);
        if (total <= 0) { throw new IllegalArgumentException("That duration is zero."); }
        if (total > 365L * 86400) { throw new IllegalArgumentException("Keep it under a year."); }
        return total;
    }

    /** Human breakdown: "1 hour, 30 minutes". */
    public static String describe(long seconds) {
        if (seconds <= 0) { return "finished"; }
        long days = seconds / 86400, hours = seconds / 3600 % 24;
        long minutes = seconds / 60 % 60, secs = seconds % 60;
        List<String> parts = new ArrayList<>();
        if (days > 0) { parts.add(days + (days == 1 ? " day" : " days")); }
        if (hours > 0) { parts.add(hours + (hours == 1 ? " hour" : " hours")); }
        if (minutes > 0) { parts.add(minutes + (minutes == 1 ? " minute" : " minutes")); }
        if (secs > 0 || parts.isEmpty()) { parts.add(secs + (secs == 1 ? " second" : " seconds")); }
        return String.join(", ", parts);
    }

    public static String clock(long seconds) {
        if (seconds < 0) { seconds = 0; }
        return String.format("%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    public void start(long seconds) { deadline = Instant.now().plusSeconds(seconds); }

    public long remaining() {
        return deadline == null ? 0 : Math.max(0, Duration.between(Instant.now(), deadline).toSeconds());
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Durations like 90s, 1h30m, 2 hours 15 minutes, or 01:30:00.");
        while (true) {
            String input = io.ask("duration:");
            if (input.isEmpty()) { return; }
            try {
                long seconds = parse(input);
                start(seconds);
                io.result(clock(seconds), describe(seconds));
                io.muted("Finishes at " + LocalTime.now().plusSeconds(seconds)
                        .format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "compute", "start" -> {
                    long seconds = parse(Json.str(body, "input", ""));
                    start(seconds);
                    return Json.ok("result", clock(seconds),
                            "seconds", seconds,
                            "detail", describe(seconds) + "\nFinishes at "
                                    + LocalTime.now().plusSeconds(seconds)
                                        .format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                }
                case "tick" -> {
                    long left = remaining();
                    return Json.ok("result", clock(left), "seconds", left,
                            "detail", left == 0 ? "Time is up." : describe(left) + " remaining");
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
