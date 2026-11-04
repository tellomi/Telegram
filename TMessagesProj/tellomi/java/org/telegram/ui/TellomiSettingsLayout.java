package org.telegram.ui;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Draws {@link TellomiSettingsModel} on the settings tab.
 * Home is a card plus three group rows. Each row opens a secondary page.
 * Help reads {@code tellomi_help_url}, or https://tellomi.cn/help.
 */
public final class TellomiSettingsLayout {

    public static final String HELP_CONFIG_KEY = TellomiSettingsModel.HELP_CONFIG_KEY;
    public static final String DEFAULT_HELP_URL = TellomiSettingsModel.DEFAULT_HELP_URL;
    public static final int ROW_ACCOUNT = TellomiSettingsModel.ROW_ACCOUNT;
    public static final int ROW_GENERAL = TellomiSettingsModel.ROW_GENERAL;
    public static final int ROW_SUPPORT = TellomiSettingsModel.ROW_SUPPORT;

    private static volatile Map<Integer, List<UItem>> pages = Collections.emptyMap();
    /** Header views that already watch attach, so bind can reapply the card inset. */
    private static final Set<View> sideMarginHooks = Collections.newSetFromMap(new WeakHashMap<>());

    private TellomiSettingsLayout() {
    }

    public interface RowOpener {
        void open(int rowId);
    }

    public static TellomiSettingsModel.Copy currentCopy() {
        String language = null;
        try {
            LocaleController.LocaleInfo info = LocaleController.peekCurrentLocaleInfo();
            if (info != null && info.shortName != null && info.shortName.length() > 0) {
                language = info.shortName;
            }
        } catch (Throwable ignored) {
        }
        if (language == null || language.length() == 0) {
            language = Locale.getDefault().getLanguage();
        }
        return TellomiSettingsModel.Copy.resolve(language, TellomiPack.device());
    }

    public static boolean isGroup(int id) {
        return TellomiSettingsModel.isGroup(id);
    }

    public static String helpUrl(int account) {
        String raw = null;
        try {
            raw = MessagesController.getInstance(account).config.tellomiHelpUrl.get();
        } catch (Throwable ignored) {
        }
        return TellomiSettingsModel.normalizeHelpUrl(raw);
    }

    public static void applyCard(int account, TLRPC.User user, TextView title, TextView subtitle, View cardView) {
        String name = "";
        String phoneDisplay = null;
        String email = null;
        String username = null;
        try {
            if (user != null) {
                name = UserObject.getUserName(user);
                username = UserObject.getPublicUsername(user);
            }
            email = TellomiAccount.emailOf(account, user);
            String digits = TellomiAccount.phoneOf(user);
            if (digits != null) {
                try {
                    phoneDisplay = PhoneFormat.getInstance().format("+" + digits);
                } catch (Throwable ignored) {
                    phoneDisplay = "+" + digits;
                }
            }
        } catch (Throwable ignored) {
        }
        TellomiSettingsModel.Card card = TellomiSettingsModel.card(name, phoneDisplay, email, username, currentCopy());
        if (title != null) {
            title.setText(card.title);
        }
        if (subtitle != null) {
            subtitle.setSingleLine(false);
            subtitle.setMaxLines(3);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            int pad = AndroidUtilities.dp(16);
            subtitle.setPadding(pad, subtitle.getPaddingTop(), pad, subtitle.getPaddingBottom());
            subtitle.setText(card.subtitle);
        }
        decorate(cardView);
    }

    public static void decorate(View card) {
        if (card == null) {
            return;
        }
        try {
            card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), Theme.getColor(Theme.key_windowBackgroundWhite)));
            applySideMargins(card);
            watchSideMargins(card);
        } catch (Throwable ignored) {
        }
    }

    /**
     * The header is bound with match-parent layout params and no inset.
     * Those params do not exist until the row is attached, so the first
     * {@link #decorate} call cannot see them. Without the inset the rounded
     * rect sits on the screen edge and the corners disappear.
     */
    static void applySideMargins(View card) {
        if (card == null) {
            return;
        }
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        int margin = AndroidUtilities.dp(12);
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
        if (mlp.leftMargin == margin && mlp.rightMargin == margin) {
            return;
        }
        mlp.leftMargin = margin;
        mlp.rightMargin = margin;
        card.setLayoutParams(mlp);
    }

    private static void watchSideMargins(View card) {
        if (!sideMarginHooks.add(card)) {
            return;
        }
        card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                applySideMargins(v);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
            }
        });
    }

    /** Rebuilds the settings list into the three group rows and remembers each page. */
    public static void arrange(ArrayList<UItem> items) {
        if (items == null) {
            pages = Collections.emptyMap();
            return;
        }
        TellomiSettingsModel.Copy copy = currentCopy();
        List<TellomiSettingsModel.Entry> source = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            UItem item = items.get(i);
            source.add(new TellomiSettingsModel.Entry(item == null ? 0 : item.id, kindOf(item), textOf(item), i));
        }
        Map<Integer, List<UItem>> nextPages = new HashMap<>();
        nextPages.put(ROW_ACCOUNT, take(items, TellomiSettingsModel.pageEntries(ROW_ACCOUNT, source, copy)));
        nextPages.put(ROW_GENERAL, take(items, TellomiSettingsModel.pageEntries(ROW_GENERAL, source, copy)));
        nextPages.put(ROW_SUPPORT, take(items, TellomiSettingsModel.pageEntries(ROW_SUPPORT, source, copy)));
        pages = nextPages;
        List<TellomiSettingsModel.Entry> planned = TellomiSettingsModel.arrangeEntries(source, copy);
        ArrayList<UItem> next = new ArrayList<>(planned.size());
        for (int i = 0; i < planned.size(); i++) {
            TellomiSettingsModel.Entry entry = planned.get(i);
            if (entry.sourceIndex >= 0) {
                next.add(items.get(entry.sourceIndex));
            } else if (isGroup(entry.id)) {
                next.add(groupRow(entry.id, entry.text));
            } else if (entry.kind == TellomiSettingsModel.KIND_SHADOW) {
                next.add(UItem.asShadow(null));
            }
        }
        items.clear();
        items.addAll(next);
    }

    public static List<UItem> pageItems(int groupId) {
        List<UItem> stored = pages.get(groupId);
        if (stored == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(stored);
    }

    public static BaseFragment section(int groupId, RowOpener opener) {
        return new Section(groupId, opener);
    }

    private static List<UItem> take(ArrayList<UItem> items, List<TellomiSettingsModel.Entry> entries) {
        ArrayList<UItem> page = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            TellomiSettingsModel.Entry entry = entries.get(i);
            if (entry.sourceIndex < 0 || entry.sourceIndex >= items.size()) {
                continue;
            }
            UItem item = items.get(entry.sourceIndex);
            if (entry.id == TellomiSettingsModel.FAQ_ID && entry.text != null) {
                item.text = entry.text;
            }
            page.add(item);
        }
        return page;
    }

    private static UItem groupRow(int id, String title) {
        int icon = R.drawable.msg_settings;
        if (id == ROW_ACCOUNT) {
            icon = R.drawable.settings_account;
        } else if (id == ROW_SUPPORT) {
            icon = R.drawable.settings_faq;
        }
        return SettingsActivity.SettingCell.Factory.of(
            id,
            IconBackgroundColors.GRAY.top,
            IconBackgroundColors.GRAY.bottom,
            icon,
            title == null ? "" : title
        );
    }

    private static int kindOf(UItem item) {
        if (item == null) {
            return TellomiSettingsModel.KIND_OTHER;
        }
        int viewType = item.viewType;
        if (viewType == UniversalAdapter.VIEW_TYPE_HEADER
            || viewType == UniversalAdapter.VIEW_TYPE_GRAY_SECTION
            || viewType == UniversalAdapter.VIEW_TYPE_BLACK_HEADER
            || viewType == UniversalAdapter.VIEW_TYPE_LARGE_HEADER) {
            return TellomiSettingsModel.KIND_HEADER;
        }
        if (viewType == UniversalAdapter.VIEW_TYPE_CUSTOM_SHADOW) {
            return TellomiSettingsModel.KIND_CUSTOM;
        }
        if (viewType == UniversalAdapter.VIEW_TYPE_SHADOW || viewType == UniversalAdapter.VIEW_TYPE_LARGE_SHADOW) {
            return TellomiSettingsModel.KIND_SHADOW;
        }
        try {
            if (item.instanceOf(SettingsActivity.SettingCell.Factory.class)) {
                return TellomiSettingsModel.KIND_ROW;
            }
        } catch (Throwable ignored) {
        }
        return TellomiSettingsModel.KIND_OTHER;
    }

    private static String textOf(UItem item) {
        if (item == null || item.text == null) {
            return null;
        }
        return item.text.toString();
    }

    /** One of the three secondary pages. The action bar title is the group title. */
    public static final class Section extends BaseFragment {

        private final int groupId;
        private final RowOpener opener;
        private UniversalRecyclerView listView;

        Section(int groupId, RowOpener opener) {
            this.groupId = groupId;
            this.opener = opener;
        }

        @Override
        public View createView(android.content.Context context) {
            actionBar.setBackButtonDrawable(new BackDrawable(false));
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(TellomiSettingsModel.pageTitle(groupId, currentCopy()));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    }
                }
            });
            FrameLayout content = new FrameLayout(context);
            content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
            listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
            listView.setSections();
            content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            return fragmentView = content;
        }

        @Override
        public void onResume() {
            super.onResume();
            if (actionBar != null) {
                actionBar.setTitle(TellomiSettingsModel.pageTitle(groupId, currentCopy()));
            }
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        }

        private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.addAll(pageItems(groupId));
            items.add(UItem.asShadow(null));
        }

        private void onClick(UItem item, View view, int position, float x, float y) {
            if (item == null || opener == null) {
                return;
            }
            TellomiSettingsModel.dispatch(opener::open, item.id);
        }
    }
}
