package com.randomjava.lib;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free JSON reader/writer.
 *
 * <p>The whole suite deliberately avoids external jars so that every project
 * compiles with nothing but a JDK. This class is the only serialization layer:
 * projects return plain {@link Map}/{@link List}/String/Number/Boolean values
 * from {@code api(...)} and this turns them into JSON for the browser.
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------
    // Building helpers
    // ------------------------------------------------------------------

    /** Builds an ordered map from alternating key/value arguments. */
    public static Map<String, Object> map(Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("map() needs an even number of arguments");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            m.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return m;
    }

    /** Shorthand for an error payload the browser shell knows how to display. */
    public static Map<String, Object> error(String message) {
        return map("ok", false, "error", message);
    }

    /** Shorthand for a success payload. */
    public static Map<String, Object> ok(Object... keyValuePairs) {
        Map<String, Object> m = map(keyValuePairs);
        m.put("ok", true);
        return m;
    }

    /**
     * Turns a character grid into nested lists of one-character strings.
     *
     * <p>A {@code char[][]} serializes to the right JSON, but it leaves the
     * Java-side value a raw array while the browser sees a list of lists. Going
     * through this keeps the in-process type the same shape as the wire format,
     * so tests and callers can read a board without knowing which side they are
     * on.
     */
    public static List<List<String>> grid(char[][] cells) {
        List<List<String>> rows = new ArrayList<>(cells.length);
        for (char[] row : cells) {
            List<String> out = new ArrayList<>(row.length);
            for (char cell : row) {
                out.add(String.valueOf(cell));
            }
            rows.add(out);
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Reading values out of a request map
    // ------------------------------------------------------------------

    public static String str(Map<String, Object> src, String key, String fallback) {
        Object v = src == null ? null : src.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    public static double num(Map<String, Object> src, String key, double fallback) {
        Object v = src == null ? null : src.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static int integer(Map<String, Object> src, String key, int fallback) {
        return (int) Math.round(num(src, key, fallback));
    }

    public static boolean bool(Map<String, Object> src, String key, boolean fallback) {
        Object v = src == null ? null : src.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> src, String key) {
        Object v = src == null ? null : src.get(key);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        encode(value, sb);
        return sb.toString();
    }

    private static void encode(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            quote(s, sb);
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            sb.append(Double.isNaN(d) || Double.isInfinite(d) ? "null" : trimNumber(d));
        } else if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                encode(e.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object item : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                encode(item, sb);
            }
            sb.append(']');
        } else if (value.getClass().isArray()) {
            sb.append('[');
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                encode(Array.get(value, i), sb);
            }
            sb.append(']');
        } else {
            quote(String.valueOf(value), sb);
        }
    }

    /** Renders whole doubles as {@code 5} rather than {@code 5.0}. */
    private static String trimNumber(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /** Parses a JSON object. Returns an empty map for blank or invalid input. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readObject(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = new Parser(json).parseValue();
            return parsed instanceof Map ? (Map<String, Object>) parsed : new LinkedHashMap<>();
        } catch (RuntimeException e) {
            return new LinkedHashMap<>();
        }
    }

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalStateException("unexpected end of input");
            }
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> out = new LinkedHashMap<>();
            pos++; // consume '{'
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return out;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                out.put(key, parseValue());
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return out;
                }
                if (c != ',') {
                    throw new IllegalStateException("expected , or } at " + pos);
                }
            }
        }

        private List<Object> parseArray() {
            List<Object> out = new ArrayList<>();
            pos++; // consume '['
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return out;
            }
            while (true) {
                out.add(parseValue());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return out;
                }
                if (c != ',') {
                    throw new IllegalStateException("expected , or ] at " + pos);
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char esc = next();
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalStateException("bad escape \\" + esc);
                }
            }
        }

        private Object parseNumber() {
            int start = pos;
            while (pos < src.length() && "+-0123456789.eE".indexOf(src.charAt(pos)) >= 0) {
                pos++;
            }
            String text = src.substring(start, pos);
            if (text.contains(".") || text.contains("e") || text.contains("E")) {
                return Double.parseDouble(text);
            }
            long asLong = Long.parseLong(text);
            // Deliberately not a ternary. Java promotes the branches of a
            // conditional to a common numeric type, so `cond ? (int) x : x`
            // would box every result as a Long and quietly break any caller
            // testing for Integer.
            if (asLong >= Integer.MIN_VALUE && asLong <= Integer.MAX_VALUE) {
                return (int) asLong;
            }
            return asLong;
        }

        private Object parseLiteral(String literal, Object value) {
            if (!src.startsWith(literal, pos)) {
                throw new IllegalStateException("bad literal at " + pos);
            }
            pos += literal.length();
            return value;
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            return pos < src.length() ? src.charAt(pos) : '\0';
        }

        private char next() {
            if (pos >= src.length()) {
                throw new IllegalStateException("unexpected end of input");
            }
            return src.charAt(pos++);
        }

        private void expect(char c) {
            if (next() != c) {
                throw new IllegalStateException("expected " + c + " at " + (pos - 1));
            }
        }
    }
}
