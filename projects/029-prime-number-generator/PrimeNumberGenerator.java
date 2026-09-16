package com.randomjava.projects.primenumbergenerator;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prime Number Generator - a sieve of Eratosthenes for bulk generation, plus
 * trial division for factorising and testing single numbers.
 */
public final class PrimeNumberGenerator implements Project {

    public static final Meta META = new Meta(29, "prime-number-generator", "Prime Number Generator", "Algorithms and Data Structures", Kind.TOOL,
            Difficulty.BEGINNER, "Sieve primes up to a limit, factorise a number and test primality.",
            "", true);

    /** Guard so a careless input cannot allocate a huge array. */
    private static final int MAX_LIMIT = 5_000_000;

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // Core
    // ------------------------------------------------------------------

    /** Every prime up to and including {@code limit}, by sieve of Eratosthenes. */
    public List<Integer> sieve(int limit) {
        if (limit < 2) {
            return List.of();
        }
        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Keep the limit at or below " + MAX_LIMIT + ".");
        }
        boolean[] composite = new boolean[limit + 1];
        List<Integer> primes = new ArrayList<>();
        for (int candidate = 2; candidate <= limit; candidate++) {
            if (composite[candidate]) {
                continue;
            }
            primes.add(candidate);
            // Start at the square: smaller multiples already have a smaller factor.
            for (long multiple = (long) candidate * candidate;
                    multiple <= limit; multiple += candidate) {
                composite[(int) multiple] = true;
            }
        }
        return primes;
    }

    /** Trial division up to the square root, skipping even numbers after 2. */
    public boolean isPrime(long value) {
        if (value < 2) {
            return false;
        }
        if (value % 2 == 0) {
            return value == 2;
        }
        for (long divisor = 3; divisor * divisor <= value; divisor += 2) {
            if (value % divisor == 0) {
                return false;
            }
        }
        return true;
    }

    /** Prime factors with repeats, so 12 comes back as 2, 2, 3. */
    public List<Long> factorise(long value) {
        List<Long> factors = new ArrayList<>();
        if (value < 2) {
            return factors;
        }
        long remaining = value;
        for (long divisor = 2; divisor * divisor <= remaining; divisor += divisor == 2 ? 1 : 2) {
            while (remaining % divisor == 0) {
                factors.add(divisor);
                remaining /= divisor;
            }
        }
        if (remaining > 1) {
            factors.add(remaining);
        }
        return factors;
    }

    private static String join(List<?> values, int max) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(values.size(), max);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values.get(i));
        }
        if (values.size() > shown) {
            sb.append(", ... (").append(values.size() - shown).append(" more)");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        while (true) {
            int choice = io.menu("Primes", List.of(
                    "List primes up to a limit", "Test one number", "Factorise a number"));
            if (choice < 0) {
                return;
            }
            if (choice == 0) {
                int limit = io.askInt("limit:", 2, MAX_LIMIT, 100);
                List<Integer> primes = sieve(limit);
                io.result(primes.size() + " primes up to " + limit, join(primes, 60));
            } else if (choice == 1) {
                long value = (long) io.askDouble("number:", 97);
                io.result(String.valueOf(value), isPrime(value) ? "prime" : "not prime");
            } else {
                long value = (long) io.askDouble("number:", 360);
                List<Long> factors = factorise(value);
                io.result(value + " factorises to", factors.isEmpty() ? "nothing" : join(factors, 40));
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "compute", "sieve" -> {
                    int limit = Json.integer(body, "limit", 100);
                    List<Integer> primes = sieve(limit);
                    return Json.ok(
                            "result", primes.size() + " primes up to " + limit,
                            "detail", primes.isEmpty() ? "none" : join(primes, 200));
                }
                case "test" -> {
                    long value = (long) Json.num(body, "value", 0);
                    return Json.ok(
                            "result", value + " is " + (isPrime(value) ? "prime" : "not prime"),
                            "detail", isPrime(value) ? "No divisors other than 1 and itself."
                                    : "Factors: " + join(factorise(value), 40));
                }
                case "factorise" -> {
                    long value = (long) Json.num(body, "value", 0);
                    List<Long> factors = factorise(value);
                    return Json.ok(
                            "result", factors.isEmpty() ? "no prime factors" : join(factors, 60),
                            "detail", value + " has " + factors.size() + " prime factors counted with repeats.");
                }
                default -> {
                    return Json.error("Unknown action: " + action);
                }
            }
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }
}
