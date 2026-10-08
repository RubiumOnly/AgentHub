package com.agenthub.orchestration.domain.evaluation;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safe, sandboxed template and condition expression evaluator.
 * Prevents SpEL / reflection / arbitrary code execution vulnerabilities.
 */
public class SafeExpressionEvaluator {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * Resolves a template string or expression against the execution context.
     * If the template is exactly "{{path}}", returns the raw object value.
     */
    public Object resolveTemplate(String template, WorkflowExecutionContext context) {
        if (template == null) {
            return null;
        }
        String trimmed = template.trim();
        if (trimmed.startsWith("{{") && trimmed.endsWith("}}")) {
            String inner = trimmed.substring(2, trimmed.length() - 2).trim();
            // Check if there are no other brackets inside
            if (!inner.contains("{{") && !inner.contains("}}")) {
                return context.resolvePath(inner);
            }
        }

        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(1).trim();
            Object val = context.resolvePath(path);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(val != null ? String.valueOf(val) : ""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolves an entire map of input definitions against the execution context.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveInputs(Map<String, Object> inputs, WorkflowExecutionContext context) {
        if (inputs == null || inputs.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            resolved.put(entry.getKey(), resolveValue(entry.getValue(), context));
        }
        return resolved;
    }

    /**
     * Resolves workflow outputs against the execution context.
     */
    public Map<String, Object> resolveOutputs(Map<String, Object> outputs, WorkflowExecutionContext context) {
        return resolveInputs(outputs, context);
    }

    @SuppressWarnings("unchecked")
    private Object resolveValue(Object value, WorkflowExecutionContext context) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return resolveTemplate((String) value, context);
        } else if (value instanceof Map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                nested.put(String.valueOf(e.getKey()), resolveValue(e.getValue(), context));
            }
            return nested;
        } else if (value instanceof List) {
            List<Object> list = new ArrayList<>();
            for (Object item : (List<?>) value) {
                list.add(resolveValue(item, context));
            }
            return list;
        }
        return value;
    }

    /**
     * Evaluates a boolean condition expression against the execution context.
     * Expressions support:
     * - '==', '!=', '>', '<', '>=', '<='
     * - '&&', '||', '!'
     * - 'true', 'false', 'null', numbers, single/double quoted strings
     * - Context paths e.g. 'steps.gen.status', 'steps.gate.outputs.approved'
     */
    public boolean evaluateCondition(String expression, WorkflowExecutionContext context) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }

        String expr = expression.trim();
        // Unwrap {{ ... }} if user wrapped the whole condition
        if (expr.startsWith("{{") && expr.endsWith("}}")) {
            expr = expr.substring(2, expr.length() - 2).trim();
        }

        try {
            ConditionParser parser = new ConditionParser(expr, context);
            return parser.parseExpression();
        } catch (Exception e) {
            // If condition parsing fails, return false safely
            return false;
        }
    }

    // =========================================================================
    // Lightweight, Safe Recursive-Descent Expression Parser
    // =========================================================================

    private static class ConditionParser {
        private final String input;
        private final WorkflowExecutionContext context;
        private int pos = 0;

        ConditionParser(String input, WorkflowExecutionContext context) {
            this.input = input;
            this.context = context;
        }

        boolean parseExpression() {
            boolean result = parseOr();
            skipWhitespace();
            if (pos < input.length()) {
                throw new IllegalArgumentException("Unexpected trailing tokens: " + input.substring(pos));
            }
            return result;
        }

        private boolean parseOr() {
            boolean left = parseAnd();
            while (true) {
                skipWhitespace();
                if (match("||")) {
                    boolean right = parseAnd();
                    left = left || right;
                } else {
                    break;
                }
            }
            return left;
        }

        private boolean parseAnd() {
            boolean left = parseComparison();
            while (true) {
                skipWhitespace();
                if (match("&&")) {
                    boolean right = parseComparison();
                    left = left && right;
                } else {
                    break;
                }
            }
            return left;
        }

        private boolean parseComparison() {
            skipWhitespace();
            if (match("!")) {
                return !parseComparison();
            }

            Object left = parsePrimary();
            skipWhitespace();

            if (match("==")) {
                Object right = parsePrimary();
                return objectsEqual(left, right);
            } else if (match("!=")) {
                Object right = parsePrimary();
                return !objectsEqual(left, right);
            } else if (match(">=")) {
                Object right = parsePrimary();
                return compareNumbers(left, right) >= 0;
            } else if (match("<=")) {
                Object right = parsePrimary();
                return compareNumbers(left, right) <= 0;
            } else if (match(">")) {
                Object right = parsePrimary();
                return compareNumbers(left, right) > 0;
            } else if (match("<")) {
                Object right = parsePrimary();
                return compareNumbers(left, right) < 0;
            }

            // If no operator, treat left as a boolean
            return toBoolean(left);
        }

        private Object parsePrimary() {
            skipWhitespace();
            if (pos >= input.length()) {
                return null;
            }

            if (match("(")) {
                boolean val = parseOr();
                skipWhitespace();
                if (!match(")")) {
                    throw new IllegalArgumentException("Unclosed parenthesis in expression");
                }
                return val;
            }

            char c = input.charAt(pos);
            if (c == '\'' || c == '"') {
                return parseStringLiteral();
            }

            if (Character.isDigit(c) || (c == '-' && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1)))) {
                return parseNumberLiteral();
            }

            // Word: could be true, false, null, or a path
            int start = pos;
            while (pos < input.length() && isIdentifierChar(input.charAt(pos))) {
                pos++;
            }
            String token = input.substring(start, pos).trim();

            if ("true".equalsIgnoreCase(token)) return true;
            if ("false".equalsIgnoreCase(token)) return false;
            if ("null".equalsIgnoreCase(token)) return null;

            // Dotted path resolution
            return context.resolvePath(token);
        }

        private String parseStringLiteral() {
            char quote = input.charAt(pos++);
            StringBuilder sb = new StringBuilder();
            boolean closed = false;
            while (pos < input.length()) {
                char c = input.charAt(pos++);
                if (c == quote) {
                    closed = true;
                    break;
                }
                if (c == '\\' && pos < input.length()) {
                    sb.append(input.charAt(pos++));
                } else {
                    sb.append(c);
                }
            }
            if (!closed) {
                throw new IllegalArgumentException("Unclosed string literal");
            }
            return sb.toString();
        }

        private Number parseNumberLiteral() {
            int start = pos;
            if (input.charAt(pos) == '-') pos++;
            boolean hasDot = false;
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' && !hasDot) {
                    hasDot = true;
                    pos++;
                } else {
                    break;
                }
            }
            String numStr = input.substring(start, pos);
            if (hasDot) {
                return Double.parseDouble(numStr);
            } else {
                return Long.parseLong(numStr);
            }
        }

        private boolean isIdentifierChar(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-';
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private boolean match(String expected) {
            skipWhitespace();
            if (input.startsWith(expected, pos)) {
                pos += expected.length();
                return true;
            }
            return false;
        }

        private boolean objectsEqual(Object a, Object b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            if (a instanceof Number && b instanceof Number) {
                return ((Number) a).doubleValue() == ((Number) b).doubleValue();
            }
            if (a instanceof Boolean && b instanceof String) {
                return String.valueOf(a).equalsIgnoreCase((String) b);
            }
            if (b instanceof Boolean && a instanceof String) {
                return String.valueOf(b).equalsIgnoreCase((String) a);
            }
            if (a instanceof Number && b instanceof String) {
                try {
                    return ((Number) a).doubleValue() == Double.parseDouble((String) b);
                } catch (NumberFormatException ignored) {}
            }
            if (b instanceof Number && a instanceof String) {
                try {
                    return ((Number) b).doubleValue() == Double.parseDouble((String) a);
                } catch (NumberFormatException ignored) {}
            }
            return Objects.equals(String.valueOf(a), String.valueOf(b));
        }

        private int compareNumbers(Object a, Object b) {
            double da = toDouble(a);
            double db = toDouble(b);
            return Double.compare(da, db);
        }

        private double toDouble(Object obj) {
            if (obj == null) return 0.0;
            if (obj instanceof Number) return ((Number) obj).doubleValue();
            try {
                return Double.parseDouble(String.valueOf(obj));
            } catch (Exception e) {
                return 0.0;
            }
        }

        private boolean toBoolean(Object obj) {
            if (obj == null) return false;
            if (obj instanceof Boolean) return (Boolean) obj;
            if (obj instanceof Number) return ((Number) obj).doubleValue() != 0;
            String str = String.valueOf(obj).trim();
            if ("true".equalsIgnoreCase(str) || "succeeded".equalsIgnoreCase(str) || "pass".equalsIgnoreCase(str)) {
                return true;
            }
            if ("null".equalsIgnoreCase(str) || "undefined".equalsIgnoreCase(str) || "none".equalsIgnoreCase(str) || "nil".equalsIgnoreCase(str)) {
                return false;
            }
            return !str.isEmpty() && !"false".equalsIgnoreCase(str) && !"0".equals(str);
        }
    }
}
