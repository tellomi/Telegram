package org.telegram.ui.tellomi.feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TellomiDiscoverFlags;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.TellomiTabNavigation;
import org.telegram.ui.TellomiTabs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Live discover tab. While the flag is off or the account is logged out this
 * keeps the empty state and does not build a request.
 */
final class TellomiDiscoverScreen implements TellomiDiscoverComposeFragment.Publisher {

    private static final String CITY_KEY = "tellomi_discover_city";
    private static final int POST_PRESENT_ATTEMPTS = 12;
    private static final int POST_PRESENT_RETRY_MS = 80;

    private final FeedActivity activity;
    private final FrameLayout root;
    private final Context context;
    private final TellomiDiscover.Loader loader;
    private final int account;

    private TellomiDiscover.FeedKind kind = TellomiDiscover.FeedKind.RECOMMEND;
    private final List<TellomiDiscover.FeedKind> tabs = new ArrayList<>();
    private final Map<TellomiDiscover.FeedKind, List<TellomiDiscover.Post>> posts = new HashMap<>();
    private final Map<TellomiDiscover.FeedKind, String> cursors = new HashMap<>();
    private String cityCode;
    private boolean locationAsked;
    private boolean destroyed;
    private String banner;
    private boolean sawCapabilities;
    private boolean engineHasFeed;
    private Boolean engineVisible;
    private boolean engineConfigLoaded;
    private int textLimit = TellomiDiscover.POST_TEXT_LIMIT;
    private boolean loading;
    private boolean loadingMore;
    private int feedGeneration;
    private boolean failed;
    private String cityFilter = "";
    private boolean openingPost;
    private int postPresentAttempts;

    TellomiDiscoverScreen(FeedActivity activity, FrameLayout root, Context context) {
        this.activity = activity;
        this.root = root;
        this.context = context;
        this.account = activity.getCurrentAccount();
        this.loader = TellomiDiscoverClient.loader(account);
        this.cityCode = storedCity();
        tabs.add(TellomiDiscover.FeedKind.RECOMMEND);
        tabs.add(TellomiDiscover.FeedKind.FOLLOWING);
    }

    void destroy() {
        destroyed = true;
        loader.cancel();
    }

    void onLocationPermission(int requestCode, int[] grantResults) {
        if (requestCode != TellomiDiscoverLocation.REQUEST_CODE || destroyed) {
            return;
        }
        if (TellomiDiscoverLocation.hasCoarse(activity.getParentActivity())) {
            TellomiDiscoverLocation.locateCity(activity.getParentActivity(), cityCallback);
        } else {
            sync(false);
        }
    }

    void sync(boolean reload) {
        if (destroyed) {
            return;
        }
        TellomiDiscover.Configuration configuration = configuration();
        boolean loggedIn = loggedIn();
        consumePending(loggedIn, configuration.enabled);
        TellomiDiscover.Chrome chrome = TellomiDiscover.chrome(loggedIn, configuration.enabled, kind, null, failed, cityCode);
        if (!chrome.issuesNetwork()) {
            loader.cancel();
            loading = false;
            loadingMore = false;
        }
        TellomiDiscover.Copy copy = copy();
        if (chrome.showsCompose && !Boolean.FALSE.equals(engineVisible)) {
            activity.showComposeButton(true, copy.compose);
        } else {
            activity.showComposeButton(false, copy.compose);
        }
        if (chrome.surface == TellomiDiscover.Surface.LEGACY_EMPTY || Boolean.FALSE.equals(engineVisible)) {
            String pending = pendingPost();
            boolean unavailable = chrome.postUnavailable || (pending != null && !pending.isEmpty());
            buildLegacy(unavailable);
        } else if (chrome.surface == TellomiDiscover.Surface.CHOOSE_CITY) {
            buildCity(copy);
        } else {
            buildFeed(copy, chrome);
        }
        if (chrome.loadsFeed && !Boolean.FALSE.equals(engineVisible) && !loading && (reload || (posts.get(kind) == null && !failed))) {
            loadFeed(true);
        }
    }

    private void consumePending(boolean loggedIn, boolean enabled) {
        if (openingPost) {
            return;
        }
        String pending = pendingPost();
        if (pending == null) {
            return;
        }
        TellomiDiscover.Chrome post = TellomiDiscover.chrome(loggedIn, enabled, kind, pending, false, cityCode);
        if (post.surface != TellomiDiscover.Surface.DETAIL) {
            return;
        }
        openingPost = true;
        postPresentAttempts = 0;
        final String id = post.detailId;
        AndroidUtilities.runOnUIThread(() -> presentOpenedPost(id));
    }

    /** A chat close can still be in progress. Retry until the post can be presented (D-57). */
    private void presentOpenedPost(String id) {
        if (destroyed) {
            openingPost = false;
            return;
        }
        if (activity.presentFragment(new TellomiDiscoverPostFragment(id))) {
            openingPost = false;
            postPresentAttempts = 0;
            FeedActivity.takePendingPostId();
            activity.clearOpenedPost();
            return;
        }
        if (postPresentAttempts < POST_PRESENT_ATTEMPTS) {
            postPresentAttempts++;
            AndroidUtilities.runOnUIThread(() -> presentOpenedPost(id), POST_PRESENT_RETRY_MS);
            return;
        }
        openingPost = false;
        postPresentAttempts = 0;
    }

    private String pendingPost() {
        String opened = activity.getOpenedPostId();
        if (opened != null && !opened.isEmpty()) {
            return opened;
        }
        return FeedActivity.peekPendingPostId();
    }

    void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        TellomiDiscover.FeedKind next = tabs.get(index);
        if (next == kind) {
            loadFeed(true);
            return;
        }
        kind = next;
        failed = false;
        banner = null;
        loading = false;
        loadingMore = false;
        if (kind == TellomiDiscover.FeedKind.NEARBY) {
            considerNearbyLocation();
        }
        sync(true);
    }

    private void considerNearbyLocation() {
        TellomiDiscover.NearbyGate gate = TellomiDiscover.nearbyGate(cityCode, locationGate(), locationAsked);
        if (!gate.feed && gate.requestLocation) {
            locationAsked = true;
            startLocator();
        }
    }

    private TellomiDiscover.LocationGate locationGate() {
        if (TellomiDiscoverLocation.hasCoarse(activity.getParentActivity())) {
            return TellomiDiscover.LocationGate.ALLOWED;
        }
        if (locationAsked) {
            return TellomiDiscover.LocationGate.DENIED;
        }
        return TellomiDiscover.LocationGate.NOT_DETERMINED;
    }

    private void startLocator() {
        if (TellomiDiscoverLocation.hasCoarse(activity.getParentActivity())) {
            TellomiDiscoverLocation.locateCity(activity.getParentActivity(), cityCallback);
            return;
        }
        TellomiDiscoverLocation.requestCoarse(activity.getParentActivity());
    }

    private final TellomiDiscoverLocation.Callback cityCallback = new TellomiDiscoverLocation.Callback() {
        @Override
        public void onCity(String code) {
            if (destroyed) {
                return;
            }
            if (cityCode == null && code != null) {
                applyCity(code);
            } else {
                sync(false);
            }
        }

        @Override
        public void onDenied() {
            if (!destroyed) {
                sync(false);
            }
        }
    };

    private void applyCity(String code) {
        String known = TellomiDiscoverCity.knownCode(code);
        if (known == null) {
            banner = copy().invalidCity;
            sync(false);
            return;
        }
        cityCode = known;
        storeCity(known);
        banner = null;
        failed = false;
        posts.remove(TellomiDiscover.FeedKind.NEARBY);
        cursors.remove(TellomiDiscover.FeedKind.NEARBY);
        sync(true);
    }

    private void loadFeed(boolean reset) {
        TellomiDiscover.Configuration configuration = configuration();
        TellomiDiscover.Chrome chrome = TellomiDiscover.chrome(loggedIn(), configuration.enabled, kind, null, false, cityCode);
        if (!chrome.loadsFeed) {
            return;
        }
        String cursor = reset ? null : cursors.get(kind);
        if (!reset && (loading || loadingMore || cursor == null)) {
            return;
        }
        feedGeneration++;
        int generation = feedGeneration;
        if (reset) {
            loading = true;
            loadingMore = false;
            sawCapabilities = false;
            engineVisible = null;
            engineConfigLoaded = false;
        } else {
            loadingMore = true;
        }
        advance(reset, generation, kind, cursor, configuration);
    }

    private void advance(final boolean reset, final int generation, final TellomiDiscover.FeedKind requested, final String cursor, final TellomiDiscover.Configuration configuration) {
        if (generation != feedGeneration || destroyed) {
            return;
        }
        final TellomiDiscover.Operation operation;
        if (!sawCapabilities) {
            operation = TellomiDiscover.Operation.capabilities();
        } else if (!engineHasFeed) {
            finishHidden(generation);
            return;
        } else if (engineVisible == null) {
            operation = TellomiDiscover.Operation.status();
        } else if (!engineVisible) {
            finishHidden(generation);
            return;
        } else if (!engineConfigLoaded && reset) {
            operation = TellomiDiscover.Operation.engineConfig();
        } else if (requested == TellomiDiscover.FeedKind.NEARBY) {
            String city = TellomiDiscoverCity.knownCode(cityCode);
            if (city == null) {
                loading = false;
                loadingMore = false;
                sync(false);
                return;
            }
            operation = TellomiDiscover.Operation.nearby(city, cursor);
        } else {
            operation = TellomiDiscover.Operation.feed(requested, cursor, true);
        }
        loader.run(true, configuration, operation, new TellomiDiscover.Completion() {
            @Override
            public void onResult(TellomiDiscover.Outcome result) {
                if (destroyed || generation != feedGeneration) {
                    return;
                }
                if (result.ok() && result.value.kind == TellomiDiscover.Decoded.Kind.CAPABILITIES) {
                    sawCapabilities = true;
                    engineHasFeed = result.value.capabilities.features.contains("feed");
                    advance(reset, generation, requested, cursor, configuration);
                    return;
                }
                if (result.ok() && result.value.kind == TellomiDiscover.Decoded.Kind.STATUS) {
                    engineVisible = result.value.status;
                    advance(reset, generation, requested, cursor, configuration);
                    return;
                }
                if (result.ok() && result.value.kind == TellomiDiscover.Decoded.Kind.ENGINE_CONFIG) {
                    engineConfigLoaded = true;
                    textLimit = result.value.engineConfig.postTextChars;
                    if (!result.value.engineConfig.tabs.isEmpty()) {
                        tabs.clear();
                        tabs.addAll(result.value.engineConfig.tabs);
                        if (!tabs.contains(kind)) {
                            kind = tabs.get(0);
                            if (kind == TellomiDiscover.FeedKind.NEARBY) {
                                considerNearbyLocation();
                            }
                        }
                    }
                    advance(reset, generation, kind, cursor, configuration);
                    return;
                }
                if (result.ok() && result.value.kind == TellomiDiscover.Decoded.Kind.FEED) {
                    if (result.value.feed.items.isEmpty()) {
                        if (reset) {
                            posts.put(requested, new ArrayList<TellomiDiscover.Post>());
                        }
                        cursors.remove(requested);
                        failed = false;
                        loading = false;
                        loadingMore = false;
                        sync(false);
                    } else {
                        loadCards(result.value.feed, reset, generation, requested, configuration);
                    }
                    return;
                }
                if (result.error == TellomiDiscover.ClientError.HIDDEN) {
                    finishHidden(generation);
                    return;
                }
                if (result.error == TellomiDiscover.ClientError.INVALID_CITY) {
                    cityCode = null;
                    storeCity(null);
                    banner = copy().invalidCity;
                    loading = false;
                    loadingMore = false;
                    sync(false);
                    return;
                }
                if (result.error == TellomiDiscover.ClientError.DISABLED || result.error == TellomiDiscover.ClientError.SIGNED_OUT) {
                    failed = false;
                    loading = false;
                    loadingMore = false;
                    sync(false);
                    return;
                }
                if (reset) {
                    failed = true;
                }
                loading = false;
                loadingMore = false;
                sync(false);
            }
        });
    }

    private void loadCards(final TellomiDiscover.FeedPage page, final boolean reset, final int generation, final TellomiDiscover.FeedKind requested, TellomiDiscover.Configuration configuration) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < page.items.size(); i++) {
            ids.add(page.items.get(i).id);
        }
        loader.run(true, configuration, TellomiDiscover.Operation.cards(ids), new TellomiDiscover.Completion() {
            @Override
            public void onResult(TellomiDiscover.Outcome result) {
                if (destroyed || generation != feedGeneration) {
                    return;
                }
                loading = false;
                loadingMore = false;
                if (result.ok() && result.value.kind == TellomiDiscover.Decoded.Kind.CARDS) {
                    List<TellomiDiscover.Post> displayed = new ArrayList<>();
                    for (int i = 0; i < result.value.cards.size(); i++) {
                        displayed.add(TellomiDiscoverParse.displayPost(result.value.cards.get(i)));
                    }
                    if (reset || posts.get(requested) == null) {
                        posts.put(requested, displayed);
                    } else {
                        posts.get(requested).addAll(displayed);
                    }
                    if (page.cursor != null) {
                        cursors.put(requested, page.cursor);
                    } else {
                        cursors.remove(requested);
                    }
                    failed = false;
                } else if (result.error == TellomiDiscover.ClientError.HIDDEN) {
                    engineVisible = false;
                    failed = false;
                } else if (reset) {
                    failed = true;
                }
                sync(false);
            }
        });
    }

    private void finishHidden(int generation) {
        if (generation != feedGeneration || destroyed) {
            return;
        }
        engineVisible = false;
        failed = false;
        loading = false;
        loadingMore = false;
        posts.put(kind, new ArrayList<TellomiDiscover.Post>());
        sync(false);
    }

    void compose() {
        TellomiDiscover.Configuration configuration = configuration();
        if (!TellomiDiscover.chrome(loggedIn(), configuration.enabled, kind, null, false, cityCode).showsCompose || Boolean.FALSE.equals(engineVisible)) {
            return;
        }
        activity.presentFragment(TellomiDiscoverComposeFragment.create(textLimit, this));
    }

    @Override
    public void publish(String text, TellomiDiscover.Visibility visibility, long[] userIds, String nonce, final TellomiDiscover.Completion completion) {
        final TellomiDiscover.Configuration configuration = configuration();
        if (!loggedIn() || !configuration.enabled) {
            completion.onResult(TellomiDiscover.Outcome.failure(loggedIn() ? TellomiDiscover.ClientError.DISABLED : TellomiDiscover.ClientError.SIGNED_OUT));
            return;
        }
        long now = System.currentTimeMillis() / 1000L;
        TellomiDiscover.PublishStep step = TellomiDiscover.publishStep(text, visibility, userIds, UserConfig.getInstance(account).getClientUserId(), nonce, textLimit, null, now, cityCode);
        if (step.kind == TellomiDiscover.PublishStep.Kind.READY) {
            loader.run(true, configuration, TellomiDiscover.Operation.publish(text, visibility, null, nonce, textLimit, cityCode), completion);
            return;
        }
        if (step.kind == TellomiDiscover.PublishStep.Kind.REJECTED) {
            completion.onResult(TellomiDiscover.Outcome.failure(step.error));
            return;
        }
        TellomiDiscoverClient.fetchAudienceToken(account, step.tokenKind, step.userIds, new TellomiDiscoverClient.AudienceCallback() {
            @Override
            public void onResult(TellomiDiscover.AudienceToken token, TellomiDiscover.ClientError error) {
                if (destroyed) {
                    completion.onResult(TellomiDiscover.Outcome.failure(TellomiDiscover.ClientError.SIGNED_OUT));
                    return;
                }
                if (error != null || token == null) {
                    completion.onResult(TellomiDiscover.Outcome.failure(error == null ? TellomiDiscover.ClientError.UNAVAILABLE : error));
                    return;
                }
                loader.run(true, configuration, TellomiDiscover.Operation.publish(text, visibility, token.token, nonce, textLimit, cityCode), completion);
            }
        });
    }

    @Override
    public void published() {
        posts.remove(kind);
        sync(true);
    }

    private void buildLegacy(boolean postUnavailable) {
        root.removeAllViews();
        activity.titleView = null;
        activity.messageView = null;
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        TellomiTabNavigation.Copy tabCopy = TellomiTabs.currentCopy();
        TextView title = new TextView(context);
        title.setText(tabCopy.feedEmptyTitle);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, activity.getResourceProvider()));
        column.addView(title, layout(LinearLayout.LayoutParams.MATCH_PARENT));
        TextView message = new TextView(context);
        message.setText(postUnavailable ? tabCopy.feedPostUnavailable : tabCopy.feedEmptyText);
        message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        message.setGravity(Gravity.CENTER);
        message.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, activity.getResourceProvider()));
        LinearLayout.LayoutParams messageLp = layout(LinearLayout.LayoutParams.MATCH_PARENT);
        messageLp.topMargin = dp(8);
        column.addView(message, messageLp);
        column.setPadding(dp(32), 0, dp(32), 0);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        activity.titleView = title;
        activity.messageView = message;
    }

    private void buildCity(TellomiDiscover.Copy copy) {
        root.removeAllViews();
        activity.titleView = null;
        activity.messageView = null;
        LinearLayout column = column();
        addTabs(column, copy);
        column.addView(label(copy.cityPrompt, 17, true), layout(LinearLayout.LayoutParams.MATCH_PARENT));
        TextView hint = label(copy.cityDenied, 14, false);
        LinearLayout.LayoutParams hintLp = layout(LinearLayout.LayoutParams.MATCH_PARENT);
        hintLp.topMargin = dp(4);
        column.addView(hint, hintLp);
        TextView locate = label(copy.useLocation, 16, true);
        locate.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, activity.getResourceProvider()));
        locate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLocator();
            }
        });
        LinearLayout.LayoutParams locateLp = layout(LinearLayout.LayoutParams.MATCH_PARENT);
        locateLp.topMargin = dp(12);
        column.addView(locate, locateLp);
        final EditText filter = new EditText(context);
        filter.setHint(copy.cityPlaceholder);
        filter.setSingleLine(true);
        filter.setText(cityFilter);
        filter.setBackground(null);
        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String next = s == null ? "" : s.toString();
                if (next.equals(cityFilter)) {
                    return;
                }
                cityFilter = next;
                sync(false);
            }
        });
        column.addView(filter, layout(LinearLayout.LayoutParams.MATCH_PARENT));
        if (!cityFilter.isEmpty()) {
            filter.requestFocus();
            filter.setSelection(filter.getText() == null ? 0 : filter.getText().length());
        }
        List<TellomiDiscoverCity.City> cities = TellomiDiscoverCity.cities(language(), org.telegram.ui.TellomiPack.device());
        String needle = cityFilter.trim().toLowerCase(java.util.Locale.US);
        for (int i = 0; i < cities.size(); i++) {
            final TellomiDiscoverCity.City city = cities.get(i);
            if (!needle.isEmpty() && !city.name.toLowerCase(java.util.Locale.US).contains(needle) && !city.code.contains(needle)) {
                continue;
            }
            TextView row = label(city.name, 16, false);
            row.setMinHeight(dp(48));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyCity(city.code);
                }
            });
            column.addView(row, layout(LinearLayout.LayoutParams.MATCH_PARENT));
        }
        scroll(column);
    }

    private void buildFeed(TellomiDiscover.Copy copy, TellomiDiscover.Chrome chrome) {
        root.removeAllViews();
        activity.titleView = null;
        activity.messageView = null;
        LinearLayout column = column();
        addTabs(column, copy);
        if (banner != null || chrome.failure) {
            TextView failure = label(banner != null ? banner : copy.failed, 14, false);
            failure.setTextColor(Theme.getColor(Theme.key_text_RedRegular, activity.getResourceProvider()));
            failure.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    failed = false;
                    banner = null;
                    loadFeed(true);
                }
            });
            column.addView(failure, layout(LinearLayout.LayoutParams.MATCH_PARENT));
        }
        if (loading || loadingMore) {
            ProgressBar progress = new ProgressBar(context);
            column.addView(progress, new LinearLayout.LayoutParams(dp(32), dp(32)));
        }
        List<TellomiDiscover.Post> list = posts.get(kind);
        if (list == null || list.isEmpty()) {
            if (!loading) {
                TextView title = label(kind == TellomiDiscover.FeedKind.NEARBY ? copy.nearbyEmpty : copy.emptyTitle, 17, true);
                title.setGravity(Gravity.CENTER);
                column.addView(title, layout(LinearLayout.LayoutParams.MATCH_PARENT));
                TextView body = label(kind == TellomiDiscover.FeedKind.NEARBY ? copy.cityDenied : copy.emptyText, 15, false);
                body.setGravity(Gravity.CENTER);
                column.addView(body, layout(LinearLayout.LayoutParams.MATCH_PARENT));
                activity.titleView = title;
                activity.messageView = body;
            }
        } else {
            for (int i = 0; i < list.size(); i++) {
                final TellomiDiscover.Post post = list.get(i);
                TellomiDiscover.PostSummary summary = TellomiDiscover.postSummary(post, copy, true);
                TextView row = new TextView(context);
                String text = summary.title;
                if (!summary.body.isEmpty()) {
                    text = text.isEmpty() ? summary.body : text + "\n" + summary.body;
                }
                if (!summary.meta.isEmpty()) {
                    text = text + "\n" + summary.meta;
                }
                row.setText(text);
                row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                row.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, activity.getResourceProvider()));
                row.setPadding(0, dp(12), 0, dp(12));
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        activity.presentFragment(new TellomiDiscoverPostFragment(post.id));
                    }
                });
                LinearLayout line = new LinearLayout(context);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                line.addView(row);
                if (!TellomiDiscoverReport.menuActions(loggedIn()).isEmpty()) {
                    ImageView more = new ImageView(context);
                    more.setImageResource(R.drawable.ic_ab_other);
                    more.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, activity.getResourceProvider()));
                    more.setPadding(dp(8), dp(12), 0, dp(12));
                    more.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            TellomiDiscoverReport.showMenu(activity, more, post.id, null);
                        }
                    });
                    line.addView(more, new LinearLayout.LayoutParams(dp(40), dp(40)));
                }
                column.addView(line, layout(LinearLayout.LayoutParams.MATCH_PARENT));
            }
        }
        ScrollView scroll = scroll(column);
        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                View child = scroll.getChildAt(0);
                if (child != null && scrollY + scroll.getHeight() >= child.getHeight() - dp(48)) {
                    loadFeed(false);
                }
            }
        });
    }

    private void addTabs(LinearLayout column, TellomiDiscover.Copy copy) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            TellomiDiscover.FeedKind tab = tabs.get(i);
            TextView label = label(tabTitle(tab, copy), 15, tab == kind);
            label.setGravity(Gravity.CENTER);
            label.setPadding(dp(12), dp(8), dp(12), dp(8));
            label.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTab(index);
                }
            });
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        column.addView(row, layout(LinearLayout.LayoutParams.MATCH_PARENT));
    }

    private String tabTitle(TellomiDiscover.FeedKind tab, TellomiDiscover.Copy copy) {
        if (tab == TellomiDiscover.FeedKind.FOLLOWING) {
            return copy.following;
        }
        if (tab == TellomiDiscover.FeedKind.NEARBY) {
            return copy.nearby;
        }
        return copy.recommend;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), 0, dp(16), dp(16));
        return column;
    }

    private ScrollView scroll(LinearLayout column) {
        ScrollView scroll = new ScrollView(context);
        scroll.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return scroll;
    }

    private TextView label(String text, int size, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        if (bold) {
            view.setTypeface(AndroidUtilities.bold());
        }
        view.setTextColor(Theme.getColor(bold ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText, activity.getResourceProvider()));
        return view;
    }

    private static LinearLayout.LayoutParams layout(int width) {
        return new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private TellomiDiscover.Configuration configuration() {
        return TellomiDiscoverFlags.configuration(account);
    }

    private boolean loggedIn() {
        return UserConfig.getInstance(account).isClientActivated();
    }

    private TellomiDiscover.Copy copy() {
        return TellomiDiscover.Copy.resolve(language(), org.telegram.ui.TellomiPack.device());
    }

    private String language() {
        try {
            LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
            if (info != null && info.shortName != null && !info.shortName.isEmpty()) {
                return info.shortName;
            }
        } catch (Throwable ignored) {
        }
        return java.util.Locale.getDefault().toLanguageTag();
    }

    private String storedCity() {
        SharedPreferences preferences = MessagesController.getMainSettings(account);
        if (preferences == null) {
            return null;
        }
        return TellomiDiscoverCity.knownCode(preferences.getString(CITY_KEY, null));
    }

    private void storeCity(String city) {
        SharedPreferences preferences = MessagesController.getMainSettings(account);
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        String code = TellomiDiscoverCity.knownCode(city);
        if (code == null) {
            editor.remove(CITY_KEY);
        } else {
            editor.putString(CITY_KEY, code);
        }
        editor.apply();
    }
}
