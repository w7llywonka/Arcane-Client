package dev.arcane.loader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {
    }

    static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            quote(text, out);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                quote(entry.getKey().toString(), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof Iterable<?> values) {
            out.append('[');
            boolean first = true;
            for (Object item : values) {
                if (!first) out.append(',');
                first = false;
                write(item, out);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
        }
    }

    private static void quote(String text, StringBuilder out) {
        out.append('"');
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    static Map<String, Object> parseObject(String input) {
        Object value = new Parser(input).parse();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected JSON object");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) result.put(entry.getKey().toString(), entry.getValue());
        return result;
    }

    private static final class Parser {
        private final String input;
        private int cursor;

        private Parser(String input) {
            this.input = input;
        }

        private Object parse() {
            Object value = value();
            whitespace();
            if (cursor != input.length()) throw failure("Unexpected trailing content");
            return value;
        }

        private Object value() {
            whitespace();
            if (cursor >= input.length()) throw failure("Unexpected end of JSON");
            return switch (input.charAt(cursor)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            cursor++;
            Map<String, Object> map = new LinkedHashMap<>();
            whitespace();
            if (take('}')) return map;
            while (true) {
                whitespace();
                if (cursor >= input.length() || input.charAt(cursor) != '"') throw failure("Expected object key");
                String key = string();
                whitespace();
                expect(':');
                map.put(key, value());
                whitespace();
                if (take('}')) return map;
                expect(',');
            }
        }

        private List<Object> array() {
            cursor++;
            List<Object> list = new ArrayList<>();
            whitespace();
            if (take(']')) return list;
            while (true) {
                list.add(value());
                whitespace();
                if (take(']')) return list;
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (cursor < input.length()) {
                char c = input.charAt(cursor++);
                if (c == '"') return value.toString();
                if (c != '\\') {
                    value.append(c);
                    continue;
                }
                if (cursor >= input.length()) throw failure("Incomplete escape");
                char escaped = input.charAt(cursor++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (cursor + 4 > input.length()) throw failure("Incomplete Unicode escape");
                        value.append((char) Integer.parseInt(input.substring(cursor, cursor + 4), 16));
                        cursor += 4;
                    }
                    default -> throw failure("Invalid escape");
                }
            }
            throw failure("Unterminated string");
        }

        private Object number() {
            int start = cursor;
            if (take('-')) {
                // Optional sign consumed.
            }
            while (cursor < input.length() && Character.isDigit(input.charAt(cursor))) cursor++;
            boolean decimal = false;
            if (take('.')) {
                decimal = true;
                while (cursor < input.length() && Character.isDigit(input.charAt(cursor))) cursor++;
            }
            if (cursor < input.length() && (input.charAt(cursor) == 'e' || input.charAt(cursor) == 'E')) {
                decimal = true;
                cursor++;
                if (cursor < input.length() && (input.charAt(cursor) == '+' || input.charAt(cursor) == '-')) cursor++;
                while (cursor < input.length() && Character.isDigit(input.charAt(cursor))) cursor++;
            }
            if (start == cursor) throw failure("Expected value");
            String text = input.substring(start, cursor);
            if (decimal) return Double.parseDouble(text);
            return Long.parseLong(text);
        }

        private Object literal(String expected, Object value) {
            if (!input.startsWith(expected, cursor)) throw failure("Invalid literal");
            cursor += expected.length();
            return value;
        }

        private void whitespace() {
            while (cursor < input.length() && Character.isWhitespace(input.charAt(cursor))) cursor++;
        }

        private boolean take(char expected) {
            if (cursor < input.length() && input.charAt(cursor) == expected) {
                cursor++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw failure("Expected '" + expected + "'");
        }

        private IllegalArgumentException failure(String message) {
            return new IllegalArgumentException(message + " at character " + cursor);
        }
    }
}
