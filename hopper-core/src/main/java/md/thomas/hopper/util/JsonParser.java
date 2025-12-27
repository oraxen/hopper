package md.thomas.hopper.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Lightweight JSON parser using only JDK.
 * <p>
 * Supports parsing JSON objects and arrays. Does not support writing
 * (use string concatenation for simple output).
 */
public final class JsonParser {
    
    private final String json;
    private int pos;
    
    private JsonParser(String json) {
        this.json = json;
        this.pos = 0;
    }
    
    /**
     * Parse a JSON object.
     *
     * @param json the JSON string
     * @return the parsed object as a Map, or null if parsing fails
     */
    @Nullable
    public static Map<String, Object> parseObject(@NotNull String json) {
        try {
            JsonParser parser = new JsonParser(json);
            parser.skipWhitespace();
            return parser.readObject();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Parse a JSON array.
     *
     * @param json the JSON string
     * @return the parsed array as a List, or null if parsing fails
     */
    @Nullable
    public static List<Object> parseArray(@NotNull String json) {
        try {
            JsonParser parser = new JsonParser(json);
            parser.skipWhitespace();
            return parser.readArray();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Escape a string for JSON output.
     */
    @NotNull
    public static String escape(@Nullable String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
    
    /**
     * Get a nested value from a parsed JSON object using dot notation.
     * <p>
     * Example: {@code getString(obj, "data.versions.0.name")}
     */
    @Nullable
    public static String getString(@NotNull Map<String, Object> obj, @NotNull String path) {
        Object value = getPath(obj, path);
        return value != null ? value.toString() : null;
    }
    
    /**
     * Get a nested value from a parsed JSON object using dot notation.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static Object getPath(@NotNull Map<String, Object> obj, @NotNull String path) {
        String[] parts = path.split("\\.");
        Object current = obj;
        
        for (String part : parts) {
            if (current == null) return null;
            
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else if (current instanceof List) {
                try {
                    int index = Integer.parseInt(part);
                    List<?> list = (List<?>) current;
                    current = index < list.size() ? list.get(index) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    /**
     * Get a list from a parsed JSON object.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static List<Object> getList(@NotNull Map<String, Object> obj, @NotNull String path) {
        Object value = getPath(obj, path);
        return value instanceof List ? (List<Object>) value : null;
    }
    
    // ========== Parser Implementation ==========
    
    private void skipWhitespace() {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
            pos++;
        }
    }
    
    private char peek() {
        return pos < json.length() ? json.charAt(pos) : '\0';
    }
    
    private char read() {
        return pos < json.length() ? json.charAt(pos++) : '\0';
    }
    
    private void expect(char c) {
        if (read() != c) {
            throw new IllegalStateException("Expected '" + c + "' at position " + (pos - 1));
        }
    }
    
    private Object readValue() {
        skipWhitespace();
        char c = peek();
        
        if (c == '{') return readObject();
        if (c == '[') return readArray();
        if (c == '"') return readString();
        if (c == 't' || c == 'f') return readBoolean();
        if (c == 'n') return readNull();
        if (c == '-' || Character.isDigit(c)) return readNumber();
        
        throw new IllegalStateException("Unexpected character '" + c + "' at position " + pos);
    }
    
    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        
        if (peek() == '}') {
            read();
            return map;
        }
        
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            Object value = readValue();
            map.put(key, value);
            
            skipWhitespace();
            char c = read();
            if (c == '}') break;
            if (c != ',') throw new IllegalStateException("Expected ',' or '}' at position " + (pos - 1));
        }
        
        return map;
    }
    
    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        
        if (peek() == ']') {
            read();
            return list;
        }
        
        while (true) {
            list.add(readValue());
            
            skipWhitespace();
            char c = read();
            if (c == ']') break;
            if (c != ',') throw new IllegalStateException("Expected ',' or ']' at position " + (pos - 1));
        }
        
        return list;
    }
    
    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        
        while (true) {
            char c = read();
            if (c == '"') break;
            if (c == '\\') {
                c = read();
                switch (c) {
                    case '"': case '\\': case '/': sb.append(c); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        String hex = json.substring(pos, pos + 4);
                        pos += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                        break;
                    default:
                        throw new IllegalStateException("Invalid escape sequence at position " + (pos - 1));
                }
            } else {
                sb.append(c);
            }
        }
        
        return sb.toString();
    }
    
    private Number readNumber() {
        int start = pos;
        
        if (peek() == '-') pos++;
        while (Character.isDigit(peek())) pos++;
        
        boolean isFloat = false;
        if (peek() == '.') {
            isFloat = true;
            pos++;
            while (Character.isDigit(peek())) pos++;
        }
        
        if (peek() == 'e' || peek() == 'E') {
            isFloat = true;
            pos++;
            if (peek() == '+' || peek() == '-') pos++;
            while (Character.isDigit(peek())) pos++;
        }
        
        String num = json.substring(start, pos);
        return isFloat ? Double.parseDouble(num) : Long.parseLong(num);
    }
    
    private Boolean readBoolean() {
        if (json.startsWith("true", pos)) {
            pos += 4;
            return true;
        }
        if (json.startsWith("false", pos)) {
            pos += 5;
            return false;
        }
        throw new IllegalStateException("Invalid boolean at position " + pos);
    }
    
    private Object readNull() {
        if (json.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new IllegalStateException("Invalid null at position " + pos);
    }
}
