package com.randomjava.projects.simplecalculator;

import com.randomjava.lib.ConsoleUI;
import com.randomjava.lib.Difficulty;
import com.randomjava.lib.Json;
import com.randomjava.lib.Kind;
import com.randomjava.lib.Meta;
import com.randomjava.lib.Project;

import java.util.Map;

/**
 * Simple Calculator - evaluates a whole arithmetic expression rather than
 * asking for two numbers and an operator, which makes it useful as more than a
 * demo.
 *
 * <p>The evaluator is a small recursive descent parser with the usual
 * precedence: parentheses, then unary sign, then {@code ^} (right associative),
 * then {@code * / %}, then {@code + -}.
 */
public final class SimpleCalculator implements Project {

    public static final Meta META = new Meta(1, "simple-calculator", "Simple Calculator", "Beginner Friendly", Kind.TOOL,
            Difficulty.BEGINNER, "Evaluate an arithmetic expression with +, -, *, /, %, ^ and parentheses.",
            "", true);

    @Override
    public Meta meta() {
        return META;
    }

    // ------------------------------------------------------------------
    // Evaluation
    // ------------------------------------------------------------------

    /** Evaluates an expression, throwing {@link IllegalArgumentException} on bad input. */
    public double evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Type an expression, for example 2 * (3 + 4)");
        }
        return new Parser(expression).parse();
    }

    /** Trims the trailing {@code .0} so whole answers read as whole numbers. */
    public static String format(double value) {
        if (Double.isNaN(value)) {
            return "not a number";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "infinity" : "-infinity";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        String text = String.format("%.10f", value);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    /**
     * Recursive descent over the expression grammar:
     * <pre>
     *   expression := term   (( '+' | '-' ) term)*
     *   term       := factor (( '*' | '/' | '%' ) factor)*
     *   factor     := ('+' | '-') factor | atom ('^' factor)?
     *   atom       := number | '(' expression ')'
     * </pre>
     */
    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        double parse() {
            double value = expression();
            skipSpace();
            if (pos < src.length()) {
                throw new IllegalArgumentException(
                        "Unexpected '" + src.charAt(pos) + "' at position " + (pos + 1));
            }
            return value;
        }

        private double expression() {
            double value = term();
            while (true) {
                if (eat('+')) {
                    value += term();
                } else if (eat('-')) {
                    value -= term();
                } else {
                    return value;
                }
            }
        }

        private double term() {
            double value = factor();
            while (true) {
                if (eat('*')) {
                    value *= factor();
                } else if (eat('/')) {
                    double divisor = factor();
                    if (divisor == 0) {
                        throw new IllegalArgumentException("Division by zero");
                    }
                    value /= divisor;
                } else if (eat('%')) {
                    double divisor = factor();
                    if (divisor == 0) {
                        throw new IllegalArgumentException("Cannot take a remainder by zero");
                    }
                    value %= divisor;
                } else {
                    return value;
                }
            }
        }

        private double factor() {
            if (eat('+')) {
                return factor();
            }
            if (eat('-')) {
                return -factor();
            }
            double value = atom();
            if (eat('^')) {
                value = Math.pow(value, factor());
            }
            return value;
        }

        private double atom() {
            if (eat('(')) {
                double value = expression();
                if (!eat(')')) {
                    throw new IllegalArgumentException("Missing a closing parenthesis");
                }
                return value;
            }
            skipSpace();
            int start = pos;
            while (pos < src.length()
                    && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                String where = pos < src.length()
                        ? "'" + src.charAt(pos) + "' at position " + (pos + 1)
                        : "the end of the expression";
                throw new IllegalArgumentException("Expected a number but found " + where);
            }
            try {
                return Double.parseDouble(src.substring(start, pos));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + src.substring(start, pos) + "' is not a number");
            }
        }

        private void skipSpace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private boolean eat(char symbol) {
            skipSpace();
            if (pos < src.length() && src.charAt(pos) == symbol) {
                pos++;
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Front ends
    // ------------------------------------------------------------------

    @Override
    public void runText(ConsoleUI io) {
        io.muted("Examples:  2 + 3 * 4    (8 - 3) ^ 2    22 % 7    -5 + 2.5");
        io.muted("Blank line to finish.");
        while (true) {
            String input = io.ask("expr:");
            if (input.isEmpty()) {
                return;
            }
            try {
                io.result(input + "  =", format(evaluate(input)));
            } catch (RuntimeException e) {
                io.error(e.getMessage());
            }
        }
    }

    @Override
    public Object api(String action, Map<String, Object> body) {
        if (!"compute".equals(action)) {
            return Json.error("Unknown action: " + action);
        }
        String expression = Json.str(body, "input", "");
        try {
            return Json.ok("result", format(evaluate(expression)), "detail", expression);
        } catch (RuntimeException e) {
            return Json.error(e.getMessage());
        }
    }
}
