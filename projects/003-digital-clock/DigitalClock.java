package com.randomjava.projects.digitalclock;

import com.randomjava.lib.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Digital Clock - the time in any timezone, in 12 or 24 hour form.
 *
 * <p>Timezone names are matched loosely, because nobody types
 * "America/Argentina/Buenos_Aires" correctly first time. A bare city name is
 * matched against the last segment of every zone id.
 */
public final class DigitalClock implements Project {

    public static final Meta META = new Meta(3, "digital-clock", "Digital Clock", "Beginner Friendly", Kind.TOOL,
            Difficulty.BEGINNER, "Show the current time, with timezone and 12 or 24 hour formatting.",
            "", true);

    @Override public Meta meta() { return META; }

    /** Resolves a loose name like "tokyo" or "Europe/Paris" to a real zone. */
    public ZoneId zone(String text) {
        if (text == null || text.isBlank()) { return ZoneId.systemDefault(); }
        String wanted = text.trim().replace(' ', '_');
        try { return ZoneId.of(wanted); } catch (RuntimeException ignored) { /* try harder */ }
        String lower = wanted.toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (String id : ZoneId.getAvailableZoneIds()) {
            String tail = id.substring(id.lastIndexOf('/') + 1);
            if (tail.equalsIgnoreCase(lower) || id.toLowerCase(Locale.ROOT).contains(lower)) {
                hits.add(id);
            }
        }
        if (hits.isEmpty()) {
            throw new IllegalArgumentException("No timezone matches '" + text
                    + "'. Try a city such as Tokyo, or a full id such as Europe/Paris.");
        }
        Collections.sort(hits);
        return ZoneId.of(hits.get(0));
    }

    public String time(ZoneId zone, boolean twelveHour) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern(
                twelveHour ? "hh:mm:ss a" : "HH:mm:ss");
        return ZonedDateTime.now(zone).format(format);
    }

    public String detail(ZoneId zone) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        Duration offset = Duration.ofSeconds(now.getOffset().getTotalSeconds());
        long hours = offset.toHours();
        long minutes = Math.abs(offset.toMinutesPart());
        return "Zone: " + zone.getId()
                + "\nDate: " + now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))
                + "\nUTC offset: " + (hours >= 0 ? "+" : "") + hours
                + String.format(":%02d", minutes)
                + "\nDaylight saving in effect: "
                + (zone.getRules().isDaylightSavings(now.toInstant()) ? "yes" : "no");
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Enter a city or zone id. Blank uses this machine's zone, and a blank line stops.");
        while (true) {
            String input = io.ask("zone:");
            ZoneId zone;
            try { zone = zone(input); } catch (RuntimeException e) { io.error(e.getMessage()); continue; }
            io.result(time(zone, false), zone.getId());
            io.muted(detail(zone).replace("\n", "  |  "));
            if (!io.askYesNo("Another?", true)) { return; }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            ZoneId zone = zone(Json.str(body, "input", ""));
            boolean twelve = Json.bool(body, "twelveHour", false);
            return Json.ok("result", time(zone, twelve), "detail", detail(zone));
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
