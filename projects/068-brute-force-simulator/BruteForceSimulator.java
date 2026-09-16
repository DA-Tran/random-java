package com.randomjava.projects.bruteforcesimulator;

import com.randomjava.lib.*;
import java.math.BigInteger;
import java.util.*;

/**
 * Brute Force Simulator - how long a password survives a given attack rate.
 *
 * <p>The keyspace is alphabet size raised to the length, which overflows a long
 * almost immediately: a 20-character password from a 95-character alphabet is
 * about 10^39. So the arithmetic is done in {@link BigInteger} and only
 * converted to a duration at the very end. Doing it in doubles loses precision
 * exactly where the interesting answers are.
 *
 * <p>Expected time is half the keyspace, not all of it, because on average the
 * right answer turns up halfway through.
 */
public final class BruteForceSimulator implements Project {

    public static final Meta META = new Meta(68, "brute-force-simulator", "Brute Force Simulator", "Cybersecurity", Kind.TOOL,
            Difficulty.BEGINNER, "Estimate how long a password survives a given attack rate.",
            "", true);

    /** Guesses per second for attacks people actually run. */
    public static final Map<String, Double> ATTACKS = new LinkedHashMap<>();
    static {
        ATTACKS.put("online, rate limited", 100.0);
        ATTACKS.put("online, unthrottled", 1_000_000.0);
        ATTACKS.put("offline, slow hash (bcrypt)", 20_000.0);
        ATTACKS.put("offline, fast hash (SHA-256 GPU)", 10_000_000_000.0);
        ATTACKS.put("offline, large GPU cluster", 100_000_000_000_000.0);
    }

    @Override public Meta meta() { return META; }

    /** The alphabet a password implies: only the classes it actually uses. */
    public static int alphabet(String password) {
        boolean lower = false, upper = false, digit = false, symbol = false;
        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) { lower = true; }
            else if (Character.isUpperCase(c)) { upper = true; }
            else if (Character.isDigit(c)) { digit = true; }
            else { symbol = true; }
        }
        int size = (lower ? 26 : 0) + (upper ? 26 : 0) + (digit ? 10 : 0) + (symbol ? 33 : 0);
        return Math.max(size, 1);
    }

    /** alphabet^length, exact, however large. */
    public static BigInteger keyspace(int alphabet, int length) {
        if (length < 0 || length > 256) {
            throw new IllegalArgumentException("Length must be between 0 and 256.");
        }
        return BigInteger.valueOf(alphabet).pow(length);
    }

    /** Average time to find it, in seconds, as an exact big number. */
    public static BigInteger seconds(BigInteger keyspace, double guessesPerSecond) {
        if (guessesPerSecond <= 0) { throw new IllegalArgumentException("The rate must be positive."); }
        return keyspace.divide(BigInteger.TWO)
                .divide(BigInteger.valueOf((long) Math.max(1, guessesPerSecond)));
    }

    /** Turns a duration into something a person can picture. */
    public static String describe(BigInteger seconds) {
        BigInteger[] scale = {
            BigInteger.ONE, BigInteger.valueOf(60), BigInteger.valueOf(3600),
            BigInteger.valueOf(86400), BigInteger.valueOf(31_557_600L)};
        String[] names = {"seconds", "minutes", "hours", "days", "years"};
        if (seconds.compareTo(BigInteger.ONE) < 0) { return "instantly"; }
        for (int i = names.length - 1; i >= 0; i--) {
            BigInteger value = seconds.divide(scale[i]);
            if (value.signum() > 0) {
                if (i == 4 && value.compareTo(BigInteger.valueOf(13_800_000_000L)) > 0) {
                    BigInteger universes = value.divide(BigInteger.valueOf(13_800_000_000L));
                    return String.format("%,d times the age of the universe", universes);
                }
                return String.format("%,d %s", value, names[i]);
            }
        }
        return "under a second";
    }

    private String detail(String password, int alphabet, int length) {
        BigInteger space = keyspace(alphabet, length);
        double bits = length * (Math.log(alphabet) / Math.log(2));
        StringBuilder sb = new StringBuilder(String.format(
                "Length %d over an alphabet of %d gives %.1f bits.%n"
                + "Keyspace: %s combinations.%n"
                + "Expected time is half of that, since the answer turns up halfway on average.",
                length, alphabet, bits, space.bitLength() > 80
                        ? String.format("about 10^%d", (int) (space.bitLength() * 0.30103))
                        : String.format("%,d", space)));
        for (Map.Entry<String, Double> attack : ATTACKS.entrySet()) {
            sb.append(String.format("%n  %-34s %s", attack.getKey(),
                    describe(seconds(space, attack.getValue()))));
        }
        sb.append("\nAdding one character multiplies every one of those by ").append(alphabet).append(".");
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Type a password to size it up, or a number to use that length. Blank to finish.");
        while (true) {
            String input = io.ask("password or length:");
            if (input.isEmpty()) { return; }
            try {
                int alphabet, length;
                if (input.matches("\\d{1,3}")) {
                    length = Integer.parseInt(input);
                    alphabet = io.askInt("alphabet size:", 2, 200, 95);
                } else {
                    length = input.length();
                    alphabet = alphabet(input);
                }
                BigInteger space = keyspace(alphabet, length);
                io.result(describe(seconds(space, 10_000_000_000.0)), "against a fast GPU hash");
                io.println("  " + detail(input, alphabet, length).replace("\n", "\n  "));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            String input = Json.str(body, "input", "");
            if (input.isBlank()) { return Json.error("Type a password, or a length in digits."); }
            int alphabet, length;
            if (input.matches("\\d{1,3}")) {
                length = Integer.parseInt(input);
                alphabet = Json.integer(body, "alphabet", 95);
            } else {
                length = input.length();
                alphabet = alphabet(input);
            }
            BigInteger space = keyspace(alphabet, length);
            return Json.ok("result", describe(seconds(space, 10_000_000_000.0))
                            + "  against a fast GPU hash",
                    "detail", detail(input, alphabet, length));
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
