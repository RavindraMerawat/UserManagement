package com.user.management.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The badge movements on one seating day, split the way the office counts them.
 *
 * @param issuedMale     badges handed out to men on the day
 * @param issuedFemale   badges handed out to women on the day
 * @param receivedMale   badges taken back from men on the day
 * @param receivedFemale badges taken back from women on the day
 */
@Schema(description = "Issued and received counts for one seating day")
public record SeatingDaySummary(
        long issuedMale,
        long issuedFemale,
        long receivedMale,
        long receivedFemale) {
}
