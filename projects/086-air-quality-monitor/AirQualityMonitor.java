package com.randomjava.projects.airqualitymonitor;

import com.randomjava.lib.*;
import java.util.*;

/**
 * Air Quality Monitor - converts a pollutant reading into a US EPA AQI band.
 *
 * <p>AQI is not a straight scale. Each pollutant has its own breakpoints, and
 * the index is linearly interpolated within whichever band the reading falls
 * in, so the same number means the same health risk across pollutants. A naive
 * "concentration times a constant" gets every value wrong except by accident.
 *
 * <p>When several pollutants are given, the overall AQI is the <em>worst</em> of
 * them, not the average - that is what the standard specifies, and averaging
 * would hide a single dangerous reading.
 */
public final class AirQualityMonitor implements Project {

    public static final Meta META = new Meta(86, "air-quality-monitor", "Air Quality Monitor", "Data Science", Kind.TOOL,
            Difficulty.INTERMEDIATE, "Turn pollutant readings into an AQI band and health advice.",
            "", true);

    /** {concentration low, concentration high, aqi low, aqi high} per band. */
    private static final Map<String, double[][]> BREAKPOINTS = new LinkedHashMap<>();
    static {
        BREAKPOINTS.put("pm25", new double[][]{
            {0.0, 12.0, 0, 50}, {12.1, 35.4, 51, 100}, {35.5, 55.4, 101, 150},
            {55.5, 150.4, 151, 200}, {150.5, 250.4, 201, 300}, {250.5, 500.4, 301, 500}});
        BREAKPOINTS.put("pm10", new double[][]{
            {0, 54, 0, 50}, {55, 154, 51, 100}, {155, 254, 101, 150},
            {255, 354, 151, 200}, {355, 424, 201, 300}, {425, 604, 301, 500}});
        BREAKPOINTS.put("o3", new double[][]{
            {0, 54, 0, 50}, {55, 70, 51, 100}, {71, 85, 101, 150},
            {86, 105, 151, 200}, {106, 200, 201, 300}});
        BREAKPOINTS.put("co", new double[][]{
            {0.0, 4.4, 0, 50}, {4.5, 9.4, 51, 100}, {9.5, 12.4, 101, 150},
            {12.5, 15.4, 151, 200}, {15.5, 30.4, 201, 300}, {30.5, 50.4, 301, 500}});
        BREAKPOINTS.put("no2", new double[][]{
            {0, 53, 0, 50}, {54, 100, 51, 100}, {101, 360, 101, 150},
            {361, 649, 151, 200}, {650, 1249, 201, 300}, {1250, 2049, 301, 500}});
        BREAKPOINTS.put("so2", new double[][]{
            {0, 35, 0, 50}, {36, 75, 51, 100}, {76, 185, 101, 150},
            {186, 304, 151, 200}, {305, 604, 201, 300}, {605, 1004, 301, 500}});
    }

    @Override public Meta meta() { return META; }

    public Set<String> pollutants() { return BREAKPOINTS.keySet(); }

    /** Linear interpolation inside the matching band, exactly as the EPA defines. */
    public int aqi(String pollutant, double concentration) {
        String key = pollutant.trim().toLowerCase(Locale.ROOT).replace(".", "").replace("_", "");
        double[][] bands = BREAKPOINTS.get(key);
        if (bands == null) {
            throw new IllegalArgumentException("'" + pollutant + "' is not known. Try: "
                    + String.join(", ", BREAKPOINTS.keySet()));
        }
        if (concentration < 0) { throw new IllegalArgumentException("A concentration cannot be negative."); }
        for (double[] band : bands) {
            if (concentration <= band[1]) {
                double low = Math.max(band[0], 0);
                return (int) Math.round((band[3] - band[2]) / (band[1] - low) * (concentration - low) + band[2]);
            }
        }
        return 500;
    }

    public static String band(int aqi) {
        if (aqi <= 50) { return "Good"; }
        if (aqi <= 100) { return "Moderate"; }
        if (aqi <= 150) { return "Unhealthy for sensitive groups"; }
        if (aqi <= 200) { return "Unhealthy"; }
        if (aqi <= 300) { return "Very unhealthy"; }
        return "Hazardous";
    }

    public static String advice(int aqi) {
        if (aqi <= 50) { return "Air quality is satisfactory and poses little or no risk."; }
        if (aqi <= 100) { return "Unusually sensitive people should consider limiting long outdoor exertion."; }
        if (aqi <= 150) { return "People with heart or lung disease, older adults and children should limit exertion."; }
        if (aqi <= 200) { return "Everyone may begin to experience effects; sensitive groups more seriously."; }
        if (aqi <= 300) { return "Health alert: everyone may experience more serious effects."; }
        return "Health warning of emergency conditions. Everyone is likely to be affected.";
    }

    /** Parses "pm25 35.5, o3 60" and returns the worst reading, as the standard requires. */
    public Map<String, Integer> readings(String text) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String chunk : text.split("[,;\\n]+")) {
            String piece = chunk.trim();
            if (piece.isEmpty()) { continue; }
            String[] bits = piece.split("[\\s:=]+");
            if (bits.length < 2) {
                throw new IllegalArgumentException("Write each reading as: pm25 35.5");
            }
            try {
                out.put(bits[0].toLowerCase(Locale.ROOT), aqi(bits[0], Double.parseDouble(bits[1])));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + bits[1] + "' is not a number.");
            }
        }
        if (out.isEmpty()) { throw new IllegalArgumentException("No readings given."); }
        return out;
    }

    private String detail(Map<String, Integer> readings) {
        int worst = 0;
        String driver = "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : readings.entrySet()) {
            sb.append("\n  ").append(e.getKey()).append("  AQI ").append(e.getValue())
              .append("  ").append(band(e.getValue()));
            if (e.getValue() > worst) { worst = e.getValue(); driver = e.getKey(); }
        }
        return "Overall AQI is the worst single pollutant, not the average."
                + sb + "\n\nDriven by " + driver + ".\n" + advice(worst);
    }

    @Override public void runText(ConsoleUI io) {
        io.muted("Readings like: pm25 35.5, o3 60. Known: " + String.join(", ", pollutants()));
        while (true) {
            String input = io.ask("readings:");
            if (input.isEmpty()) { return; }
            try {
                Map<String, Integer> readings = readings(input);
                int worst = Collections.max(readings.values());
                io.result("AQI " + worst, band(worst));
                io.println("  " + detail(readings).replace("\n", "\n  "));
            } catch (RuntimeException e) { io.error(e.getMessage()); }
        }
    }

    @Override public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) { return Json.error("Unknown action: " + action); }
        try {
            Map<String, Integer> readings = readings(Json.str(body, "input", ""));
            int worst = Collections.max(readings.values());
            return Json.ok("result", "AQI " + worst + "  " + band(worst), "detail", detail(readings));
        } catch (RuntimeException e) { return Json.error(e.getMessage()); }
    }
}
