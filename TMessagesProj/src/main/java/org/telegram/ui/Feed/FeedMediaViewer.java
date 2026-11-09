package org.telegram.ui.Feed;

import android.app.Dialog;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.VideoPlayer;

import java.util.ArrayList;

/**
 * Full-screen media. Remote cards are not Telegram messages, so PhotoViewer
 * (which keys off MessageObject) is not given a fake message. Playback uses
 * the same VideoPlayer the chat viewer uses, and rotation follows the activity.
 */
public final class FeedMediaViewer {

    private FeedMediaViewer() {
    }

    public static void open(BaseFragment fragment, FeedModels.Post post, int index) {
        if (fragment.getParentActivity() == null || post == null) {
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
        FeedModels.Media item = media.get(start);
        Dialog dialog = new Dialog(fragment.getParentActivity(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        FrameLayout root = new FrameLayout(fragment.getParentActivity());
        root.setBackgroundColor(Color.BLACK);
        BackupImageView image = new BackupImageView(fragment.getParentActivity());
        root.addView(image, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        ImageView close = new ImageView(fragment.getParentActivity());
        close.setImageResource(R.drawable.ic_ab_back);
        close.setColorFilter(Color.WHITE);
        close.setContentDescription(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Close));
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(close, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.LEFT, 4, AndroidUtilities.statusBarHeight / AndroidUtilities.density, 0, 0));
        VideoPlayer[] player = new VideoPlayer[1];
        if ("video".equals(item.type) && item.url != null && item.url.length() > 0) {
            android.view.TextureView texture = new android.view.TextureView(fragment.getParentActivity());
            root.addView(texture, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            player[0] = new VideoPlayer();
            player[0].setTextureView(texture);
            player[0].preparePlayer(Uri.parse(item.url), "other");
            player[0].setPlayWhenReady(true);
            image.setContentDescription(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.FeedVideo));
        } else if (item.url != null && item.url.length() > 0) {
            image.setImage(item.url, null, null);
            image.setContentDescription(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.FeedPhoto));
        }
        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            if (player[0] != null) {
                player[0].releasePlayer(true);
            }
        });
        dialog.show();
    }
}
