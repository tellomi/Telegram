package org.telegram.ui.Feed;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.feed.FeedConfig;

import java.io.File;

/**
 * Records with MediaController and asks it not to send the voice into a chat
 * (stopRecording send=2 returns the file through audioDidSent).
 */
public final class FeedVoiceCapture implements NotificationCenter.NotificationCenterDelegate {

    public interface Callback {
        void onFile(File file);
        void onError();
    }

    private final int account;
    private final int guid = Utilities.random.nextInt() & 0x7fffffff;
    private final Callback callback;
    private boolean recording;
    private boolean finished;
    private final Runnable cap = new Runnable() {
        @Override
        public void run() {
            if (!recording) {
                return;
            }
            stop();
        }
    };

    public FeedVoiceCapture(int account, Callback callback) {
        this.account = account;
        this.callback = callback;
    }

    public boolean isRecording() {
        return recording;
    }

    public void start() {
        if (recording) {
            return;
        }
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.recordStarted);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.audioDidSent);
        recording = true;
        AndroidUtilities.runOnUIThread(cap, FeedConfig.MAX_VOICE_SEC * 1000L);
        MediaController.getInstance().startRecording(account, Long.MIN_VALUE, null, null, null, guid, true, null, 0, null);
    }

    public void stop() {
        if (!recording) {
            return;
        }
        recording = false;
        AndroidUtilities.cancelRunOnUIThread(cap);
        MediaController.getInstance().stopRecording(2, false, 0, false, 0);
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.recordStarted);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.audioDidSent);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (args.length > 0 && args[0] instanceof Integer && ((Integer) args[0]) != guid && id != NotificationCenter.recordProgressChanged) {
            if (id != NotificationCenter.audioDidSent) {
                return;
            }
        }
        if (id == NotificationCenter.recordStartError) {
            if (args.length > 0 && args[0] instanceof Integer && ((Integer) args[0]) != guid) {
                return;
            }
            recording = false;
            finish();
            if (callback != null) {
                callback.onError();
            }
        } else if (id == NotificationCenter.audioDidSent) {
            if (args.length < 1 || !(args[0] instanceof Integer) || ((Integer) args[0]) != guid) {
                return;
            }
            String path = args.length > 2 && args[2] instanceof String ? (String) args[2] : null;
            finish();
            if (callback != null && path != null) {
                callback.onFile(new File(path));
            } else if (callback != null) {
                callback.onError();
            }
        }
    }

}
