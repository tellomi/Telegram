package org.telegram.ui.tellomi.feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TellomiDiscoverFlags;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** One post, loaded with POST /v1/feed/cards. Signed out does not send. */
public class TellomiDiscoverPostFragment extends BaseFragment {

    private final String postId;
    private TellomiDiscover.Loader loader;
    private TextView bodyView;
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
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        bodyView = new TextView(context);
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        bodyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.addView(bodyView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));
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
            }
        });
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
        return TellomiDiscover.Copy.resolve(language);
    }
}
