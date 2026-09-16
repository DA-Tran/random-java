package com.randomjava.projects.encryptedchatapp;

import com.randomjava.lib.*;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/**
 * Encrypted Chat - messages are encrypted on the sender's machine and decrypted
 * on the recipient's, so the server in the middle only ever holds ciphertext.
 *
 * <p>The key exchange is <b>ECDH</b>, and it is the part worth understanding.
 * Alice and Bob each generate a keypair and send each other only the
 * <em>public</em> half. Each then combines their own private key with the
 * other's public key, and - this is the surprising bit - both arrive at the
 * identical shared secret, which was never transmitted and never existed on the
 * wire in any form. Someone who recorded every byte exchanged cannot reproduce
 * it, because doing so needs one of the two private keys.
 *
 * <p>That shared secret keys AES-GCM for the messages themselves. Public key
 * cryptography is far too slow for bulk data, so every real system does exactly
 * this: use it once to agree a symmetric key, then use the fast cipher.
 *
 * <p><b>What this does not have is forward secrecy.</b> The keypairs here are
 * long-lived, so one stolen private key decrypts every message ever sent to
 * that person, including any an attacker recorded years earlier. Real messengers
 * derive a fresh key per message and throw the old one away, so a key stolen
 * today cannot open yesterday's traffic. That ratchet is the substantial part of
 * Signal's protocol and is not modelled here - "end to end encrypted" is not one
 * property but several, and this demonstrates the first of them.
 *
 * <p>The transport is in-memory. The cryptography is real.
 */
public final class EncryptedChatApp implements Project {

    public static final Meta META = new Meta(66, "encrypted-chat-app", "Encrypted Chat App", "Cybersecurity", Kind.LIST,
            Difficulty.ADVANCED, "Chat where every message is encrypted before it leaves the client.",
            "", true);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    /** A participant. Only {@code publicKey} is ever meant to leave the device. */
    public record Person(String name, PublicKey publicKey, PrivateKey privateKey) {
        public String shareable() {
            return Base64.getEncoder().encodeToString(publicKey.getEncoded());
        }
    }

    /** What the server stores: who, when, and bytes it cannot read. */
    public record Envelope(int id, String from, String to, String cipherText) { }

    private final Map<String, Person> people = new LinkedHashMap<>();
    private final List<Envelope> server = new ArrayList<>();
    private int nextId = 1;

    public EncryptedChatApp() {
        join("alice");
        join("bob");
    }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Keys
    // ------------------------------------------------------------------

    public static Person generate(String name) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            KeyPair pair = generator.generateKeyPair();
            return new Person(name, pair.getPublic(), pair.getPrivate());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("This JDK cannot generate EC keys: " + e, e);
        }
    }

    /**
     * The shared secret, derived from one private key and the other public one.
     *
     * <p>Run it as Alice with Bob's public key, or as Bob with Alice's, and the
     * bytes are the same. Nothing about the secret was ever sent.
     */
    public static byte[] sharedSecret(PrivateKey mine, PublicKey theirs) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(mine);
            agreement.doPhase(theirs, true);
            // Hashed into a clean 256-bit key. The raw agreement output is a
            // curve coordinate, which is not uniformly distributed and is the
            // wrong shape to hand straight to AES.
            return MessageDigest.getInstance("SHA-256").digest(agreement.generateSecret());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Key agreement failed: " + e.getMessage(), e);
        }
    }

    public static PublicKey readPublicKey(String base64) {
        try {
            return KeyFactory.getInstance("EC").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("That is not a usable public key.");
        }
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    public static String encrypt(String plainText, byte[] key) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] body = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] blob = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, blob, 0, iv.length);
            System.arraycopy(body, 0, blob, iv.length, body.length);
            return Base64.getEncoder().encodeToString(blob);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed: " + e.getMessage(), e);
        }
    }

    public static String decrypt(String cipherText, byte[] key) {
        try {
            byte[] blob = Base64.getDecoder().decode(cipherText);
            if (blob.length < IV_BYTES + 1) {
                throw new IllegalArgumentException("That message is truncated.");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(blob, 0, IV_BYTES)));
            return new String(cipher.doFinal(
                    Arrays.copyOfRange(blob, IV_BYTES, blob.length)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Cannot read this message: it was not sent to you, or it has been altered.");
        }
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    public Person join(String name) {
        String key = String.valueOf(name).toLowerCase(Locale.ROOT).trim();
        if (key.isEmpty()) { throw new IllegalArgumentException("A person needs a name."); }
        Person person = generate(key);
        people.put(key, person);
        return person;
    }

    public Person person(String name) {
        Person found = people.get(String.valueOf(name).toLowerCase(Locale.ROOT).trim());
        if (found == null) {
            throw new IllegalArgumentException("Nobody here called \"" + name + "\". Present: "
                    + String.join(", ", people.keySet()));
        }
        return found;
    }

    public Set<String> present() { return people.keySet(); }

    public Envelope send(String fromName, String toName, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("There is no message to send.");
        }
        Person from = person(fromName);
        Person to = person(toName);
        if (from.name().equals(to.name())) {
            throw new IllegalArgumentException("Pick someone else to send to.");
        }
        Envelope envelope = new Envelope(nextId++, from.name(), to.name(),
                encrypt(text, sharedSecret(from.privateKey(), to.publicKey())));
        server.add(envelope);
        return envelope;
    }

    /** Reads a message as a given person. Fails for anyone it was not addressed to. */
    public String read(int id, String asName) {
        Person reader = person(asName);
        for (Envelope envelope : server) {
            if (envelope.id() != id) { continue; }
            String otherName = envelope.from().equals(reader.name())
                    ? envelope.to() : envelope.from();
            if (!envelope.from().equals(reader.name()) && !envelope.to().equals(reader.name())) {
                // Try anyway with whatever key this person has. It will fail,
                // which is the honest demonstration.
                otherName = envelope.from();
            }
            return decrypt(envelope.cipherText(),
                    sharedSecret(reader.privateKey(), person(otherName).publicKey()));
        }
        throw new IllegalArgumentException("No message with id " + id + ".");
    }

    /** Exactly what the server can see. */
    public List<Envelope> stored() { return List.copyOf(server); }
    public boolean remove(int id) { return server.removeIf(e -> e.id() == id); }
    public void clear() { server.clear(); }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private String status() {
        return server.size() + " message" + (server.size() == 1 ? "" : "s")
                + " on the server, all ciphertext. Present: " + String.join(", ", present())
                + ". ECDH P-256 key agreement, AES-256-GCM messages.";
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Envelope envelope : server) {
            String readable;
            try {
                readable = read(envelope.id(), envelope.to());
            } catch (RuntimeException e) {
                readable = "unreadable";
            }
            out.add(Json.map("id", envelope.id(),
                    "label", envelope.from() + " to " + envelope.to() + ": " + readable,
                    "meta", "on the server: " + envelope.cipherText().substring(0,
                            Math.min(32, envelope.cipherText().length())) + "...",
                    "done", true));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            io.muted(status());
            int choice = io.menu("Encrypted Chat", List.of(
                    "Send a message", "Read as someone", "Show what the server holds",
                    "Add a person", "Clear"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> {
                        Envelope sent = send(io.ask("from:", "alice"), io.ask("to:", "bob"),
                                io.ask("message:"));
                        io.result("sent as #" + sent.id(),
                                "the server stored " + sent.cipherText().substring(0, 24) + "...");
                    }
                    case 1 -> io.result(read(io.askInt("message id:", 1, 9999, 1),
                            io.ask("reading as:", "bob")), "decrypted");
                    case 2 -> {
                        for (Envelope envelope : server) {
                            io.println("  #" + envelope.id() + " " + envelope.from()
                                    + " -> " + envelope.to());
                            io.muted("     " + envelope.cipherText());
                        }
                    }
                    case 3 -> { join(io.ask("name:")); io.ok("Generated a keypair."); }
                    default -> { clear(); io.ok("Cleared."); }
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    String text = Json.str(body, "label", "");
                    String route = Json.str(body, "meta", "alice to bob");
                    String[] parts = route.toLowerCase(Locale.ROOT).split("\\s+to\\s+");
                    if (parts.length != 2) {
                        return Json.error("Write the route as \"alice to bob\".");
                    }
                    Envelope sent = send(parts[0].trim(), parts[1].trim(), text);
                    return Json.ok("items", snapshot(), "message", "Sent encrypted",
                            "detail", "The server stored only this:\n  " + sent.cipherText()
                                    + "\n\n" + status());
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
