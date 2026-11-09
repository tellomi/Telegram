package org.telegram.messenger;

import androidx.annotation.Nullable;

public class BetaUpdate {

    public final String version;
    public final int versionCode;

    @Nullable
    public final String changelog;

    public BetaUpdate(String version, int versionCode, String changelog) {
        this.version = version;
        this.versionCode = versionCode;
        this.changelog = changelog;
    }

    public boolean higherThan(BetaUpdate update) {
        if (update == null) {
            return true;
        }
        if (versionCode > 0 && update.versionCode > 0) {
            return versionCode > update.versionCode;
        }
        return SharedConfig.versionBiggerOrEqual(version, update.version) && versionCode > update.versionCode;
    }

}
