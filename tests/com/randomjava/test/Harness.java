package com.randomjava.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A very small test runner, written the same way as the rest of the suite: no
 * external jars, just a JDK.
 *
 * <p>Two things matter here beyond counting passes. Each test runs inside its
 * own try/catch, so one exception reports as a single failure instead of
 * stopping the run; and every assertion carries a sentence describing what was
 * expected, so a red line tells you what broke without opening the test.
 */
public final class Harness {

    private final List<String> failures = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();
    private String group = "general";
    private int checks;
    private int groupChecks;
    private long groupStart;

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    /** Runs one named group of tests, isolating any exception it throws. */
    public void group(String name, Consumer<Harness> body) {
        group = name;
        groupChecks = 0;
        int before = failures.size();
        groupStart = System.currentTimeMillis();
        try {
            body.accept(this);
        } catch (Throwable error) {
            failures.add(name + ": threw " + error.getClass().getSimpleName()
                    + " - " + error.getMessage());
        }
        long ms = System.currentTimeMillis() - groupStart;
        int failed = failures.size() - before;
        groups.add(String.format("  %-34s %4d checks  %s  %5d ms",
                name, groupChecks, failed == 0 ? "ok      " : failed + " FAILED", ms));
    }

    // ------------------------------------------------------------------
    // Assertions
    // ------------------------------------------------------------------

    public void check(String what, boolean ok) {
        checks++;
        groupChecks++;
        if (!ok) {
            failures.add(group + ": " + what);
        }
    }

    public void equal(String what, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        String detail = "  (expected <" + expected + "> but was <" + actual + ">)";
        // When both sides print the same but are not equal, the difference is
        // the type. Saying so turns a baffling failure into an obvious one.
        if (!ok && expected != null && actual != null
                && String.valueOf(expected).equals(String.valueOf(actual))) {
            detail = "  (same text but different types: expected "
                    + expected.getClass().getSimpleName() + " <" + expected + "> but was "
                    + actual.getClass().getSimpleName() + " <" + actual + ">)";
        }
        check(what + detail, ok);
    }

    public void near(String what, double expected, double actual, double tolerance) {
        check(what + "  (expected ~" + expected + " but was " + actual + ")",
                Math.abs(expected - actual) <= tolerance);
    }

    public void contains(String what, String haystack, String needle) {
        check(what + "  (expected to find <" + needle + "> in <" + shorten(haystack) + ">)",
                haystack != null && haystack.contains(needle));
    }

    public void absent(String what, String haystack, String needle) {
        check(what + "  (did not expect <" + needle + "> in <" + shorten(haystack) + ">)",
                haystack == null || !haystack.contains(needle));
    }

    /** Asserts the body throws, which is how error handling gets tested. */
    public void throwsError(String what, Runnable body) {
        checks++;
        groupChecks++;
        try {
            body.run();
            failures.add(group + ": " + what + "  (expected it to be rejected, but it was not)");
        } catch (RuntimeException expected) {
            // good
        }
    }

    /** Asserts the body does not throw. */
    public void survives(String what, Runnable body) {
        checks++;
        groupChecks++;
        try {
            body.run();
        } catch (Throwable error) {
            failures.add(group + ": " + what + "  (threw " + error.getClass().getSimpleName()
                    + ": " + error.getMessage() + ")");
        }
    }

    private static String shorten(String text) {
        if (text == null) {
            return "null";
        }
        String flat = text.replace("\n", " ");
        return flat.length() <= 70 ? flat : flat.substring(0, 70) + "...";
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    /** Prints the summary and returns true when everything passed. */
    public boolean report(long totalMs) {
        System.out.println();
        for (String line : groups) {
            System.out.println(line);
        }
        System.out.println();
        System.out.println("  " + checks + " checks in " + groups.size() + " groups, "
                + totalMs + " ms");
        if (failures.isEmpty()) {
            System.out.println("  ALL PASSED");
            return true;
        }
        System.out.println("  " + failures.size() + " FAILED");
        System.out.println();
        for (String failure : failures) {
            System.out.println("    x " + failure);
        }
        return false;
    }

    public int failureCount() {
        return failures.size();
    }
}
