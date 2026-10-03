package com.user.management;

import com.user.management.util.Csv;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The quoting rules, asserted against the files people actually upload: saved from
 * Excel, exported from another system, or typed in a text editor.
 */
class CsvTest {

    @Test
    @DisplayName("plain rows split on commas")
    void plainRows() {
        assertThat(Csv.parse("a,b,c\n1,2,3"))
                .containsExactly(List.of("a", "b", "c"), List.of("1", "2", "3"));
    }

    @Test
    @DisplayName("a quoted cell may hold a comma, a quote or a line break")
    void quotedCells() {
        List<List<String>> rows = Csv.parse("\"Singh, Harpreet\",\"say \"\"hello\"\"\",\"two\nlines\"");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsExactly("Singh, Harpreet", "say \"hello\"", "two\nlines");
    }

    @Test
    @DisplayName("the byte order mark Excel writes is not part of the first heading")
    void byteOrderMark() {
        assertThat(Csv.parse("﻿GR. No,Name").get(0).get(0)).isEqualTo("GR. No");
    }

    @Test
    @DisplayName("Windows line endings and a trailing newline do not make phantom rows")
    void lineEndings() {
        assertThat(Csv.parse("a,b\r\n1,2\r\n")).hasSize(2);
        assertThat(Csv.parse("a,b\n1,2\n")).hasSize(2);
    }

    @Test
    @DisplayName("empty cells are kept, because their position is what names them")
    void emptyCells() {
        assertThat(Csv.parse("a,,c")).containsExactly(List.of("a", "", "c"));
        assertThat(Csv.parse(",,")).containsExactly(List.of("", "", ""));
    }

    @Test
    @DisplayName("a row of empty cells is blank; a row with one value is not")
    void blankRows() {
        assertThat(Csv.isBlank(List.of("", "  ", ""))).isTrue();
        assertThat(Csv.isBlank(List.of("", "B00123", ""))).isFalse();
    }

    @Test
    @DisplayName("writing quotes only what would otherwise break the row")
    void writing() {
        assertThat(Csv.line(List.of("plain", "has,comma", "has\"quote")))
                .isEqualTo("plain,\"has,comma\",\"has\"\"quote\"\r\n");
    }

    @Test
    @DisplayName("what is written comes back unchanged")
    void roundTrip() {
        List<String> cells = List.of("Singh, Harpreet", "say \"hello\"", "", "9876543210");
        assertThat(Csv.parse(Csv.line(cells))).containsExactly(cells);
    }
}
