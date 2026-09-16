package com.randomjava.lib;

import java.util.Locale;

/**
 * Roughly how much work a project is, used for the badge on the hub and the
 * filter in the launcher.
 *
 * <p>This measures the ambition of the brief, not the size of the scaffold: an
 * advanced project starts from the same generated stub as a beginner one.
 */
public enum Difficulty {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced");

    private final String label;

    Difficulty(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Difficulty parse(String text) {
        if (text == null || text.isBlank()) {
            return INTERMEDIATE;
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "beginner", "b" -> BEGINNER;
            case "advanced", "a" -> ADVANCED;
            default -> INTERMEDIATE;
        };
    }
}
