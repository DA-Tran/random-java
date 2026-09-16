package com.randomjava.projects.securenotetakingapp;

import com.randomjava.lib.*;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/**
 * Secure Note-Taking App - notes encrypted at rest behind one master password.
 *
 * <p>Three decisions carry the whole thing, and each has a plausible-looking
 * alternative that quietly breaks it.
 *
 * <ul>
 *   <li><b>The password is stretched, not used as a key.</b> A password is
 *       low-entropy and the wrong length; hashing it once with SHA-256 gives
 *       the right length and none of the entropy, so an attacker with the file
 *       can try millions of guesses a second. PBKDF2 with
 *       {@value #ITERATIONS} iterations makes each guess cost a measurable
 *       amount of work, and a random per-note salt means the work cannot be
 *       done once and reused across notes or users.</li>
 *   <li><b>A fresh random IV for every encryption.</b> Reusing a nonce with
 *       GCM is not a small weakness - it leaks the XOR of the two plaintexts
 *       and can expose the authentication key outright. Because the IV is
 *       random, encrypting the same note twice gives different bytes, which
 *       looks like a bug and is the opposite of one.</li>
 *   <li><b>Authenticated encryption, so tampering is detected.</b> This is the
 *       one people skip. Plain AES-CBC will happily decrypt altered ciphertext
 *       and hand back plausible-looking garbage, with an attacker able to flip
 *       chosen bits of the plaintext by flipping bits of the block before it.
 *       GCM carries an authentication tag, so an altered byte anywhere makes
 *       decryption <em>fail</em> rather than return something wrong.</li>
 * </ul>
 *
 * <p>A wrong password and a tampered note therefore produce the same outcome:
 * a refusal. That is correct. Distinguishing them would tell an attacker which
 * of their guesses was closer.
 *
 * <p>All JDK crypto. This stores notes in memory for a session rather than on
 * disk, so it demonstrates encryption at rest without pretending to be a
 * password manager anyone should trust with real secrets.
 */
public final class SecureNoteTakingApp implements Project {

    public static final Meta META = new Meta(69, "secure-note-taking-app", "Secure Note-Taking App", "Cybersecurity", Kind.LIST,
            Difficulty.ADVANCED, "Notes and saved passwords, encrypted at rest behind one master password.",
            "", true);

    public static final int ITERATIONS = 210_000;
    public static final int KEY_BITS = 256;
    public static final int SALT_BYTES = 16;
    public static final int IV_BYTES = 12;
    public static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** A note. The title is kept in the clear so the list is usable when locked. */
    public record Note(int id, String title, String sealed) { }

    private final List<Note> notes = new ArrayList<>();
    private int nextId = 1;
    private String password = "";

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Crypto
    // ------------------------------------------------------------------

    private static SecretKey stretch(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return new SecretKeySpec(key, "AES");
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("This JDK cannot do PBKDF2: " + e, e);
        }
    }

    /**
     * Encrypts to a single base64 blob of salt, IV and ciphertext.
     *
     * <p>The salt and IV are stored alongside, in the clear. They are not
     * secrets - their job is to be unique, not hidden - and decryption is
     * impossible without them.
     */
    public static String seal(String plainText, String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("The vault needs a master password.");
        }
        try {
            byte[] salt = new byte[SALT_BYTES];
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(salt);
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, stretch(password, salt),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(
                    String.valueOf(plainText).getBytes(StandardCharsets.UTF_8));

            byte[] blob = new byte[salt.length + iv.length + cipherText.length];
            System.arraycopy(salt, 0, blob, 0, salt.length);
            System.arraycopy(iv, 0, blob, salt.length, iv.length);
            System.arraycopy(cipherText, 0, blob, salt.length + iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(blob);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Reverses {@link #seal}. A wrong password and a tampered blob both throw
     * the same thing, deliberately: telling them apart is information an
     * attacker can use.
     */
    public static String unseal(String sealed, String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Unlock the vault first.");
        }
        byte[] blob;
        try {
            blob = Base64.getDecoder().decode(sealed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("That note is not readable.");
        }
        if (blob.length < SALT_BYTES + IV_BYTES + 1) {
            throw new IllegalArgumentException("That note is truncated.");
        }
        try {
            byte[] salt = Arrays.copyOfRange(blob, 0, SALT_BYTES);
            byte[] iv = Arrays.copyOfRange(blob, SALT_BYTES, SALT_BYTES + IV_BYTES);
            byte[] cipherText = Arrays.copyOfRange(blob, SALT_BYTES + IV_BYTES, blob.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, stretch(password, salt),
                    new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (java.security.GeneralSecurityException e) {
            // AEADBadTagException lands here. Same message either way.
            throw new IllegalArgumentException(
                    "Wrong password, or the note has been altered since it was written.");
        }
    }

    // ------------------------------------------------------------------
    // Vault
    // ------------------------------------------------------------------

    public boolean unlocked() { return !password.isEmpty(); }

    public void unlock(String master) {
        if (master == null || master.isBlank()) {
            throw new IllegalArgumentException("The master password cannot be empty.");
        }
        if (master.length() < 8) {
            throw new IllegalArgumentException(
                    "Use at least 8 characters. PBKDF2 slows guessing down; it does not make a "
                    + "four-character password safe.");
        }
        password = master;
    }

    /** Forgets the password. The notes stay, unreadable, which is the point. */
    public void lock() { password = ""; }

    public Note add(String title, String bodyText) {
        if (!unlocked()) { throw new IllegalStateException("Unlock the vault first."); }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A note needs a title.");
        }
        Note note = new Note(nextId++, title.trim(), seal(bodyText, password));
        notes.add(note);
        return note;
    }

    public String read(int id) {
        if (!unlocked()) { throw new IllegalStateException("Unlock the vault first."); }
        for (Note note : notes) {
            if (note.id() == id) { return unseal(note.sealed(), password); }
        }
        throw new IllegalArgumentException("No note with id " + id + ".");
    }

    public List<Note> notes() { return List.copyOf(notes); }
    public boolean remove(int id) { return notes.removeIf(n -> n.id() == id); }
    public void clear() { notes.clear(); }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private String status() {
        return notes.size() + (notes.size() == 1 ? " note" : " notes") + ", vault "
                + (unlocked() ? "unlocked" : "locked")
                + ". PBKDF2-SHA256 at " + String.format("%,d", ITERATIONS)
                + " iterations, AES-" + KEY_BITS + "-GCM.";
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Note note : notes) {
            String preview;
            if (!unlocked()) {
                preview = "locked - " + note.sealed().substring(0, 24) + "...";
            } else {
                try {
                    String body = unseal(note.sealed(), password);
                    preview = body.length() > 60 ? body.substring(0, 57) + "..." : body;
                } catch (RuntimeException e) {
                    preview = "cannot be read with this password";
                }
            }
            out.add(Json.map("id", note.id(), "label", note.title(),
                    "meta", preview, "done", unlocked()));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            io.muted(status());
            int choice = io.menu("Secure Notes", List.of(
                    "Unlock", "Add a note", "Read a note", "Lock", "Clear"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> { unlock(io.ask("master password:")); io.ok("Unlocked."); }
                    case 1 -> {
                        add(io.ask("title:"), io.ask("body:"));
                        io.ok("Sealed.");
                    }
                    case 2 -> io.result(read(io.askInt("note id:", 1, 9999, 1)), "decrypted");
                    case 3 -> { lock(); io.ok("Locked. The notes are now unreadable."); }
                    default -> { clear(); io.ok("Cleared."); }
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "unlock" -> {
                    unlock(Json.str(body, "password", ""));
                    return Json.ok("items", snapshot(), "message", "Unlocked",
                            "detail", status());
                }
                case "lock" -> {
                    lock();
                    return Json.ok("items", snapshot(), "message", "Locked",
                            "detail", status() + " The notes are still here and still sealed.");
                }
                case "add" -> {
                    Note note = add(Json.str(body, "label", ""), Json.str(body, "meta", ""));
                    return Json.ok("items", snapshot(), "message", "Sealed",
                            "detail", "Encrypted \"" + note.title() + "\". Same text sealed twice "
                                    + "gives different bytes: the IV is fresh each time.");
                }
                case "remove" -> {
                    remove(Json.integer(body, "id", -1));
                    return Json.ok("items", snapshot(), "message", "Removed", "detail", status());
                }
                case "clear" -> {
                    clear();
                    return Json.ok("items", snapshot(), "message", "Cleared", "detail", status());
                }
                case "list", "toggle" -> {
                    return Json.ok("items", snapshot(), "detail", status());
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
