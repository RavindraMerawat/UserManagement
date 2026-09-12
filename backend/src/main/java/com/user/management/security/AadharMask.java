package com.user.management.security;

/**
 * Turns an Aadhaar number into the form a role that may not read it is allowed to
 * see, and recognises that form coming back.
 *
 * <p>Masking is applied on the <b>server</b>, in {@code SewadarResponse} and
 * {@code CheckInOutService}, so a masked response never carries the other eight
 * digits and no UI mistake can expose them. Who gets the mask is decided once, in
 * {@link CurrentUserService#canViewFullAadhar(Long)}.</p>
 */
public final class AadharMask {

    /** What a masked value shows when there are not even four digits to show. */
    private static final String FULLY_MASKED = "XXXX XXXX XXXX";

    private AadharMask() {
    }

    /**
     * Display form for a role that may not see the whole number: the last four
     * digits, and nothing else. Null or blank stays null, so a sewadar whose number
     * has not been collected is not reported as having a hidden one.
     */
    public static String mask(String digits) {
        if (digits == null || digits.isBlank()) {
            return null;
        }
        String bare = digits.replaceAll("[^0-9]", "");
        if (bare.length() < 4) {
            return FULLY_MASKED;
        }
        return "XXXX XXXX " + bare.substring(bare.length() - 4);
    }

    /**
     * True when a value looks like something {@link #mask} produced rather than a
     * real number. Saving one back would replace a good number with its last four
     * digits, so the service refuses it instead.
     */
    public static boolean looksMasked(String value) {
        return value != null && (value.indexOf('X') >= 0 || value.indexOf('x') >= 0);
    }
}
