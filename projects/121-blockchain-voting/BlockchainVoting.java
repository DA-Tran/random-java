package com.randomjava.projects.blockchainvoting;

import com.randomjava.lib.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Blockchain Voting - votes recorded in a hash-linked chain anyone can verify.
 *
 * <p>Each block carries the hash of the one before it, so a block's hash
 * depends on every block that came before. Change a vote in the middle and its
 * own hash changes, which no longer matches what the next block recorded as its
 * parent, and the break is visible from that point to the end. Tampering is not
 * prevented - anyone with the file can edit it - it is made <em>detectable</em>,
 * and {@link #verify} reports which block is the first to fail.
 *
 * <p><b>What this is not.</b> A hash chain is the easy part of a blockchain and
 * nowhere near a voting system:
 *
 * <ul>
 *   <li>There is no distributed consensus. One party holds the chain, and a
 *       party that can rewrite it can rehash every block after the change and
 *       produce a chain that verifies perfectly. Real chains make that
 *       expensive through proof of work or stake; this does nothing.</li>
 *   <li>Votes are visible. Everyone can read who voted for what, which is the
 *       opposite of a secret ballot.</li>
 *   <li>Voter identity is asserted, not proved. There is no signature, so
 *       anyone can vote as anyone.</li>
 * </ul>
 *
 * <p>Saying so matters, because "blockchain voting" is routinely proposed as
 * though the chain solved the hard problems. The chain is the part that was
 * never hard; secrecy, eligibility and coercion are.
 */
public final class BlockchainVoting implements Project {

    public static final Meta META = new Meta(121, "blockchain-voting", "Blockchain Voting", "Advanced and Experimental", Kind.LIST,
            Difficulty.ADVANCED, "Cast votes into a hash-linked chain that can be independently verified.",
            "", true);

    public record Block(int index, String voter, String choice, String previousHash,
                        String hash) { }

    /** Where verification failed, and why. */
    public record Audit(boolean valid, int firstBadBlock, String reason) { }

    public static final String GENESIS = "0".repeat(64);

    private final List<Block> chain = new ArrayList<>();
    private final Set<String> voted = new LinkedHashSet<>();

    public BlockchainVoting() { }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Hashing
    // ------------------------------------------------------------------

    public static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) { sb.append(String.format("%02x", b)); }
            return sb.toString();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No SHA-256 available", e);
        }
    }

    /**
     * A block's hash covers its contents <em>and</em> its parent's hash, which
     * is what makes the chain a chain rather than a list of receipts.
     */
    public static String hashOf(int index, String voter, String choice, String previousHash) {
        return sha256(index + "|" + voter + "|" + choice + "|" + previousHash);
    }

    // ------------------------------------------------------------------
    // Voting
    // ------------------------------------------------------------------

    public Block cast(String voter, String choice) {
        if (voter == null || voter.isBlank()) {
            throw new IllegalArgumentException("A vote needs a voter.");
        }
        if (choice == null || choice.isBlank()) {
            throw new IllegalArgumentException("A vote needs a choice.");
        }
        String name = voter.trim().toLowerCase(Locale.ROOT);
        if (!voted.add(name)) {
            throw new IllegalArgumentException(voter.trim() + " has already voted.");
        }
        int index = chain.size();
        String previous = chain.isEmpty() ? GENESIS : chain.get(index - 1).hash();
        Block block = new Block(index, name, choice.trim(), previous,
                hashOf(index, name, choice.trim(), previous));
        chain.add(block);
        return block;
    }

    /**
     * Walks the chain checking that each block's recorded hash still matches
     * its contents, and that each links to the one before.
     */
    public Audit verify() {
        String expectedPrevious = GENESIS;
        for (int i = 0; i < chain.size(); i++) {
            Block block = chain.get(i);
            if (block.index() != i) {
                return new Audit(false, i, "Block " + i + " claims to be block " + block.index());
            }
            if (!block.previousHash().equals(expectedPrevious)) {
                return new Audit(false, i,
                        "Block " + i + " does not link to the block before it. Something earlier "
                        + "in the chain was changed.");
            }
            String recomputed = hashOf(block.index(), block.voter(), block.choice(),
                    block.previousHash());
            if (!recomputed.equals(block.hash())) {
                return new Audit(false, i,
                        "Block " + i + " has been altered: its contents no longer produce the "
                        + "hash it carries.");
            }
            expectedPrevious = block.hash();
        }
        return new Audit(true, -1, chain.size() + " blocks verified end to end.");
    }

    /**
     * Rewrites one block's choice without fixing any hashes, the way an editor
     * with the file would. Exposed so the break can be demonstrated.
     */
    public void tamper(int index, String newChoice) {
        if (index < 0 || index >= chain.size()) {
            throw new IllegalArgumentException("No block " + index + ".");
        }
        Block old = chain.get(index);
        chain.set(index, new Block(old.index(), old.voter(), newChoice,
                old.previousHash(), old.hash()));
    }

    /**
     * Rewrites a block and rehashes everything after it, which is what an
     * attacker who controls the chain would do. The result verifies cleanly,
     * which is the honest limit of a hash chain with no consensus behind it.
     */
    public void rewrite(int index, String newChoice) {
        if (index < 0 || index >= chain.size()) {
            throw new IllegalArgumentException("No block " + index + ".");
        }
        String previous = index == 0 ? GENESIS : chain.get(index - 1).hash();
        for (int i = index; i < chain.size(); i++) {
            Block old = chain.get(i);
            String choice = i == index ? newChoice : old.choice();
            String hash = hashOf(i, old.voter(), choice, previous);
            chain.set(i, new Block(i, old.voter(), choice, previous, hash));
            previous = hash;
        }
    }

    public Map<String, Integer> tally() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Block block : chain) { counts.merge(block.choice(), 1, Integer::sum); }
        return counts;
    }

    public List<Block> chain() { return List.copyOf(chain); }

    public void clear() {
        chain.clear();
        voted.clear();
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private String summary() {
        Audit audit = verify();
        StringBuilder sb = new StringBuilder(chain.size() + " votes. "
                + (audit.valid() ? "Chain verifies." : "CHAIN BROKEN: " + audit.reason()));
        tally().forEach((choice, count) ->
                sb.append(String.format("%n  %-16s %d", choice, count)));
        if (!chain.isEmpty()) {
            sb.append("\n\n  Votes are in the open and identities are asserted, not proved. "
                    + "A hash chain makes edits visible; it does not make an election secret, "
                    + "and one party holding the chain can rewrite it wholesale.");
        }
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        Audit audit = verify();
        for (Block block : chain) {
            out.add(Json.map("id", block.index(),
                    "label", "#" + block.index() + " " + block.voter() + " -> " + block.choice(),
                    "meta", block.hash().substring(0, 16) + "... prev "
                            + block.previousHash().substring(0, 8),
                    "done", audit.valid() || block.index() < audit.firstBadBlock()));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            io.muted(summary().replace("\n", "\n  "));
            int choice = io.menu("Blockchain Voting", List.of(
                    "Cast a vote", "Verify the chain", "Tamper with a block",
                    "Rewrite from a block", "Clear"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> {
                        Block block = cast(io.ask("voter:"), io.ask("choice:"));
                        io.ok("Block " + block.index() + " " + block.hash().substring(0, 16));
                    }
                    case 1 -> {
                        Audit audit = verify();
                        if (audit.valid()) { io.ok(audit.reason()); }
                        else { io.error(audit.reason()); }
                    }
                    case 2 -> {
                        tamper(io.askInt("block:", 0, Math.max(0, chain.size() - 1), 0),
                                io.ask("new choice:"));
                        io.muted("Changed without rehashing, as an editor would.");
                    }
                    case 3 -> {
                        rewrite(io.askInt("block:", 0, Math.max(0, chain.size() - 1), 0),
                                io.ask("new choice:"));
                        io.muted("Rewritten and rehashed. This verifies - that is the point.");
                    }
                    default -> { clear(); io.ok("Cleared."); }
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    Block block = cast(Json.str(body, "label", ""), Json.str(body, "meta", ""));
                    return Json.ok("items", snapshot(),
                            "message", "Block " + block.index() + " added",
                            "detail", summary());
                }
                case "toggle" -> {
                    // Demonstrate the break: alter a block without rehashing.
                    if (chain.isEmpty()) { return Json.error("Cast a vote first."); }
                    tamper(Json.integer(body, "id", 0), "TAMPERED");
                    Audit audit = verify();
                    return Json.ok("items", snapshot(), "message", "Block altered",
                            "detail", "A block was edited without rehashing.\n  "
                                    + audit.reason() + "\n\n" + summary());
                }
                case "clear" -> {
                    clear();
                    return Json.ok("items", snapshot(), "message", "Cleared",
                            "detail", summary());
                }
                case "remove", "list" -> {
                    return Json.ok("items", snapshot(), "detail", summary());
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
