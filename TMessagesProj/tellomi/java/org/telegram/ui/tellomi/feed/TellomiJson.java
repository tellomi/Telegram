package org.telegram.ui.tellomi.feed;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON used by the discover client. Bool and number stay distinct. */
final class TellomiJson {

    enum Type {
        NULL, BOOL, NUMBER, STRING, ARRAY, OBJECT
    }

    final Type type;
    final boolean boolValue;
    final double numberValue;
    final String stringValue;
    final List<TellomiJson> arrayValue;
    final LinkedHashMap<String, TellomiJson> objectValue;

    private TellomiJson(Type type, boolean boolValue, double numberValue, String stringValue, List<TellomiJson> arrayValue, LinkedHashMap<String, TellomiJson> objectValue) {
        this.type = type;
        this.boolValue = boolValue;
        this.numberValue = numberValue;
        this.stringValue = stringValue;
        this.arrayValue = arrayValue;
        this.objectValue = objectValue;
    }

    static TellomiJson parse(byte[] data) {
        if (data == null) {
            return null;
        }
        Parser parser = new Parser(new String(data, StandardCharsets.UTF_8));
        try {
            TellomiJson value = parser.parseValue();
            parser.skip();
            if (!parser.end()) {
                return null;
            }
            return value;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static byte[] write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            writeString(out, (String) value);
        } else if (value instanceof Boolean) {
            out.append(((Boolean) value) ? "true" : "false");
        } else if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (number == Math.rint(number) && !Double.isInfinite(number) && number <= Long.MAX_VALUE && number >= Long.MIN_VALUE) {
                out.append((long) number);
            } else {
                out.append(number);
            }
        } else if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeValue(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof List) {
            out.append('[');
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(out, item);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                    break;
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        boolean end() {
            return index >= text.length();
        }

        void skip() {
            while (!end()) {
                char c = text.charAt(index);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                    break;
                }
                index++;
            }
        }

        TellomiJson parseValue() {
            skip();
            if (end()) {
                throw new IllegalArgumentException();
            }
            char c = text.charAt(index);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return new TellomiJson(Type.STRING, false, 0, parseString(), null, null);
            }
            if (c == 't' || c == 'f') {
                return new TellomiJson(Type.BOOL, parseLiteral(), 0, null, null, null);
            }
            if (c == 'n') {
                expect("null");
                return new TellomiJson(Type.NULL, false, 0, null, null, null);
            }
            return new TellomiJson(Type.NUMBER, false, parseNumber(), null, null, null);
        }

        private TellomiJson parseObject() {
            index++;
            LinkedHashMap<String, TellomiJson> object = new LinkedHashMap<>();
            skip();
            if (!end() && text.charAt(index) == '}') {
                index++;
                return new TellomiJson(Type.OBJECT, false, 0, null, null, object);
            }
            while (true) {
                skip();
                if (end() || text.charAt(index) != '"') {
                    throw new IllegalArgumentException();
                }
                String key = parseString();
                skip();
                if (end() || text.charAt(index) != ':') {
                    throw new IllegalArgumentException();
                }
                index++;
                object.put(key, parseValue());
                skip();
                if (end()) {
                    throw new IllegalArgumentException();
                }
                char c = text.charAt(index);
                index++;
                if (c == '}') {
                    return new TellomiJson(Type.OBJECT, false, 0, null, null, object);
                }
                if (c != ',') {
                    throw new IllegalArgumentException();
                }
            }
        }

        private TellomiJson parseArray() {
            index++;
            List<TellomiJson> array = new ArrayList<>();
            skip();
            if (!end() && text.charAt(index) == ']') {
                index++;
                return new TellomiJson(Type.ARRAY, false, 0, null, array, null);
            }
            while (true) {
                array.add(parseValue());
                skip();
                if (end()) {
                    throw new IllegalArgumentException();
                }
                char c = text.charAt(index);
                index++;
                if (c == ']') {
                    return new TellomiJson(Type.ARRAY, false, 0, null, array, null);
                }
                if (c != ',') {
                    throw new IllegalArgumentException();
                }
            }
        }

        private String parseString() {
            index++;
            StringBuilder out = new StringBuilder();
            while (!end()) {
                char c = text.charAt(index);
                index++;
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (end()) {
                        throw new IllegalArgumentException();
                    }
                    char esc = text.charAt(index);
                    index++;
                    switch (esc) {
                        case '"':
                        case '\\':
                        case '/':
                            out.append(esc);
                            break;
                        case 'b':
                            out.append('\b');
                            break;
                        case 'f':
                            out.append('\f');
                            break;
                        case 'n':
                            out.append('\n');
                            break;
                        case 'r':
                            out.append('\r');
                            break;
                        case 't':
                            out.append('\t');
                            break;
                        case 'u':
                            if (index + 4 > text.length()) {
                                throw new IllegalArgumentException();
                            }
                            int code = Integer.parseInt(text.substring(index, index + 4), 16);
                            index += 4;
                            out.append((char) code);
                            break;
                        default:
                            throw new IllegalArgumentException();
                    }
                } else {
                    out.append(c);
                }
            }
            throw new IllegalArgumentException();
        }

        private boolean parseLiteral() {
            if (text.startsWith("true", index)) {
                index += 4;
                return true;
            }
            if (text.startsWith("false", index)) {
                index += 5;
                return false;
            }
            throw new IllegalArgumentException();
        }

        private void expect(String literal) {
            if (!text.startsWith(literal, index)) {
                throw new IllegalArgumentException();
            }
            index += literal.length();
        }

        private double parseNumber() {
            int start = index;
            if (!end() && text.charAt(index) == '-') {
                index++;
            }
            if (end() || !isDigit(text.charAt(index))) {
                throw new IllegalArgumentException();
            }
            while (!end() && isDigit(text.charAt(index))) {
                index++;
            }
            if (!end() && text.charAt(index) == '.') {
                index++;
                if (end() || !isDigit(text.charAt(index))) {
                    throw new IllegalArgumentException();
                }
                while (!end() && isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (!end() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                index++;
                if (!end() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                if (end() || !isDigit(text.charAt(index))) {
                    throw new IllegalArgumentException();
                }
                while (!end() && isDigit(text.charAt(index))) {
                    index++;
                }
            }
            return Double.parseDouble(text.substring(start, index));
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }
}
