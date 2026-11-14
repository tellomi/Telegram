package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.NotificationCenter;
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
 * Settings → General → Navigation bar. Discover, Calls, and Contacts toggle
 * in {@link TellomiMainTabs}, the same store the calls long-press writes.
 * Chats, Settings, and Profile stay on.
 */
public class TellomiNavBarActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ROW_TAB = 10;

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.TellomiNavBar));
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
        if (!super.onFragmentCreate()) {
            return false;
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mainTabsSettingsUpdated);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.mainTabsSettingsUpdated);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mainTabsSettingsUpdated && listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        TellomiMainTabs.Store store = TellomiMainTabsStore.device();
        for (int tab : TellomiMainTabs.BAR_ORDER) {
            int title = TellomiMenuSettingsActivity.titleRes(tab);
            if (title == 0) {
                continue;
            }
            boolean locked = TellomiMainTabs.isLocked(tab);
            items.add(UItem.asCheck(ROW_TAB + tab, getString(title))
                    .setChecked(TellomiMainTabs.isVisible(store, tab))
                    .setEnabled(!locked));
        }
        items.add(UItem.asShadow(null));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id < ROW_TAB || item.id >= ROW_TAB + TellomiMainTabs.COUNT) {
            return;
        }
        int tab = item.id - ROW_TAB;
        if (TellomiMainTabs.isLocked(tab)) {
            return;
        }
        TellomiMainTabs.Store store = TellomiMainTabsStore.device();
        if (!TellomiMainTabs.setVisible(store, tab, !TellomiMainTabs.isVisible(store, tab))) {
            return;
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mainTabsSettingsUpdated);
    }
}
