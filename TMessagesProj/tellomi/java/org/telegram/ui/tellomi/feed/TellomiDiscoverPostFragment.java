package org.telegram.ui.tellomi.feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
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
    private TellomiDiscover.Loader loader;
    private TextView bodyView;
    private LinearLayout column;
    private boolean started;

    public TellomiDiscoverPostFragment(String postId) {
        super(new Bundle());
        this.postId = postId;
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
                addForward(post);
                addPoll(post);
                addComments(post);
            }
        });
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
            row.setTextColor(Theme.getColor(option.voted ? Theme.key_windowBackgroundWhiteBlueText4 : Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
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
                if (outcome != null && outcome.ok()) {
                    started = false;
                    if (column != null) {
                        column.removeViews(1, column.getChildCount() - 1);
                    }
                    loadIfAllowed();
                }
            }
        });
    }

    @Override
    public void publish(String text, TellomiDiscover.Visibility visibility, long[] userIds, String nonce, String quoteOf, java.util.List<String> pollOptions, TellomiDiscover.Completion completion) {
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
        TellomiDiscover.PublishStep step = TellomiDiscover.publishStep(text, visibility, userIds, UserConfig.getInstance(currentAccount).getClientUserId(), nonce, TellomiDiscover.POST_TEXT_LIMIT, null, now, null, quoteOf, pollOptions);
        if (step.kind == TellomiDiscover.PublishStep.Kind.REJECTED) {
            completion.onResult(TellomiDiscover.Outcome.failure(step.error));
            return;
        }
        if (step.kind == TellomiDiscover.PublishStep.Kind.READY) {
            loader.run(true, configuration, TellomiDiscover.Operation.publish(text, visibility, null, nonce, TellomiDiscover.POST_TEXT_LIMIT, null, quoteOf, pollOptions), completion);
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
                publishing.run(true, configuration, TellomiDiscover.Operation.publish(text, visibility, token.token, nonce, TellomiDiscover.POST_TEXT_LIMIT, null, quoteOf, pollOptions), completion);
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
