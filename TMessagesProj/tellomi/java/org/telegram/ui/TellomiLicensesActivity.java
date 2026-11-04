package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** Open-source credits for the bundled wallpaper patterns. */
public class TellomiLicensesActivity extends BaseFragment {

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.TellomiOpenSourceLicenses));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        TextView textView = new TextView(context);
        textView.setText(TellomiWallpaperCatalog.licenseNotice());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextIsSelectable(true);
        int pad = AndroidUtilities.dp(16);
        textView.setPadding(pad, AndroidUtilities.dp(12), pad, AndroidUtilities.dp(24));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        scrollView.addView(textView);
        fragmentView = scrollView;
        return fragmentView;
    }
}
