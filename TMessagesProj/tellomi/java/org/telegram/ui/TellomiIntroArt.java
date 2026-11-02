package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.R;

/**
 * Six intro marks that slide with the same pager offset as the titles.
 * Page 0 is the Tellomi mark. The other five are MingCute, one per page.
 */
public final class TellomiIntroArt extends View {

    private final Drawable[] pages = new Drawable[6];
    private int page;
    private float pageOffset;
    private int tint = 0xFF111111;

    public TellomiIntroArt(Context context) {
        super(context);
        pages[0] = context.getDrawable(R.drawable.tellomi_intro_logo).mutate();
        pages[1] = context.getDrawable(R.drawable.tellomi_intro_nearby).mutate();
        pages[2] = context.getDrawable(R.drawable.tellomi_intro_devices).mutate();
        pages[3] = context.getDrawable(R.drawable.tellomi_intro_chat).mutate();
        pages[4] = context.getDrawable(R.drawable.tellomi_intro_photo).mutate();
        pages[5] = context.getDrawable(R.drawable.tellomi_intro_lock).mutate();
        applyTint();
    }

    /** {@code position} is the page on the left. {@code offset} runs 0 to 1 toward the next page. */
    public void setPage(int position, float offset) {
        if (position < 0) {
            position = 0;
        }
        if (position > pages.length - 1) {
            position = pages.length - 1;
        }
        if (offset < 0f) {
            offset = 0f;
        } else if (offset > 1f) {
            offset = 1f;
        }
        if (page == position && pageOffset == offset) {
            return;
        }
        page = position;
        pageOffset = offset;
        invalidate();
    }

    public void setTint(int color) {
        if (tint == color) {
            return;
        }
        tint = color;
        applyTint();
        invalidate();
    }

    private void applyTint() {
        PorterDuffColorFilter filter = new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN);
        for (Drawable drawable : pages) {
            drawable.setColorFilter(filter);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        float shift = -pageOffset * width;
        drawPage(canvas, pages[page], shift, width, height);
        if (pageOffset > 0f && page + 1 < pages.length) {
            drawPage(canvas, pages[page + 1], shift + width, width, height);
        }
    }

    private static void drawPage(Canvas canvas, Drawable drawable, float left, int width, int height) {
        int size = (int) (Math.min(width, height) * 0.78f);
        int x = (int) (left + (width - size) / 2f);
        int y = (height - size) / 2;
        drawable.setBounds(x, y, x + size, y + size);
        drawable.draw(canvas);
    }
}
