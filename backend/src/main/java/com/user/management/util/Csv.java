package com.user.management.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reading and writing CSV, to the rules Excel actually follows (RFC 4180).
 *
 * <p>Small on purpose. The alternative was another dependency for two methods, and
 * the format's whole difficulty is quoting: a cell may contain a comma, a quote or
 * a line break as long as it is wrapped in quotes, and a quote inside such a cell
 * is written twice. Everything here exists to get that right.</p>
 */
public final class Csv {

    private Csv() {
    }

    /**
     * Splits CSV text into rows of cells.
     *
     * <p>Handles quoted cells, doubled quotes inside them, both line endings, and the
     * byte order mark Excel writes at the start of a UTF-8 file. A trailing newline
     * does not produce an extra empty row, which matters because a spreadsheet saved
     * from Excel always has one.</p>
     */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        String input = text.charAt(0) == '﻿' ? text.substring(1) : text;

        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean started = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quoted) {
                if (c != '"') {
                    cell.append(c);
                } else if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    quoted = true;
                    started = true;
                }
                case ',' -> {
                    row.add(cell.toString());
                    cell.setLength(0);
                    started = true;
                }
                case '\r' -> {
                    // Part of CRLF; the newline below ends the row.
                }
                case '\n' -> {
                    row.add(cell.toString());
                    cell.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                    started = false;
                }
                default -> {
                    cell.append(c);
                    started = true;
                }
            }
        }
        if (started || cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    /** One CSV line, with CRLF, quoting only the cells that need it. */
    public static String line(Collection<String> cells) {
        return cells.stream().map(Csv::quote).collect(Collectors.joining(",")) + "\r\n";
    }

    /** True when the row has nothing in it - the blank lines a filled-in file collects. */
    public static boolean isBlank(List<String> row) {
        return row.stream().allMatch(cell -> cell == null || cell.isBlank());
    }

    private static String quote(String value) {
        String s = value == null ? "" : value;
        boolean needs = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        return needs ? '"' + s.replace("\"", "\"\"") + '"' : s;
    }
}
