package org.telegram.ui.Feed;

import android.view.View;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.StatsController;
import org.telegram.messenger.feed.FeedConfig;
import org.telegram.messenger.feed.FeedContract;
import org.telegram.messenger.feed.FeedModels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Hidden ImageReceivers for the posts just outside the viewport.
 * Same setup as PeerStoriesView's preload receivers: low priority, and the
 * visible cell adopts the bitmap when the url and filter match
 * ({@code ImageLoader} keys are {@code url@filter}). At most
 * {@link FeedContract#PREFETCH_MAX_INFLIGHT} downloads run at once.
 *
 * The window follows Glide's RecyclerViewPreloader (a few items ahead in the
 * scroll direction, same decode size as the cell) and Fresco's cancel-when-unused
 * prefetch. A fast fling does not start new work, matching Glide's pause-while-scrolling.
 * https://bumptech.github.io/glide/int/recyclerview.html
 * https://frescolib.org/docs/prefetching.html
 * https://developer.android.com/topic/performance/graphics/load-bitmap
 */
public final class FeedPreloader {

    public static int debugHits;
    public static int debugMisses;

    private final int account;
    private final ArrayList<ImageReceiver> pool = new ArrayList<>();
    private final HashMap<String, Slot> holding = new HashMap<>();
    private final ArrayList<FeedContract.PrefetchHit> waiting = new ArrayList<>();
    private boolean attached;
    private boolean fling;
    private boolean pumping;
    private int inflight;
    private View parent;

    public FeedPreloader(int account) {
        this.account = account;
    }

    public List<ImageReceiver> receivers() {
        return pool;
    }

    public void setParent(View view) {
        parent = view;
        for (int i = 0; i < pool.size(); i++) {
            pool.get(i).setParentView(view);
        }
    }

    public void setAttached(boolean on) {
        attached = on;
        for (int i = 0; i < pool.size(); i++) {
            ImageReceiver receiver = pool.get(i);
            if (on) {
                receiver.onAttachedToWindow();
            } else {
                receiver.onDetachedFromWindow();
            }
        }
        if (on && !fling) {
            pump();
        }
    }

    public void update(ArrayList<FeedModels.Post> posts, int first, int last, int direction, boolean flinging) {
        fling = flinging;
        int screen = org.telegram.messenger.AndroidUtilities.displaySize.x;
        float density = org.telegram.messenger.AndroidUtilities.density;
        ArrayList<FeedContract.PrefetchPost> described = new ArrayList<>();
        if (posts != null) {
            for (int i = 0; i < posts.size(); i++) {
                described.add(describe(posts.get(i), account, screen, density));
            }
        }
        ArrayList<FeedContract.PrefetchHit> plan = FeedContract.prefetchPlan(described, first, last, direction, allowed(account), lowTier());
        HashMap<String, Boolean> wanted = new HashMap<>();
        for (int i = 0; i < plan.size(); i++) {
            wanted.put(plan.get(i).key, Boolean.TRUE);
        }
        ArrayList<String> drop = new ArrayList<>();
        for (String key : holding.keySet()) {
            if (!wanted.containsKey(key)) {
                drop.add(key);
            }
        }
        for (int i = 0; i < drop.size(); i++) {
            release(drop.get(i));
        }
        waiting.clear();
        if (!fling) {
            for (int i = 0; i < plan.size(); i++) {
                if (!holding.containsKey(plan.get(i).key)) {
                    waiting.add(plan.get(i));
                }
            }
        }
        pump();
    }

    public static boolean allowed(int account) {
        DownloadController controller = DownloadController.getInstance(account);
        int network = ApplicationLoader.getAutodownloadNetworkType();
        DownloadController.Preset preset;
        if (network == StatsController.TYPE_WIFI) {
            preset = controller.getCurrentWiFiPreset();
        } else if (network == StatsController.TYPE_ROAMING) {
            preset = controller.getCurrentRoamingPreset();
        } else {
            preset = controller.getCurrentMobilePreset();
        }
        return preset.enabled && (preset.mask[3] & DownloadController.AUTODOWNLOAD_TYPE_PHOTO) != 0;
    }

    public static boolean lowTier() {
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            return true;
        }
        return ApplicationLoader.getAutodownloadNetworkType() == StatsController.TYPE_ROAMING;
    }

    private void pump() {
        if (pumping || !attached || fling) {
            return;
        }
        pumping = true;
        try {
            while (inflight < FeedContract.PREFETCH_MAX_INFLIGHT && !waiting.isEmpty()) {
                FeedContract.PrefetchHit hit = waiting.remove(0);
                if (hit.url.length() == 0 || holding.containsKey(hit.key)) {
                    continue;
                }
                ImageReceiver receiver = obtain();
                Slot slot = new Slot();
                slot.hit = hit;
                slot.receiver = receiver;
                slot.loading = true;
                holding.put(hit.key, slot);
                inflight++;
                receiver.setDelegate((imageReceiver, set, thumb, memCache) -> {
                    Slot done = holding.get(hit.key);
                    if (done != null && done.loading) {
                        done.loading = false;
                        inflight = Math.max(0, inflight - 1);
                        if (BuildVars.DEBUG_VERSION) {
                            if (memCache) {
                                debugHits++;
                            } else {
                                debugMisses++;
                            }
                        }
                    }
                    pump();
                });
                receiver.setImage(ImageLocation.getForPath(hit.url), hit.filter, null, null, null, 0);
            }
        } finally {
            pumping = false;
        }
    }

    private void release(String key) {
        Slot slot = holding.remove(key);
        if (slot == null) {
            return;
        }
        if (slot.loading) {
            inflight = Math.max(0, inflight - 1);
        }
        slot.receiver.clearImage();
        slot.receiver.setDelegate(null);
    }

    private ImageReceiver obtain() {
        for (int i = 0; i < pool.size(); i++) {
            ImageReceiver receiver = pool.get(i);
            boolean used = false;
            for (Slot slot : holding.values()) {
                if (slot.receiver == receiver) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return receiver;
            }
        }
        ImageReceiver receiver = new ImageReceiver();
        receiver.setAllowLoadingOnAttachedOnly(true);
        receiver.ignoreNotifications = true;
        receiver.setFileLoadingPriority(FileLoader.PRIORITY_LOW);
        receiver.setCurrentAccount(account);
        receiver.setParentView(parent);
        if (attached) {
            receiver.onAttachedToWindow();
        }
        pool.add(receiver);
        return receiver;
    }

    private static FeedContract.PrefetchPost describe(FeedModels.Post post, int account, int screen, float density) {
        FeedContract.PrefetchPost described = new FeedContract.PrefetchPost();
        if (post == null) {
            return described;
        }
        if (post.author != null) {
            described.avatar = post.author.avatarUrl == null ? "" : post.author.avatarUrl;
            if (described.avatar.length() == 0 && post.author.avatarKey != null && post.author.avatarKey.length() > 0) {
                described.avatar = FeedConfig.mediaUrl(account, post.author.avatarKey);
            }
        }
        int visuals = 0;
        ArrayList<FeedModels.Media> shown = new ArrayList<>();
        if (post.media != null) {
            for (int i = 0; i < post.media.size(); i++) {
                FeedModels.Media media = post.media.get(i);
                if (media != null && (media.isVideo() || media.isImage())) {
                    shown.add(media);
                }
            }
        }
        visuals = shown.size();
        for (int i = 0; i < shown.size(); i++) {
            String cover = shown.get(i).listUrl(account, visuals);
            if (cover.length() > 0) {
                described.images.add(cover);
            }
        }
        int[] cell = FeedContract.mediaCellPx(Math.max(1, visuals), screen, density);
        described.imageFilter = FeedContract.imageFilter(cell[0], cell[1], density);
        if (post.linkPreview != null && post.linkPreview.image != null && post.linkPreview.image.length() > 0) {
            described.linkImage = FeedConfig.mediaUrl(account, post.linkPreview.image);
        }
        return described;
    }

    private static final class Slot {
        FeedContract.PrefetchHit hit;
        ImageReceiver receiver;
        boolean loading;
    }
}
