package com.user.management;

/**
 * The product's name, in one place on the server.
 *
 * <p>The browser has its own copy in {@code frontend/src/brand.js} and cannot read
 * this file, so a rename is two edits - here and there. Before this existed it was
 * three: the Swagger title, the report email footer, and now the PDF heading each
 * carried the string separately.</p>
 */
public final class AppInfo {

    public static final String NAME = "Pandal Department";

    private AppInfo() {
    }
}
