package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;

/**
 * Scrollable column for a Tellomi login page. The slide view is given an exact
 * height, so a page that does not scroll clips the button on a short window,
 * in landscape, or above the numeric keypad. Wide windows keep the column at
 * {@link TellomiLoginFit#MAX_CONTENT_DP}.
 */
public final class TellomiFormHost {

    public final ScrollView scroll;
    public final LinearLayout column;

    public TellomiFormHost(Context context) {
        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        column = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int pad = TellomiLoginFit.extraSidePadPx(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(TellomiLoginFit.MAX_CONTENT_DP));
                if (getPaddingLeft() != pad || getPaddingRight() != pad) {
                    setPadding(pad, getPaddingTop(), pad, getPaddingBottom());
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int content = getMeasuredHeight();
                int mode = MeasureSpec.getMode(heightMeasureSpec);
                int size = MeasureSpec.getSize(heightMeasureSpec);
                if (TellomiLoginFit.expandToViewport(content, mode, size)) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }
        };
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        scroll.addView(column, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
    }

    public void add(View child, int index, ViewGroup.LayoutParams params) {
        column.addView(child, index, params);
    }

    public boolean contains(View child) {
        return child != null && child != scroll && column.indexOfChild(child) >= 0;
    }
}
