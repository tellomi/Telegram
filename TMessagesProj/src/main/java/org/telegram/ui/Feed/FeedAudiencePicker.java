package org.telegram.ui.Feed;

import org.telegram.messenger.feed.FeedContract;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Stories.recorder.StoryPrivacyBottomSheet;

import java.util.ArrayList;
import java.util.List;

public final class FeedAudiencePicker {

    public interface Listener {
        void onSelected(StoryPrivacyBottomSheet.StoryPrivacy privacy);
    }

    private FeedAudiencePicker() {
    }

    public static void open(BaseFragment fragment, StoryPrivacyBottomSheet.StoryPrivacy current, Listener listener) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        StoryPrivacyBottomSheet sheet = new StoryPrivacyBottomSheet(fragment.getParentActivity(), Integer.MAX_VALUE, fragment.getResourceProvider());
        sheet.setFeedMode();
        sheet.whenSelectedRules((privacy, allowComments, allowScreenshots, keepInProfile, isRtmp, peer, price, loaded, cancelled) -> {
            if (listener != null && privacy != null && privacy.type != 0) {
                listener.onSelected(privacy);
            }
            if (loaded != null) {
                loaded.run();
            }
            sheet.dismiss();
        }, false);
        sheet.show();
    }

    /** Selected people and excluded people, including members chosen as a group. */
    public static ArrayList<Long> memberIds(StoryPrivacyBottomSheet.StoryPrivacy privacy) {
        ArrayList<List<Long>> groups = new ArrayList<>();
        if (privacy != null && privacy.selectedUserIdsByGroup != null) {
            groups.addAll(privacy.selectedUserIdsByGroup.values());
        }
        return FeedContract.uniqueMemberIds(privacy == null ? null : privacy.selectedUserIds, groups);
    }

    public static String kind(int type) {
        if (type == StoryPrivacyBottomSheet.TYPE_CONTACTS) {
            return "contacts";
        }
        if (type == StoryPrivacyBottomSheet.TYPE_CLOSE_FRIENDS) {
            return "close_friends";
        }
        if (type == StoryPrivacyBottomSheet.TYPE_SELECTED_CONTACTS) {
            return "selected";
        }
        if (type == StoryPrivacyBottomSheet.TYPE_MUTUAL_FOLLOWERS) {
            return "mutual_followers";
        }
        if (type == StoryPrivacyBottomSheet.TYPE_EVERYONE) {
            return "public";
        }
        return "";
    }

    public static String label(StoryPrivacyBottomSheet.StoryPrivacy privacy) {
        if (privacy == null || privacy.type == 0) {
            return "";
        }
        return privacy.toString();
    }
}
