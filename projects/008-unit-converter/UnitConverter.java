package com.randomjava.projects.unitconverter;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unit Converter - length, mass, volume, speed, data and temperature.
 *
 * <p>Everything except temperature is a simple ratio, so each unit is stored as
 * its size in one base unit and conversion is a multiply then a divide.
 * Temperature needs offsets as well as scaling, so it is handled separately.
 */
public final class UnitConverter implements Project {

    public static final Meta META = new Meta(8, "unit-converter", "Unit Converter", "Beginner Friendly", Kind.TOOL,
            Difficulty.BEGINNER, "Convert length, mass, temperature, volume, speed and data sizes.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    /** Ratio-based categories: unit name to how many base units it is worth. */
    private static final Map<String, Map<String, Double>> RATIOS = new LinkedHashMap<>();

    static {
        RATIOS.put("length", ordered(
                "mm", 0.001, "cm", 0.01, "m", 1.0, "km", 1000.0,
                "in", 0.0254, "ft", 0.3048, "yd", 0.9144, "mi", 1609.344,
                "nmi", 1852.0));
        RATIOS.put("mass", ordered(
                "mg", 0.000001, "g", 0.001, "kg", 1.0, "t", 1000.0,
                "oz", 0.028349523125, "lb", 0.45359237, "st", 6.35029318));
        RATIOS.put("volume", ordered(
                "ml", 0.001, "l", 1.0, "m3", 1000.0,
                "tsp", 0.00492892159375, "tbsp", 0.01478676478125,
                "cup", 0.2365882365, "pt", 0.473176473,
                "qt", 0.946352946, "gal", 3.785411784));
        RATIOS.put("speed", ordered(
                "m/s", 1.0, "km/h", 0.2777777777777778, "mph", 0.44704,
                "kn", 0.5144444444444445, "ft/s", 0.3048));
        RATIOS.put("data", ordered(
                "bit", 0.125, "B", 1.0, "KB", 1024.0, "MB", 1048576.0,
                "GB", 1073741824.0, "TB", 1099511627776.0));
    }

    private static final List<String> TEMPERATURE_UNITS = List.of("C", "F", "K");

    private static Map<String, Double> ordered(Object... pairs) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Double) pairs[i + 1]);
        }
        return map;
    }

    // ------------------------------------------------------------------
    // Core
    // ------------------------------------------------------------------

    public List<String> categories() {
        List<String> out = new ArrayList<>(RATIOS.keySet());
        out.add("temperature");
        return out;
    }

    public List<String> unitsFor(String category) {
        if ("temperature".equalsIgnoreCase(category)) {
            return TEMPERATURE_UNITS;
        }
        Map<String, Double> units = RATIOS.get(category.toLowerCase(Locale.ROOT));
        if (units == null) {
            throw new IllegalArgumentException("No category called '" + category + "'.");
        }
        return new ArrayList<>(units.keySet());
    }

    /** Converts {@code value} from one unit to another within a category. */
    public double convert(String category, String from, String to, double value) {
        if ("temperature".equalsIgnoreCase(category)) {
            return convertTemperature(from, to, value);
        }
        Map<String, Double> units = RATIOS.get(category.toLowerCase(Locale.ROOT));
        if (units == null) {
            throw new IllegalArgumentException("No category called '" + category + "'.");
        }
        Double fromRatio = units.get(from);
        Double toRatio = units.get(to);
        if (fromRatio == null) {
            throw new IllegalArgumentException("'" + from + "' is not a " + category + " unit.");
        }
        if (toRatio == null) {
            throw new IllegalArgumentException("'" + to + "' is not a " + category + " unit.");
        }
        return value * fromRatio / toRatio;
    }

    /** Temperature needs offsets, so it goes via Celsius rather than a ratio. */
    private double convertTemperature(String from, String to, double value) {
        double celsius = switch (from.toUpperCase(Locale.ROOT)) {
            case "C" -> value;
            case "F" -> (value - 32) * 5.0 / 9.0;
            case "K" -> value - 273.15;
            default -> throw new IllegalArgumentException("Use C, F or K, not '" + from + "'.");
        };
        return switch (to.toUpperCase(Locale.ROOT)) {
            case "C" -> celsius;
            case "F" -> celsius * 9.0 / 5.0 + 32;
            case "K" -> celsius + 273.15;
            default -> throw new IllegalArgumentException("Use C, F or K, not '" + to + "'.");
        };
    }

    public static String format(double value) {
        if (value != 0 && (Math.abs(value) < 0.0001 || Math.abs(value) >= 1e12)) {
            return String.format("%.6g", value);
        }
        String text = String.format("%.6f", value);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        while (true) {
            List<String> categories = categories();
            int choice = io.menu("Convert what?", categories);
            if (choice < 0) {
                return;
            }
            String category = categories.get(choice);
            List<String> units = unitsFor(category);
            io.muted("Units: " + String.join(", ", units));
            String from = io.ask("from unit:", units.get(0));
            String to = io.ask("to unit:", units.get(Math.min(1, units.size() - 1)));
            double value = io.askDouble("value:", 1);
            try {
                double result = convert(category, from, to, value);
                io.result(format(value) + " " + from + "  =", format(result) + " " + to);
            } catch (RuntimeException e) {
                io.error(e.getMessage());
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        try {
            switch (action) {
                case "units" -> {
                    String category = Json.str(body, "category", "length");
                    return Json.ok("units", unitsFor(category), "category", category,
                            "categories", categories());
                }
                case "compute", "convert" -> {
                    String category = Json.str(body, "category", "length");
                    String from = Json.str(body, "from", "");
                    String to = Json.str(body, "to", "");
                    double value = Json.num(body, "value", 0);
                    double result = convert(category, from, to, value);
                    return Json.ok(
                            "result", format(result) + " " + to,
                            "detail", format(value) + " " + from + " converted to " + to
                                    + "\nCategory: " + category,
                            "units", unitsFor(category));
                }
                default -> {
                    return Json.error("Unknown action: " + action);
                }
            }
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }
}
