package com.user.management.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * What came of a bulk upload.
 *
 * <p>Either every row was good and {@code created} says how many sewadars were added,
 * or nothing was saved at all and {@code problems} says why, row by row. There is no
 * half-imported file: a part-loaded register is worse than a rejected one, because
 * the office cannot tell which rows still need doing.</p>
 *
 * @param rows     data rows found in the file, blank lines excluded
 * @param created  sewadars added; zero whenever there is a problem
 * @param problems every problem found, in file order
 */
public record BulkImportResponse(
        int rows,
        int created,
        List<RowProblem> problems) {

    /**
     * One problem, addressed the way the person will look for it: by the line number
     * their spreadsheet shows and the column heading they can see.
     *
     * @param line    line number in the file, counting the heading row as line 1
     * @param column  the column heading, or null when the problem is the whole row
     * @param value   what was in the cell, so the message can be matched to the file
     * @param message what is wrong, in words the office can act on
     */
    @Schema(description = "A problem with one row of the uploaded file")
    public record RowProblem(int line, String column, String value, String message) {
    }

    public static BulkImportResponse rejected(int rows, List<RowProblem> problems) {
        return new BulkImportResponse(rows, 0, problems);
    }

    public static BulkImportResponse saved(int rows, int created) {
        return new BulkImportResponse(rows, created, List.of());
    }
}
