/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Pre-login language list. Same search field, row, and selection mark as
 * LanguageSelectActivity (native name, English name, animated radio).
 * Translation toggles are omitted: they need a signed-in account.
 * Rows are the bundled packs, not a three-item card. See UPSTREAM_PATCHES.md.
 */

package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextRadioCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

public class TellomiLanguageSelectActivity extends BaseFragment {

    public interface Callback {
        void onLanguage(LocaleController.LocaleInfo info);
    }

    private final Callback callback;
    private RecyclerListView listView;
    private EmptyTextProgressView emptyView;
    private ListAdapter listAdapter;
    private ListAdapter searchAdapter;
    private boolean searchWas;
    private ArrayList<LocaleController.LocaleInfo> searchResult;
    private ArrayList<LocaleController.LocaleInfo> languages;

    public TellomiLanguageSelectActivity(Callback callback) {
        this.callback = callback;
    }

    @Override
    public View createView(Context context) {
        languages = new ArrayList<>(TellomiLanguageSheet.bundledChoices());
        searchWas = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.Language));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem searchItem = menu.addItem(0, R.drawable.outline_header_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
            }

            @Override
            public void onSearchCollapse() {
                searchWas = false;
                searchResult = null;
                if (listView != null) {
                    emptyView.setVisibility(View.GONE);
                    listView.setAdapter(listAdapter);
                }
            }

            @Override
            public void onTextChanged(EditText editText) {
                String text = editText.getText().toString();
                if (text.length() != 0) {
                    searchWas = true;
                    if (listView != null) {
                        listView.setAdapter(searchAdapter);
                    }
                    processSearch(text);
                } else {
                    searchWas = false;
                    searchResult = null;
                    if (listView != null) {
                        emptyView.setVisibility(View.GONE);
                        listView.setAdapter(listAdapter);
                    }
                }
            }
        });
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));

        listAdapter = new ListAdapter(false);
        searchAdapter = new ListAdapter(true);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        FrameLayout frame = (FrameLayout) fragmentView;

        emptyView = new EmptyTextProgressView(context);
        emptyView.setText(LocaleController.getString(R.string.NoResult));
        emptyView.showTextView();
        emptyView.setShowAtCenter(true);
        frame.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setEmptyView(emptyView);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(listAdapter);
        androidx.recyclerview.widget.DefaultItemAnimator itemAnimator = new androidx.recyclerview.widget.DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        listView.setItemAnimator(itemAnimator);
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            LocaleController.LocaleInfo info = languageAt(position);
            if (info == null) {
                return;
            }
            if (callback != null && !TellomiLanguageSheet.sameLanguage(info, LocaleController.getInstance().getCurrentLocaleInfo())) {
                callback.onLanguage(info);
            }
            finishFragment();
        });
        return fragmentView;
    }

    private LocaleController.LocaleInfo languageAt(int position) {
        List<LocaleController.LocaleInfo> source = searchWas ? searchResult : languages;
        if (source == null || position < 0 || position >= source.size()) {
            return null;
        }
        return source.get(position);
    }

    private void processSearch(String query) {
        final String q = query.trim().toLowerCase();
        Utilities.searchQueue.postRunnable(() -> {
            ArrayList<LocaleController.LocaleInfo> result = new ArrayList<>();
            if (q.length() != 0) {
                for (int i = 0; i < languages.size(); i++) {
                    LocaleController.LocaleInfo info = languages.get(i);
                    String name = info.name == null ? "" : info.name.toLowerCase();
                    String english = info.nameEnglish == null ? "" : info.nameEnglish.toLowerCase();
                    if (name.startsWith(q) || english.startsWith(q)) {
                        result.add(info);
                    }
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                searchResult = result;
                if (searchAdapter != null) {
                    searchAdapter.notifyDataSetChanged();
                }
            });
        });
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final boolean search;

        ListAdapter(boolean search) {
            this.search = search;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            List<LocaleController.LocaleInfo> source = search ? searchResult : languages;
            return source == null ? 0 : source.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextRadioCell cell = new TextRadioCell(parent.getContext());
            cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TextRadioCell cell = (TextRadioCell) holder.itemView;
            List<LocaleController.LocaleInfo> source = search ? searchResult : languages;
            if (source == null || position < 0 || position >= source.size()) {
                return;
            }
            LocaleController.LocaleInfo info = source.get(position);
            boolean last = position == source.size() - 1;
            String english = info.nameEnglish == null ? "" : info.nameEnglish;
            cell.setTextAndValueAndCheck(info.name, english, false, false, !last);
            boolean checked = TellomiLanguageSheet.sameLanguage(info, LocaleController.getInstance().getCurrentLocaleInfo());
            cell.setChecked(checked);
        }
    }
}
