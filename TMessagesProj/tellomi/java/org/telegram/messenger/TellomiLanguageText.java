package org.telegram.messenger;

/**
 * Traditional display for one resolved string.
 * A values-zh-rTW (or zh-Hant) string that is not the simplified fallback is
 * kept. Otherwise the simplified text, including the bundled Chinese bin, is
 * converted on the device. English is not passed here.
 */
public final class TellomiLanguageText {

    private TellomiLanguageText() {
    }

    public static String traditionalDisplay(String resolved, String simplified, String traditionalResource) {
        if (traditionalResource != null && traditionalResource.length() > 0
                && (simplified == null || !traditionalResource.equals(simplified))) {
            return traditionalResource;
        }
        String base = resolved != null ? resolved : simplified;
        return TellomiChineseScript.toTraditional(base);
    }
}
