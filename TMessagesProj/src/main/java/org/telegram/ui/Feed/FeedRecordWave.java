package org.telegram.ui.Feed;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlobDrawable;
import org.telegram.ui.Components.WaveDrawable;

import static org.telegram.messenger.AndroidUtilities.dp;

/**
 * The chat record waveform (RecordCircle's two BlobDrawables).
 * The slide-to-cancel lock chrome stays on ChatActivityEnterView and is not pulled out.
 */
@SuppressLint("ViewConstructor")
public class FeedRecordWave extends View {

    private final BlobDrawable tinyWaveDrawable = new BlobDrawable(11, LiteMode.FLAGS_CHAT);
    private final BlobDrawable bigWaveDrawable = new BlobDrawable(12, LiteMode.FLAGS_CHAT);
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long lastUpdateTime;

    public FeedRecordWave(Context context) {
        super(context);
        tinyWaveDrawable.minRadius = dp(47);
        tinyWaveDrawable.maxRadius = dp(55);
        tinyWaveDrawable.generateBlob();
        bigWaveDrawable.minRadius = dp(47);
        bigWaveDrawable.maxRadius = dp(55);
        bigWaveDrawable.generateBlob();
        updateColors();
    }

    public void updateColors() {
        int color = Theme.getColor(Theme.key_chat_messagePanelVoiceBackground);
        circlePaint.setColor(color);
        tinyWaveDrawable.paint.setColor(ColorUtils.setAlphaComponent(color, (int) (255 * WaveDrawable.CIRCLE_ALPHA_2)));
        bigWaveDrawable.paint.setColor(ColorUtils.setAlphaComponent(color, (int) (255 * WaveDrawable.CIRCLE_ALPHA_1)));
    }

    /** Same amplitude mapping as ChatActivityEnterView.RecordCircle.setAmplitude. */
    public void setAmplitude(double value) {
        float capped = (float) (Math.min(WaveDrawable.MAX_AMPLITUDE, value) / WaveDrawable.MAX_AMPLITUDE);
        bigWaveDrawable.setValue(capped, true);
        tinyWaveDrawable.setValue(capped, false);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();
        long dt = lastUpdateTime == 0 ? 16 : now - lastUpdateTime;
        lastUpdateTime = now;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        if (LiteMode.isEnabled(LiteMode.FLAGS_CHAT)) {
            tinyWaveDrawable.minRadius = dp(47);
            tinyWaveDrawable.maxRadius = dp(47) + dp(15) * BlobDrawable.FORM_SMALL_MAX;
            bigWaveDrawable.minRadius = dp(50);
            bigWaveDrawable.maxRadius = dp(50) + dp(12) * BlobDrawable.FORM_BIG_MAX;
            bigWaveDrawable.updateAmplitude(dt);
            bigWaveDrawable.update(bigWaveDrawable.amplitude, 1.01f);
            tinyWaveDrawable.updateAmplitude(dt);
            tinyWaveDrawable.update(tinyWaveDrawable.amplitude, 1.02f);
        }
        float big = BlobDrawable.SCALE_BIG_MIN + 1.4f * bigWaveDrawable.amplitude;
        canvas.save();
        canvas.scale(big, big, cx, cy);
        bigWaveDrawable.draw(cx, cy, canvas, bigWaveDrawable.paint);
        canvas.restore();
        float small = BlobDrawable.SCALE_SMALL_MIN + 1.4f * tinyWaveDrawable.amplitude;
        canvas.save();
        canvas.scale(small, small, cx, cy);
        tinyWaveDrawable.draw(cx, cy, canvas, tinyWaveDrawable.paint);
        canvas.restore();
        canvas.drawCircle(cx, cy, dp(41), circlePaint);
        invalidate();
    }
}
