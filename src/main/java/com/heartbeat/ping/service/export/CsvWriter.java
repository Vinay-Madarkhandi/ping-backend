package com.heartbeat.ping.service.export;

import java.util.List;

/** Minimal RFC 4180 CSV writer: quotes a field only when it contains a comma, quote, or newline. */
public final class CsvWriter {

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
        boolean needsQuoting = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
