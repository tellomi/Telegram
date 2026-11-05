package org.telegram.ui.tellomi.feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.TellomiDiscoverFlags;
import org.telegram.ui.ActionBar.Theme;

import java.util.List;

/** A row of image slots. Each slot is a picture, not a count. */
final class TellomiDiscoverImageRow {

    private TellomiDiscoverImageRow() {
    }

    static void add(LinearLayout column, final Context context, final int account, final List<TellomiDiscover.Image> images, final View.OnClickListener open) {
        if (column == null || images == null || images.isEmpty()) {
            return;
        }
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final ImageView[] slots = new ImageView[images.size()];
        for (int i = 0; i < images.size(); i++) {
            ImageView slot = new ImageView(context);
            slot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            slot.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            slot.setContentDescription(Integer.toString(i + 1));
            if (open != null) {
                slot.setOnClickListener(open);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(112), dp(112));
            lp.rightMargin = dp(6);
            lp.topMargin = dp(6);
            lp.bottomMargin = dp(6);
            row.addView(slot, lp);
            slots[i] = slot;
        }
        scroll.addView(row);
        column.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TellomiDiscover.Configuration configuration = TellomiDiscoverFlags.configuration(account);
        String base = configuration == null ? null : configuration.baseUrl;
        TellomiDiscoverClient.loadImages(account, base, images, new TellomiDiscoverClient.ImageCallback() {
            @Override
            public void onImage(int index, byte[] bytes) {
                if (index < 0 || index >= slots.length) {
                    return;
                }
                ImageView slot = slots[index];
                if (slot == null || bytes == null || bytes.length == 0) {
                    return;
                }
                Bitmap bitmap = decode(bytes);
                if (bitmap != null) {
                    slot.setImageBitmap(bitmap);
                }
            }
        });
    }

    static Bitmap decode(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int sample = 1;
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        while (max / sample > 1280 && sample < 64) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }
}
