package org.telegram.ui.tellomi.feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TellomiDiscoverFlags;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** One post, loaded with POST /v1/feed/cards. Signed out does not send. */
public class TellomiDiscoverPostFragment extends BaseFragment implements TellomiDiscoverComposeFragment.Publisher {

    private static final int MENU_OTHER = 1;
    private static final int MENU_REPORT = 2;

    private final String postId;
    private final String surface;
    private final boolean capabilitiesKnown;
    private TellomiDiscover.Loader loader;
    private TellomiDiscover.Post shown;
    private TextView bodyView;
    private LinearLayout column;
    private LinearLayout commentList;
    private TextView likeButton;
    private TextView commentStatus;
    private EditText commentInput;
    private TextView replyLabel;
    private boolean started;
    private boolean reactions;
    private boolean commentsOn;
    private String replyParent;
    private TellomiDiscoverReact.Like likeSession;
    private final TellomiDiscoverReact.Comments commentThread = new TellomiDiscoverReact.Comments();

    public TellomiDiscoverPostFragment(String postId) {
        this(postId, "recommend", false, false, false);
    }

    public TellomiDiscoverPostFragment(String postId, String surface, boolean reactions, boolean comments) {
        this(postId, surface, reactions, comments, true);
    }

    private TellomiDiscoverPostFragment(String postId, String surface, boolean reactions, boolean comments, boolean capabilitiesKnown) {
        super(new Bundle());
        this.postId = postId;
        this.surface = TellomiDiscoverReact.surface(surface);
        this.reactions = reactions;
        this.commentsOn = comments;
        this.capabilitiesKnown = capabilitiesKnown;
    }

    @Override
    public void onFragmentDestroy() {
        if (loader != null) {
            loader.cancel();
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.TellomiTabFeed));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        if (!TellomiDiscoverReport.menuActions(UserConfig.getInstance(currentAccount).isClientActivated()).isEmpty()) {
            ActionBarMenuItem other = actionBar.createMenu().addItem(MENU_OTHER, R.drawable.ic_ab_other);
            other.addSubItem(MENU_REPORT, R.drawable.msg_report, org.telegram.ui.TellomiPack.text("Tellomi.Discover.Report", copy().languageCode, org.telegram.ui.TellomiPack.device()));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_REPORT) {
                    TellomiDiscoverReport.open(TellomiDiscoverPostFragment.this, postId, null);
                }
            }
        });
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        bodyView = new TextView(context);
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        bodyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        column.addView(bodyView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(context);
        scroll.setPadding(dp(16), dp(12), dp(16), dp(12));
        scroll.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.TOP));
        fragmentView = root;
        loadIfAllowed();
        return fragmentView;
    }

    private void loadIfAllowed() {
        if (started) {
            return;
        }
        TellomiDiscover.Configuration configuration = TellomiDiscoverFlags.configuration(currentAccount);
        boolean loggedIn = UserConfig.getInstance(currentAccount).isClientActivated();
        TellomiDiscover.Chrome chrome = TellomiDiscover.chrome(loggedIn, configuration.enabled, TellomiDiscover.FeedKind.RECOMMEND, postId, false, null);
        if (!chrome.loadsPost || chrome.surface != TellomiDiscover.Surface.DETAIL) {
            showUnavailable();
            return;
        }
        started = true;
        loader = TellomiDiscoverClient.loader(currentAccount);
        loader.run(true, configuration, TellomiDiscover.Operation.cards(java.util.Collections.singletonList(chrome.detailId)), new TellomiDiscover.Completion() {
            @Override
            public void onResult(TellomiDiscover.Outcome outcome) {
                if (!outcome.ok() || outcome.value.cards == null || outcome.value.cards.isEmpty()) {
                    showUnavailable();
                    return;
                }
                TellomiDiscover.Post post = TellomiDiscoverParse.displayPost(outcome.value.cards.get(0));
                if (post.unavailable) {
                    showUnavailable();
                    return;
                }
                TellomiDiscover.PostSummary summary = TellomiDiscover.postSummary(post, copy(), false);
                StringBuilder text = new StringBuilder();
                if (!summary.title.isEmpty()) {
                    text.append(summary.title);
                }
                if (!summary.body.isEmpty()) {
                    if (text.length() > 0) {
                        text.append("\n\n");
                    }
                    text.append(summary.body);
                }
                if (!summary.meta.isEmpty()) {
                    if (text.length() > 0) {
                        text.append("\n\n");
                    }
                    text.append(summary.meta);
                }
                bodyView.setText(text.toString());
                shown = post;
                addImages(post);
                addLink(post);
                addForward(post);
                addPoll(post);
                if (capabilitiesKnown) {
                    addInteraction();
                } else {
                    loadCapabilities();
                }
            }
        });
    }

    private void loadCapabilities() {
        loader.run(true, TellomiDiscoverFlags.configuration(currentAccount), TellomiDiscover.Operation.capabilities(), new TellomiDiscover.Completion() {
            @Override
            public void onResult(TellomiDiscover.Outcome outcome) {
                if (outcome != null && outcome.ok() && outcome.value.capabilities != null) {
                    reactions = TellomiDiscoverReact.feature(outcome.value.capabilities, "reactions");
                    commentsOn = TellomiDiscoverReact.feature(outcome.value.capabilities, "comments");
                }
                addInteraction();
            }
        });
    }

    private void addInteraction() {
        if (column == null || shown == null) {
            return;
        }
        if (reactions) {
            likeSession = TellomiDiscoverReact.Like.start(shown.id, shown.liked, shown.likeCount, shown.likesHidden, surface);
            likeButton = action(likeCaption(likeSession.view()));
            likeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleLike();
                }
            });
            column.addView(likeButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        if (!commentsOn) {
            addComments(shown);
            return;
        }
        commentStatus = action("");
        commentStatus.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
        column.addView(commentStatus, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        commentList = new LinearLayout(column.getContext());
        commentList.setOrientation(LinearLayout.VERTICAL);
        column.addView(commentList, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        replyLabel = action("");
        column.addView(replyLabel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        commentInput = new EditText(column.getContext());
        commentInput.setHint(phrase("Tellomi.Discover.CommentHint"));
        commentInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        commentInput.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        commentInput.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        column.addView(commentInput, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView send = action(phrase("Tellomi.Discover.Comment"));
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendComment();
            }
        });
        column.addView(send, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TellomiDiscoverReact.Comments.Load load = commentThread.open(postId);
        sendCommentLoad(load);
    }

    private void toggleLike() {
        if (likeSession == null) {
            return;
        }
        TellomiDiscoverReact.Like.Request request = likeSession.tap();
        paintLike();
        if (request != null) {
            sendLike(request);
        }
    }

    private void sendLike(final TellomiDiscoverReact.Like.Request request) {
        loader.run(true, TellomiDiscoverFlags.configuration(currentAccount), TellomiDiscover.Operation.like(request.postId, request.on, request.surface), new TellomiDiscover.Completion() {
            @Override
            public void onResult(TellomiDiscover.Outcome outcome) {
                if (likeSession == null) {
                    return;
                }
                boolean ok = outcome != null && outcome.ok();
                TellomiDiscoverReact.Like.Request next = likeSession.complete(request.generation, request.version, ok);
                paintLike();
                if (!ok) {
                    setCommentStatus(outcome == null ? TellomiDiscover.ClientError.UNAVAILABLE : outcome.error);
                }
                if (next != null) {
                    sendLike(next);
                }
            }
        });
    }

    private void paintLike() {
        if (likeButton == null || likeSession == null) {
            return;
        }
        likeButton.setText(likeCaption(likeSession.view()));
    }

    private String likeCaption(TellomiDiscoverReact.Like.View view) {
        String label = phrase(view.liked ? "Tellomi.Discover.Liked" : "Tellomi.Discover.Like");
        if (!view.hidden && view.count > 0) {
            return label + " " + view.count;
        }
        return label;
    }

    private void sendCommentLoad(final TellomiDiscoverReact.Comments.Load load) {
        loader.run(true, TellomiDiscoverFlags.configuration(currentAccount), TellomiDiscover.Operation.comments(load.postId, load.cursor), new TellomiDiscover.Completion() {
            @Override
            public void onResult(TellomiDiscover.Outcome outcome) {
                if (outcome != null && outcome.ok() && outcome.value.commentPage != null && commentThread.accept(load.generation, load.version, outcome.value.commentPage)) {
                    setCommentStatus(null);
                } else {
                    commentThread.failLoad(load.generation, load.version);
                    if (outcome == null || !outcome.ok()) {
                        setCommentStatus(outcome == null ? TellomiDiscover.ClientError.UNAVAILABLE : outcome.error);
                    }
                }
                renderComments();
            }
        });
    }

    private void sendComment() {
        if (commentInput == null) {
            return;
        }
        TellomiDiscoverReact.Comments.Send send = commentThread.submit(commentInput.getText().toString(), replyParent, java.util.UUID.randomUUID().toString());
        if (send == null) {
            return;
        }
        if (!send.ready()) {
            setCommentStatus(send.error);
            return;
        }
        loader.run(true, TellomiDiscoverFlags.configuration(currentAccount), TellomiDiscover.Operation.comment(send.postId, send.text, send.parentId, send.nonce), new TellomiDiscover.Completion() {
            @Override
            public void onResult(TellomiDiscover.Outcome outcome) {
                if (outcome != null && outcome.ok() && commentThread.finishSend(send.generation, send.version, outcome.value.commentCreated)) {
                    if (commentInput != null) {
                        commentInput.setText("");
                    }
                    replyParent = null;
                    setCommentStatus(null);
                } else {
                    commentThread.failSend(send.generation, send.version);
                    setCommentStatus(outcome == null || outcome.ok() ? TellomiDiscover.ClientError.UNREADABLE : outcome.error);
                }
                renderComments();
            }
        });
    }

    private void deleteComment(final String commentId) {
        final TellomiDiscoverReact.Comments.Delete delete = commentThread.delete(commentId);
        if (delete == null) {
            return;
        }
        loader.run(true, TellomiDiscoverFlags.configuration(currentAccount), TellomiDiscover.Operation.deleteComment(delete.commentId), new TellomiDiscover.Completion() {
            @Override
            public void onResult(TellomiDiscover.Outcome outcome) {
                boolean ok = outcome != null && outcome.ok();
                commentThread.finishDelete(delete.generation, delete.version, delete.commentId, ok);
                if (!ok) {
                    setCommentStatus(outcome != null && outcome.error == TellomiDiscover.ClientError.NOT_FOUND ? TellomiDiscover.ClientError.NOT_FOUND : (outcome == null ? TellomiDiscover.ClientError.UNAVAILABLE : outcome.error));
                    if (outcome != null && outcome.error == TellomiDiscover.ClientError.NOT_FOUND && commentStatus != null) {
                        commentStatus.setText(phrase("Tellomi.Discover.CommentNotFound"));
                    }
                }
                renderComments();
            }
        });
    }

    private void renderComments() {
        if (commentList == null) {
            return;
        }
        commentList.removeAllViews();
        java.util.List<TellomiDiscover.Comment> rows = new java.util.ArrayList<>(commentThread.visible());
        rows.addAll(commentThread.pendingRows());
        for (int i = 0; i < rows.size(); i++) {
            final TellomiDiscover.Comment comment = rows.get(i);
            boolean pending = comment.state != null && !"ok".equals(comment.state);
            LinearLayout line = new LinearLayout(column.getContext());
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView text = new TextView(column.getContext());
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            String label = comment.authorName == null || comment.authorName.isEmpty() ? "" : comment.authorName + "  ";
            label = label + (comment.text == null ? "" : comment.text);
            if (pending) {
                label = label + "  " + phrase("Tellomi.Discover.CommentPending");
            }
            if (comment.parentId != null) {
                label = phrase("Tellomi.Discover.Reply") + "  " + label;
            }
            text.setText(label);
            text.setPadding(0, dp(8), 0, dp(8));
            if (!pending) {
                text.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        replyParent = commentThread.replyTo(comment);
                        if (replyLabel != null) {
                            replyLabel.setText(replyParent == null ? "" : phrase("Tellomi.Discover.Reply"));
                        }
                    }
                });
            }
            line.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (commentThread.canDelete(comment.id)) {
                TextView delete = action(phrase("Tellomi.Discover.DeleteComment"));
                delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        deleteComment(comment.id);
                    }
                });
                line.addView(delete, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            commentList.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        if (commentThread.hasMore()) {
            TextView more = action(phrase("Tellomi.Discover.LoadMore"));
            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TellomiDiscoverReact.Comments.Load load = commentThread.more();
                    if (load != null) {
                        sendCommentLoad(load);
                    }
                }
            });
            commentList.addView(more, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private void setCommentStatus(TellomiDiscover.ClientError error) {
        if (commentStatus == null) {
            return;
        }
        commentStatus.setText(error == null ? "" : phrase(TellomiDiscoverReact.messageKey(error)));
    }

    private TextView action(String text) {
        TextView view = new TextView(column.getContext());
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider));
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private void addImages(TellomiDiscover.Post post) {
        if (column == null || post == null || post.images == null || post.images.isEmpty()) {
            return;
        }
        TellomiDiscoverImageRow.add(column, column.getContext(), currentAccount, null, post.images, null);
    }

    private void addLink(TellomiDiscover.Post post) {
        if (column == null || post == null || post.linkPreview == null) {
            return;
        }
        TellomiDiscoverLinkCard card = TellomiDiscoverLinkCard.create(column.getContext(), resourceProvider);
        card.show(post.linkPreview, true);
        column.addView(card);
    }

    private void addForward(TellomiDiscover.Post post) {
        if (column == null || post == null || !post.shareable) {
            return;
        }
        TextView forward = new TextView(column.getContext());
        forward.setText(phrase("Tellomi.Discover.Forward"));
        forward.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        forward.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider));
        forward.setPadding(0, dp(8), 0, dp(8));
        forward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TellomiDiscoverShare.show(TellomiDiscoverPostFragment.this, post.id, true, new Runnable() {
                    @Override
                    public void run() {
                        if (loader == null) {
                            loader = TellomiDiscoverClient.loader(currentAccount);
                        }
                        loader.run(true, TellomiDiscoverFlags.configuration(currentAccount), TellomiDiscover.Operation.repost(post.id), new TellomiDiscover.Completion() {
                            @Override
                            public void onResult(TellomiDiscover.Outcome outcome) {
                                if (bodyView != null && outcome != null && outcome.ok()) {
                                    forward.setText(phrase("Tellomi.Discover.Reposted"));
                                }
                            }
                        });
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        presentFragment(TellomiDiscoverComposeFragment.create(TellomiDiscover.POST_TEXT_LIMIT, TellomiDiscoverPostFragment.this, post.id));
                    }
                });
            }
        });
        column.addView(forward, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addPoll(TellomiDiscover.Post post) {
        if (column == null || post == null || post.poll == null || post.poll.options == null) {
            return;
        }
        for (int i = 0; i < post.poll.options.size(); i++) {
            final int index = i;
            TellomiDiscover.PollOption option = post.poll.options.get(i);
            TextView row = new TextView(column.getContext());
            String label = option.text == null ? "" : option.text;
            if (option.votes > 0) {
                label = label + "  " + option.votes;
            }
            row.setText(label);
            row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            boolean chosen = post.poll.voted != null && post.poll.voted.contains(index);
            row.setTextColor(Theme.getColor(chosen ? Theme.key_windowBackgroundWhiteBlueText4 : Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            row.setPadding(0, dp(10), 0, dp(10));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    vote(index);
                }
            });
            column.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private void vote(int index) {
        if (loader == null) {
            loader = TellomiDiscoverClient.loader(currentAccount);
        }
        loader.run(true, TellomiDiscoverFlags.configuration(currentAccount), TellomiDiscover.Operation.vote(postId, index), new TellomiDiscover.Completion() {
            @Override
            public void onResult(TellomiDiscover.Outcome outcome) {
                if (outcome == null || !outcome.ok() || outcome.value == null || outcome.value.vote == null || shown == null || shown.poll == null) {
                    return;
                }
                shown.poll = TellomiDiscover.applyVote(shown.poll, outcome.value.vote);
                if (column != null && column.getChildCount() > 1) {
                    column.removeViews(1, column.getChildCount() - 1);
                }
                addImages(shown);
                addLink(shown);
                addForward(shown);
                addPoll(shown);
                addComments(shown);
            }
        });
    }

    @Override
    public void publish(String text, TellomiDiscover.Visibility visibility, long[] userIds, String nonce, String quoteOf, java.util.List<String> pollOptions, TellomiDiscoverLink.Preview linkPreview, java.util.List<String> mediaKeys, TellomiDiscover.Completion completion) {
        TellomiDiscover.Configuration configuration = TellomiDiscoverFlags.configuration(currentAccount);
        boolean loggedIn = UserConfig.getInstance(currentAccount).isClientActivated();
        if (!loggedIn || configuration == null || !configuration.enabled) {
            completion.onResult(TellomiDiscover.Outcome.failure(loggedIn ? TellomiDiscover.ClientError.DISABLED : TellomiDiscover.ClientError.SIGNED_OUT));
            return;
        }
        if (loader == null) {
            loader = TellomiDiscoverClient.loader(currentAccount);
        }
        long now = System.currentTimeMillis() / 1000L;
        TellomiDiscover.PublishStep step = TellomiDiscover.publishStep(text, visibility, userIds, UserConfig.getInstance(currentAccount).getClientUserId(), nonce, TellomiDiscover.POST_TEXT_LIMIT, null, now, null, quoteOf, pollOptions, linkPreview, mediaKeys);
        if (step.kind == TellomiDiscover.PublishStep.Kind.REJECTED) {
            completion.onResult(TellomiDiscover.Outcome.failure(step.error));
            return;
        }
        if (step.kind == TellomiDiscover.PublishStep.Kind.READY) {
            loader.run(true, configuration, TellomiDiscover.Operation.publish(text, visibility, null, nonce, TellomiDiscover.POST_TEXT_LIMIT, null, quoteOf, pollOptions, linkPreview, mediaKeys), completion);
            return;
        }
        final TellomiDiscover.Loader publishing = loader;
        TellomiDiscoverClient.fetchAudienceToken(currentAccount, step.tokenKind, step.userIds, new TellomiDiscoverClient.AudienceCallback() {
            @Override
            public void onResult(TellomiDiscover.AudienceToken token, TellomiDiscover.ClientError error) {
                if (error != null || token == null) {
                    completion.onResult(TellomiDiscover.Outcome.failure(error == null ? TellomiDiscover.ClientError.UNAVAILABLE : error));
                    return;
                }
                publishing.run(true, configuration, TellomiDiscover.Operation.publish(text, visibility, token.token, nonce, TellomiDiscover.POST_TEXT_LIMIT, null, quoteOf, pollOptions, linkPreview, mediaKeys), completion);
            }
        });
    }

    @Override
    public void published() {
    }

    private String phrase(String key) {
        TellomiDiscover.Copy copy = copy();
        return org.telegram.ui.TellomiPack.text(key, copy.languageCode, copy.source);
    }

    private void addComments(TellomiDiscover.Post post) {
        if (column == null || post.comments == null || post.comments.isEmpty()) {
            return;
        }
        boolean loggedIn = UserConfig.getInstance(currentAccount).isClientActivated();
        for (int i = 0; i < post.comments.size(); i++) {
            final TellomiDiscover.Comment comment = post.comments.get(i);
            LinearLayout line = new LinearLayout(column.getContext());
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView text = new TextView(column.getContext());
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            text.setText(comment.text == null ? "" : comment.text);
            text.setPadding(0, dp(8), 0, dp(8));
            line.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (!TellomiDiscoverReport.menuActions(loggedIn).isEmpty()) {
                ImageView more = new ImageView(column.getContext());
                more.setImageResource(R.drawable.ic_ab_other);
                more.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
                more.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TellomiDiscoverReport.showMenu(TellomiDiscoverPostFragment.this, more, postId, comment.id);
                    }
                });
                line.addView(more, new LinearLayout.LayoutParams(dp(40), dp(40)));
            }
            column.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private void showUnavailable() {
        if (bodyView != null) {
            bodyView.setText(copy().legacyPostUnavailable);
        }
    }

    private TellomiDiscover.Copy copy() {
        String language = "en";
        try {
            LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
            if (info != null && info.shortName != null) {
                language = info.shortName;
            }
        } catch (Throwable ignored) {
        }
        return TellomiDiscover.Copy.resolve(language, org.telegram.ui.TellomiPack.device());
    }
}
