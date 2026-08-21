package com.heartbeat.ping.service.export;

import java.util.List;

/**
 * Minimal RFC 4180 CSV writer: quotes a field only when it contains a comma, quote, or newline.
 *
 * <p>Also guards against CSV/formula injection (OWASP): several exported fields (monitor name,
 * tags, incident notes) are user-controlled, and a value like {@code =cmd|'/c calc'!A1} is
 * executed as a formula by Excel/Sheets when the file is opened rather than shown as text. Any
 * field starting with {@code = + - @} or a tab/CR (which Excel also treats as a formula prefix)
 * is neutralized by prefixing it with a leading apostrophe, forcing spreadsheet apps to render it
 * as plain text.
 */
public final class CsvWriter {

    private static final String FORMULA_TRIGGER_CHARS = "=+-@\t\r";

    private CsvWriter() {
    }

    public static String write(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        writeRow(sb, headers);
        for (List<String> row : rows) {
            writeRow(sb, row);
        }
        return sb.toString();
    }

    private static void writeRow(StringBuilder sb, List<String> row) {
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(row.get(i)));
        }
        sb.append("\r\n");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = neutralizeFormula(value);
        boolean needsQuoting = sanitized.contains(",") || sanitized.contains("\"")
                || sanitized.contains("\n") || sanitized.contains("\r");
        if (!needsQuoting) {
            return sanitized;
        }
        return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }

    private static String neutralizeFormula(String value) {
        if (value.isEmpty() || FORMULA_TRIGGER_CHARS.indexOf(value.charAt(0)) < 0) {
            return value;
        }
        return "'" + value;
    }
}
