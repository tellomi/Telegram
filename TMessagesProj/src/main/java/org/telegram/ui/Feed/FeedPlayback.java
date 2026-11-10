package org.telegram.ui.Feed;

import android.graphics.Rect;
import android.net.Uri;
import android.view.TextureView;
import android.view.View;
import org.telegram.messenger.MediaController;
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
    private VideoPlayer player;
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

    private static boolean voicePlaying() {
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
            choose();
        }
    }

}
