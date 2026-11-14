package org.telegram.ui;

/**
 * Phone-login country list. {@code help.getCountriesList} marks internal
 * rows such as Test +999 with {@code hidden}. The release picker must not
 * show them. A debug build can still select them.
 */
public final class TellomiLoginCountries {

    private TellomiLoginCountries() {
    }

    public static boolean visibleInLoginPicker(boolean hidden, boolean debugBuild) {
        return !hidden || debugBuild;
    }
}
