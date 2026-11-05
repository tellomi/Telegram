package org.telegram.ui.tellomi.feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.TellomiPack;

/** Title, description, and cover for one stored or just-fetched preview. */
final class TellomiDiscoverLinkCard extends LinearLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final TextView titleView;
    private final TextView siteView;
    private final TextView bodyView;
    private final ImageView imageView;
    private String openUrl;

    private TellomiDiscoverLinkCard(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(10);
        setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(10));
        background.setColor(Theme.getColor(Theme.key_graySection, resourcesProvider));
        setBackground(background);
        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setVisibility(GONE);
        addView(imageView, new LayoutParams(dp(72), dp(72)));
        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        text.setPadding(dp(10), 0, 0, 0);
        titleView = line(context, 15, true);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        siteView = line(context, 12, false);
        siteView.setMaxLines(1);
        siteView.setEllipsize(TextUtils.TruncateAt.END);
        bodyView = line(context, 13, false);
        bodyView.setMaxLines(3);
        bodyView.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(titleView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        text.addView(siteView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        text.addView(bodyView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
    }

    static TellomiDiscoverLinkCard create(Context context, Theme.ResourcesProvider resourcesProvider) {
        TellomiDiscoverLinkCard card = new TellomiDiscoverLinkCard(context, resourcesProvider);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        card.setLayoutParams(params);
        return card;
    }

    void showLoading() {
        openUrl = null;
        imageView.setImageBitmap(null);
        imageView.setVisibility(GONE);
        titleView.setText(loadingText());
        siteView.setVisibility(GONE);
        bodyView.setVisibility(GONE);
        setOnClickListener(null);
        setClickable(false);
        setVisibility(VISIBLE);
    }

    void show(TellomiDiscoverLink.Preview preview, boolean openOnClick) {
        if (preview == null || preview.url == null) {
            hide();
            return;
        }
        openUrl = TellomiDiscoverLink.fetchable(preview.url) ? preview.url : null;
        titleView.setText(preview.titleLine());
        String site = preview.siteLine();
        if (site == null) {
            siteView.setVisibility(GONE);
        } else {
            siteView.setText(site);
            siteView.setVisibility(VISIBLE);
        }
        if (preview.description == null || preview.description.isEmpty()) {
            bodyView.setVisibility(GONE);
        } else {
            bodyView.setText(preview.description);
            bodyView.setVisibility(VISIBLE);
        }
        TellomiDiscoverLinkImage.load(imageView, preview.image);
        if (openOnClick && openUrl != null) {
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    open();
                }
            });
        } else {
            setOnClickListener(null);
            setClickable(false);
        }
        setVisibility(VISIBLE);
    }

    void hide() {
        openUrl = null;
        imageView.setImageBitmap(null);
        setVisibility(GONE);
    }

    private void open() {
        if (openUrl == null) {
            return;
        }
        try {
            getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(openUrl)));
        } catch (ActivityNotFoundException ignored) {
        } catch (RuntimeException ignored) {
        }
    }

    private TextView line(Context context, int size, boolean bold) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        if (bold) {
            view.setTypeface(AndroidUtilities.bold());
        } else {
            view.setTypeface(Typeface.DEFAULT);
        }
        view.setTextColor(Theme.getColor(bold ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        return view;
    }

    private static String loadingText() {
        String language = "en";
        try {
            LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
            if (info != null && info.shortName != null) {
                language = info.shortName;
            }
        } catch (Throwable ignored) {
        }
        return TellomiPack.text("Tellomi.Discover.LinkPreviewLoading", language, TellomiPack.device());
    }
}
