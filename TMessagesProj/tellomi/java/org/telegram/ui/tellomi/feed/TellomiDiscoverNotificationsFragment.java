package org.telegram.ui.tellomi.feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TellomiDiscoverFlags;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.TellomiPack;

import java.util.List;

/** In-app discover notifications. {@code GET /v1/feed/me/notifications}. */
public class TellomiDiscoverNotificationsFragment extends BaseFragment {

    private TellomiDiscover.Loader loader;
    private LinearLayout column;
    private boolean started;

    @Override
    public void onFragmentDestroy() {
        if (loader != null) {
            loader.cancel();
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        TellomiDiscover.Copy copy = copy();
        actionBar.setTitle(phrase(copy, "Tellomi.Discover.Notifications"));
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
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(context);
        scroll.setPadding(dp(16), dp(12), dp(16), dp(12));
        scroll.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.TOP));
        fragmentView = root;
        load();
        return fragmentView;
    }

    private void load() {
        if (started || column == null) {
            return;
        }
        TellomiDiscover.Configuration configuration = TellomiDiscoverFlags.configuration(currentAccount);
        boolean loggedIn = UserConfig.getInstance(currentAccount).isClientActivated();
        if (!loggedIn || configuration == null || !configuration.enabled) {
            showLine(copy().failed, false, null);
            return;
        }
        started = true;
        loader = TellomiDiscoverClient.loader(currentAccount);
        loader.run(true, configuration, TellomiDiscover.Operation.notifications(null), result -> {
            if (column == null) {
                return;
            }
            column.removeAllViews();
            if (result == null || !result.ok() || result.value == null || result.value.notices == null) {
                showLine(copy().failed, false, null);
                return;
            }
            List<TellomiDiscover.Notice> items = result.value.notices.items;
            if (items == null || items.isEmpty()) {
                showLine(phrase(copy(), "Tellomi.Discover.NotificationsEmpty"), false, null);
                return;
            }
            TellomiDiscover.Copy copy = copy();
            for (int i = 0; i < items.size(); i++) {
                TellomiDiscover.Notice notice = items.get(i);
                String key = TellomiDiscoverPush.phraseKey(notice.kind);
                String phrase = key == null ? notice.kind : phrase(copy, key);
                String title = notice.actorName == null || notice.actorName.isEmpty() ? phrase : notice.actorName + " " + phrase;
                String body = notice.text == null ? "" : notice.text;
                String line = body.isEmpty() ? title : title + "\n" + body;
                showLine(line, true, notice.postId);
            }
        });
    }

    private void showLine(String text, boolean open, String postId) {
        if (column == null) {
            return;
        }
        TextView view = new TextView(column.getContext());
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        view.setPadding(0, dp(12), 0, dp(12));
        if (open && postId != null) {
            view.setOnClickListener(v -> presentFragment(new TellomiDiscoverPostFragment(postId)));
        }
        column.addView(view, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private String phrase(TellomiDiscover.Copy copy, String key) {
        return TellomiPack.text(key, copy.languageCode, copy.source);
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
        return TellomiDiscover.Copy.resolve(language, TellomiPack.device());
    }
}
