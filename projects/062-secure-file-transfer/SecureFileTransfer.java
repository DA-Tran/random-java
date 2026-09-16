package com.randomjava.projects.securefiletransfer;

import com.randomjava.lib.*;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/**
 * Secure File Transfer - encrypts a payload, protects it in transit, and checks
 * on arrival that what turned up is what was sent.
 *
 * <p>The interesting part is the integrity check, because the obvious one does
 * not do the job people think it does.
 *
 * <p><b>A checksum detects accidents. An HMAC detects attackers.</b> Publishing
 * a SHA-256 alongside a file catches a truncated download or a flipped bit on
 * a bad cable, and that is genuinely useful. It catches nothing deliberate:
 * anyone who can alter the file in transit can also recompute its SHA-256 and
 * replace that too, because computing SHA-256 needs no secret. The check passes
 * and reports success on a file that was rewritten.
 *
 * <p>An HMAC mixes a shared key into the hash. Without the key an attacker can
 * still change the file, but cannot produce a tag that matches - so the
 * receiver sees the mismatch. The difference is not the strength of the hash;
 * SHA-256 is the same function in both. The difference is whether forging the
 * check requires a secret.
 *
 * <p>Both are computed and reported here so the gap is visible: the demo can
 * tamper with a transfer and show the plain checksum happily agreeing while the
 * HMAC refuses.
 *
 * <p>Transfer itself is simulated in memory. The cryptography is real; the
 * socket is not.
 */
public final class SecureFileTransfer implements Project {

    public static final Meta META = new Meta(62, "secure-file-transfer", "Secure File Transfer", "Cybersecurity", Kind.TOOL,
            Difficulty.ADVANCED, "Move a file between hosts with encryption and an integrity check.",
            "", true);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    /** What travels: the ciphertext plus the two integrity values. */
    public record Parcel(String name, String payload, String checksum, String hmac, int bytes) { }

    /** What the receiver concluded. */
    public record Arrival(boolean checksumOk, boolean hmacOk, boolean decrypted,
                          String contents, String verdict) {
        public boolean trustworthy() { return hmacOk && decrypted; }
    }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    private static byte[] keyFrom(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No SHA-256 available", e);
        }
    }

    public static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(text).getBytes(StandardCharsets.UTF_8));
            return hex(hash);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No SHA-256 available", e);
        }
    }

    /** Keyed hash. Unforgeable without the shared secret, which is the point. */
    public static String hmac(String text, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyFrom(secret), "HmacSHA256"));
            return hex(mac.doFinal(String.valueOf(text).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No HmacSHA256 available", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) { sb.append(String.format("%02x", b)); }
        return sb.toString();
    }

    /**
     * Constant-time comparison. Returning early on the first differing byte
     * leaks how much of a guessed tag was right, which is enough to recover the
     * whole tag one byte at a time given enough attempts.
     */
    public static boolean sameTag(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) { return false; }
        int difference = 0;
        for (int i = 0; i < a.length(); i++) { difference |= a.charAt(i) ^ b.charAt(i); }
        return difference == 0;
    }

    // ------------------------------------------------------------------
    // Sending and receiving
    // ------------------------------------------------------------------

    public static Parcel send(String name, String contents, String secret) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("The file needs a name.");
        }
        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException("There is nothing to send.");
        }
        if (secret == null || secret.length() < 8) {
            throw new IllegalArgumentException("Use a shared secret of at least 8 characters.");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyFrom(secret), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(contents.getBytes(StandardCharsets.UTF_8));

            byte[] blob = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, blob, 0, iv.length);
            System.arraycopy(cipherText, 0, blob, iv.length, cipherText.length);
            String payload = Base64.getEncoder().encodeToString(blob);

            return new Parcel(name.trim(), payload, sha256(payload), hmac(payload, secret),
                    contents.getBytes(StandardCharsets.UTF_8).length);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Checks both integrity values, then decrypts. The checksum result is
     * reported even when it is useless, because seeing it pass on a forged
     * parcel is the clearest way to understand why it is not enough.
     */
    public static Arrival receive(Parcel parcel, String secret) {
        boolean checksumOk = sameTag(sha256(parcel.payload()), parcel.checksum());
        boolean hmacOk = sameTag(hmac(parcel.payload(), secret), parcel.hmac());

        if (!hmacOk) {
            return new Arrival(checksumOk, false, false, "",
                    checksumOk
                            ? "The checksum matches but the HMAC does not. The file was changed "
                              + "by someone who recomputed the checksum - which anyone can do, "
                              + "since it needs no secret. Rejected."
                            : "Neither the checksum nor the HMAC matches. The file was corrupted "
                              + "or altered in transit. Rejected.");
        }
        try {
            byte[] blob = Base64.getDecoder().decode(parcel.payload());
            byte[] iv = Arrays.copyOfRange(blob, 0, IV_BYTES);
            byte[] cipherText = Arrays.copyOfRange(blob, IV_BYTES, blob.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyFrom(secret), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            String contents = new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
            return new Arrival(checksumOk, true, true, contents,
                    "Delivered intact. The HMAC proves it came from someone holding the shared "
                    + "secret and has not changed since.");
        } catch (java.security.GeneralSecurityException | IllegalArgumentException e) {
            return new Arrival(checksumOk, true, false, "",
                    "The HMAC matched but decryption failed, which normally means the wrong "
                    + "key: " + e.getMessage());
        }
    }

    /**
     * Rewrites a parcel the way an attacker in the middle would, fixing up the
     * checksum because they can, and leaving the HMAC because they cannot.
     */
    public static Parcel tamper(Parcel parcel) {
        char[] chars = parcel.payload().toCharArray();
        int at = chars.length / 2;
        chars[at] = chars[at] == 'A' ? 'B' : 'A';
        String altered = new String(chars);
        return new Parcel(parcel.name(), altered, sha256(altered), parcel.hmac(), parcel.bytes());
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private static String describe(Parcel parcel, Arrival arrival) {
        return String.format(
                "%s (%d bytes)%n  checksum %s ... %s%n  hmac     %s ... %s%n%n%s%s",
                parcel.name(), parcel.bytes(),
                parcel.checksum().substring(0, 16), arrival.checksumOk() ? "matches" : "MISMATCH",
                parcel.hmac().substring(0, 16), arrival.hmacOk() ? "matches" : "MISMATCH",
                arrival.verdict(),
                arrival.trustworthy() ? "\n\n  contents: " + arrival.contents() : "");
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            int choice = io.menu("Secure File Transfer", List.of(
                    "Send and receive a file", "Send, tamper in transit, then receive"));
            if (choice < 0) { return; }
            try {
                String name = io.ask("file name:", "report.txt");
                String contents = io.ask("contents:", "quarterly numbers, confidential");
                String secret = io.ask("shared secret:", "shared-secret-value");
                Parcel parcel = send(name, contents, secret);
                if (choice == 1) {
                    parcel = tamper(parcel);
                    io.muted("An attacker altered the payload and recomputed the checksum.");
                }
                io.println();
                io.println(describe(parcel, receive(parcel, secret)));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!List.of("compute", "send", "tamper", "input").contains(action)) {
            return Json.error("Unknown action: " + action);
        }
        try {
            String name = Json.str(body, "name", "report.txt");
            String contents = Json.str(body, "contents", "");
            if (contents.isBlank()) { contents = Json.str(body, "input", ""); }
            if (contents.isBlank()) { contents = "quarterly numbers, confidential"; }
            String secret = Json.str(body, "secret", "shared-secret-value");

            Parcel parcel = send(name, contents, secret);
            boolean attacked = action.equals("tamper");
            if (attacked) { parcel = tamper(parcel); }
            Arrival arrival = receive(parcel, secret);

            return Json.ok("result", arrival.trustworthy() ? "Delivered intact" : "Rejected",
                    "detail", (attacked
                            ? "An attacker altered the payload in transit and recomputed the "
                              + "checksum to match, which needs no secret.\n\n" : "")
                            + describe(parcel, arrival));
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
