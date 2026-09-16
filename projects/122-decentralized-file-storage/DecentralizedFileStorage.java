package com.randomjava.projects.decentralizedfilestorage;

import com.randomjava.lib.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Decentralized File Storage - split a file across nodes with redundancy and
 * content addressing.
 *
 * <p><b>Content addressing is the idea that carries the design.</b> A shard is
 * named by the hash of its own bytes rather than by where it happens to live.
 * Three things follow, and none of them needs any extra machinery:
 *
 * <ul>
 *   <li><b>Corruption is detectable.</b> Rehash what a node returns; if it does
 *       not match the name it was asked for, that node is lying or broken. A
 *       location-addressed store has no way to tell a corrupted shard from a
 *       good one and will hand back the bad bytes with confidence.</li>
 *   <li><b>Duplicate content is stored once.</b> Two identical shards have the
 *       same name, so they are the same shard.</li>
 *   <li><b>Any node can serve any shard.</b> The name says nothing about
 *       location, so a replica is interchangeable, and a failed read can be
 *       retried elsewhere without a lookup table.</li>
 * </ul>
 *
 * <p>Redundancy is plain replication: every shard is placed on
 * {@link #replicas} different nodes, so the file survives any
 * {@code replicas - 1} of them failing. Erasure coding would store far less for
 * the same durability, and is genuinely harder - it needs finite field
 * arithmetic, and it is not implemented here.
 *
 * <p>When too many nodes are gone, retrieval <b>fails and says which shard is
 * unreachable</b>. Returning the file with a hole in it would be the worst
 * possible behaviour: silently wrong data that looks like success.
 */
public final class DecentralizedFileStorage implements Project {

    public static final Meta META = new Meta(122, "decentralized-file-storage", "Decentralized File Storage", "Advanced and Experimental", Kind.LIST,
            Difficulty.ADVANCED, "Shard a file across nodes with redundancy and content addressing.",
            "", true);

    public static final int SHARD_SIZE = 24;

    /** A stored file: its shards, in order. */
    public record StoredFile(String name, List<String> shardIds, int bytes) { }

    /** One node, holding shards by their content address. */
    public static final class Node {
        private final String name;
        private final Map<String, String> shards = new LinkedHashMap<>();
        private boolean online = true;

        Node(String name) { this.name = name; }
        public String name() { return name; }
        public boolean online() { return online; }
        public int held() { return shards.size(); }
        public boolean holds(String id) { return shards.containsKey(id); }
    }

    private final List<Node> nodes = new ArrayList<>();
    private final Map<String, StoredFile> files = new LinkedHashMap<>();
    private int replicas = 3;

    public DecentralizedFileStorage() {
        for (int i = 1; i <= 6; i++) { nodes.add(new Node("node-" + i)); }
    }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Addressing
    // ------------------------------------------------------------------

    /** A shard's name is the hash of its bytes. */
    public static String address(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) { sb.append(String.format("%02x", digest[i])); }
            return sb.toString();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No SHA-256 available", e);
        }
    }

    public static List<String> split(String content) {
        List<String> shards = new ArrayList<>();
        for (int i = 0; i < content.length(); i += SHARD_SIZE) {
            shards.add(content.substring(i, Math.min(content.length(), i + SHARD_SIZE)));
        }
        if (shards.isEmpty()) { shards.add(""); }
        return shards;
    }

    public List<Node> nodes() { return List.copyOf(nodes); }
    public Collection<StoredFile> files() { return List.copyOf(files.values()); }
    public int replicas() { return replicas; }

    public void setReplicas(int count) {
        if (count < 1 || count > nodes.size()) {
            throw new IllegalArgumentException(
                    "Replicas must be between 1 and the number of nodes (" + nodes.size() + ").");
        }
        replicas = count;
    }

    // ------------------------------------------------------------------
    // Storing and retrieving
    // ------------------------------------------------------------------

    public StoredFile store(String name, String content) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A file needs a name.");
        }
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("There is nothing to store.");
        }
        List<Node> up = nodes.stream().filter(Node::online).toList();
        if (up.size() < replicas) {
            throw new IllegalStateException("Only " + up.size() + " nodes are online, and "
                    + replicas + " replicas are required.");
        }

        List<String> ids = new ArrayList<>();
        int placed = 0;
        for (String piece : split(content)) {
            String id = address(piece);
            ids.add(id);
            // Spread replicas round-robin so no node holds every copy.
            for (int r = 0; r < replicas; r++) {
                up.get((placed + r) % up.size()).shards.put(id, piece);
            }
            placed++;
        }
        StoredFile file = new StoredFile(name.trim(), ids, content.length());
        files.put(file.name(), file);
        return file;
    }

    /**
     * Rebuilds a file, checking every shard against its own address.
     *
     * <p>A node that returns something other than what was asked for is
     * skipped and another replica tried, which is only possible because the
     * shard's name is derived from its content.
     */
    public String retrieve(String name) {
        StoredFile file = files.get(String.valueOf(name).trim());
        if (file == null) { throw new IllegalArgumentException("No file called \"" + name + "\"."); }

        StringBuilder rebuilt = new StringBuilder();
        for (String id : file.shardIds()) {
            String piece = null;
            for (Node node : nodes) {
                if (!node.online() || !node.holds(id)) { continue; }
                String candidate = node.shards.get(id);
                // The integrity check content addressing gives for free.
                if (!address(candidate).equals(id)) { continue; }
                piece = candidate;
                break;
            }
            if (piece == null) {
                throw new IllegalStateException("Shard " + id + " is unreachable: every node "
                        + "holding it is offline or its copy is corrupt. The file cannot be "
                        + "rebuilt, and returning it with a hole would be worse.");
            }
            rebuilt.append(piece);
        }
        return rebuilt.toString();
    }

    /** How many online, uncorrupted copies of a shard exist. */
    public int healthOf(String shardId) {
        int count = 0;
        for (Node node : nodes) {
            if (node.online() && node.holds(shardId)
                    && address(node.shards.get(shardId)).equals(shardId)) {
                count++;
            }
        }
        return count;
    }

    public void setOnline(String nodeName, boolean online) {
        for (Node node : nodes) {
            if (node.name().equalsIgnoreCase(String.valueOf(nodeName).trim())) {
                node.online = online;
                return;
            }
        }
        throw new IllegalArgumentException("No node called \"" + nodeName + "\".");
    }

    /** Corrupts one node's copy of a shard, for demonstrating the check. */
    public boolean corrupt(String nodeName, String shardId) {
        for (Node node : nodes) {
            if (node.name().equalsIgnoreCase(String.valueOf(nodeName).trim())
                    && node.holds(shardId)) {
                node.shards.put(shardId, "CORRUPTED");
                return true;
            }
        }
        return false;
    }

    public void clear() {
        files.clear();
        for (Node node : nodes) {
            node.shards.clear();
            node.online = true;
        }
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private String summary() {
        long up = nodes.stream().filter(Node::online).count();
        StringBuilder sb = new StringBuilder(String.format(
                "%d files, %d of %d nodes online, %d replicas each.",
                files.size(), up, nodes.size(), replicas));
        for (Node node : nodes) {
            sb.append(String.format("%n  %-8s %-7s %d shards",
                    node.name(), node.online() ? "online" : "OFFLINE", node.held()));
        }
        for (StoredFile file : files.values()) {
            int weakest = file.shardIds().stream().mapToInt(this::healthOf).min().orElse(0);
            sb.append(String.format("%n  %s: %d shards, %d bytes, weakest shard has %d copies%s",
                    file.name(), file.shardIds().size(), file.bytes(), weakest,
                    weakest == 0 ? " - UNRECOVERABLE" : ""));
        }
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        int id = 1;
        for (StoredFile file : files.values()) {
            int weakest = file.shardIds().stream().mapToInt(this::healthOf).min().orElse(0);
            out.add(Json.map("id", id++, "label", file.name(),
                    "meta", file.shardIds().size() + " shards, " + file.bytes()
                            + " bytes, weakest has " + weakest + " good copies",
                    "done", weakest > 0));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            io.muted(summary().replace("\n", "\n  "));
            int choice = io.menu("Decentralized Storage", List.of(
                    "Store a file", "Retrieve a file", "Take a node offline",
                    "Corrupt a shard", "Clear"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> {
                        StoredFile file = store(io.ask("name:"), io.ask("contents:"));
                        io.ok("Stored in " + file.shardIds().size() + " shards.");
                    }
                    case 1 -> io.result(retrieve(io.ask("name:")), "rebuilt from shards");
                    case 2 -> {
                        setOnline(io.ask("node:", "node-1"), false);
                        io.muted("Offline.");
                    }
                    case 3 -> {
                        String name = io.ask("file:");
                        StoredFile file = files.get(name);
                        if (file == null) { throw new IllegalArgumentException("No such file."); }
                        io.muted(corrupt(io.ask("node:", "node-1"), file.shardIds().get(0))
                                ? "Corrupted. Retrieval should still work from another replica."
                                : "That node does not hold that shard.");
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
                    StoredFile file = store(Json.str(body, "label", ""),
                            Json.str(body, "meta", ""));
                    return Json.ok("items", snapshot(), "message", "Stored",
                            "detail", "Split into " + file.shardIds().size()
                                    + " shards, each on " + replicas + " nodes.\n\n" + summary());
                }
                case "toggle" -> {
                    // Knock a node offline to show the redundancy working.
                    for (Node node : nodes) {
                        if (node.online()) {
                            node.online = false;
                            break;
                        }
                    }
                    return Json.ok("items", snapshot(), "message", "A node went offline",
                            "detail", summary());
                }
                case "remove" -> {
                    for (Node node : nodes) { node.online = true; }
                    return Json.ok("items", snapshot(), "message", "All nodes back online",
                            "detail", summary());
                }
                case "clear" -> {
                    clear();
                    return Json.ok("items", snapshot(), "message", "Cleared",
                            "detail", summary());
                }
                case "list" -> { return Json.ok("items", snapshot(), "detail", summary()); }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
