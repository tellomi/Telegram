package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.R;
import org.telegram.messenger.TellomiMainTabs;
import org.telegram.messenger.TellomiMainTabsStore;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/**
 * Settings → Tools → Menu settings. One switch hides tab captions. The next
 * section shows or hides each bottom-bar tab. Chats and Profile stay on.
 */
public class TellomiMenuSettingsActivity extends BaseFragment implements TellomiMainTabs.Listener {

    private static final int ROW_HIDE_LABELS = 1;
    private static final int ROW_TAB = 10;

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString("TellomiMenuSettings", R.string.TellomiMenuSettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView, true);
        return fragmentView = contentView;
    }

    @Override
    public boolean onFragmentCreate() {
        TellomiMainTabs.addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        TellomiMainTabs.removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    public void onTellomiMainTabsChanged() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        TellomiMainTabs.Store store = TellomiMainTabsStore.device();
        items.add(UItem.asCheck(ROW_HIDE_LABELS, getString("TellomiHideTabLabels", R.string.TellomiHideTabLabels))
                .setChecked(TellomiMainTabs.hideLabels(store)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString("TellomiNavBarButtons", R.string.TellomiNavBarButtons)));
        for (int tab : TellomiMainTabs.SETTINGS_ORDER) {
            boolean locked = TellomiMainTabs.isLocked(tab);
            items.add(UItem.asCheck(ROW_TAB + tab, getString(titleRes(tab)))
                    .setChecked(TellomiMainTabs.isVisible(store, tab))
                    .setEnabled(!locked));
        }
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        TellomiMainTabs.Store store = TellomiMainTabsStore.device();
        if (item.id == ROW_HIDE_LABELS) {
            TellomiMainTabs.setHideLabels(store, !TellomiMainTabs.hideLabels(store));
        } else if (item.id >= ROW_TAB && item.id < ROW_TAB + TellomiMainTabs.COUNT) {
            int tab = item.id - ROW_TAB;
            if (TellomiMainTabs.isLocked(tab)) {
                return;
            }
            TellomiMainTabs.setVisible(store, tab, !TellomiMainTabs.isVisible(store, tab));
        } else {
            return;
        }
        TellomiMainTabs.notifyChanged();
    }

    static int titleRes(int tab) {
        switch (tab) {
            case TellomiMainTabs.CONTACTS:
                return R.string.MainTabsContacts;
            case TellomiMainTabs.CALLS:
                return R.string.MainTabsCalls;
            case TellomiMainTabs.FEED:
                return R.string.Moments_Title;
            case TellomiMainTabs.CHATS:
                return R.string.MainTabsChats;
            case TellomiMainTabs.SETTINGS:
                return R.string.Settings;
            case TellomiMainTabs.PROFILE:
                return R.string.MainTabsProfile;
            default:
                return R.string.TellomiMenuSettings;
        }
    }
}
