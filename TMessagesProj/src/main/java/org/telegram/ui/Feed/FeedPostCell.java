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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.feed.FeedConfig;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

/**
 * Threads-style row: no card fill, hairline divider, colors from Theme.
 */
@SuppressLint("ViewConstructor")
public class FeedPostCell extends LinearLayout {

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
    private final TextView repostLine;
    private final TextView body;
    private final TextView full;
    private final AlbumView album;
    private final TextView linkCard;
    private final TextView quoteCard;
    private final LinearLayout pollBox;
    private final TextView location;
    private final ImageView like;
    private final TextView likeCount;
    private final ImageView comment;
    private final TextView commentCount;
    private final ImageView repost;
    private final ImageView share;
    private final ImageView save;
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

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        avatar = new BackupImageView(context);
        avatar.setRoundRadius(dp(18));
        header.addView(avatar, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(VERTICAL);
        name = label(context, true);
        meta = label(context, false);
        meta.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        titles.addView(name);
        titles.addView(meta);
        header.addView(titles, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        follow = label(context, true);
        follow.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        follow.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.addView(follow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        more = FeedUi.icon(context, R.drawable.ic_ab_other, getString(R.string.AccDescrMoreOptions));
        header.addView(more, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL));
        addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        repostLine = label(context, false);
        repostLine.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        repostLine.setPadding(dp(46), dp(2), 0, 0);
        addView(repostLine, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        body = new TextView(context);
        body.setTextSize(SharedConfig.fontSize);
        body.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        body.setPadding(dp(46), dp(4), 0, 0);
        addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        full = label(context, true);
        full.setText(getString(R.string.FeedFullText));
        full.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        full.setPadding(dp(46), dp(2), 0, 0);
        addView(full, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        album = new AlbumView(context);
        LinearLayout.LayoutParams albumLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 180, 46, 8, 0, 0);
        addView(album, albumLp);

        linkCard = card(context);
        addView(linkCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 46, 8, 0, 0));
        quoteCard = card(context);
        addView(quoteCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 46, 8, 0, 0));

        pollBox = new LinearLayout(context);
        pollBox.setOrientation(VERTICAL);
        addView(pollBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 46, 8, 0, 0));

        location = label(context, false);
        location.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        location.setPadding(dp(46), dp(6), 0, 0);
        addView(location, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(40), 0, 0, 0);
        like = FeedUi.icon(context, R.drawable.media_like, getString(R.string.FeedLike));
        likeCount = label(context, false);
        comment = FeedUi.icon(context, R.drawable.input_reply, getString(R.string.FeedComment));
        commentCount = label(context, false);
        repost = FeedUi.icon(context, R.drawable.msg_forward, getString(R.string.FeedRepost));
        share = FeedUi.icon(context, R.drawable.msg_share, getString(R.string.FeedShare));
        save = FeedUi.icon(context, R.drawable.msg_saved, getString(R.string.FeedSave));
        actions.addView(like, LayoutHelper.createLinear(40, 40));
        actions.addView(likeCount, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        actions.addView(comment, LayoutHelper.createLinear(40, 40));
        actions.addView(commentCount, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        actions.addView(repost, LayoutHelper.createLinear(40, 40));
        actions.addView(share, LayoutHelper.createLinear(40, 40));
        actions.addView(save, LayoutHelper.createLinear(40, 40));
        addView(actions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        dividerView = new View(context);
        addView(dividerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, 6, 0, 0));
        divider.setColor(Theme.getColor(Theme.key_divider));

        avatar.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onAuthor(post); });
        name.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onAuthor(post); });
        follow.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onFollow(post); });
        more.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onMore(post, more); });
        full.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onOpen(post); });
        body.setOnClickListener(v -> { if (post != null && delegate != null && !detail) delegate.onOpen(post); });
        like.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onLike(post); });
        comment.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onComment(post); });
        repost.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onRepost(post); });
        share.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onShare(post); });
        save.setOnClickListener(v -> { if (post != null && delegate != null) delegate.onSave(post); });
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
        if (post == null || post.unavailable) {
            name.setText(getString(R.string.FeedUnavailable));
            body.setText("");
            meta.setText("");
            follow.setVisibility(GONE);
            album.setVisibility(GONE);
            linkCard.setVisibility(GONE);
            quoteCard.setVisibility(GONE);
            pollBox.setVisibility(GONE);
            location.setVisibility(GONE);
            repostLine.setVisibility(GONE);
            return;
        }
        FeedUi.bindAvatar(avatar, post.author, account);
        name.setText(FeedUi.displayName(post.author, account));
        String audience = FeedUi.audienceLabel(post.audienceKind);
        String when = FeedUi.timeAgo(post.createdAt);
        String edited = post.editedAt > 0 ? " · " + getString(R.string.FeedEdited) : "";
        meta.setText(audience.length() == 0 ? when + edited : when + " · " + audience + edited);
        meta.setContentDescription(meta.getText());
        if (showFollow && !FeedUi.isSelf(account, post.author) && !post.following) {
            follow.setVisibility(VISIBLE);
            follow.setText(getString(R.string.FeedFollow));
        } else if (showFollow && post.following && !FeedUi.isSelf(account, post.author)) {
            follow.setVisibility(VISIBLE);
            follow.setText(post.mutual ? getString(R.string.FeedMutual) : getString(R.string.FeedFollowingState));
        } else {
            follow.setVisibility(GONE);
        }
        if (post.repostedByName != null && post.repostedByName.length() > 0) {
            repostLine.setVisibility(VISIBLE);
            repostLine.setText(LocaleSafe(post.repostedByName));
        } else {
            repostLine.setVisibility(GONE);
        }
        body.setText(post.text);
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
        if (post.linkPreview != null && !post.hasImages() && !post.hasVideo() && post.poll == null) {
            linkCard.setVisibility(VISIBLE);
            linkCard.setText(post.linkPreview.title.length() > 0 ? post.linkPreview.title + "\n" + post.linkPreview.url : post.linkPreview.url);
        } else {
            linkCard.setVisibility(GONE);
        }
        if (post.quote != null || (post.quoteId != null && post.quoteId.length() > 0)) {
            quoteCard.setVisibility(VISIBLE);
            if (post.quote == null || post.quote.unavailable) {
                quoteCard.setText(getString(R.string.FeedUnavailable));
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
        like.setContentDescription(getString(R.string.FeedLike) + " " + post.likes);
        commentCount.setText(post.comments > 0 ? String.valueOf(post.comments) : "");
        comment.setContentDescription(getString(R.string.FeedComment) + " " + post.comments);
        save.setColorFilter(post.saved ? Theme.getColor(Theme.key_featuredStickers_addButton) : Theme.getColor(Theme.key_dialogIcon));
        save.setContentDescription(post.saved ? getString(R.string.FeedSaved) : getString(R.string.FeedSave));
        boolean canRepost = post.isPublic() || !FeedConfig.ONLY_PUBLIC_CAN_REPOST;
        repost.setAlpha(canRepost ? 1f : 0.35f);
    }

    private String LocaleSafe(String name) {
        return getString(R.string.FeedRepostedBy).replace("%s", name);
    }

    private void bindPoll(FeedModels.Post post) {
        pollBox.removeAllViews();
        if (post.poll == null) {
            pollBox.setVisibility(GONE);
            return;
        }
        pollBox.setVisibility(VISIBLE);
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

    private class AlbumView extends RecyclerListView {
        AlbumView(Context context) {
            super(context);
            setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            setOverScrollMode(OVER_SCROLL_NEVER);
        }

        @Override
        public boolean canScrollHorizontally(int direction) {
            Adapter adapter = getAdapter();
            return adapter != null && adapter.getItemCount() > 1;
        }

        void bind(FeedModels.Post post, Delegate delegate) {
            int count = 0;
            String kind = null;
            for (int i = 0; i < post.media.size(); i++) {
                String type = post.media.get(i).type;
                if ("image".equals(type) || "video".equals(type)) {
                    if (kind == null) {
                        kind = type;
                    }
                    if (type.equals(kind)) {
                        count++;
                    }
                }
            }
            if (count == 0) {
                setVisibility(GONE);
                setAdapter(null);
                return;
            }
            setVisibility(VISIBLE);
            final String mediaKind = kind;
            final int mediaCount = count;
            setAdapter(new RecyclerListView.SelectionAdapter() {
                @Override
                public boolean isEnabled(RecyclerView.ViewHolder holder) {
                    return true;
                }

                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    FrameLayout frame = new FrameLayout(parent.getContext());
                    BackupImageView image = new BackupImageView(parent.getContext());
                    image.setRoundRadius(dp(8));
                    frame.addView(image, LayoutHelper.createFrame(160, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
                    frame.setLayoutParams(new RecyclerView.LayoutParams(dp(168), dp(180)));
                    return new RecyclerListView.Holder(frame);
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    FrameLayout frame = (FrameLayout) holder.itemView;
                    BackupImageView image = (BackupImageView) frame.getChildAt(0);
                    int seen = 0;
                    FeedModels.Media media = null;
                    for (int i = 0; i < post.media.size(); i++) {
                        if (mediaKind.equals(post.media.get(i).type)) {
                            if (seen == position) {
                                media = post.media.get(i);
                                break;
                            }
                            seen++;
                        }
                    }
                    if (media != null && media.url != null && media.url.length() > 0) {
                        image.setImage(media.url, "160_180", new ColorDrawable(Theme.getColor(Theme.key_graySection)));
                    } else {
                        image.setImage(null, null, new ColorDrawable(Theme.getColor(Theme.key_graySection)));
                    }
                    image.setContentDescription(("video".equals(mediaKind) ? getString(R.string.FeedVideo) + ", " + (media == null ? 0 : media.durationMs / 1000) + "s" : getString(R.string.FeedPhoto)) + " " + (position + 1) + "/" + mediaCount);
                    FeedPlayback.untrack(frame);
                    if (frame.getChildCount() > 1) {
                        frame.removeViewAt(0);
                    }
                    if ("video".equals(mediaKind) && media != null && media.url != null && media.url.startsWith("http")) {
                        TextureView texture = new TextureView(frame.getContext());
                        frame.addView(texture, 0, LayoutHelper.createFrame(160, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
                        FeedPlayback.track(frame, media.url, texture);
                    }
                    final int index = position;
                    frame.setOnClickListener(v -> {
                        post.albumPage = index;
                        if (delegate != null) {
                            delegate.onMedia(post, index);
                        }
                    });
                    if (FeedConfig.DOUBLE_TAP_LIKE && "image".equals(mediaKind)) {
                        frame.setOnTouchListener(new DoubleTap(v -> {
                            if (delegate != null) {
                                delegate.onLike(post);
                                FeedUi.playHeart(frame);
                            }
                        }));
                    }
                }

                @Override
                public int getItemCount() {
                    return mediaCount;
                }
            });
            if (post.albumPage > 0 && post.albumPage < count) {
                scrollToPosition(post.albumPage);
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            return super.onInterceptTouchEvent(e);
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
