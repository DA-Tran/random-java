package com.randomjava.projects.keyloggerdetector;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Keylogger Detector - scores running processes on how much they look like
 * something recording keystrokes.
 *
 * <p>This is a defensive triage tool, and the hard part is not spotting
 * suspicious behaviour. It is that every individual signal here has a
 * completely legitimate owner. Low-level keyboard hooks are how password
 * managers, accessibility tools, screen readers and hotkey utilities work.
 * Running at startup is how most useful software works. Being unsigned is how
 * everything anyone builds themselves works.
 *
 * <p>So a detector that flags any single signal reports most of the machine,
 * which is the same as reporting nothing: an alert list nobody can act on gets
 * ignored, and then the real one is ignored too.
 *
 * <p>The design follows from that:
 *
 * <ul>
 *   <li><b>Signals are weighted and combined</b>, not ORed. Suspicion is the
 *       sum, so it takes a combination to clear the bar.</li>
 *   <li><b>A keyboard hook plus network activity is worth more than the sum of
 *       the two.</b> Reading keystrokes is what an accessibility tool does;
 *       sending them somewhere is not. The combination is the behaviour, so it
 *       gets its own bonus rather than being implied.</li>
 *   <li><b>Known-good software is scored down, not skipped.</b> Recognising a
 *       password manager and exiting early makes the allowlist a perfect place
 *       to hide - anything that names itself <i>1password</i> becomes invisible.
 *       It reduces the score, so an allowlisted name doing genuinely unusual
 *       things can still surface.</li>
 *   <li><b>Every verdict lists which signals fired.</b> A score with no
 *       reasoning cannot be argued with, and a human has to make the call.</li>
 * </ul>
 *
 * <p>This scores process descriptions that are given to it. Enumerating real
 * processes and inspecting real hooks is platform-specific work well outside a
 * zero-dependency Java project, and pretending otherwise would be the dishonest
 * part of a tool like this.
 */
public final class KeyloggerDetector implements Project {

    public static final Meta META = new Meta(64, "keylogger-detector", "Keylogger Detector", "Cybersecurity", Kind.LIST,
            Difficulty.ADVANCED, "Flag processes and hooks that look like they are capturing keystrokes.",
            "", true);

    /** One observable trait and what it is worth on its own. */
    public enum Signal {
        KEYBOARD_HOOK(30, "installs a low-level keyboard hook"),
        HIDDEN_WINDOW(15, "runs with no visible window"),
        WRITES_LOG(20, "writes steadily to a file it never reads back"),
        NETWORK(15, "keeps an outbound connection open"),
        AUTOSTART(10, "starts itself at login"),
        UNSIGNED(10, "is not signed"),
        RANDOM_NAME(15, "has a machine-generated name"),
        TEMP_DIRECTORY(15, "runs from a temporary directory"),
        SCREENSHOTS(15, "captures the screen periodically");

        private final int weight;
        private final String meaning;
        Signal(int weight, String meaning) { this.weight = weight; this.meaning = meaning; }
        public int weight() { return weight; }
        public String meaning() { return meaning; }
    }

    /**
     * Software with a real reason to hook the keyboard. Scored down rather than
     * skipped: an allowlist you can exit early from is a hiding place.
     */
    private static final Set<String> KNOWN_GOOD = Set.of(
            "1password", "bitwarden", "keepass", "lastpass", "dashlane",
            "autohotkey", "karabiner", "nvda", "jaws", "voiceover", "dragon",
            "synergy", "barrier", "logioptions", "steelseriesgg", "razersynapse");
    public static final int KNOWN_GOOD_DISCOUNT = 35;

    /** Where a verdict lands. */
    public enum Verdict { CLEAN, WORTH_A_LOOK, SUSPICIOUS, LIKELY_KEYLOGGER }

    public record Process(int id, String name, Set<Signal> signals) { }

    public record Assessment(Process process, int score, Verdict verdict,
                             List<String> reasons, boolean allowlisted) { }

    private final List<Process> processes = new ArrayList<>();
    private int nextId = 1;

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Scoring
    // ------------------------------------------------------------------

    /**
     * A name like "svch0st" or "a7f3k2q9" is worth noticing. Deliberately
     * conservative: plenty of legitimate binaries have terse names, so this
     * wants either a long unbroken run of consonants or a digit spliced into
     * the middle of a word.
     */
    public static boolean looksGenerated(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("\\.(exe|app|bin)$", "");
        if (base.length() < 4) { return false; }
        if (base.matches(".*[a-z]\\d[a-z].*")) { return true; }
        int consonants = 0;
        for (char c : base.toCharArray()) {
            if (Character.isLetter(c) && "aeiou".indexOf(c) < 0) {
                consonants++;
                if (consonants >= 5) { return true; }
            } else {
                consonants = 0;
            }
        }
        return false;
    }

    public static boolean allowlisted(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("\\.(exe|app|bin)$", "");
        for (String good : KNOWN_GOOD) {
            if (base.contains(good)) { return true; }
        }
        return false;
    }

    public Assessment assess(Process process) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        for (Signal signal : Signal.values()) {
            if (process.signals().contains(signal)) {
                score += signal.weight();
                reasons.add(String.format("+%-3d %s", signal.weight(), signal.meaning()));
            }
        }
        if (looksGenerated(process.name()) && !process.signals().contains(Signal.RANDOM_NAME)) {
            score += Signal.RANDOM_NAME.weight();
            reasons.add(String.format("+%-3d the name itself looks machine-generated",
                    Signal.RANDOM_NAME.weight()));
        }

        // Reading keystrokes has honest uses. Reading them and holding a socket
        // open is the actual behaviour being looked for, so the pair is worth
        // more than the two parts.
        if (process.signals().contains(Signal.KEYBOARD_HOOK)
                && process.signals().contains(Signal.NETWORK)) {
            score += 25;
            reasons.add("+25  hooks the keyboard AND talks to the network - "
                    + "capturing is normal, sending is not");
        }
        if (process.signals().contains(Signal.KEYBOARD_HOOK)
                && process.signals().contains(Signal.WRITES_LOG)) {
            score += 20;
            reasons.add("+20  hooks the keyboard AND writes a growing file it never reads");
        }

        boolean known = allowlisted(process.name());
        if (known) {
            score -= KNOWN_GOOD_DISCOUNT;
            reasons.add(String.format("-%d  \"%s\" matches software with a real reason to do "
                    + "this - scored down, not skipped", KNOWN_GOOD_DISCOUNT, process.name()));
        }
        score = Math.max(0, score);
        return new Assessment(process, score, verdictFor(score), reasons, known);
    }

    public static Verdict verdictFor(int score) {
        if (score >= 85) { return Verdict.LIKELY_KEYLOGGER; }
        if (score >= 55) { return Verdict.SUSPICIOUS; }
        if (score >= 30) { return Verdict.WORTH_A_LOOK; }
        return Verdict.CLEAN;
    }

    // ------------------------------------------------------------------
    // Inventory
    // ------------------------------------------------------------------

    public static Set<Signal> parseSignals(String text) {
        Set<Signal> out = EnumSet.noneOf(Signal.class);
        if (text == null) { return out; }
        for (String piece : text.split("[,;\\s]+")) {
            if (piece.isBlank()) { continue; }
            String key = piece.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            try {
                out.add(Signal.valueOf(key));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown signal \"" + piece + "\". Known: "
                        + String.join(", ", names()));
            }
        }
        return out;
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (Signal signal : Signal.values()) { out.add(signal.name().toLowerCase(Locale.ROOT)); }
        return out;
    }

    public Process add(String name, Set<Signal> signals) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A process needs a name.");
        }
        Process process = new Process(nextId++, name.trim(),
                signals == null ? EnumSet.noneOf(Signal.class) : EnumSet.copyOf(
                        signals.isEmpty() ? EnumSet.noneOf(Signal.class) : signals));
        processes.add(process);
        return process;
    }

    public List<Process> processes() { return List.copyOf(processes); }
    public boolean remove(int id) { return processes.removeIf(p -> p.id() == id); }
    public void clear() { processes.clear(); }

    /** A plausible machine: two innocent, one allowlisted, one that is not. */
    public void sample() {
        add("notepad.exe", EnumSet.noneOf(Signal.class));
        add("backup-agent.exe", EnumSet.of(Signal.AUTOSTART, Signal.NETWORK, Signal.HIDDEN_WINDOW));
        add("1password.exe", EnumSet.of(Signal.KEYBOARD_HOOK, Signal.AUTOSTART, Signal.NETWORK));
        add("svch0st.exe", EnumSet.of(Signal.KEYBOARD_HOOK, Signal.HIDDEN_WINDOW,
                Signal.WRITES_LOG, Signal.NETWORK, Signal.AUTOSTART, Signal.UNSIGNED,
                Signal.TEMP_DIRECTORY));
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private String summary() {
        if (processes.isEmpty()) {
            return "Nothing scanned. Add processes with the signals observed for each.\n  Signals: "
                    + String.join(", ", names());
        }
        Map<Verdict, Integer> counts = new EnumMap<>(Verdict.class);
        for (Process process : processes) {
            counts.merge(assess(process).verdict(), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder(processes.size() + " processes scanned.");
        counts.forEach((verdict, count) ->
                sb.append(String.format("%n  %-18s %d", verdict, count)));
        return sb.toString();
    }

    private String detail(Assessment assessment) {
        StringBuilder sb = new StringBuilder(String.format("%s - %s (%d)",
                assessment.process().name(), assessment.verdict(), assessment.score()));
        if (assessment.reasons().isEmpty()) {
            sb.append("\n  nothing unusual observed");
        }
        for (String reason : assessment.reasons()) { sb.append("\n  ").append(reason); }
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Process process : processes) {
            Assessment assessment = assess(process);
            out.add(Json.map("id", process.id(), "label", process.name(),
                    "meta", assessment.verdict() + " (" + assessment.score() + ") - "
                            + assessment.reasons().size() + " signals",
                    "done", assessment.verdict() == Verdict.CLEAN));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Signals: " + String.join(", ", names()));
        while (true) {
            io.println();
            io.muted(summary().replace("\n", "\n  "));
            int choice = io.menu("Keylogger Detector", List.of(
                    "Add a process", "Explain a process", "Load a sample machine", "Clear"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> {
                        Process process = add(io.ask("process name:"),
                                parseSignals(io.ask("signals observed:")));
                        io.println(detail(assess(process)));
                    }
                    case 1 -> {
                        int id = io.askInt("process id:", 1, 9999, 1);
                        for (Process process : processes) {
                            if (process.id() == id) { io.println(detail(assess(process))); }
                        }
                    }
                    case 2 -> { sample(); io.ok("Loaded."); }
                    default -> { clear(); io.ok("Cleared."); }
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    Process process = add(Json.str(body, "label", ""),
                            parseSignals(Json.str(body, "meta", "")));
                    return Json.ok("items", snapshot(),
                            "message", assess(process).verdict().toString(),
                            "detail", detail(assess(process)) + "\n\n" + summary());
                }
                case "remove" -> {
                    remove(Json.integer(body, "id", -1));
                    return Json.ok("items", snapshot(), "message", "Removed",
                            "detail", summary());
                }
                case "clear" -> {
                    clear();
                    return Json.ok("items", snapshot(), "message", "Cleared",
                            "detail", summary());
                }
                case "toggle" -> {
                    if (processes.isEmpty()) { sample(); }
                    StringBuilder sb = new StringBuilder();
                    for (Process process : processes) {
                        sb.append(detail(assess(process))).append("\n\n");
                    }
                    return Json.ok("items", snapshot(), "message", "Sample machine scanned",
                            "detail", sb.toString().stripTrailing());
                }
                case "list" -> {
                    return Json.ok("items", snapshot(), "detail", summary()
                            + "\n\n  Signals: " + String.join(", ", names()));
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
