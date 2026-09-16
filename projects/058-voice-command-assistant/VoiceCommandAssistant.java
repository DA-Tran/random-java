package com.randomjava.projects.voicecommandassistant;

import com.randomjava.lib.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Voice Command Assistant - parses a spoken command into an intent and its
 * slots, then dispatches it.
 *
 * <p>The parsing is pattern matching with <b>slot filling</b>, and the slots are
 * the point. Recognising that "set a timer for five minutes" is a timer request
 * is the easy half. The half that decides whether the assistant is usable is
 * what happens to "set a timer" - which is recognisably a timer request with no
 * duration in it.
 *
 * <p>Three ways to handle that, and only one of them is right:
 *
 * <ul>
 *   <li>Fire the handler with a missing value and let it default to something.
 *       This is how an assistant ends up setting a zero-second timer, or one
 *       for the last duration someone else asked for.</li>
 *   <li>Fail the whole match and fall through to "I did not understand", which
 *       is untrue and unhelpful - it understood perfectly, it is just missing
 *       one detail.</li>
 *   <li>Match the intent, notice the empty slot, and ask for exactly that.
 *       That is what happens here: an intent with an unfilled required slot
 *       becomes {@link Status#NEEDS_DETAIL} and names what is missing.</li>
 * </ul>
 *
 * <p>Numbers are read as words as well as digits, since "five minutes" is how
 * people speak and "5 minutes" is how they type, and a speech front end will
 * hand over either depending on the day.
 */
public final class VoiceCommandAssistant implements Project {

    public static final Meta META = new Meta(58, "voice-command-assistant", "Voice Command Assistant", "AI and Machine Learning", Kind.LIST,
            Difficulty.ADVANCED, "Parse spoken or typed commands and dispatch them to handlers.",
            "", true);

    public enum Status {
        /** Understood and acted on. */
        DONE,
        /** Understood, but a required slot was empty. */
        NEEDS_DETAIL,
        /** Not recognised as any known command. */
        UNKNOWN
    }

    /** What the parser made of one utterance. */
    public record Command(int id, String spoken, String intent, Map<String, String> slots,
                          Status status, String reply) { }

    /**
     * One recognised phrasing: an intent, a regex, and the slots the regex
     * captures. Required slots that come back empty stop the dispatch.
     */
    private record Rule(String intent, Pattern pattern, List<String> slots,
                        List<String> required) { }

    private static final Map<String, Integer> NUMBER_WORDS = new LinkedHashMap<>();
    static {
        String[] words = {"zero", "one", "two", "three", "four", "five", "six", "seven",
                "eight", "nine", "ten", "eleven", "twelve", "fifteen", "twenty", "thirty",
                "forty", "forty five", "fifty", "sixty", "ninety"};
        int[] values = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 20, 30, 40, 45, 50, 60, 90};
        for (int i = 0; i < words.length; i++) { NUMBER_WORDS.put(words[i], values[i]); }
        NUMBER_WORDS.put("a", 1);
        NUMBER_WORDS.put("an", 1);
        NUMBER_WORDS.put("half", 0);
    }

    private static final Map<String, Integer> UNIT_SECONDS = Map.of(
            "second", 1, "seconds", 1, "sec", 1, "secs", 1,
            "minute", 60, "minutes", 60, "min", 60, "mins", 60,
            "hour", 3600, "hours", 3600);

    private static Rule rule(String intent, String regex, String slots, String required) {
        return new Rule(intent, Pattern.compile(regex, Pattern.CASE_INSENSITIVE),
                split(slots), split(required));
    }

    private static List<String> split(String csv) {
        return csv.isEmpty() ? List.of() : List.of(csv.split(","));
    }

    /**
     * Ordered: the first match wins, so the more specific phrasings come first.
     * "Set a timer for five minutes" must not be caught by the bare "set a
     * timer" rule that exists to detect the missing duration.
     */
    private static final List<Rule> RULES = List.of(
            rule("timer", "^(?:set|start)\\s+(?:an?\\s+)?timer\\s+for\\s+(.+)$",
                    "duration", "duration"),
            rule("timer", "^(?:set|start)\\s+(?:an?\\s+)?timer\\s*(.*)$", "duration", "duration"),
            // The preposition is captured rather than discarded. Dropping it
            // gives "I will remind you to call mum six", which is not English,
            // and "in ten minutes" and "at ten" mean different things.
            rule("reminder", "^remind\\s+me\\s+to\\s+(.+?)\\s+(in|at)\\s+(.+)$",
                    "task,preposition,when", "task,when"),
            rule("reminder", "^remind\\s+me\\s*(?:to\\s+(.*))?$", "task", "task,when"),
            rule("light", "^turn\\s+(on|off)\\s+(?:the\\s+)?(.+)$", "state,device", "state,device"),
            rule("light", "^turn\\s+(on|off)\\s*(.*)$", "state,device", "state,device"),
            rule("play", "^play\\s+(.+)$", "what", "what"),
            rule("weather", "^(?:what(?:'?s| is)\\s+the\\s+)?weather\\s+(?:in|for)\\s+(.+)$",
                    "place", "place"),
            rule("weather", "^(?:what(?:'?s| is)\\s+the\\s+)?weather\\s*(.*)$", "place", "place"),
            rule("cancel", "^cancel\\s+(?:the\\s+)?(.+)$", "what", "what"),
            rule("volume", "^(?:set\\s+)?volume\\s+(?:to\\s+)?(.+)$", "level", "level"));

    private final List<Command> history = new ArrayList<>();
    private int nextId = 1;

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Numbers
    // ------------------------------------------------------------------

    /**
     * Seconds in a phrase like "five minutes", "90 seconds" or "an hour".
     * Returns -1 when there is no duration in there at all, which is different
     * from a duration of zero and has to stay different.
     */
    public static int seconds(String phrase) {
        if (phrase == null || phrase.isBlank()) { return -1; }
        String text = phrase.toLowerCase(Locale.ROOT).trim();
        for (Map.Entry<String, Integer> word : NUMBER_WORDS.entrySet()) {
            text = text.replaceAll("\\b" + Pattern.quote(word.getKey()) + "\\b",
                    " " + word.getValue() + " ");
        }
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([a-z]+)").matcher(text);
        int total = 0;
        boolean found = false;
        while (matcher.find()) {
            Integer unit = UNIT_SECONDS.get(matcher.group(2));
            if (unit == null) { continue; }
            total += (int) Math.round(Double.parseDouble(matcher.group(1)) * unit);
            found = true;
        }
        return found ? total : -1;
    }

    /** "1h 05m 00s" style, for reading back what was understood. */
    public static String clock(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = totalSeconds % 3600 / 60;
        int secs = totalSeconds % 60;
        if (hours > 0) { return String.format("%dh %02dm %02ds", hours, minutes, secs); }
        if (minutes > 0) { return String.format("%dm %02ds", minutes, secs); }
        return secs + "s";
    }

    // ------------------------------------------------------------------
    // Parsing and dispatch
    // ------------------------------------------------------------------

    public Command handle(String spoken) {
        if (spoken == null || spoken.isBlank()) {
            throw new IllegalArgumentException("Say something.");
        }
        String clean = spoken.trim().replaceAll("[.?!]+$", "").replaceAll("\\s+", " ");

        for (Rule candidate : RULES) {
            Matcher matcher = candidate.pattern().matcher(clean);
            if (!matcher.matches()) { continue; }

            Map<String, String> slots = new LinkedHashMap<>();
            for (int i = 0; i < candidate.slots().size(); i++) {
                String value = i + 1 <= matcher.groupCount() ? matcher.group(i + 1) : null;
                slots.put(candidate.slots().get(i),
                        value == null ? "" : value.trim());
            }

            List<String> missing = new ArrayList<>();
            for (String slot : candidate.required()) {
                if (slots.getOrDefault(slot, "").isBlank()) { missing.add(slot); }
            }
            // A duration that does not parse is as missing as one not spoken.
            if (candidate.intent().equals("timer") && missing.isEmpty()
                    && seconds(slots.get("duration")) < 0) {
                missing.add("duration");
            }

            if (!missing.isEmpty()) {
                return record(clean, candidate.intent(), slots, Status.NEEDS_DETAIL,
                        ask(candidate.intent(), missing));
            }
            return record(clean, candidate.intent(), slots, Status.DONE,
                    dispatch(candidate.intent(), slots));
        }
        return record(clean, "unknown", Map.of(), Status.UNKNOWN,
                "I did not understand that. Try \"set a timer for five minutes\", "
                        + "\"turn off the kitchen light\" or \"play something quiet\".");
    }

    /** Asks for exactly the slot that is empty, rather than for the whole command again. */
    private static String ask(String intent, List<String> missing) {
        String slot = missing.get(0);
        return switch (slot) {
            case "duration" -> "A timer for how long?";
            case "task" -> "Remind you to do what?";
            case "when" -> "When should I remind you?";
            case "device" -> "Turn what " + (intent.equals("light") ? "on or off" : "") + "?";
            case "state" -> "On or off?";
            case "place" -> "Weather where?";
            case "what" -> intent.equals("cancel") ? "Cancel what?" : "Play what?";
            case "level" -> "Set the volume to what?";
            default -> "I need the " + slot + " as well.";
        };
    }

    /** The handlers. Reached only once every required slot is filled. */
    private String dispatch(String intent, Map<String, String> slots) {
        switch (intent) {
            case "timer" -> {
                int total = seconds(slots.get("duration"));
                return "Timer set for " + clock(total) + ".";
            }
            case "reminder" -> {
                String preposition = slots.getOrDefault("preposition", "");
                return "I will remind you to " + slots.get("task")
                        + (preposition.isBlank() ? " " : " " + preposition + " ")
                        + slots.get("when") + ".";
            }
            case "light" -> {
                return "Turning " + slots.get("state") + " the " + slots.get("device") + ".";
            }
            case "play" -> { return "Playing " + slots.get("what") + "."; }
            case "weather" -> { return "Fetching the weather for " + slots.get("place") + "."; }
            case "cancel" -> { return "Cancelled the " + slots.get("what") + "."; }
            case "volume" -> {
                String level = slots.get("level").toLowerCase(Locale.ROOT);
                Integer word = NUMBER_WORDS.get(level);
                String shown = word != null ? String.valueOf(word) : level;
                return "Volume set to " + shown + ".";
            }
            default -> { return "Done."; }
        }
    }

    private Command record(String spoken, String intent, Map<String, String> slots,
                           Status status, String reply) {
        Command command = new Command(nextId++, spoken, intent,
                new LinkedHashMap<>(slots), status, reply);
        history.add(command);
        return command;
    }

    public List<Command> history() { return List.copyOf(history); }
    public boolean remove(int id) { return history.removeIf(c -> c.id() == id); }
    public void clear() { history.clear(); }

    /** The phrasings the assistant knows, for showing in the UI. */
    public static List<String> knownIntents() {
        List<String> out = new ArrayList<>();
        for (Rule candidate : RULES) {
            if (!out.contains(candidate.intent())) { out.add(candidate.intent()); }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private static String describe(Command command) {
        StringBuilder sb = new StringBuilder(command.reply());
        sb.append(String.format("%n  intent: %s (%s)", command.intent(), command.status()));
        if (!command.slots().isEmpty()) {
            List<String> parts = new ArrayList<>();
            command.slots().forEach((key, value) ->
                    parts.add(key + "=" + (value.isBlank() ? "(empty)" : value)));
            sb.append("\n  slots: ").append(String.join(", ", parts));
        }
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Command command : history) {
            out.add(Json.map("id", command.id(), "label", command.spoken(),
                    "meta", command.intent() + " - " + command.reply(),
                    "done", command.status() == Status.DONE));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Knows: " + String.join(", ", knownIntents()));
        io.muted("Try \"set a timer for five minutes\" or \"turn off the kitchen light\".");
        while (true) {
            io.println();
            String spoken = io.ask("say (blank to quit):");
            if (spoken.isEmpty()) { return; }
            try {
                io.println(describe(handle(spoken)));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    Command command = handle(Json.str(body, "label", ""));
                    return Json.ok("items", snapshot(), "message", command.reply(),
                            "detail", describe(command));
                }
                case "remove" -> {
                    remove(Json.integer(body, "id", -1));
                    return Json.ok("items", snapshot(), "message", "Removed");
                }
                case "clear" -> {
                    clear();
                    return Json.ok("items", snapshot(), "message", "Cleared",
                            "detail", "Knows: " + String.join(", ", knownIntents()));
                }
                case "list", "toggle" -> {
                    return Json.ok("items", snapshot(), "detail",
                            "Knows: " + String.join(", ", knownIntents())
                            + ". Type a command to try it.");
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
