package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/**
 * Settings → Navigation Bar (M2 / D-6, D-38). Discover, Calls, and Contacts
 * can be turned on or off. Chats stays on. Settings is not a bottom tab. Calls writes
 * {@code UserConfig.showCallsTab}, so the existing calls switch still works.
 */
public class TellomiNavigationSettings extends BaseFragment {

    /** Row on {@link SettingsActivity}, after Language. */
    public static final int ROW_ID = 40;

    private UniversalRecyclerView listView;

    public static void appendRow(ArrayList<UItem> items) {
        if (!TellomiTabs.enabled() || items == null) {
            return;
        }
        items.add(SettingsActivity.SettingCell.Factory.of(
            ROW_ID,
            IconBackgroundColors.GRAY.top,
            IconBackgroundColors.GRAY.bottom,
            R.drawable.settings_folders,
            TellomiTabs.currentCopy().navigationTitle
        ));
    }

    @Override
    public View createView(Context context) {
        TellomiTabNavigation.Copy copy = TellomiTabs.currentCopy();
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(copy.navigationTitle);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setSections();
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView = contentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        TellomiTabNavigation.Copy copy = TellomiTabs.currentCopy();
        TellomiTabs.Visibility visibility = TellomiTabs.read(currentAccount);
        for (int i = 0; i < TellomiTabNavigation.ROWS.size(); i++) {
            TellomiTabNavigation.Row row = TellomiTabNavigation.ROWS.get(i);
            boolean on = checked(visibility, row);
            UItem item = UItem.asSwitch(row.id, title(copy, row)).setChecked(on).setEnabled(row.toggleable);
            if (!row.toggleable) {
                item.onBind(view -> {
                    if (view instanceof TextCheckCell2) {
                        TextCheckCell2 cell = (TextCheckCell2) view;
                        cell.setEnabled(false);
                        if (cell.getCheckBox() != null) {
                            cell.getCheckBox().setEnabled(false);
                        }
                    }
                });
            }
            items.add(item);
        }
        items.add(UItem.asShadow(copy.navigationFooter));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        TellomiTabNavigation.Row row = TellomiTabNavigation.row(item.id);
        if (row == null || !row.toggleable || !TellomiTabs.enabled()) {
            return;
        }
        TellomiTabs.Visibility next = TellomiTabNavigation.apply(TellomiTabs.read(currentAccount), row, !item.checked);
        TellomiTabs.commit(currentAccount, next);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private static boolean checked(TellomiTabs.Visibility visibility, TellomiTabNavigation.Row row) {
        if (visibility == null || row == null) {
            return false;
        }
        switch (row) {
            case FEED:
                return visibility.feed;
            case CALLS:
                return visibility.calls;
            case CONTACTS:
                return visibility.contacts;
            case CHATS:
            case SETTINGS:
                return true;
            default:
                return false;
        }
    }

    private static String title(TellomiTabNavigation.Copy copy, TellomiTabNavigation.Row row) {
        switch (row) {
            case FEED:
                return copy.feed;
            case CHATS:
                return getString(R.string.MainTabsChats);
            case CALLS:
                return getString(R.string.MainTabsCalls);
            case CONTACTS:
                return getString(R.string.MainTabsContacts);
            case SETTINGS:
                return getString(R.string.Settings);
            default:
                return "";
        }
    }
}
