package com.randomjava.projects.blooddonationfinder;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Blood Donation Finder - matches donors to recipients and to nearby drives.
 *
 * <p><b>Blood compatibility is not symmetric, and treating it as though it were
 * is the bug this exists to demonstrate.</b> O negative can give to every
 * blood type and can receive from none but its own. AB positive is the exact
 * opposite. "A can donate to B" and "B can donate to A" are different
 * questions with different answers, and a matcher that stores compatibility as
 * an undirected relation - a set of pairs, a symmetric matrix - will answer one
 * of them with the other.
 *
 * <p>It is a quiet failure. Most pairs happen to work in both directions or
 * neither, so a test built from a few examples passes while the universal donor
 * and universal recipient - the two types that matter most in an emergency -
 * are exactly the ones it gets backwards.
 *
 * <p>The rules themselves are the antigen ones: a donor's A, B and Rh antigens
 * must all be present in the recipient, so compatibility is a subset test on
 * three booleans rather than a lookup table anyone has to maintain.
 */
public final class BloodDonationFinder implements Project {

    public static final Meta META = new Meta(114, "blood-donation-finder", "Blood Donation Finder", "Community and Social Good", Kind.LIST,
            Difficulty.INTERMEDIATE, "Find compatible donors and drives near a location.",
            "", true);

    /** A blood type as its three antigens, which is what compatibility turns on. */
    public enum BloodType {
        O_NEG("O-", false, false, false),
        O_POS("O+", false, false, true),
        A_NEG("A-", true, false, false),
        A_POS("A+", true, false, true),
        B_NEG("B-", false, true, false),
        B_POS("B+", false, true, true),
        AB_NEG("AB-", true, true, false),
        AB_POS("AB+", true, true, true);

        private final String label;
        private final boolean a;
        private final boolean b;
        private final boolean rh;

        BloodType(String label, boolean a, boolean b, boolean rh) {
            this.label = label;
            this.a = a;
            this.b = b;
            this.rh = rh;
        }

        public String label() { return label; }

        /**
         * True when this type's red cells can go into {@code recipient}.
         *
         * <p>Directional on purpose. Every antigen the donor carries must
         * already be present in the recipient, or the recipient's immune system
         * attacks it. An antigen the recipient has and the donor lacks is
         * harmless, which is exactly why the relation does not run both ways.
         */
        public boolean canDonateTo(BloodType recipient) {
            return (!a || recipient.a) && (!b || recipient.b) && (!rh || recipient.rh);
        }

        public boolean canReceiveFrom(BloodType donor) {
            return donor.canDonateTo(this);
        }

        public static BloodType parse(String text) {
            String clean = String.valueOf(text).trim().toUpperCase(Locale.ROOT)
                    .replace("POSITIVE", "+").replace("NEGATIVE", "-")
                    .replace(" ", "");
            for (BloodType type : values()) {
                if (type.label.equals(clean)) { return type; }
            }
            throw new IllegalArgumentException("\"" + text + "\" is not a blood type. "
                    + "Use one of: " + labels());
        }

        public static String labels() {
            StringBuilder sb = new StringBuilder();
            for (BloodType type : values()) {
                if (sb.length() > 0) { sb.append(", "); }
                sb.append(type.label);
            }
            return sb.toString();
        }
    }

    public record Donor(int id, String name, BloodType type, String town, int lastDonatedDaysAgo) {
        /** Whole blood donation has a minimum gap, so recent donors are not available. */
        public boolean available() { return lastDonatedDaysAgo >= 56; }
    }

    private final List<Donor> donors = new ArrayList<>();
    private int nextId = 1;

    public BloodDonationFinder() { sample(); }

    @Override public Meta meta() { return META; }

    // ------------------------------------------------------------------
    // Registry
    // ------------------------------------------------------------------

    public Donor add(String name, String type, String town, int lastDonatedDaysAgo) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A donor needs a name.");
        }
        if (lastDonatedDaysAgo < 0) {
            throw new IllegalArgumentException("Days since the last donation cannot be negative.");
        }
        Donor donor = new Donor(nextId++, name.trim(), BloodType.parse(type),
                town == null || town.isBlank() ? "unknown" : town.trim(),
                lastDonatedDaysAgo);
        donors.add(donor);
        return donor;
    }

    /** Donors whose blood can go to this recipient, and who are eligible today. */
    public List<Donor> donorsFor(BloodType recipient, String town) {
        List<Donor> found = new ArrayList<>();
        for (Donor donor : donors) {
            if (!donor.type().canDonateTo(recipient)) { continue; }
            if (!donor.available()) { continue; }
            if (town != null && !town.isBlank() && !donor.town().equalsIgnoreCase(town.trim())) {
                continue;
            }
            found.add(donor);
        }
        // Rarest type first: an O- donor is worth saving for someone who needs it.
        found.sort(Comparator.comparingInt(d -> countCanReceive(d.type())));
        return found;
    }

    /** How many types this one can give to. Lower means rarer and more precious. */
    public static int countCanDonateTo(BloodType donor) {
        int count = 0;
        for (BloodType recipient : BloodType.values()) {
            if (donor.canDonateTo(recipient)) { count++; }
        }
        return count;
    }

    public static int countCanReceive(BloodType recipient) {
        int count = 0;
        for (BloodType donor : BloodType.values()) {
            if (donor.canDonateTo(recipient)) { count++; }
        }
        return count;
    }

    public List<Donor> donors() { return List.copyOf(donors); }
    public boolean remove(int id) { return donors.removeIf(d -> d.id() == id); }
    public void clear() { donors.clear(); }

    public void sample() {
        add("Ana", "O-", "Leeds", 90);
        add("Ben", "O+", "Leeds", 120);
        add("Cara", "A-", "Leeds", 30);
        add("Dev", "A+", "York", 200);
        add("Eve", "B+", "Leeds", 70);
        add("Finn", "AB+", "York", 400);
        add("Gita", "AB-", "Leeds", 60);
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    private String summary() {
        if (donors.isEmpty()) { return "No donors registered."; }
        Map<BloodType, Integer> counts = new EnumMap<>(BloodType.class);
        int eligible = 0;
        for (Donor donor : donors) {
            counts.merge(donor.type(), 1, Integer::sum);
            if (donor.available()) { eligible++; }
        }
        StringBuilder sb = new StringBuilder(String.format(
                "%d donors, %d eligible today (56 days between donations).",
                donors.size(), eligible));
        counts.forEach((type, count) ->
                sb.append(String.format("%n  %-4s %d  (can give to %d types, receive from %d)",
                        type.label(), count, countCanDonateTo(type), countCanReceive(type))));
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Donor donor : donors) {
            out.add(Json.map("id", donor.id(),
                    "label", donor.name() + " (" + donor.type().label() + ")",
                    "meta", donor.town() + ", last gave " + donor.lastDonatedDaysAgo()
                            + " days ago" + (donor.available() ? "" : " - not yet eligible"),
                    "done", donor.available()));
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Types: " + BloodType.labels());
        while (true) {
            io.println();
            io.muted(summary().replace("\n", "\n  "));
            int choice = io.menu("Blood Donation", List.of(
                    "Find donors for a patient", "Register a donor",
                    "Check one direction", "Clear"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> {
                        BloodType recipient = BloodType.parse(io.ask("patient type:", "O-"));
                        List<Donor> found = donorsFor(recipient, io.ask("town (blank for any):"));
                        if (found.isEmpty()) { io.error("No eligible donor matches."); }
                        for (Donor donor : found) {
                            io.println(String.format("  %-6s %-4s %s",
                                    donor.name(), donor.type().label(), donor.town()));
                        }
                    }
                    case 1 -> {
                        add(io.ask("name:"), io.ask("type:", "O+"), io.ask("town:"),
                                io.askInt("days since last donation:", 0, 9999, 100));
                        io.ok("Registered.");
                    }
                    case 2 -> {
                        BloodType from = BloodType.parse(io.ask("donor type:", "O-"));
                        BloodType to = BloodType.parse(io.ask("recipient type:", "AB+"));
                        io.result(from.canDonateTo(to) ? "yes" : "no",
                                from.label() + " -> " + to.label()
                                + "; the other way is "
                                + (to.canDonateTo(from) ? "yes" : "no"));
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
                    String label = Json.str(body, "label", "");
                    String meta = Json.str(body, "meta", "");
                    // "Name, type, town, days" in one field, or a lookup if it
                    // is just a blood type.
                    if (!meta.isBlank() || label.contains(",")) {
                        String[] parts = (label + "," + meta).split(",");
                        add(parts[0],
                                parts.length > 1 ? parts[1] : "O+",
                                parts.length > 2 ? parts[2] : "",
                                parts.length > 3 ? Integer.parseInt(parts[3].trim()) : 100);
                        return Json.ok("items", snapshot(), "message", "Registered",
                                "detail", summary());
                    }
                    BloodType recipient = BloodType.parse(label);
                    List<Donor> found = donorsFor(recipient, "");
                    StringBuilder sb = new StringBuilder(String.format(
                            "%s can receive from %d of the 8 types, and can give to %d.%n",
                            recipient.label(), countCanReceive(recipient),
                            countCanDonateTo(recipient)));
                    if (found.isEmpty()) {
                        sb.append("  No eligible donor on the register matches.");
                    }
                    for (Donor donor : found) {
                        sb.append(String.format("%n  %-6s %-4s %s",
                                donor.name(), donor.type().label(), donor.town()));
                    }
                    return Json.ok("items", snapshot(),
                            "message", found.size() + " matching donors",
                            "detail", sb.toString());
                }
                case "remove" -> {
                    remove(Json.integer(body, "id", -1));
                    return Json.ok("items", snapshot(), "message", "Removed",
                            "detail", summary());
                }
                case "clear" -> {
                    clear();
                    return Json.ok("items", snapshot(), "message", "Cleared",
                            "detail", summary());
                }
                case "list", "toggle" -> {
                    return Json.ok("items", snapshot(), "detail", summary()
                            + "\n\n  Type a blood type to find donors for it, or "
                            + "\"name, type, town, days\" to register one.");
                }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (NumberFormatException e) {
            return Json.error("Days since the last donation must be a number.");
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }
}
