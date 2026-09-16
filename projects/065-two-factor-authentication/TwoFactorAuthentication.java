package com.randomjava.projects.twofactorauthentication;

import com.randomjava.lib.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;

/**
 * Two-Factor Authentication - TOTP exactly as RFC 6238 defines it.
 *
 * <p>This is the algorithm behind every authenticator app. A shared secret and
 * the current 30-second time step go through HMAC, and a four-byte window is
 * taken from a position the hash itself chooses - the "dynamic truncation" that
 * stops the code depending on any fixed part of the digest.
 *
 * <p>It is written to the spec rather than to taste, which means it can be
 * checked against the official test vectors in the RFC. Those vectors are in
 * the tests, so a subtle mistake shows up as a wrong number rather than as a
 * login failure six months later.
 */
public final class TwoFactorAuthentication implements Project {

    public static final Meta META = new Meta(65, "two-factor-authentication", "Two Factor Authentication", "Cybersecurity", Kind.TOOL,
            Difficulty.INTERMEDIATE, "Generate and verify time-based one-time passwords.",
            "", true);

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int STEP_SECONDS = 30;

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------

    /** Decodes a base32 secret, which is how authenticator apps exchange keys. */
    public static byte[] decodeBase32(String secret) {
        String clean = secret.replaceAll("[=\\s-]", "").toUpperCase(Locale.ROOT);
        if (clean.isEmpty()) { throw new IllegalArgumentException("The secret is empty."); }
        int bits = 0, value = 0;
        List<Byte> out = new ArrayList<>();
        for (char c : clean.toCharArray()) {
            int index = BASE32.indexOf(c);
            if (index < 0) {
                throw new IllegalArgumentException("'" + c + "' is not valid base32 (A-Z and 2-7).");
            }
            value = (value << 5) | index;
            bits += 5;
            if (bits >= 8) {
                out.add((byte) ((value >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        byte[] key = new byte[out.size()];
        for (int i = 0; i < key.length; i++) { key[i] = out.get(i); }
        return key;
    }

    public static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int bits = 0, value = 0;
        for (byte b : data) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) { sb.append(BASE32.charAt((value >> (bits - 5)) & 31)); bits -= 5; }
        }
        if (bits > 0) { sb.append(BASE32.charAt((value << (5 - bits)) & 31)); }
        return sb.toString();
    }

    /** HOTP: one code for one counter value. RFC 4226. */
    public static String hotp(byte[] key, long counter, int digits, String algorithm) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            // Dynamic truncation: the low nibble of the last byte picks the offset.
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int modulo = (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", binary % modulo);
        } catch (java.security.GeneralSecurityException e) {
            // Only the crypto failures are caught here. Catching Exception
            // would also swallow an ArrayIndexOutOfBounds from the truncation
            // above and report a bug in this method as "could not compute the
            // code", which sends anyone debugging it to the wrong place.
            throw new IllegalStateException("Could not compute the code: " + e.getMessage(), e);
        }
    }

    /** TOTP: HOTP with the counter taken from the clock. */
    public static String totp(String base32Secret, long epochSeconds, int digits, String algorithm) {
        return hotp(decodeBase32(base32Secret), epochSeconds / STEP_SECONDS, digits, algorithm);
    }

    public String current(String secret) {
        return totp(secret, System.currentTimeMillis() / 1000, 6, "HmacSHA1");
    }

    /**
     * Verifies a code, allowing one step either side so a slightly wrong clock
     * does not lock someone out. Comparison is constant time.
     */
    public boolean verify(String secret, String code, int windowSteps) {
        long now = System.currentTimeMillis() / 1000;
        String given = code == null ? "" : code.trim();
        boolean match = false;
        for (int step = -windowSteps; step <= windowSteps; step++) {
            String expected = totp(secret, now + step * STEP_SECONDS, given.length() == 0 ? 6 : given.length(), "HmacSHA1");
            match |= constantTimeEquals(expected, given);
        }
        return match;
    }

    /** Never short-circuits, so timing cannot leak how much of the code was right. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) { return false; }
        int difference = 0;
        for (int i = 0; i < a.length(); i++) { difference |= a.charAt(i) ^ b.charAt(i); }
        return difference == 0;
    }

    public String newSecret() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public long secondsLeft() {
        return STEP_SECONDS - (System.currentTimeMillis() / 1000) % STEP_SECONDS;
    }

    @Override public void runText(ConsoleUI io) {
        String secret = io.ask("base32 secret (blank to generate one):");
        if (secret.isBlank()) { secret = newSecret(); io.info("Secret: " + secret); }
        while (true) {
            try {
                io.result(current(secret), secondsLeft() + " seconds until it changes");
            } catch (RuntimeException e) { io.error(e.getMessage()); return; }
            String check = io.ask("code to verify (blank to refresh, q to stop):");
            if (check.equalsIgnoreCase("q")) { return; }
            if (!check.isBlank()) {
                io.println(verify(secret, check, 1) ? "  accepted" : "  rejected");
            }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            String secret = Json.str(body, "input", "").trim();
            switch (action) {
                case "compute" -> {
                    if (secret.isBlank()) { secret = newSecret(); }
                    return Json.ok("result", current(secret),
                            "detail", "Secret: " + secret
                                    + "\nChanges in " + secondsLeft() + " seconds."
                                    + "\nSix digits, SHA-1, 30 second steps - RFC 6238.");
                }
                case "verify" -> {
                    String code = Json.str(body, "code", "");
                    if (secret.isBlank()) { return Json.error("Give the secret as well as the code."); }
                    boolean ok = verify(secret, code, 1);
                    return Json.ok("result", ok ? "Accepted" : "Rejected",
                            "detail", ok ? "The code matches within one time step either side."
                                    : "That code is not valid for this secret right now.");
                }
                case "secret" -> {
                    return Json.ok("result", newSecret(), "detail", "A fresh 160-bit secret.");
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
