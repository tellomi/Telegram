package org.telegram.ui.Feed;

import android.graphics.Rect;
import android.net.Uri;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.VideoPlayer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * One muted list video at a time. Uses the chat VideoPlayer and stops when a
 * voice message is playing or autoplay is off.
 */
public final class FeedPlayback implements NotificationCenter.NotificationCenterDelegate {

    private static final FeedPlayback INSTANCE = new FeedPlayback();

    private static class Slot {
        WeakReference<View> view;
        String url;
        TextureView texture;
    }

    private final ArrayList<Slot> slots = new ArrayList<>();
    private final WeakHashMap<View, Boolean> hooked = new WeakHashMap<>();
    private static final int PLAYER_ENDED = 4;

    private VideoPlayer player;
    private VideoPlayer voicePlayer;
    private String voiceUrl;
    private final ArrayList<WeakReference<TextView>> voiceRows = new ArrayList<>();
    private String playingUrl;
    private WeakReference<View> playingView;
    private boolean listening;

    private FeedPlayback() {
    }

    public static void track(View view, String url, TextureView texture) {
        if (view == null || url == null || texture == null) {
            return;
        }
        INSTANCE.ensureListening();
        for (int i = INSTANCE.slots.size() - 1; i >= 0; i--) {
            View existing = INSTANCE.slots.get(i).view.get();
            if (existing == null || existing == view) {
                INSTANCE.slots.remove(i);
            }
        }
        Slot slot = new Slot();
        slot.view = new WeakReference<>(view);
        slot.url = url;
        slot.texture = texture;
        INSTANCE.slots.add(slot);
        if (INSTANCE.hooked.get(view) == null) {
            INSTANCE.hooked.put(view, Boolean.TRUE);
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    choose();
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    untrack(v);
                }
            });
        }
        choose();
    }

    public static void untrack(View view) {
        if (view == null) {
            return;
        }
        for (int i = INSTANCE.slots.size() - 1; i >= 0; i--) {
            View existing = INSTANCE.slots.get(i).view.get();
            if (existing == null || existing == view) {
                INSTANCE.slots.remove(i);
            }
        }
        View playing = INSTANCE.playingView == null ? null : INSTANCE.playingView.get();
        if (playing == view) {
            stop();
        }
        choose();
    }

    public static void choose() {
        if (!FeedActivity.autoplayAllowed() || voicePlaying()) {
            stop();
            return;
        }
        Slot best = null;
        int bestHeight = 0;
        Rect rect = new Rect();
        for (int i = 0; i < INSTANCE.slots.size(); i++) {
            Slot slot = INSTANCE.slots.get(i);
            View view = slot.view.get();
            if (view == null || !view.isShown()) {
                continue;
            }
            if (!view.getGlobalVisibleRect(rect)) {
                continue;
            }
            int height = rect.height();
            if (height > bestHeight && height > view.getHeight() / 2) {
                bestHeight = height;
                best = slot;
            }
        }
        if (best == null) {
            stop();
            return;
        }
        View current = INSTANCE.playingView == null ? null : INSTANCE.playingView.get();
        if (best.view.get() == current && best.url.equals(INSTANCE.playingUrl) && INSTANCE.player != null) {
            return;
        }
        play(best);
    }

    public static void stop() {
        if (INSTANCE.player != null) {
            INSTANCE.player.releasePlayer(true);
            INSTANCE.player = null;
        }
        showPoster(INSTANCE.playingView == null ? null : INSTANCE.playingView.get(), true);
        INSTANCE.playingUrl = null;
        INSTANCE.playingView = null;
    }

    private static void play(Slot slot) {
        stop();
        View view = slot.view.get();
        if (view == null) {
            return;
        }
        VideoPlayer videoPlayer = new VideoPlayer();
        videoPlayer.setTextureView(slot.texture);
        videoPlayer.preparePlayer(Uri.parse(slot.url), "other");
        videoPlayer.setTextureView(slot.texture);
        videoPlayer.setMute(true);
        videoPlayer.setLooping(true);
        videoPlayer.setPlayWhenReady(true);
        INSTANCE.player = videoPlayer;
        INSTANCE.playingUrl = slot.url;
        INSTANCE.playingView = slot.view;
        showPoster(view, false);
    }

    private static void showPoster(View view, boolean show) {
        if (!(view instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof BackupImageView) {
                child.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    public static void watchVoice(TextView row) {
        if (row == null) {
            return;
        }
        for (int i = INSTANCE.voiceRows.size() - 1; i >= 0; i--) {
            TextView existing = INSTANCE.voiceRows.get(i).get();
            if (existing == null || existing == row) {
                INSTANCE.voiceRows.remove(i);
            }
        }
        INSTANCE.voiceRows.add(new WeakReference<>(row));
        notifyVoice();
    }

    public static String voiceUrl() {
        return INSTANCE.voiceUrl == null ? "" : INSTANCE.voiceUrl;
    }

    public static void toggleVoice(String url) {
        if (url == null || url.length() == 0) {
            return;
        }
        INSTANCE.ensureListening();
        if (url.equals(INSTANCE.voiceUrl)) {
            stopVoice();
            return;
        }
        MessageObject chatAudio = MediaController.getInstance().getPlayingMessageObject();
        if (chatAudio != null && MediaController.getInstance().isPlayingMessage(chatAudio)) {
            MediaController.getInstance().pauseMessage(chatAudio);
        }
        stop();
        stopVoice();
        VideoPlayer voice = new VideoPlayer();
        voice.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
            @Override
            public void onStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == PLAYER_ENDED) {
                    AndroidUtilities.runOnUIThread(FeedPlayback::stopVoice);
                }
            }

            @Override
            public void onError(VideoPlayer player, Exception e) {
                AndroidUtilities.runOnUIThread(FeedPlayback::stopVoice);
            }

            @Override
            public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
            }

            @Override
            public void onRenderedFirstFrame() {
            }
        });
        voice.preparePlayer(Uri.parse(url), "other");
        voice.setMute(false);
        voice.setLooping(false);
        voice.setPlayWhenReady(true);
        INSTANCE.voicePlayer = voice;
        INSTANCE.voiceUrl = url;
        notifyVoice();
    }

    public static void stopVoice() {
        if (INSTANCE.voicePlayer != null) {
            INSTANCE.voicePlayer.releasePlayer(true);
            INSTANCE.voicePlayer = null;
        }
        INSTANCE.voiceUrl = null;
        notifyVoice();
    }

    private static void notifyVoice() {
        String url = INSTANCE.voiceUrl == null ? "" : INSTANCE.voiceUrl;
        for (int i = INSTANCE.voiceRows.size() - 1; i >= 0; i--) {
            TextView row = INSTANCE.voiceRows.get(i).get();
            if (row == null) {
                INSTANCE.voiceRows.remove(i);
                continue;
            }
            Object tag = row.getTag();
            boolean playing = tag instanceof String && url.equals(tag);
            row.setTextColor(playing
                    ? org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_featuredStickers_addButton)
                    : org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteBlackText));
        }
    }

    private static boolean voicePlaying() {
        if (INSTANCE.voicePlayer != null) {
            return true;
        }
        MediaController controller = MediaController.getInstance();
        return controller.getPlayingMessageObject() != null && controller.isPlayingMessage(controller.getPlayingMessageObject());
    }

    private void ensureListening() {
        if (listening) {
            return;
        }
        listening = true;
        NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingPlayStateChanged) {
            MessageObject chatAudio = MediaController.getInstance().getPlayingMessageObject();
            if (chatAudio != null && MediaController.getInstance().isPlayingMessage(chatAudio)) {
                stopVoice();
            }
            choose();
        }
    }

}
