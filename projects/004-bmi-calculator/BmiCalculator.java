package com.randomjava.projects.bmicalculator;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.Map;

/**
 * BMI Calculator - body mass index in metric or imperial, with the healthy
 * weight range for the given height so the number has some context.
 *
 * <p>BMI is a population-level screening measure. It takes no account of muscle
 * mass, build or body composition, so an athlete and a sedentary person of the
 * same height and weight get the same score. The wording below reflects that.
 */
public final class BmiCalculator implements Project {

    public static final Meta META = new Meta(4, "bmi-calculator", "BMI Calculator", "Beginner Friendly", Kind.TOOL,
            Difficulty.BEGINNER, "Work out body mass index from height and weight, in metric or imperial.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    /** A computed result: the index, the band it falls in, and context. */
    public record Result(double bmi, String band, double healthyLow, double healthyHigh, String units) {
    }

    // ------------------------------------------------------------------
    // Calculation
    // ------------------------------------------------------------------

    /**
     * @param heightCm height in centimetres
     * @param weightKg mass in kilograms
     */
    public Result metric(double heightCm, double weightKg) {
        if (heightCm <= 0 || weightKg <= 0) {
            throw new IllegalArgumentException("Height and weight both need to be greater than zero.");
        }
        if (heightCm < 50 || heightCm > 280) {
            throw new IllegalArgumentException("That height looks wrong - expected roughly 50 to 280 cm.");
        }
        double metres = heightCm / 100.0;
        double bmi = weightKg / (metres * metres);
        return new Result(bmi, band(bmi), 18.5 * metres * metres, 24.9 * metres * metres, "kg");
    }

    /**
     * @param inches total height in inches
     * @param pounds mass in pounds
     */
    public Result imperial(double inches, double pounds) {
        if (inches <= 0 || pounds <= 0) {
            throw new IllegalArgumentException("Height and weight both need to be greater than zero.");
        }
        Result metricResult = metric(inches * 2.54, pounds * 0.45359237);
        return new Result(metricResult.bmi(), metricResult.band(),
                metricResult.healthyLow() / 0.45359237,
                metricResult.healthyHigh() / 0.45359237, "lb");
    }

    /** The standard adult WHO bands. */
    public static String band(double bmi) {
        if (bmi < 16.0) {
            return "severely underweight";
        }
        if (bmi < 18.5) {
            return "underweight";
        }
        if (bmi < 25.0) {
            return "healthy weight";
        }
        if (bmi < 30.0) {
            return "overweight";
        }
        if (bmi < 35.0) {
            return "obese class I";
        }
        if (bmi < 40.0) {
            return "obese class II";
        }
        return "obese class III";
    }

    private static String round(double value, int places) {
        return String.format("%." + places + "f", value);
    }

    private String detail(Result result) {
        return "Band: " + result.band()
                + "\nHealthy range for this height: " + round(result.healthyLow(), 1)
                + " to " + round(result.healthyHigh(), 1) + " " + result.units()
                + "\nBMI is a rough screening measure and ignores build and muscle mass.";
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        boolean useMetric = io.askYesNo("Use metric (cm and kg)?", true);
        Result result;
        if (useMetric) {
            double height = io.askDouble("height in cm:", 175);
            double weight = io.askDouble("weight in kg:", 70);
            result = metric(height, weight);
        } else {
            double feet = io.askDouble("height, feet:", 5);
            double inches = io.askDouble("height, extra inches:", 9);
            double pounds = io.askDouble("weight in lb:", 154);
            result = imperial(feet * 12 + inches, pounds);
        }
        io.result("BMI", round(result.bmi(), 1) + "  (" + result.band() + ")");
        io.muted("Healthy range for this height: " + round(result.healthyLow(), 1)
                + " to " + round(result.healthyHigh(), 1) + " " + result.units());
        io.muted("BMI ignores build and muscle mass, so read it as a rough guide only.");
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) {
            return Json.error("Unknown action: " + action);
        }
        try {
            boolean useMetric = !"imperial".equalsIgnoreCase(Json.str(body, "units", "metric"));
            Result result = useMetric
                    ? metric(Json.num(body, "height", 0), Json.num(body, "weight", 0))
                    : imperial(Json.num(body, "height", 0), Json.num(body, "weight", 0));
            return Json.ok("result", round(result.bmi(), 1) + "  " + result.band(),
                    "detail", detail(result));
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }
}
