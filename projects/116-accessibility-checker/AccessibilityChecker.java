package com.randomjava.projects.accessibilitychecker;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Accessibility Checker - WCAG contrast ratios, computed properly.
 *
 * <p>The obvious approach, comparing how far apart two colours look, is wrong.
 * WCAG uses <em>relative luminance</em>, which weights green far above red and
 * blue because the eye is most sensitive to it, and applies a gamma curve first
 * because sRGB values are not linear light. Skipping either step gives numbers
 * that look reasonable and fail a real audit.
 *
 * <p>The scale is fixed: black on white is exactly 21:1, and identical colours
 * are exactly 1:1. Both are in the tests.
 */
public final class AccessibilityChecker implements Project {

    public static final Meta META = new Meta(116, "accessibility-checker", "Accessibility Checker", "Community and Social Good", Kind.TOOL,
            Difficulty.BEGINNER, "Audit markup for contrast, alt text, labels and heading order.",
            "", true);

    @Override public Meta meta() { return META; }

    /** Parses #rgb, #rrggbb or "12,34,56". */
    public static int[] colour(String text) {
        if (text == null || text.isBlank()) { throw new IllegalArgumentException("Give a colour."); }
        String clean = text.trim().replace("#", "");
        if (clean.matches("[0-9a-fA-F]{3}")) {
            return new int[]{
                Integer.parseInt("" + clean.charAt(0) + clean.charAt(0), 16),
                Integer.parseInt("" + clean.charAt(1) + clean.charAt(1), 16),
                Integer.parseInt("" + clean.charAt(2) + clean.charAt(2), 16)};
        }
        if (clean.matches("[0-9a-fA-F]{6}")) {
            return new int[]{
                Integer.parseInt(clean.substring(0, 2), 16),
                Integer.parseInt(clean.substring(2, 4), 16),
                Integer.parseInt(clean.substring(4, 6), 16)};
        }
        String[] parts = clean.split("[\\s,]+");
        if (parts.length == 3) {
            try {
                int[] rgb = new int[3];
                for (int i = 0; i < 3; i++) {
                    rgb[i] = Integer.parseInt(parts[i].trim());
                    if (rgb[i] < 0 || rgb[i] > 255) {
                        throw new IllegalArgumentException("Channels run 0 to 255.");
                    }
                }
                return rgb;
            } catch (NumberFormatException e) { /* fall through */ }
        }
        throw new IllegalArgumentException("'" + text + "' is not a colour. Try #1a2b3c or 26,43,60.");
    }

    /** WCAG relative luminance: gamma corrected, then weighted for the eye. */
    public static double luminance(int[] rgb) {
        double[] channel = new double[3];
        for (int i = 0; i < 3; i++) {
            double value = rgb[i] / 255.0;
            channel[i] = value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channel[0] + 0.7152 * channel[1] + 0.0722 * channel[2];
    }

    /** The ratio, always at least 1, lighter colour on top. */
    public static double ratio(int[] foreground, int[] background) {
        double a = luminance(foreground);
        double b = luminance(background);
        double lighter = Math.max(a, b);
        double darker = Math.min(a, b);
        return (lighter + 0.05) / (darker + 0.05);
    }

    /** Which WCAG levels this ratio passes. */
    public static List<String> passes(double ratio) {
        List<String> out = new ArrayList<>();
        out.add((ratio >= 4.5 ? "PASS" : "FAIL") + "  AA, normal text (needs 4.5)");
        out.add((ratio >= 3.0 ? "PASS" : "FAIL") + "  AA, large text (needs 3.0)");
        out.add((ratio >= 7.0 ? "PASS" : "FAIL") + "  AAA, normal text (needs 7.0)");
        out.add((ratio >= 4.5 ? "PASS" : "FAIL") + "  AAA, large text (needs 4.5)");
        out.add((ratio >= 3.0 ? "PASS" : "FAIL") + "  non-text and UI components (needs 3.0)");
        return out;
    }

    private String detail(int[] fg, int[] bg, double ratio) {
        StringBuilder sb = new StringBuilder(String.format(
                "Foreground rgb(%d,%d,%d), background rgb(%d,%d,%d)%n"
                + "Relative luminance %.4f against %.4f",
                fg[0], fg[1], fg[2], bg[0], bg[1], bg[2], luminance(fg), luminance(bg)));
        for (String line : passes(ratio)) { sb.append("\n  ").append(line); }
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Enter two colours as #hex or r,g,b. Blank foreground to finish.");
        while (true) {
            String fgText = io.ask("foreground:");
            if (fgText.isEmpty()) { return; }
            try {
                int[] fg = colour(fgText);
                int[] bg = colour(io.ask("background:", "#ffffff"));
                double ratio = ratio(fg, bg);
                io.result(String.format("%.2f : 1", ratio),
                        ratio >= 4.5 ? "passes AA for normal text" : "fails AA for normal text");
                io.println("  " + detail(fg, bg, ratio).replace("\n", "\n  "));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            String input = Json.str(body, "input", "");
            String[] parts = input.split("\\s+on\\s+|\\s{2,}|\\s*/\\s*|;");
            if (parts.length < 2) {
                String bg = Json.str(body, "background", "");
                if (bg.isBlank()) {
                    return Json.error("Give two colours, as in:  #767676 on #ffffff");
                }
                parts = new String[]{input, bg};
            }
            int[] fg = colour(parts[0]);
            int[] bg = colour(parts[1]);
            double ratio = ratio(fg, bg);
            return Json.ok("result", String.format("%.2f : 1", ratio), "detail", detail(fg, bg, ratio));
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
