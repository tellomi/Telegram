package org.telegram.ui.Feed;

import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.util.ArrayList;

/**
 * Full-screen media goes through PhotoViewer the same way cached media does:
 * local files as PhotoEntry, opened with SELECT_TYPE_NO_SELECT and a place provider.
 */
public final class FeedMediaViewer implements NotificationCenter.NotificationCenterDelegate {

    private static FeedMediaViewer pending;

    private final BaseFragment fragment;
    private final ArrayList<FeedModels.Media> media;
    private final int index;
    private final String waitingUrl;
    private int account;

    private FeedMediaViewer(BaseFragment fragment, ArrayList<FeedModels.Media> media, int index, String waitingUrl) {
        this.fragment = fragment;
        this.media = media;
        this.index = index;
        this.waitingUrl = waitingUrl;
        this.account = fragment.getCurrentAccount();
    }

    public static void open(BaseFragment fragment, FeedModels.Post post, int index) {
        if (fragment == null || fragment.getParentActivity() == null || post == null) {
            return;
        }
        ArrayList<FeedModels.Media> media = new ArrayList<>();
        for (int i = 0; i < post.media.size(); i++) {
            String type = post.media.get(i).type;
            if ("image".equals(type) || "video".equals(type)) {
                media.add(post.media.get(i));
            }
        }
        if (media.isEmpty()) {
            return;
        }
        int start = Math.max(0, Math.min(index, media.size() - 1));
        FeedPlayback.stop();
        String missing = firstMissing(media);
        if (missing == null) {
            show(fragment, media, start);
            return;
        }
        if (pending != null) {
            NotificationCenter.getInstance(pending.account).removeObserver(pending, NotificationCenter.httpFileDidLoad);
            NotificationCenter.getInstance(pending.account).removeObserver(pending, NotificationCenter.httpFileDidFailedLoad);
        }
        pending = new FeedMediaViewer(fragment, media, start, missing);
        NotificationCenter.getInstance(fragment.getCurrentAccount()).addObserver(pending, NotificationCenter.httpFileDidLoad);
        NotificationCenter.getInstance(fragment.getCurrentAccount()).addObserver(pending, NotificationCenter.httpFileDidFailedLoad);
        for (int i = 0; i < media.size(); i++) {
            String url = media.get(i).url;
            if (url != null && url.startsWith("http") && !localFile(media.get(i)).exists()) {
                ImageLoader.getInstance().loadHttpFile(url, "video".equals(media.get(i).type) ? "mp4" : "jpg", fragment.getCurrentAccount());
            }
        }
    }

    private static String firstMissing(ArrayList<FeedModels.Media> media) {
        for (int i = 0; i < media.size(); i++) {
            String url = media.get(i).url;
            if (url != null && url.startsWith("http") && !localFile(media.get(i)).exists()) {
                return url;
            }
        }
        return null;
    }

    private static File localFile(FeedModels.Media item) {
        if (item.url == null) {
            return new File("");
        }
        if (!item.url.startsWith("http")) {
            return new File(item.url);
        }
        return ImageLoader.getHttpFilePath(item.url, "video".equals(item.type) ? "mp4" : "jpg");
    }

    private static void show(BaseFragment fragment, ArrayList<FeedModels.Media> media, int index) {
        ArrayList<Object> photos = new ArrayList<>();
        for (int i = 0; i < media.size(); i++) {
            FeedModels.Media item = media.get(i);
            File file = localFile(item);
            boolean video = "video".equals(item.type);
            int duration = item.durationMs > 0 ? Math.max(1, item.durationMs / 1000) : 0;
            photos.add(new MediaController.PhotoEntry(0, i, 0, file.getAbsolutePath(), 0, duration, video, item.w, item.h, file.length()));
        }
        PhotoViewer.getInstance().setParentActivity(fragment);
        PhotoViewer.getInstance().openPhotoForSelect(photos, index, PhotoViewer.SELECT_TYPE_NO_SELECT, false, new PhotoViewer.EmptyPhotoViewerProvider() {
            @Override
            public PhotoViewer.PlaceProviderObject getPlaceForPhoto(org.telegram.messenger.MessageObject messageObject, org.telegram.tgnet.TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
                return null;
            }
        }, null);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (args.length == 0 || waitingUrl == null || !waitingUrl.equals(args[0])) {
            return;
        }
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.httpFileDidLoad);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.httpFileDidFailedLoad);
        if (pending == this) {
            pending = null;
        }
        if (id == NotificationCenter.httpFileDidFailedLoad) {
            if (fragment.getParentActivity() != null) {
                BulletinFactory.of(fragment).createErrorBulletin(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.FeedRetry)).show();
            }
            return;
        }
        if (firstMissing(media) != null) {
            return;
        }
        Utilities.stageQueue.postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
            if (fragment.getParentActivity() != null) {
                show(fragment, media, index);
            }
        }));
    }

}
