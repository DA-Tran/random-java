package com.randomjava.projects.autoformfiller;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Auto Form Filler - matches stored details to a form's fields.
 *
 * <p>Real forms never agree on names. One asks for "email", the next for
 * "emailAddress", "e-mail" or "contact_email". So matching is done on a
 * normalised key with a table of known aliases behind it, rather than on exact
 * equality, which would fill almost nothing.
 *
 * <p>Fields it cannot match are listed rather than guessed at. Filling a form
 * with a plausible wrong value is worse than leaving it blank, because nobody
 * checks a field that already looks answered.
 */
public final class AutoFormFiller implements Project {

    public static final Meta META = new Meta(100, "auto-form-filler", "Auto Form Filler", "Automation and Tools", Kind.LIST,
            Difficulty.BEGINNER, "Store field profiles and apply them to a form definition.",
            "", true);

    /** Alias to canonical field name. */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    private static void alias(String canonical, String... names) {
        ALIASES.put(canonical, canonical);
        for (String name : names) { ALIASES.put(name, canonical); }
    }

    static {
        alias("firstname", "fname", "givenname", "forename", "first");
        alias("lastname", "lname", "surname", "familyname", "last");
        alias("fullname", "name", "yourname", "displayname");
        alias("email", "emailaddress", "mail", "contactemail", "useremail");
        alias("phone", "telephone", "tel", "mobile", "phonenumber", "contactnumber");
        alias("address", "street", "streetaddress", "addressline1", "address1");
        alias("city", "town", "locality");
        alias("postcode", "zip", "zipcode", "postalcode");
        alias("country", "nation");
        alias("company", "organisation", "organization", "employer");
        alias("jobtitle", "title", "role", "position");
        alias("website", "url", "homepage", "site");
    }

    private final Map<String, String> profile = new LinkedHashMap<>();

    @Override public Meta meta() { return META; }

    /** Strips punctuation and case so "E-Mail Address" and "email_address" agree. */
    public static String normalise(String field) {
        return field == null ? "" : field.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** The canonical name for a field, or the normalised name when unknown. */
    public static String canonical(String field) {
        String key = normalise(field);
        return ALIASES.getOrDefault(key, key);
    }

    public void set(String field, String value) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Give the field a name.");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Give the field a value.");
        }
        profile.put(canonical(field), value.trim());
    }

    public boolean remove(String field) { return profile.remove(canonical(field)) != null; }
    public void clear() { profile.clear(); }
    public Map<String, String> profile() { return profile; }

    /** Looks a field up, falling back to composing a full name from its parts. */
    public Optional<String> lookup(String field) {
        String key = canonical(field);
        String direct = profile.get(key);
        if (direct != null) { return Optional.of(direct); }
        if (key.equals("fullname")) {
            String first = profile.get("firstname");
            String last = profile.get("lastname");
            if (first != null && last != null) { return Optional.of(first + " " + last); }
        }
        if (key.equals("firstname") && profile.containsKey("fullname")) {
            return Optional.of(profile.get("fullname").split("\\s+")[0]);
        }
        if (key.equals("lastname") && profile.containsKey("fullname")) {
            String[] parts = profile.get("fullname").split("\\s+");
            if (parts.length > 1) { return Optional.of(parts[parts.length - 1]); }
        }
        return Optional.empty();
    }

    public record Filled(Map<String, String> values, List<String> unmatched) { }

    /** Fills what it can from a comma or newline separated list of field names. */
    public Filled fill(String formFields) {
        if (formFields == null || formFields.isBlank()) {
            throw new IllegalArgumentException("List the form's fields.");
        }
        Map<String, String> values = new LinkedHashMap<>();
        List<String> unmatched = new ArrayList<>();
        for (String chunk : formFields.split("[,;\\n]+")) {
            String field = chunk.trim();
            if (field.isEmpty()) { continue; }
            Optional<String> value = lookup(field);
            if (value.isPresent()) { values.put(field, value.get()); }
            else { unmatched.add(field); }
        }
        return new Filled(values, unmatched);
    }

    private String summary() {
        if (profile.isEmpty()) { return "No details stored yet."; }
        StringBuilder sb = new StringBuilder(profile.size() + " fields stored:");
        for (Map.Entry<String, String> e : profile.entrySet()) {
            sb.append("\n  ").append(e.getKey()).append(": ").append(e.getValue());
        }
        return sb.toString();
    }

    private List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> out = new ArrayList<>();
        int id = 1;
        for (Map.Entry<String, String> e : profile.entrySet()) {
            out.add(Json.map("id", e.getKey(), "label", e.getKey() + ": " + e.getValue(),
                    "meta", "", "done", false));
            id++;
        }
        return out;
    }

    @Override public void runText(ConsoleUI io) {
        while (true) {
            io.println();
            io.muted(summary().replace("\n", "\n  "));
            int choice = io.menu("Form filler", List.of(
                    "Store a field", "Fill a form", "Remove a field", "Clear"));
            if (choice < 0) { return; }
            try {
                switch (choice) {
                    case 0 -> { set(io.ask("field:"), io.ask("value:")); io.ok("Stored."); }
                    case 1 -> {
                        Filled filled = fill(io.ask("form fields, comma separated:"));
                        io.println();
                        for (Map.Entry<String, String> e : filled.values().entrySet()) {
                            io.ok(e.getKey() + " = " + e.getValue());
                        }
                        for (String field : filled.unmatched()) {
                            io.warn(field + " - nothing stored, left blank rather than guessed");
                        }
                    }
                    case 2 -> io.println(remove(io.ask("field:")) ? "  removed" : "  not stored");
                    default -> { clear(); io.ok("Cleared."); }
                }
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "add" -> {
                    set(Json.str(body, "label", ""), Json.str(body, "meta", ""));
                    return Json.ok("items", snapshot(), "message", "Stored", "detail", summary());
                }
                case "remove" -> {
                    remove(Json.str(body, "id", ""));
                    return Json.ok("items", snapshot(), "message", "Removed", "detail", summary());
                }
                case "clear" -> {
                    clear();
                    return Json.ok("items", snapshot(), "message", "Cleared", "detail", summary());
                }
                case "fill" -> {
                    Filled filled = fill(Json.str(body, "label", ""));
                    StringBuilder sb = new StringBuilder("Filled " + filled.values().size()
                            + " of " + (filled.values().size() + filled.unmatched().size()) + ":");
                    for (Map.Entry<String, String> e : filled.values().entrySet()) {
                        sb.append("\n  ").append(e.getKey()).append(" = ").append(e.getValue());
                    }
                    for (String field : filled.unmatched()) {
                        sb.append("\n  ").append(field)
                          .append(" - nothing stored, left blank rather than guessed");
                    }
                    return Json.ok("items", snapshot(), "detail", sb.toString());
                }
                case "list", "toggle" -> { return Json.ok("items", snapshot(), "detail", summary()); }
                default -> { return Json.error("Unknown action: " + action); }
            }
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
