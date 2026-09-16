package com.randomjava.test;

/**
 * Runs every test in the suite.
 *
 * <pre>
 *   ./test.sh            compile and run everything
 *   .\test.ps1           the same on Windows
 *
 *   java -cp build/classes:build/test-classes com.randomjava.test.AllTests
 * </pre>
 *
 * <p>Exits non-zero when anything fails, so it can gate a commit.
 */
public final class AllTests {

    public static void main(String[] args) {
        System.out.println();
        System.out.println("  random-java test suite");

        long started = System.currentTimeMillis();
        Harness harness = new Harness();

        CoreTests.run(harness);
        ProjectTests.run(harness);
        SuiteTests.run(harness);

        boolean passed = harness.report(System.currentTimeMillis() - started);
        System.out.println();
        System.exit(passed ? 0 : 1);
    }
}
