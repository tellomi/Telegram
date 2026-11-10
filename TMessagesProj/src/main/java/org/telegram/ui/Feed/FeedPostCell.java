package org.telegram.ui.Feed;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.feed.FeedConfig;
import org.telegram.messenger.feed.FeedContract;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

/**
 * Threads-style row: no card fill, hairline divider, colors from Theme.
 */
@SuppressLint("ViewConstructor")
public class FeedPostCell extends LinearLayout {

    private static final HashMap<String, WeakReference<BackupImageView>> thumbs = new HashMap<>();

    static BackupImageView thumb(String postId, int index) {
        WeakReference<BackupImageView> ref = thumbs.get(postId + ":" + index);
        if (ref == null) {
            return null;
        }
        BackupImageView view = ref.get();
        if (view == null) {
            return null;
        }
        Object tag = view.getTag();
        if (tag == null || !tag.equals(postId + ":" + index)) {
            return null;
        }
        return view;
    }

    private static int viewerIndex(FeedModels.Post post, FeedModels.Media media) {
        if (post == null || media == null) {
            return -1;
        }
        int index = 0;
        for (int i = 0; i < post.media.size(); i++) {
            FeedModels.Media item = post.media.get(i);
            if (!item.isImage() && !item.isVideo()) {
                continue;
            }
            if (item == media) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public interface Delegate {
        void onOpen(FeedModels.Post post);
        void onLike(FeedModels.Post post);
        void onComment(FeedModels.Post post);
        void onRepost(FeedModels.Post post);
        void onShare(FeedModels.Post post);
        void onSave(FeedModels.Post post);
        void onMore(FeedModels.Post post, View anchor);
        void onFollow(FeedModels.Post post);
        void onAuthor(FeedModels.Post post);
        void onVote(FeedModels.Post post, int option);
        void onMedia(FeedModels.Post post, int index);
    }

    private final int account;
    private final boolean detail;
    private final boolean showFollow;
    private final Delegate delegate;
    private FeedModels.Post post;

    private final BackupImageView avatar;
    private final TextView name;
    private final TextView meta;
    private final TextView follow;
    private final ImageView more;
    private final TextView pin;
    private final TextView repostLine;
    private final TextView titleView;
    private final TextView body;
    private final TextView full;
    private final HorizontalScrollView albumScroll;
    private final MediaGrid album;
    private final LinearLayout files;
    private final TextView linkCard;
    private final TextView quoteCard;
    private final LinearLayout pollBox;
    private final TextView location;
    private final ImageView like;
    private final TextView likeCount;
    private final ImageView comment;
    private final TextView commentCount;
    private final ImageView repost;
    private final TextView repostCount;
    private final ImageView share;
    private final ImageView save;
    private final View threadLine;
    private final Paint divider = new Paint();
    private final View dividerView;

    public FeedPostCell(Context context, int account, boolean detail, boolean showFollow, Delegate delegate) {
        super(context);
        this.account = account;
        this.detail = detail;
        this.showFollow = showFollow;
        this.delegate = delegate;
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        int pad = dp(12);
        setPadding(pad, dp(10), pad, dp(4));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);

        LinearLayout left = new LinearLayout(context);
        left.setOrientation(VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout avatarBox = new FrameLayout(context);
        avatar = new BackupImageView(context);
        avatar.setRoundRadius(dp(20));
        avatarBox.addView(avatar, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.LEFT));
        follow = label(context, true);
        follow.setText("+");
        follow.setGravity(Gravity.CENTER);
        follow.setTextSize(12);
        follow.setTextColor(0xffffffff);
        follow.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(16), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        avatarBox.addView(follow, LayoutHelper.createFrame(16, 16, Gravity.BOTTOM | Gravity.RIGHT));
        left.addView(avatarBox, LayoutHelper.createLinear(40, 40));
        threadLine = new View(context);
        threadLine.setBackgroundColor(Theme.getColor(Theme.key_divider));
        threadLine.setVisibility(GONE);
        left.addView(threadLine, LayoutHelper.createLinear(2, 0, 1f, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
        row.addView(left, LayoutHelper.createLinear(48, LayoutHelper.MATCH_PARENT, Gravity.TOP));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        name = label(context, true);
        name.setSingleLine(true);
        header.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_VERTICAL));
        meta = label(context, false);
        meta.setTextSize(13);
        meta.setSingleLine(true);
        meta.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        header.addView(meta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
        more = FeedUi.icon(context, R.drawable.ic_ab_other, getString(R.string.AccDescrMoreOptions));
        header.addView(more, LayoutHelper.createLinear(32, 32, Gravity.CENTER_VERTICAL));
        header.setClickable(true);
        header.setTag(FeedContract.ACTION_TAG);
        column.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        pin = label(context, true);
        pin.setText(FeedUi.s("Moments.Pinned", R.string.FeedPinned));
        pin.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        pin.setVisibility(GONE);
        column.addView(pin, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        repostLine = label(context, false);
        repostLine.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        column.addView(repostLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        titleView = label(context, true);
        titleView.setSingleLine(false);
        titleView.setMaxLines(3);
        titleView.setVisibility(GONE);
        column.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        body = new TextView(context);
        body.setTextSize(15);
        body.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        column.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        full = label(context, true);
        full.setText(FeedUi.s("Moments.FullText", R.string.FeedFullText));
        full.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        column.addView(full, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        albumScroll = new HorizontalScrollView(context);
        albumScroll.setHorizontalScrollBarEnabled(false);
        albumScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        album = new MediaGrid(context);
        album.setTag(FeedContract.ACTION_TAG);
        albumScroll.addView(album, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        column.addView(albumScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        files = new LinearLayout(context);
        files.setOrientation(VERTICAL);
        files.setVisibility(GONE);
        column.addView(files, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        linkCard = card(context);
        column.addView(linkCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        quoteCard = card(context);
        column.addView(quoteCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        pollBox = new LinearLayout(context);
        pollBox.setOrientation(VERTICAL);
        pollBox.setTag(FeedContract.ACTION_TAG);
        pollBox.setClickable(true);
        column.addView(pollBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        location = label(context, false);
        location.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        column.addView(location, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        like = FeedUi.icon(context, R.drawable.media_like, FeedUi.s("Moments.Like", R.string.FeedLike));
        likeCount = countLabel(context);
        comment = FeedUi.icon(context, R.drawable.msg_discussion, FeedUi.s("Moments.Comment", R.string.FeedComment));
        commentCount = countLabel(context);
        repost = FeedUi.icon(context, R.drawable.msg_retry, FeedUi.s("Moments.Repost", R.string.FeedRepost));
        repostCount = countLabel(context);
        share = FeedUi.icon(context, R.drawable.msg_send, FeedUi.s("Moments.Share", R.string.FeedShare));
        save = FeedUi.icon(context, R.drawable.msg_saved, FeedUi.s("Moments.Save", R.string.FeedSave));
        save.setVisibility(GONE);
        actions.addView(like, LayoutHelper.createLinear(32, 32));
        actions.addView(likeCount, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 14, 0));
        actions.addView(comment, LayoutHelper.createLinear(32, 32));
        actions.addView(commentCount, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 14, 0));
        actions.addView(repost, LayoutHelper.createLinear(32, 32));
        actions.addView(repostCount, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 14, 0));
        actions.addView(share, LayoutHelper.createLinear(32, 32));
        actions.setClickable(true);
        actions.setTag(FeedContract.ACTION_TAG);
        column.addView(actions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        row.addView(column, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        dividerView = new View(context);
        addView(dividerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, 6, 0, 0));
        divider.setColor(Theme.getColor(Theme.key_divider));

        avatar.setTag(FeedContract.ACTION_TAG);
        name.setTag(FeedContract.ACTION_TAG);
        follow.setTag(FeedContract.ACTION_TAG);
        more.setTag(FeedContract.ACTION_TAG);
        like.setTag(FeedContract.ACTION_TAG);
        likeCount.setTag(FeedContract.ACTION_TAG);
        comment.setTag(FeedContract.ACTION_TAG);
        commentCount.setTag(FeedContract.ACTION_TAG);
        repost.setTag(FeedContract.ACTION_TAG);
        repostCount.setTag(FeedContract.ACTION_TAG);
        share.setTag(FeedContract.ACTION_TAG);
        save.setTag(FeedContract.ACTION_TAG);
        avatar.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onAuthor(post); });
        name.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onAuthor(post); });
        follow.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onFollow(post); });
        more.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onMore(post, more); });
        header.setOnClickListener(v -> { if (post != null && delegate != null && !detail) delegate.onOpen(post); });
        full.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onOpen(post); });
        body.setOnClickListener(v -> { if (post != null && delegate != null && !detail) delegate.onOpen(post); });
        like.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onLike(post); });
        likeCount.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onLike(post); });
        comment.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onComment(post); });
        repost.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onRepost(post); });
        repostCount.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onRepost(post); });
        share.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onShare(post); });
        save.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onSave(post); });
    }

    /**
     * False when the tap lands on like, comment, media, or another action.
     * The list row's open-detail click must ignore those taps.
     */
    public boolean tapOpensDetail(float x, float y) {
        ArrayList<FeedContract.Zone> zones = new ArrayList<>();
        collectActions(this, 0, 0, zones);
        return FeedContract.tapOpensDetail(detail, zones, x, y);
    }

    private void collectActions(View view, float originX, float originY, ArrayList<FeedContract.Zone> zones) {
        if (view.getVisibility() != VISIBLE) {
            return;
        }
        if (view != this && FeedContract.ACTION_TAG.equals(view.getTag())) {
            zones.add(new FeedContract.Zone(originX, originY, originX + view.getWidth(), originY + view.getHeight()));
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                collectActions(child, originX + child.getLeft(), originY + child.getTop(), zones);
            }
        }
    }

    private TextView label(Context context, boolean bold) {
        TextView view = new TextView(context);
        view.setTextSize(bold ? 15 : 13);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        if (bold) {
            view.setTypeface(AndroidUtilities.bold());
        }
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private TextView countLabel(Context context) {
        TextView view = label(context, false);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        return view;
    }

    private TextView card(Context context) {
        TextView view = new TextView(context);
        view.setTextSize(14);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.getColor(Theme.key_graySection)));
        view.setPadding(dp(10), dp(8), dp(10), dp(8));
        view.setVisibility(GONE);
        return view;
    }

    public void setPost(FeedModels.Post post) {
        this.post = post;
        applyTheme();
        if (post == null || post.unavailable) {
            name.setText(FeedUi.s("Moments.Unavailable", R.string.FeedUnavailable));
            body.setText("");
            titleView.setVisibility(GONE);
            pin.setVisibility(GONE);
            meta.setText("");
            follow.setVisibility(GONE);
            album.setVisibility(GONE);
            albumScroll.setVisibility(GONE);
            threadLine.setVisibility(GONE);
            repostCount.setVisibility(GONE);
            files.setVisibility(GONE);
            linkCard.setVisibility(GONE);
            quoteCard.setVisibility(GONE);
            pollBox.setVisibility(GONE);
            location.setVisibility(GONE);
            repostLine.setVisibility(GONE);
            like.setVisibility(GONE);
            likeCount.setVisibility(GONE);
            comment.setVisibility(GONE);
            commentCount.setVisibility(GONE);
            repost.setVisibility(GONE);
            share.setVisibility(GONE);
            save.setVisibility(GONE);
            return;
        }
        like.setVisibility(VISIBLE);
        likeCount.setVisibility(VISIBLE);
        comment.setVisibility(VISIBLE);
        commentCount.setVisibility(VISIBLE);
        repost.setVisibility(VISIBLE);
        repostCount.setVisibility(VISIBLE);
        share.setVisibility(VISIBLE);
        save.setVisibility(GONE);
        FeedUi.bindAvatar(avatar, post.author, account);
        name.setText(FeedUi.displayName(post.author, account));
        String audience = FeedUi.audienceLabel(post.audienceKind);
        String when = FeedUi.timeAgo(post.createdAt);
        String edited = post.editedAt > 0 ? " · " + FeedUi.s("Moments.Edited", R.string.FeedEdited) : "";
        meta.setText(audience.length() == 0 ? when + edited : when + " · " + audience + edited);
        meta.setContentDescription(meta.getText());
        follow.setText("+");
        follow.setContentDescription(FeedUi.s("Moments.Follow", R.string.FeedFollow));
        follow.setVisibility(showFollow && !FeedUi.isSelf(account, post.author) && !post.following && post.author.profileId.length() > 0 ? VISIBLE : GONE);
        threadLine.setVisibility(post.quote != null ? VISIBLE : GONE);
        pin.setVisibility(post.pinned ? VISIBLE : GONE);
        if (post.repostedByName != null && post.repostedByName.length() > 0) {
            repostLine.setVisibility(VISIBLE);
            repostLine.setText(LocaleSafe(post.repostedByName));
        } else {
            repostLine.setVisibility(GONE);
        }
        if (post.title != null && post.title.length() > 0) {
            titleView.setVisibility(VISIBLE);
            titleView.setText(FeedContract.plainText(post.title));
        } else {
            titleView.setVisibility(GONE);
        }
        body.setText(FeedContract.plainText(post.text));
        body.setTextSize(SharedConfig.fontSize);
        if (detail) {
            body.setMaxLines(Integer.MAX_VALUE);
            full.setVisibility(GONE);
        } else {
            body.setMaxLines(FeedConfig.COLLAPSE_LINES);
            body.setEllipsize(TextUtils.TruncateAt.END);
            full.setVisibility(GONE);
            body.post(() -> {
                if (this.post != post) {
                    return;
                }
                boolean cut = body.getLayout() != null && body.getLineCount() >= FeedConfig.COLLAPSE_LINES
                        && body.getLayout().getEllipsisCount(Math.min(FeedConfig.COLLAPSE_LINES, body.getLineCount()) - 1) > 0;
                full.setVisibility(cut ? VISIBLE : GONE);
            });
        }
        album.bind(post, delegate);
        albumScroll.setVisibility(album.getVisibility());
        bindFiles(post);
        if (post.linkPreview != null && post.poll == null) {
            linkCard.setVisibility(VISIBLE);
            StringBuilder link = new StringBuilder();
            if (post.linkPreview.title.length() > 0) {
                link.append(post.linkPreview.title);
            }
            if (post.linkPreview.description.length() > 0) {
                if (link.length() > 0) {
                    link.append('\n');
                }
                link.append(post.linkPreview.description);
            }
            if (post.linkPreview.url.length() > 0) {
                if (link.length() > 0) {
                    link.append('\n');
                }
                link.append(post.linkPreview.url);
            }
            linkCard.setText(link.length() == 0 ? post.linkPreview.url : link);
        } else {
            linkCard.setVisibility(GONE);
        }
        if (post.quote != null || (post.quoteId != null && post.quoteId.length() > 0)) {
            quoteCard.setVisibility(VISIBLE);
            if (post.quote == null || post.quote.unavailable) {
                quoteCard.setText(FeedUi.s("Moments.Unavailable", R.string.FeedUnavailable));
            } else {
                quoteCard.setText(FeedUi.displayName(post.quote.author, account) + "\n" + post.quote.text);
            }
        } else {
            quoteCard.setVisibility(GONE);
        }
        bindPoll(post);
        if ((post.cityName != null && post.cityName.length() > 0) || (post.placeName != null && post.placeName.length() > 0)) {
            location.setVisibility(VISIBLE);
            String place = post.placeName == null ? "" : post.placeName;
            String city = post.cityName == null || post.cityName.length() == 0 ? post.cityCode : post.cityName;
            String distance = post.placeName != null && post.placeName.length() > 0 && post.distanceBand != null && post.distanceBand.length() > 0 ? " · " + post.distanceBand : "";
            location.setText((place.length() > 0 ? city + " · " + place : city) + distance);
        } else {
            location.setVisibility(GONE);
        }
        like.setImageResource(post.liked ? R.drawable.media_like_active : R.drawable.media_like);
        like.setColorFilter(post.liked ? 0xFFFF2E38 : Theme.getColor(Theme.key_dialogIcon));
        likeCount.setText(post.likes > 0 ? String.valueOf(post.likes) : "");
        like.setContentDescription(FeedUi.s("Moments.Like", R.string.FeedLike) + " " + post.likes);
        commentCount.setText(post.comments > 0 ? String.valueOf(post.comments) : "");
        comment.setContentDescription(FeedUi.s("Moments.Comment", R.string.FeedComment) + " " + post.comments);
        repostCount.setText(post.reposts > 0 ? String.valueOf(post.reposts) : "");
        repost.setContentDescription(FeedUi.s("Moments.Repost", R.string.FeedRepost) + " " + post.reposts);
        save.setColorFilter(post.saved ? Theme.getColor(Theme.key_featuredStickers_addButton) : Theme.getColor(Theme.key_dialogIcon));
        save.setContentDescription(post.saved ? FeedUi.s("Moments.Saved", R.string.FeedSaved) : FeedUi.s("Moments.Save", R.string.FeedSave));
        boolean canRepost = post.isPublic() || !FeedConfig.ONLY_PUBLIC_CAN_REPOST;
        repost.setAlpha(canRepost ? 1f : 0.35f);
    }

    private String LocaleSafe(String name) {
        return FeedUi.s("Moments.RepostHeader", R.string.FeedRepostedBy).replace("%s", name);
    }

    private void applyTheme() {
        int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        int gray = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        int card = Theme.getColor(Theme.key_graySection);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        name.setTextColor(text);
        titleView.setTextColor(text);
        body.setTextColor(text);
        meta.setTextColor(gray);
        repostLine.setTextColor(gray);
        location.setTextColor(gray);
        full.setTextColor(gray);
        pin.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        follow.setTextColor(0xffffffff);
        follow.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(16), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        threadLine.setBackgroundColor(Theme.getColor(Theme.key_divider));
        likeCount.setTextColor(gray);
        commentCount.setTextColor(gray);
        repostCount.setTextColor(gray);
        linkCard.setTextColor(text);
        quoteCard.setTextColor(text);
        linkCard.setBackground(Theme.createRoundRectDrawable(dp(12), card));
        quoteCard.setBackground(Theme.createRoundRectDrawable(dp(12), card));
        divider.setColor(Theme.getColor(Theme.key_divider));
    }

    private void bindFiles(FeedModels.Post post) {
        files.removeAllViews();
        int shown = 0;
        if ("article".equals(post.kind) || "story".equals(post.kind)) {
            String label = "article".equals(post.kind) ? FeedUi.s("Moments.Article", R.string.FeedArticle) : FeedUi.s("Moments.Story", R.string.FeedStory);
            files.addView(infoCard(label, ""), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            shown++;
        }
        for (int i = 0; i < post.media.size(); i++) {
            FeedModels.Media media = post.media.get(i);
            if (!media.isFile()) {
                continue;
            }
            String label = fileLabel(media);
            String subtitle = media.name != null && media.name.length() > 0 && !media.name.equals(label) ? media.name : "";
            TextView row = infoCard(label, subtitle);
            files.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, shown == 0 ? 0 : 6, 0, 0));
            shown++;
        }
        files.setVisibility(shown == 0 ? GONE : VISIBLE);
    }

    private String fileLabel(FeedModels.Media media) {
        if (media == null) {
            return FeedUi.s("Moments.File", R.string.FeedFile);
        }
        if ("pdf".equals(media.type) || (media.mime != null && media.mime.contains("pdf"))) {
            return FeedUi.s("Moments.Pdf", R.string.FeedPdf);
        }
        if (media.isFile() && ("audio".equals(media.type) || "voice".equals(media.type) || (media.mime != null && media.mime.startsWith("audio")))) {
            return FeedUi.s("Moments.Voice", R.string.FeedAudio);
        }
        return FeedUi.s("Moments.File", R.string.FeedFile);
    }

    private TextView infoCard(String title, String subtitle) {
        TextView view = card(getContext());
        view.setVisibility(VISIBLE);
        if (subtitle != null && subtitle.length() > 0) {
            view.setText(title + "\n" + subtitle);
        } else {
            view.setText(title);
        }
        return view;
    }

    private void bindPoll(FeedModels.Post post) {
        pollBox.removeAllViews();
        if (post.poll == null) {
            pollBox.setVisibility(GONE);
            return;
        }
        pollBox.setVisibility(VISIBLE);
        pollBox.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.getColor(Theme.key_graySection)));
        pollBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        TextView question = new TextView(getContext());
        question.setText(post.poll.question);
        question.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        question.setTypeface(AndroidUtilities.bold());
        question.setTextSize(15);
        pollBox.addView(question, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
        boolean results = post.poll.canSeeResults || post.poll.voted || post.poll.closed();
        for (int i = 0; i < post.poll.options.size(); i++) {
            final int index = i;
            FeedModels.PollOption option = post.poll.options.get(i);
            TextView row = new TextView(getContext());
            String suffix = results ? "  " + option.votes : "";
            row.setText((option.chosen ? "● " : "○ ") + option.text + suffix);
            row.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            row.setTextSize(15);
            row.setPadding(0, dp(8), 0, dp(8));
            row.setOnClickListener(v -> {
                if (delegate != null && !post.poll.closed()) {
                    delegate.onVote(post, index);
                }
            });
            pollBox.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        TextView foot = new TextView(getContext());
        foot.setTextSize(13);
        foot.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        foot.setText(String.valueOf(post.poll.totalVoters));
        pollBox.addView(foot);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int max = dp(FeedConfig.MAX_CONTENT_WIDTH_DP);
        int extra = 0;
        if (width > max) {
            extra = (width - max) / 2;
        }
        if (getPaddingLeft() != dp(12) + extra) {
            setPadding(dp(12) + extra, getPaddingTop(), dp(12) + extra, getPaddingBottom());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        divider.setColor(Theme.getColor(Theme.key_divider));
        canvas.drawRect(getPaddingLeft(), getHeight() - 1, getWidth() - getPaddingRight(), getHeight(), divider);
    }

    /**
     * Moments grid: 1 large, 2 or 4 in two columns, 3/9/10 and the rest in
     * three columns. Not a nested list, so load-more cannot recycle it out
     * from under the parent scroller.
     */
    private class MediaGrid extends ViewGroup {
        private final ArrayList<FeedModels.Media> items = new ArrayList<>();
        private int cellWidth;
        private int cellHeight;
        private int gap;

        MediaGrid(Context context) {
            super(context);
        }

        void bind(FeedModels.Post post, Delegate delegate) {
            items.clear();
            for (int i = getChildCount() - 1; i >= 0; i--) {
                FeedPlayback.untrack(getChildAt(i));
            }
            removeAllViews();
            if (post == null) {
                setVisibility(GONE);
                return;
            }
            for (int i = 0; i < post.media.size(); i++) {
                FeedModels.Media media = post.media.get(i);
                if (media.isImage() || media.isVideo()) {
                    items.add(media);
                }
            }
            if (items.isEmpty()) {
                setVisibility(GONE);
                return;
            }
            setVisibility(VISIBLE);
            int count = items.size();
            for (int position = 0; position < count; position++) {
                FeedModels.Media media = items.get(position);
                FrameLayout frame = new FrameLayout(getContext());
                frame.setTag(FeedContract.ACTION_TAG);
                BackupImageView image = new BackupImageView(getContext());
                image.setRoundRadius(dp(12));
                frame.addView(image, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                String thumb = media.thumbUrl(account);
                String play = media.displayUrl(account);
                if (thumb.length() > 0) {
                    image.setImage(thumb, count == 1 ? "800_800" : "320_320", new ColorDrawable(Theme.getColor(Theme.key_graySection)));
                } else {
                    image.setImage(null, null, new ColorDrawable(Theme.getColor(Theme.key_graySection)));
                }
                int viewerIndex = viewerIndex(post, media);
                if (post.id != null && viewerIndex >= 0) {
                    image.setTag(post.id + ":" + viewerIndex);
                    thumbs.put(post.id + ":" + viewerIndex, new WeakReference<>(image));
                }
                image.setContentDescription((media.isVideo() ? FeedUi.s("Moments.Tool.Video", R.string.FeedVideo) + ", " + (media.durationMs / 1000) + "s" : FeedUi.s("Moments.Photo", R.string.FeedPhoto)) + " " + (position + 1) + "/" + count);
                if (media.isVideo() && play.startsWith("http")) {
                    TextureView texture = new TextureView(getContext());
                    frame.addView(texture, 0, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                    FeedPlayback.track(frame, play, texture);
                }
                if (media.isVideo()) {
                    TextView badge = new TextView(getContext());
                    badge.setText(FeedUi.s("Moments.Tool.Video", R.string.FeedVideo));
                    badge.setTextColor(0xffffffff);
                    badge.setTextSize(12);
                    badge.setPadding(dp(6), dp(2), dp(6), dp(2));
                    badge.setBackground(Theme.createRoundRectDrawable(dp(6), 0x66000000));
                    frame.addView(badge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 6, 0, 0, 6));
                }
                final int openIndex = viewerIndex >= 0 ? viewerIndex : position;
                final int page = position;
                frame.setOnClickListener(v -> {
                    post.albumPage = page;
                    if (delegate != null) {
                        delegate.onMedia(post, openIndex);
                    }
                });
                if (FeedConfig.DOUBLE_TAP_LIKE && media.isImage()) {
                    frame.setOnTouchListener(new DoubleTap(v -> {
                        if (delegate != null) {
                            delegate.onLike(post);
                            FeedUi.playHeart(frame);
                        }
                    }));
                }
                addView(frame);
            }
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int n = items.size();
            if (n == 0 || width <= 0) {
                setMeasuredDimension(width, 0);
                return;
            }
            int column = width;
            if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY || column <= 0) {
                column = Math.max(dp(200), AndroidUtilities.displaySize.x - dp(72));
            }
            int total = column;
            if (n == 1) {
                gap = 0;
                cellWidth = column;
                FeedModels.Media media = items.get(0);
                if (media.isVideo()) {
                    cellHeight = Math.max(dp(180), column * 9 / 16);
                } else if (media.w > 0 && media.h > 0) {
                    cellHeight = Math.min(dp(360), Math.max(dp(120), column * media.h / Math.max(1, media.w)));
                } else {
                    cellHeight = dp(220);
                }
            } else {
                gap = dp(8);
                cellWidth = (int) (column * 0.78f);
                cellHeight = cellWidth;
                total = n * cellWidth + (n - 1) * gap;
            }
            int childW = MeasureSpec.makeMeasureSpec(Math.max(1, cellWidth), MeasureSpec.EXACTLY);
            int childH = MeasureSpec.makeMeasureSpec(Math.max(1, cellHeight), MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(childW, childH);
            }
            setMeasuredDimension(total, cellHeight);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int x = 0;
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).layout(x, 0, x + cellWidth, cellHeight);
                x += cellWidth + gap;
            }
        }
    }

    private static class DoubleTap implements OnTouchListener {
        private final View.OnClickListener listener;
        private long last;

        DoubleTap(View.OnClickListener listener) {
            this.listener = listener;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                long now = System.currentTimeMillis();
                if (now - last < 280) {
                    listener.onClick(v);
                    last = 0;
                    return true;
                }
                last = now;
            }
            return false;
        }
    }
}
