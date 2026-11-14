package org.telegram.ui;

import org.telegram.messenger.R;

/**
 * The composer send control is an up arrow. Schedule mode keeps the clock.
 * Share sheets and the photo picker stay on the paper plane.
 */
public final class TellomiSendIcon {

    private TellomiSendIcon() {
    }

    public static final String COMPOSER = "tellomi_send_up";
    public static final String SCHEDULE = "input_schedule";

    public static String composerDrawableName(boolean scheduleMode) {
        return scheduleMode ? SCHEDULE : COMPOSER;
    }

    public static int composerDrawable(boolean scheduleMode) {
        return scheduleMode ? R.drawable.input_schedule : R.drawable.tellomi_send_up;
    }
}
