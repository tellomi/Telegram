package org.telegram.ui.Components;

import android.content.Context;
import android.widget.ImageView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

/**
 * Emoji / sticker / keyboard and mic / camera button.
 * The old morph was a Lottie file. Tellomi draws the MingCute glyph and
 * crossfades with a short scale.
 */
public class ChatActivityEnterViewAnimatedIconView extends RLottieImageView {
    private State currentState;

    public ChatActivityEnterViewAnimatedIconView(Context context) {
        this(context, 32);
    }

    public ChatActivityEnterViewAnimatedIconView(Context context, int sizeDp) {
        super(context);
        setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    }

    public void setState(State state, boolean animate) {
        if (animate && state == currentState) {
            return;
        }
        currentState = state;
        final int res = iconFor(state);
        animate().cancel();
        if (!animate || getDrawable() == null) {
            setScaleX(1f);
            setScaleY(1f);
            setAlpha(1f);
            setImageResource(res);
        } else {
            animate().scaleX(0.82f).scaleY(0.82f).alpha(0.2f).setDuration(90).withEndAction(() -> {
                setImageResource(res);
                setScaleX(0.86f);
                setScaleY(0.86f);
                setAlpha(0.2f);
                animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start();
            }).start();
        }

        switch (state) {
            case VOICE:
                setContentDescription(LocaleController.getString(R.string.AccDescrVoiceMessage));
                break;
            case VIDEO:
                setContentDescription(LocaleController.getString(R.string.AccDescrVideoMessage));
                break;
        }
    }

    public State getCurrentState() {
        return currentState;
    }

    private static int iconFor(State state) {
        switch (state) {
            case VOICE:
                return R.drawable.input_mic;
            case VIDEO:
                return R.drawable.input_video;
            case STICKER:
                return R.drawable.msg_sticker;
            case KEYBOARD:
                return R.drawable.input_keyboard;
            case GIF:
                return R.drawable.msg_gif;
            case SMILE:
            default:
                return R.drawable.input_smile;
        }
    }

    public enum State {
        VOICE,
        VIDEO,
        STICKER,
        KEYBOARD,
        SMILE,
        GIF
    }
}
