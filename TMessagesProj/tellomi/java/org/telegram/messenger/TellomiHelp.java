package org.telegram.messenger;

/**
 * The help center. Login, settings, and the FAQ search index all open this URL.
 * {@link BuildVars#HELP_URL} and {@code tellomi_help_url} use the same value.
 * No trailing slash.
 */
public final class TellomiHelp {

    public static final String URL = "https://tellomi.cn/help";

    private TellomiHelp() {
    }
}
