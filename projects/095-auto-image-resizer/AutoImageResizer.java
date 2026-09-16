package com.randomjava.projects.autoimageresizer;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Auto Image Resizer - works out target dimensions without distorting anything.
 *
 * <p>Three modes, and the difference between them is what people usually get
 * wrong. <b>Fit</b> shrinks until the whole image is inside the box, leaving
 * space on one axis. <b>Fill</b> grows until the box is covered and the overflow
 * is cropped. <b>Stretch</b> ignores the aspect ratio and squashes the picture,
 * which is almost never what anyone wants and is included only so the report can
 * say how badly it distorts.
 *
 * <p>Scaling up is flagged rather than silently performed: enlarging past the
 * original only invents pixels.
 */
public final class AutoImageResizer implements Project {

    public static final Meta META = new Meta(95, "auto-image-resizer", "Auto Image Resizer", "Automation and Tools", Kind.TOOL,
            Difficulty.BEGINNER, "Batch resize to target dimensions while keeping aspect ratio.",
            "", true);

    public record Size(int width, int height) {
        public double ratio() { return height == 0 ? 0 : (double) width / height; }
        @Override public String toString() { return width + "x" + height; }
    }

    @Override public Meta meta() { return META; }

    /** Largest size fitting inside the box, ratio preserved. */
    public static Size fit(Size source, Size box) {
        double scale = Math.min((double) box.width() / source.width(),
                                (double) box.height() / source.height());
        return new Size(Math.max(1, (int) Math.round(source.width() * scale)),
                        Math.max(1, (int) Math.round(source.height() * scale)));
    }

    /** Smallest size covering the box, ratio preserved. The excess is cropped. */
    public static Size fill(Size source, Size box) {
        double scale = Math.max((double) box.width() / source.width(),
                                (double) box.height() / source.height());
        return new Size(Math.max(1, (int) Math.round(source.width() * scale)),
                        Math.max(1, (int) Math.round(source.height() * scale)));
    }

    /** How far stretching would distort the picture, as a percentage. */
    public static double distortion(Size source, Size box) {
        double ratioChange = box.ratio() / source.ratio();
        return Math.abs(1 - ratioChange) * 100;
    }

    public static Size parse(String text) {
        String clean = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        String[] bits = clean.split("[x*×,]");
        if (bits.length != 2) {
            throw new IllegalArgumentException("Write sizes as 1920x1080.");
        }
        try {
            int w = Integer.parseInt(bits[0]);
            int h = Integer.parseInt(bits[1]);
            if (w <= 0 || h <= 0) { throw new IllegalArgumentException("Dimensions must be positive."); }
            if (w > 100000 || h > 100000) { throw new IllegalArgumentException("That is absurdly large."); }
            return new Size(w, h);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a size like 1920x1080.");
        }
    }

    private String detail(Size source, Size box) {
        Size fitted = fit(source, box);
        Size filled = fill(source, box);
        int cropX = Math.max(0, filled.width() - box.width());
        int cropY = Math.max(0, filled.height() - box.height());
        StringBuilder sb = new StringBuilder(String.format(
                "Source %s (ratio %.3f), target box %s (ratio %.3f)%n%n"
                + "  fit      %-12s fits entirely inside, leaving %d x %d of space%n"
                + "  fill     %-12s covers the box, cropping %d x %d%n"
                + "  stretch  %-12s distorts by %.1f%%",
                source, source.ratio(), box, box.ratio(),
                fitted.toString(), Math.max(0, box.width() - fitted.width()),
                Math.max(0, box.height() - fitted.height()),
                filled.toString(), cropX, cropY,
                box.toString(), distortion(source, box)));
        if (fitted.width() > source.width() || fitted.height() > source.height()) {
            sb.append("\n\nNote: this scales the image up, which invents pixels rather than "
                    + "adding detail. Leave it at the original size unless you have to.");
        }
        double megapixels = fitted.width() * (double) fitted.height() / 1_000_000;
        sb.append(String.format("%nFitted result is %.2f megapixels, %.0f%% of the original.",
                megapixels, 100.0 * fitted.width() * fitted.height()
                        / (source.width() * (double) source.height())));
        return sb.toString();
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Enter a source size then a target box, as 1920x1080. Blank to finish.");
        while (true) {
            String source = io.ask("source size:");
            if (source.isEmpty()) { return; }
            try {
                Size from = parse(source);
                Size box = parse(io.ask("target box:", "800x800"));
                io.result(fit(from, box).toString(), "fitted inside " + box);
                io.println("  " + detail(from, box).replace("\n", "\n  "));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            String input = Json.str(body, "input", "");
            String[] parts = input.split("\\s+(?:into|to|in)\\s+|\\s{2,}|\\s*->\\s*|;");
            if (parts.length < 2) {
                String box = Json.str(body, "box", "");
                if (box.isBlank()) {
                    return Json.error("Give both sizes, as in:  1920x1080 into 800x800");
                }
                parts = new String[]{input, box};
            }
            Size source = parse(parts[0]);
            Size box = parse(parts[1]);
            return Json.ok("result", "fit " + fit(source, box) + "   fill " + fill(source, box),
                    "detail", detail(source, box));
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
