package org.telegram.ui.Feed;

import org.telegram.messenger.LocaleController;
import android.os.Build;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
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
    private final String postId;
    private final String waitingUrl;
    private int account;

    private FeedMediaViewer(BaseFragment fragment, ArrayList<FeedModels.Media> media, int index, String postId, String waitingUrl) {
        this.fragment = fragment;
        this.media = media;
        this.index = index;
        this.postId = postId;
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
        String missing = firstMissing(media, fragment.getCurrentAccount());
        if (missing == null) {
            show(fragment, media, start, post.id);
            return;
        }
        if (pending != null) {
            NotificationCenter.getInstance(pending.account).removeObserver(pending, NotificationCenter.httpFileDidLoad);
            NotificationCenter.getInstance(pending.account).removeObserver(pending, NotificationCenter.httpFileDidFailedLoad);
        }
        pending = new FeedMediaViewer(fragment, media, start, post.id, missing);
        NotificationCenter.getInstance(fragment.getCurrentAccount()).addObserver(pending, NotificationCenter.httpFileDidLoad);
        NotificationCenter.getInstance(fragment.getCurrentAccount()).addObserver(pending, NotificationCenter.httpFileDidFailedLoad);
        int account = fragment.getCurrentAccount();
        for (int i = 0; i < media.size(); i++) {
            String url = media.get(i).displayUrl(account);
            if (url.startsWith("http") && !localFile(media.get(i), account).exists()) {
                ImageLoader.getInstance().loadHttpFile(url, "video".equals(media.get(i).type) ? "mp4" : "jpg", account);
            }
        }
    }

    private static String firstMissing(ArrayList<FeedModels.Media> media, int account) {
        for (int i = 0; i < media.size(); i++) {
            String url = media.get(i).displayUrl(account);
            if (url.startsWith("http") && !localFile(media.get(i), account).exists()) {
                return url;
            }
        }
        return null;
    }

    private static File localFile(FeedModels.Media item, int account) {
        String url = item.displayUrl(account);
        if (url.length() == 0) {
            return new File("");
        }
        if (!url.startsWith("http")) {
            return new File(url);
        }
        return ImageLoader.getHttpFilePath(url, "video".equals(item.type) ? "mp4" : "jpg");
    }

    private static void show(BaseFragment fragment, ArrayList<FeedModels.Media> media, int index, final String postId) {
        ArrayList<Object> photos = new ArrayList<>();
        for (int i = 0; i < media.size(); i++) {
            FeedModels.Media item = media.get(i);
            File file = localFile(item, fragment.getCurrentAccount());
            boolean video = "video".equals(item.type);
            int duration = item.durationMs > 0 ? Math.max(1, item.durationMs / 1000) : 0;
            photos.add(new MediaController.PhotoEntry(0, i, 0, file.getAbsolutePath(), 0, duration, video, item.w, item.h, file.length()));
        }
        PhotoViewer.getInstance().setParentActivity(fragment);
        PhotoViewer.getInstance().openPhotoForSelect(photos, index, PhotoViewer.SELECT_TYPE_NO_SELECT, false, new PhotoViewer.EmptyPhotoViewerProvider() {
            @Override
            public PhotoViewer.PlaceProviderObject getPlaceForPhoto(org.telegram.messenger.MessageObject messageObject, org.telegram.tgnet.TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
                return place(postId, index, needPreview);
            }
        }, null);
    }

    private static PhotoViewer.PlaceProviderObject place(String postId, int index, boolean needPreview) {
        BackupImageView image = FeedPostCell.thumb(postId, index);
        if (image == null || image.getImageReceiver() == null) {
            return null;
        }
        int[] coords = new int[2];
        image.getLocationInWindow(coords);
        PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
        object.viewX = coords[0];
        object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
        object.parentView = clippingParent(image);
        object.imageReceiver = image.getImageReceiver();
        if (needPreview) {
            object.thumb = object.imageReceiver.getBitmapSafe();
        }
        int[] radius = image.getRoundRadius();
        if (radius != null && radius.length >= 4) {
            object.radius = new int[]{radius[0], radius[1], radius[2], radius[3]};
        }
        object.scale = image.getScaleX();
        return object;
    }

    private static View clippingParent(View image) {
        View list = null;
        View current = image;
        while (current.getParent() instanceof View) {
            current = (View) current.getParent();
            if (current instanceof RecyclerListView && !(current.getParent() instanceof RecyclerView)) {
                list = current;
            }
        }
        if (list == null) {
            View parent = image.getParent() instanceof View ? (View) image.getParent() : image;
            return parent;
        }
        return list;
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
                BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString("Moments_LoadFailed", R.string.Moments_LoadFailed)).show();
            }
            return;
        }
        if (firstMissing(media, account) != null) {
            return;
        }
        Utilities.stageQueue.postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
            if (fragment.getParentActivity() != null) {
                show(fragment, media, index, postId);
            }
        }));
    }

}
