package com.randomjava.projects.resumescreener;

import com.randomjava.lib.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resume Screener - scores a CV against a job description.
 *
 * <p>The scoring is <b>weighted requirement coverage</b>, and the word coverage
 * is the whole design. The obvious implementation counts how many times the
 * CV hits job description terms, and that implementation is broken: a CV that
 * says "Java" forty times and nothing else beats a CV that actually has every
 * skill on the list, because forty is a bigger number than eight. Keyword
 * stuffing is a real and well known way to beat real screeners, and it works
 * precisely because they count.
 *
 * <p>So the two sides are treated differently on purpose:
 *
 * <ul>
 *   <li><b>Job description side</b> - a term repeated across the advert is
 *       weighted more, but only by {@code 1 + log(count)}. Sublinear, so a
 *       advert that says "Java" ten times does not make Java the entire job.</li>
 *   <li><b>CV side</b> - strictly present or absent. Saying a skill twice is
 *       worth exactly what saying it once is worth, so repetition buys
 *       nothing at all.</li>
 * </ul>
 *
 * <p>Terms under a "nice to have" heading carry {@link #BONUS_WEIGHT} of their
 * normal weight, because missing an optional skill is not the same failure as
 * missing a required one, and a score that cannot tell them apart is not much
 * of a score.
 *
 * <p>Years of experience are read separately rather than as keywords, since
 * "5+ years" and "2 years" share the word "years" and differ in the only part
 * that matters.
 */
public final class ResumeScreener implements Project {

    public static final Meta META = new Meta(59, "resume-screener", "Resume Screener", "AI and Machine Learning", Kind.TOOL,
            Difficulty.ADVANCED, "Score a CV against a job description by keyword and experience.",
            "", true);

    /** What an optional requirement is worth next to a required one. */
    public static final double BONUS_WEIGHT = 0.4;

    /**
     * Words that carry no signal about whether a CV fits.
     *
     * <p>Two groups: ordinary English glue, and advert filler. Without the
     * second group "professional", "familiarity" and "knowledge" get scored as
     * if they were skills, and a candidate is marked down for not using the
     * word "professional". The principled fix is idf across a corpus of
     * adverts, which needs a corpus; a curated list is the honest substitute
     * when there is only one advert to look at.
     *
     * <p>Built by splitting a string rather than with {@code Set.of}, which
     * throws on a duplicate - and does it at class-initialisation time, turning
     * a typo in a word list into a runtime crash a long way from the cause.
     */
    private static final Set<String> STOP = words(
              "the a an and or but of to in on at for with is are was were be as "
            + "by from that this we you our your will have has had who their "
            + "professional familiarity familiar knowledge understanding "
            + "proficiency proficient expertise skills skill development "
            + "developing looking join help candidate candidates etc ideally "
            + "across within into up years year experience work working role team "
            + "job must should strong good excellent ability able plus including "
            + "such using use well also new other any ");

    private static Set<String> words(String list) {
        return new HashSet<>(Arrays.asList(list.trim().split("\\s+")));
    }

    private static final Set<String> BONUS_HEADINGS = Set.of(
            "nice to have", "nice-to-have", "preferred", "desirable", "bonus",
            "advantageous", "would be a plus", "pluses", "optional");

    private static final Map<String, Integer> NUMBER_WORDS = Map.of(
            "one", 1, "two", 2, "three", 3, "four", 4, "five", 5,
            "six", 6, "seven", 7, "eight", 8, "nine", 9, "ten", 10);

    /** A single term the advert asks for, and whether the CV has it. */
    public record Requirement(String term, double weight, boolean required, boolean met) { }

    /** The full verdict. */
    public record Report(double score, List<Requirement> requirements,
                         int yearsWanted, int yearsFound, String verdict) {

        public List<Requirement> met() {
            return requirements.stream().filter(Requirement::met).toList();
        }

        public List<Requirement> missing() {
            return requirements.stream().filter(r -> !r.met()).toList();
        }
    }

    public static final String SAMPLE_JOB = """
            Backend Engineer

            Requirements:
            - 5+ years of professional Java development
            - Strong SQL and relational database design
            - Experience with Docker and Kubernetes
            - Familiarity with AWS

            Nice to have:
            - Kafka
            - Terraform
            """;

    public static final String SAMPLE_CV = """
            Sam Rivera - Software Engineer

            7 years building backend services in Java.
            Designed PostgreSQL schemas and tuned SQL queries.
            Deployed containerised services with Docker on AWS.
            """;

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Text handling
    // ------------------------------------------------------------------

    /** Significant terms, lower-cased and stop-word filtered. Order preserved. */
    public static List<String> terms(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) { return out; }
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9+#.]+")) {
            String clean = word.replaceAll("^[.+#]+|[.+#]+$", "");
            // C++ and C# keep their punctuation; it is the whole name.
            if (word.equals("c++") || word.equals("c#")) { clean = word; }
            if (clean.length() > 1 && !STOP.contains(clean) && !clean.matches("\\d+")) {
                out.add(clean);
            }
        }
        return out;
    }

    /**
     * Years of experience stated in a piece of text.
     *
     * <p>A range like "3-5 years" is read as its lower bound, because that is
     * what a range in an advert means: three is enough. "5+" is likewise five.
     */
    public static int years(String text, boolean lowerBound) {
        if (text == null) { return 0; }
        String lower = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Integer> word : NUMBER_WORDS.entrySet()) {
            lower = lower.replaceAll("\\b" + word.getKey() + "\\b", String.valueOf(word.getValue()));
        }
        Pattern pattern = Pattern.compile("(\\d+)\\s*(?:\\+|-\\s*(\\d+))?\\s*(?:\\+\\s*)?year");
        Matcher matcher = pattern.matcher(lower);
        int best = 0;
        boolean seen = false;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            best = seen ? (lowerBound ? Math.min(best, value) : Math.max(best, value)) : value;
            seen = true;
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Scoring
    // ------------------------------------------------------------------

    /**
     * Pulls the requirement terms out of an advert, weighted, and flagged as
     * required or optional depending on the heading they sit under.
     */
    public static Map<String, double[]> requirements(String jobDescription) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Boolean> required = new LinkedHashMap<>();
        boolean inBonus = false;

        for (String line : String.valueOf(jobDescription).split("\\R")) {
            String heading = line.toLowerCase(Locale.ROOT).replace(":", "").strip();
            if (BONUS_HEADINGS.contains(heading)) { inBonus = true; continue; }
            if (heading.startsWith("requirement") || heading.startsWith("must have")
                    || heading.startsWith("essential")) {
                inBonus = false;
                continue;
            }
            for (String term : terms(line)) {
                counts.merge(term, 1, Integer::sum);
                // A term named as required anywhere stays required, even if it
                // is also listed again further down under the optional heading.
                required.merge(term, !inBonus, (a, b) -> a || b);
            }
        }

        Map<String, double[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            boolean must = required.getOrDefault(e.getKey(), true);
            // Sublinear in the advert's own repetition.
            double weight = 1 + Math.log(e.getValue());
            out.put(e.getKey(), new double[]{must ? weight : weight * BONUS_WEIGHT, must ? 1 : 0});
        }
        return out;
    }

    public Report screen(String jobDescription, String cv) {
        if (jobDescription == null || jobDescription.isBlank()) {
            throw new IllegalArgumentException("Paste in the job description.");
        }
        if (cv == null || cv.isBlank()) {
            throw new IllegalArgumentException("Paste in the CV.");
        }
        Map<String, double[]> wanted = requirements(jobDescription);
        if (wanted.isEmpty()) {
            throw new IllegalArgumentException(
                    "That job description has no scoreable terms in it.");
        }
        // A set, not a count. Repetition in the CV must buy nothing.
        Set<String> has = new HashSet<>(terms(cv));

        List<Requirement> lines = new ArrayList<>();
        double earned = 0;
        double available = 0;
        for (Map.Entry<String, double[]> e : wanted.entrySet()) {
            double weight = e.getValue()[0];
            boolean must = e.getValue()[1] == 1;
            boolean met = has.contains(e.getKey());
            available += weight;
            if (met) { earned += weight; }
            lines.add(new Requirement(e.getKey(), weight, must, met));
        }

        int yearsWanted = years(jobDescription, true);
        int yearsFound = years(cv, false);
        double score = earned / available;

        // Experience is a separate gate, not another keyword. Short of the
        // stated minimum caps the score, because no amount of skill matching
        // makes two years into five.
        String verdict;
        if (yearsWanted > 0 && yearsFound < yearsWanted) {
            score = Math.min(score, 0.6);
            verdict = String.format("Short on experience: the advert asks for %d years, the CV "
                    + "shows %d. Capped at 0.60 however well the skills match.",
                    yearsWanted, yearsFound);
        } else if (yearsWanted > 0) {
            verdict = String.format("Experience is fine: %d years against the %d asked for.",
                    yearsFound, yearsWanted);
        } else {
            verdict = "The advert does not state a number of years.";
        }

        lines.sort((a, b) -> {
            if (a.met() != b.met()) { return a.met() ? -1 : 1; }
            return Double.compare(b.weight(), a.weight());
        });
        return new Report(score, lines, yearsWanted, yearsFound, verdict);
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    public static String band(double score) {
        if (score >= 0.75) { return "Strong match"; }
        if (score >= 0.50) { return "Worth a look"; }
        if (score >= 0.25) { return "Weak match"; }
        return "Not a match";
    }

    private static String describe(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s - %.0f%% of the advert covered.%n%n",
                band(report.score()), report.score() * 100));
        sb.append(report.verdict()).append("\n\n");

        sb.append("Covered:\n");
        if (report.met().isEmpty()) { sb.append("  nothing\n"); }
        for (Requirement r : report.met()) {
            sb.append("  ").append(r.term()).append(r.required() ? "" : " (nice to have")
              .append(r.required() ? "" : ")").append('\n');
        }
        sb.append("\nMissing:\n");
        if (report.missing().isEmpty()) { sb.append("  nothing\n"); }
        for (Requirement r : report.missing()) {
            sb.append("  ").append(r.term())
              .append(r.required() ? "  <- required" : "  (nice to have)").append('\n');
        }
        return sb.toString().stripTrailing();
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("Paste each part, then a blank line to finish it.");
        while (true) {
            io.println();
            int choice = io.menu("Resume Screener", List.of(
                    "Screen a CV", "Run the built-in example"));
            if (choice < 0) { return; }
            try {
                String job = choice == 1 ? SAMPLE_JOB : block(io, "job description");
                String cv = choice == 1 ? SAMPLE_CV : block(io, "CV");
                if (choice == 1) {
                    io.muted("--- job description ---");
                    io.println(SAMPLE_JOB);
                    io.muted("--- CV ---");
                    io.println(SAMPLE_CV);
                }
                io.println();
                io.println(describe(screen(job, cv)));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    private static String block(ConsoleUI io, String what) {
        io.muted("Paste the " + what + ", blank line when done:");
        StringBuilder sb = new StringBuilder();
        while (true) {
            String line = io.ask(">");
            if (line.isEmpty()) { return sb.toString(); }
            sb.append(line).append('\n');
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!List.of("compute", "screen", "input").contains(action)) {
            return Json.error("Unknown action: " + action);
        }
        try {
            String job = Json.str(body, "jd", "");
            String cv = Json.str(body, "cv", "");
            if (job.isBlank() && cv.isBlank()) {
                // The single-field scaffold sends everything as "input".
                String raw = Json.str(body, "input", "");
                String[] halves = raw.split("(?m)^\\s*---\\s*$", 2);
                if (halves.length == 2) { job = halves[0]; cv = halves[1]; }
            }
            if (job.isBlank() && cv.isBlank()) { job = SAMPLE_JOB; cv = SAMPLE_CV; }

            Report report = screen(job, cv);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Requirement r : report.requirements()) {
                rows.add(Json.map("term", r.term(),
                        "kind", r.required() ? "required" : "nice to have",
                        "status", r.met() ? "covered" : "missing"));
            }
            return Json.ok("result", String.format("%.0f%% - %s",
                            report.score() * 100, band(report.score())),
                    "detail", describe(report),
                    "rows", rows);
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
