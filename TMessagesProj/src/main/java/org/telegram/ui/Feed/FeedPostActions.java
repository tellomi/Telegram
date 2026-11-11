package org.telegram.ui.Feed;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.feed.FeedActionPolicy;
import org.telegram.messenger.feed.FeedController;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.ShareAlert;

/**
 * Post menus. Disallowed redistribution is left out of the sheet.
 * A tap that the engine would reject shows the Moments reason instead.
 */
public final class FeedPostActions {

    private FeedPostActions() {
    }

    public static void showRepostMenu(BaseFragment fragment, FeedModels.Post post, View anchor) {
        if (fragment == null || post == null) {
            return;
        }
        FeedActionPolicy.Decision repost = decide(post, FeedActionPolicy.Action.REPOST);
        FeedActionPolicy.Decision quote = decide(post, FeedActionPolicy.Action.QUOTE);
        if (!repost.allowed && !quote.allowed) {
            bulletin(fragment, repost.reasonKey);
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(fragment, anchor == null ? fragment.getFragmentView() : anchor);
        boolean saved = post.saved;
        options.add(R.drawable.msg_saved, saved ? text("Moments_Saved", R.string.Moments_Saved) : text("Moments_Save", R.string.Moments_Save), () -> controller(fragment).toggleSave(post));
        if (repost.allowed) {
            options.add(R.drawable.msg_forward, text("Moments_Repost", R.string.Moments_Repost), () -> sendRepost(fragment, post));
        }
        if (quote.allowed) {
            options.add(R.drawable.msg_forward, text("Moments_Quote", R.string.Moments_Quote), () -> fragment.presentFragment(FeedComposerActivity.quote(post.id)));
        }
        options.show();
    }

    public static void showShareMenu(BaseFragment fragment, FeedModels.Post post, View anchor) {
        if (fragment == null || post == null) {
            return;
        }
        FeedActionPolicy.Decision chat = decide(post, FeedActionPolicy.Action.CHAT_SHARE);
        FeedActionPolicy.Decision copy = decide(post, FeedActionPolicy.Action.COPY_LINK);
        FeedActionPolicy.Decision external = decide(post, FeedActionPolicy.Action.EXTERNAL_SHARE);
        if (!chat.allowed && !copy.allowed && !external.allowed) {
            bulletin(fragment, chat.reasonKey.length() > 0 ? chat.reasonKey : copy.reasonKey);
            return;
        }
        String link = FeedUi.linkFor(post.id);
        ItemOptions options = ItemOptions.makeOptions(fragment, anchor == null ? fragment.getFragmentView() : anchor);
        if (chat.allowed) {
            options.add(R.drawable.msg_share, text("Moments_ShareChat", R.string.Moments_ShareChat), () -> {
                if (fragment.getContext() == null) {
                    return;
                }
                String copyLink = copy.allowed ? link : null;
                fragment.showDialog(ShareAlert.createShareAlert(fragment.getContext(), null, link, false, copyLink, false));
            });
        }
        if (copy.allowed) {
            options.add(R.drawable.msg_link2, text("Moments_CopyLink", R.string.Moments_CopyLink), () -> copyLink(fragment, link));
        }
        if (external.allowed) {
            options.add(R.drawable.msg_shareout, text("Moments_ExternalShare", R.string.Moments_ExternalShare), () -> shareExternal(fragment, link));
        }
        options.show();
    }

    public static void showMoreMenu(BaseFragment fragment, FeedModels.Post post, View anchor) {
        if (fragment == null || post == null || anchor == null) {
            return;
        }
        if (FeedActionPolicy.tombstone(post.unavailable, post.state)) {
            bulletin(fragment, FeedActionPolicy.REASON_UNAVAILABLE);
            return;
        }
        boolean own = FeedUi.isSelf(fragment.getCurrentAccount(), post.author);
        ItemOptions options = ItemOptions.makeOptions(fragment, anchor);
        if (own) {
            if (decide(post, true, FeedActionPolicy.Action.EDIT).allowed) {
                options.add(R.drawable.msg_edit, text("Moments_Edit", R.string.Moments_Edit), () -> fragment.presentFragment(FeedComposerActivity.edit(post.id)));
            }
            if (decide(post, true, FeedActionPolicy.Action.CHANGE_AUDIENCE).allowed) {
                options.add(R.drawable.msg_openin, text("Moments_ChangeAudience", R.string.Moments_ChangeAudience), () -> FeedAudiencePicker.open(fragment, null, privacy -> controller(fragment).updateAudience(post.id, FeedAudiencePicker.kind(privacy.type), FeedAudiencePicker.memberIds(privacy), error -> {
                    if (error != null) {
                        BulletinFactory.of(fragment).createErrorBulletin(text("Moments_Audience_Kept", R.string.Moments_Audience_Kept)).show();
                    }
                })));
            }
            options.add(R.drawable.msg_delete, text("Moments_Delete", R.string.Moments_Delete), () -> controller(fragment).deletePost(post.id));
        } else {
            options.add(R.drawable.msg_block, text("Moments_NotInterested", R.string.Moments_NotInterested), () -> controller(fragment).notInterested(post));
            options.add(R.drawable.msg_archive_hide, text("Moments_HidePost", R.string.Moments_HidePost), () -> controller(fragment).hidePost(post));
            options.add(R.drawable.msg_block2, text("Moments_Mute", R.string.Moments_Mute), () -> {
                if (post.author != null) {
                    controller(fragment).mute(post.author.profileId, true);
                }
            });
            options.add(R.drawable.msg_report, text("Moments_Report", R.string.Moments_Report), () -> FeedReportSheet.open(fragment, "post", post.id));
            if (decide(post, FeedActionPolicy.Action.COPY_LINK).allowed) {
                options.add(R.drawable.msg_link2, text("Moments_CopyLink", R.string.Moments_CopyLink), () -> copyLink(fragment, FeedUi.linkFor(post.id)));
            }
        }
        options.show();
    }

    private static void sendRepost(BaseFragment fragment, FeedModels.Post post) {
        controller(fragment).api.sendJson("PUT", "/v1/feed/posts/" + post.id + "/repost", new JSONObject(), (json, error) -> {
            if (error == null) {
                return;
            }
            String key = FeedActionPolicy.reasonKeyForRepostError(error);
            if (key.length() > 0) {
                bulletin(fragment, key);
            } else {
                BulletinFactory.of(fragment).createErrorBulletin(error).show();
            }
        });
    }

    private static void copyLink(BaseFragment fragment, String link) {
        AndroidUtilities.addToClipboard(link);
        BulletinFactory.of(fragment).createCopyLinkBulletin().show();
    }

    private static void shareExternal(BaseFragment fragment, String link) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, link);
        activity.startActivity(Intent.createChooser(intent, text("Moments_ExternalShare", R.string.Moments_ExternalShare)));
    }

    private static void bulletin(BaseFragment fragment, String key) {
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, textForReason(key)).show();
    }

    public static String textForReason(String key) {
        if (FeedActionPolicy.REASON_REPOST_MUTUAL.equals(key)) {
            return text("Moments_RepostBlocked_Mutual", R.string.Moments_RepostBlocked_Mutual);
        }
        if (FeedActionPolicy.REASON_SHARE.equals(key)) {
            return text("Moments_ShareBlocked", R.string.Moments_ShareBlocked);
        }
        if (FeedActionPolicy.REASON_QUOTE_UNAVAILABLE.equals(key)) {
            return text("Moments_QuoteUnavailable", R.string.Moments_QuoteUnavailable);
        }
        if (FeedActionPolicy.REASON_QUOTE_INVALID.equals(key)) {
            return text("Moments_QuoteInvalid", R.string.Moments_QuoteInvalid);
        }
        if (FeedActionPolicy.REASON_REPOST.equals(key)) {
            return text("Moments_RepostBlocked", R.string.Moments_RepostBlocked);
        }
        return text("Moments_Unavailable", R.string.Moments_Unavailable);
    }

    private static String text(String key, int res) {
        return LocaleController.getString(key, res);
    }

    private static FeedActionPolicy.Decision decide(FeedModels.Post post, FeedActionPolicy.Action action) {
        return decide(post, false, action);
    }

    private static FeedActionPolicy.Decision decide(FeedModels.Post post, boolean own, FeedActionPolicy.Action action) {
        if (post == null) {
            return new FeedActionPolicy.Decision(false, FeedActionPolicy.REASON_UNAVAILABLE);
        }
        return FeedActionPolicy.decide(post.actionInput(own), action);
    }

    private static FeedController controller(BaseFragment fragment) {
        return FeedController.getInstance(fragment.getCurrentAccount());
    }
}
