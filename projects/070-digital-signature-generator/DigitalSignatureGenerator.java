package com.randomjava.projects.digitalsignaturegenerator;

import com.randomjava.lib.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/**
 * Digital Signature Generator - signs a message with a private key and verifies
 * it with the public one.
 *
 * <p>A signature answers two questions at once: this message came from whoever
 * holds the private key, and it has not changed since. Encryption answers
 * neither, and the two get confused constantly - encrypting a message proves
 * nothing about who wrote it.
 *
 * <p><b>What actually gets signed is a hash, not the message.</b> RSA can only
 * operate on numbers smaller than its modulus, so a 2048-bit key cannot sign
 * anything longer than 256 bytes. Every real scheme therefore hashes first and
 * signs the digest - which is why the signature is a fixed size no matter how
 * long the message is, and why the collision resistance of the hash is load
 * bearing. A hash where two messages can be made to collide is a scheme where
 * a signature over one is a valid signature over the other.
 *
 * <p>Everything here is JDK crypto. Hand-rolling any of it would be a mistake:
 * the padding is where the security lives and it is subtle enough that the
 * standard implementations have had CVEs of their own.
 *
 * <p>SHA256withRSA is PKCS#1 v1.5, which is deterministic - the same key over
 * the same message always gives the same bytes. That makes it easy to test.
 * The modern alternative, RSASSA-PSS, adds a random salt, so signatures differ
 * every time while still verifying; that is a stronger scheme and a worse
 * demonstration.
 */
public final class DigitalSignatureGenerator implements Project {

    public static final Meta META = new Meta(70, "digital-signature-generator", "Digital Signature Generator", "Cybersecurity", Kind.TOOL,
            Difficulty.ADVANCED, "Sign a message with a private key and verify it with the public one.",
            "", true);

    public static final String ALGORITHM = "SHA256withRSA";
    public static final int KEY_BITS = 2048;

    /** A keypair in the printable form people paste around. */
    public record Identity(String name, String publicKey, String privateKey) { }

    public record Verdict(boolean valid, String detail) { }

    private final Map<String, Identity> identities = new LinkedHashMap<>();

    /**
     * The last signature produced, so Verify can be pressed straight after
     * Sign without pasting anything.
     *
     * <p>Deliberately not a map keyed by message. That was the first version
     * and it defeated the only demonstration that matters: sign a message,
     * change one character, verify. Looking the signature up by the altered
     * message finds nothing, so the tool reports "sign something first"
     * instead of "this does not verify" - which is the wrong answer to the
     * exact question a signature exists to answer.
     */
    private String lastSignature = "";

    /**
     * The two demo identities are generated on first use, not in the
     * constructor. Generating a 2048-bit RSA keypair takes real time, and the
     * project is constructed whenever the catalogue is walked - to render its
     * page, to check its metadata - almost always without anyone signing
     * anything.
     */
    private void ensureDemoIdentities() {
        if (identities.isEmpty()) {
            identities.put("alice", generate("alice"));
            identities.put("bob", generate("bob"));
        }
    }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Keys
    // ------------------------------------------------------------------

    public static Identity generate(String name) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_BITS);
            KeyPair pair = generator.generateKeyPair();
            return new Identity(name,
                    Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                    Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("This JDK cannot generate RSA keys: " + e, e);
        }
    }

    private static PrivateKey privateKey(String base64) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("That is not a usable private key.");
        }
    }

    private static PublicKey publicKey(String base64) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("That is not a usable public key.");
        }
    }

    // ------------------------------------------------------------------
    // Signing
    // ------------------------------------------------------------------

    public static String sign(String message, String privateKeyBase64) {
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("There is nothing to sign.");
        }
        try {
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(privateKey(privateKeyBase64));
            signer.update(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Checks a signature. A malformed signature is a failure, not an error:
     * "this did not verify" is the honest answer whether the bytes were
     * tampered with or were never a signature in the first place.
     */
    public static Verdict verify(String message, String signatureBase64, String publicKeyBase64) {
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey(publicKeyBase64));
            verifier.update(String.valueOf(message)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            boolean valid = verifier.verify(Base64.getDecoder().decode(signatureBase64));
            return new Verdict(valid, valid
                    ? "Signed by the holder of this key, and unchanged since."
                    : "This does not verify. Either the message changed after signing, or it "
                      + "was signed by a different key.");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return new Verdict(false, "This does not verify: " + e.getMessage());
        }
    }

    /** The digest that actually gets signed, shown because it is the whole trick. */
    public static String digest(String message) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(message)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) { sb.append(String.format("%02x", b)); }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No SHA-256 in this JDK", e);
        }
    }

    // ------------------------------------------------------------------
    // Session helpers
    // ------------------------------------------------------------------

    public Identity identity(String name) {
        ensureDemoIdentities();
        Identity found = identities.get(name.toLowerCase(Locale.ROOT));
        if (found == null) {
            throw new IllegalArgumentException("No identity called \"" + name + "\". Known: "
                    + String.join(", ", identities.keySet()));
        }
        return found;
    }

    public Identity add(String name) {
        String key = name.toLowerCase(Locale.ROOT).trim();
        if (key.isEmpty()) { throw new IllegalArgumentException("An identity needs a name."); }
        Identity made = generate(key);
        identities.put(key, made);
        return made;
    }

    public Set<String> names() {
        ensureDemoIdentities();
        return identities.keySet();
    }

    public String signAs(String name, String message) {
        lastSignature = sign(message, identity(name).privateKey());
        return lastSignature;
    }

    /** The most recent signature, or empty if nothing has been signed yet. */
    public String lastSignature() { return lastSignature; }

    private static String shorten(String base64) {
        return base64.length() <= 44 ? base64 : base64.substring(0, 40) + "... (" + base64.length() + " chars)";
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override public void runText(ConsoleUI io) {
        io.muted("Identities: " + String.join(", ", names()));
        while (true) {
            io.println();
            int choice = io.menu("Digital Signatures", List.of(
                    "Sign a message", "Verify a message", "Show a digest",
                    "New identity", "Show keys"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> {
                        String who = io.ask("sign as:", "alice");
                        String message = io.ask("message:");
                        String signature = signAs(who, message);
                        io.result(shorten(signature), "signature by " + who);
                    }
                    case 1 -> {
                        String message = io.ask("message:");
                        String signature = io.ask("signature:", lastSignature);
                        String who = io.ask("claimed signer:", "alice");
                        Verdict verdict = verify(message, signature, identity(who).publicKey());
                        if (verdict.valid()) { io.ok(verdict.detail()); }
                        else { io.error(verdict.detail()); }
                    }
                    case 2 -> io.result(digest(io.ask("message:")), "SHA-256, what gets signed");
                    case 3 -> { add(io.ask("name:")); io.ok("Generated a keypair."); }
                    default -> {
                        Identity who = identity(io.ask("name:", "alice"));
                        io.println("  public:  " + shorten(who.publicKey()));
                        io.println("  private: " + shorten(who.privateKey()));
                    }
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            String message = Json.str(body, "message", "");
            String who = Json.str(body, "identity", "alice");
            switch (action) {
                case "sign", "compute", "input" -> {
                    if (message.isBlank()) { return Json.error("There is nothing to sign."); }
                    String signature = signAs(who, message);
                    return Json.ok("result", shorten(signature),
                            "detail", String.format(
                                    "Signed by %s.%n%n  SHA-256 of the message (this is what is "
                                    + "actually signed):%n    %s%n%n  signature:%n    %s%n%n"
                                    + "  %s's public key:%n    %s",
                                    who, digest(message), signature, who,
                                    identity(who).publicKey()),
                            "signature", signature);
                }
                case "verify" -> {
                    String signature = Json.str(body, "signature", "");
                    if (signature.isBlank()) { signature = lastSignature; }
                    if (signature.isBlank()) { return Json.error("Sign something first."); }
                    Verdict verdict = verify(message, signature, identity(who).publicKey());
                    return Json.ok("result", verdict.valid() ? "VALID" : "INVALID",
                            "detail", verdict.detail() + "\n\n  checked against " + who
                                    + "'s public key\n  SHA-256: " + digest(message));
                }
                case "generate" -> {
                    Identity made = add(Json.str(body, "name", "carol"));
                    return Json.ok("result", "Generated " + made.name(),
                            "detail", "Identities: " + String.join(", ", names()));
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
