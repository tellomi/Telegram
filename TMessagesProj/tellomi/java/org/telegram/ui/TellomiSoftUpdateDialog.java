package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.TellomiApkInstall;
import org.telegram.messenger.TellomiAppUpdate;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

/**
 * Dismissible soft-update card. The primary action is a swipe, in the
 * shape of the OKX update sheet, drawn in Tellomi black and white.
 * A server {@code url} opens in the browser. A document with no url
 * keeps the APK install path. Night mode uses the same layout with
 * inverted ink.
 */
public class TellomiSoftUpdateDialog extends Dialog implements NotificationCenter.NotificationCenterDelegate {

    private final TLRPC.TL_help_appUpdate update;
    private final int account;
    private final SlideToUpdateView slider;

    private String fileName;
    private boolean watching;
    private boolean downloading;
    private boolean released;

    public TellomiSoftUpdateDialog(Context context, TLRPC.TL_help_appUpdate update, int account) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.update = update;
        this.account = account;

        boolean night = Theme.getActiveTheme() != null && Theme.isCurrentThemeDark();
        int cardColor = night ? Theme.getColor(Theme.key_dialogBackground) : 0xFFF2F2F4;
        int titleColor = night ? Theme.getColor(Theme.key_dialogTextBlack) : 0xFF111111;
        int bodyColor = night ? Theme.getColor(Theme.key_dialogTextGray3) : 0xFF8A8A8E;
        int closeColor = night ? 0xFF8E8E93 : 0xFF9A9A9E;
        int trackColor = night ? 0xFF3A3A3C : 0xFFFFFFFF;
        int ink = night ? 0xFFFFFFFF : 0xFF111111;
        int onInk = night ? 0xFF111111 : 0xFFFFFFFF;

        FrameLayout root = new FrameLayout(context);
        root.setOnClickListener(v -> dismiss());

        FrameLayout card = new FrameLayout(context);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(cardColor);
        cardBg.setCornerRadius(AndroidUtilities.dp(22));
        card.setBackground(cardBg);
        card.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setClipToOutline(true);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(22));
                }
            });
        }

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(20), AndroidUtilities.dp(22), AndroidUtilities.dp(20));

        TextView title = new TextView(context);
        title.setTextColor(titleColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        title.setTypeface(AndroidUtilities.bold());
        title.setLineSpacing(AndroidUtilities.dp(1), 1f);
        title.setText(LocaleController.getString(R.string.TellomiSoftUpdateTitle));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMarginEnd(AndroidUtilities.dp(28));
        column.addView(title, titleLp);

        TextView body = new TextView(context);
        body.setTextColor(bodyColor);
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        body.setLineSpacing(AndroidUtilities.dp(2), 1f);
        body.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink));
        body.setHighlightColor(night ? 0x33FFFFFF : 0x22000000);
        body.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        if (update == null || TextUtils.isEmpty(update.text)) {
            body.setText(LocaleController.getString(R.string.TellomiSoftUpdateBody));
        } else {
            SpannableStringBuilder builder = new SpannableStringBuilder(update.text);
            MessageObject.addEntitiesToText(builder, update.entities, false, false, false, false);
            body.setText(builder);
        }
        ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int max = AndroidUtilities.dp(240);
                int size = MeasureSpec.getSize(heightMeasureSpec);
                int mode = MeasureSpec.getMode(heightMeasureSpec);
                int cap = mode == MeasureSpec.UNSPECIFIED ? max : Math.min(max, size);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollLp.topMargin = AndroidUtilities.dp(14);
        column.addView(scroll, scrollLp);

        slider = new SlideToUpdateView(context);
        slider.setColors(trackColor, ink, onInk, ink);
        slider.setLabels(
                LocaleController.getString(R.string.TellomiSoftUpdateSlide),
                LocaleController.getString(R.string.TellomiSoftUpdateGo)
        );
        slider.setOnComplete(this::onSwiped);
        LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(52));
        sliderLp.topMargin = AndroidUtilities.dp(22);
        column.addView(slider, sliderLp);

        card.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        CloseMark close = new CloseMark(context, closeColor);
        close.setContentDescription(LocaleController.getString(R.string.TellomiSoftUpdateClose));
        close.setOnClickListener(v -> dismiss());
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(AndroidUtilities.dp(40), AndroidUtilities.dp(40), Gravity.TOP | Gravity.END);
        closeLp.topMargin = AndroidUtilities.dp(8);
        closeLp.setMarginEnd(AndroidUtilities.dp(8));
        card.addView(close, closeLp);

        int screen = AndroidUtilities.displaySize.x;
        if (screen <= 0) {
            screen = context.getResources().getDisplayMetrics().widthPixels;
        }
        int width = screen - AndroidUtilities.dp(48);
        int max = AndroidUtilities.dp(400);
        if (width > max) {
            width = max;
        }
        if (width < AndroidUtilities.dp(260)) {
            width = Math.max(AndroidUtilities.dp(240), screen - AndroidUtilities.dp(32));
        }
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(card, cardLp);

        setContentView(root);
        setCancelable(true);
        setCanceledOnTouchOutside(true);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.32f;
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            window.setAttributes(params);
        }
    }

    private void onSwiped() {
        int action = TellomiAppUpdate.primaryAction(update == null ? null : update.url, update != null && update.document instanceof TLRPC.TL_document);
        if (action == TellomiAppUpdate.OPEN_URL) {
            String url = update.url.trim();
            if (TellomiAppUpdate.preferSystemBrowser(url)) {
                Browser.openUrlInSystemBrowser(getContext(), url);
            } else {
                Browser.openUrl(getContext(), url);
            }
            dismiss();
            return;
        }
        if (action == TellomiAppUpdate.INSTALL_APK) {
            Activity activity = AndroidUtilities.getActivity(getContext());
            if (activity == null) {
                slider.reset();
                return;
            }
            if (!TellomiApkInstall.checkPermission(activity)) {
                slider.reset();
                return;
            }
            if (TellomiApkInstall.open(activity, account, update.document)) {
                dismiss();
                return;
            }
            fileName = FileLoader.getAttachFileName(update.document);
            downloading = true;
            watchFile();
            slider.showBusy(LocaleController.formatString(R.string.TellomiSoftUpdateDownloading, 0));
            FileLoader.getInstance(account).loadFile(update.document, "update", FileLoader.PRIORITY_HIGH, 1);
            return;
        }
        slider.reset();
    }

    private void watchFile() {
        if (watching) {
            return;
        }
        watching = true;
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoadFailed);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoadProgressChanged);
    }

    private void unwatch() {
        if (!watching) {
            return;
        }
        watching = false;
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileLoadFailed);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileLoadProgressChanged);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        AndroidUtilities.runOnUIThread(() -> onFileNotification(id, args));
    }

    private void onFileNotification(int id, Object... args) {
        if (!isShowing() || fileName == null || args == null || args.length == 0 || !(args[0] instanceof String) || !fileName.equals(args[0])) {
            return;
        }
        if (id == NotificationCenter.fileLoaded) {
            downloading = false;
            unwatch();
            Activity activity = AndroidUtilities.getActivity(getContext());
            if (activity != null) {
                TellomiApkInstall.open(activity, this.account, update.document);
            }
            dismiss();
        } else if (id == NotificationCenter.fileLoadFailed) {
            downloading = false;
            unwatch();
            slider.reset();
        } else if (id == NotificationCenter.fileLoadProgressChanged && args.length >= 3 && args[1] instanceof Long && args[2] instanceof Long) {
            long loaded = (Long) args[1];
            long total = (Long) args[2];
            int percent = total <= 0 ? 0 : (int) Math.max(0, Math.min(100, loaded * 100L / total));
            slider.showBusy(LocaleController.formatString(R.string.TellomiSoftUpdateDownloading, percent));
        }
    }

    @Override
    public void dismiss() {
        if (!isShowing()) {
            return;
        }
        unwatch();
        if (downloading && update != null && update.document != null) {
            downloading = false;
            FileLoader.getInstance(account).cancelLoadFile(update.document);
        }
        releasePendingSoftUpdate();
        super.dismiss();
    }

    private void releasePendingSoftUpdate() {
        if (released || update == null || update.can_not_skip || update.version == null) {
            return;
        }
        released = true;
        TLRPC.TL_help_appUpdate pending = SharedConfig.pendingAppUpdate;
        if (pending != null && !pending.can_not_skip && update.version.equals(pending.version)) {
            SharedConfig.pendingAppUpdate = null;
            SharedConfig.saveConfig();
        }
    }

    private static final class CloseMark extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CloseMark(Context context, int color) {
            super(context);
            paint.setColor(color);
            paint.setStrokeWidth(AndroidUtilities.dpf2(1.7f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float length = AndroidUtilities.dpf2(14);
            float left = (getWidth() - length) / 2f;
            float top = (getHeight() - length) / 2f;
            canvas.drawLine(left, top, left + length, top + length, paint);
            canvas.drawLine(left + length, top, left, top + length, paint);
        }
    }

    private static final class SlideToUpdateView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chevronPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path chevron = new Path();

        private String slideLabel = "";
        private String activeLabel = "";
        private String overrideLabel;
        private int labelColor = 0xFF111111;
        private int onInkColor = 0xFFFFFFFF;
        private float progress;
        private boolean dragging;
        private boolean locked;
        private boolean completed;
        private boolean settleCompletes;
        private float downX;
        private float downProgress;
        private boolean crossed;
        private ValueAnimator settle;
        private Runnable onComplete;

        SlideToUpdateView(Context context) {
            super(context);
            chevronPaint.setStyle(Paint.Style.STROKE);
            chevronPaint.setStrokeCap(Paint.Cap.ROUND);
            chevronPaint.setStrokeJoin(Paint.Join.ROUND);
            chevronPaint.setStrokeWidth(AndroidUtilities.dpf2(2.2f));
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setTextSize(AndroidUtilities.dp(15));
            textPaint.setTextAlign(Paint.Align.CENTER);
            setClickable(true);
        }

        void setColors(int track, int ink, int onInk, int label) {
            trackPaint.setColor(track);
            fillPaint.setColor(ink);
            thumbPaint.setColor(ink);
            chevronPaint.setColor(onInk);
            labelColor = label;
            onInkColor = onInk;
            textPaint.setColor(label);
        }

        void setLabels(String slide, String active) {
            slideLabel = slide == null ? "" : slide;
            activeLabel = active == null ? "" : active;
            setContentDescription(slideLabel);
            invalidate();
        }

        void setOnComplete(Runnable onComplete) {
            this.onComplete = onComplete;
        }

        void showBusy(String label) {
            locked = true;
            completed = true;
            overrideLabel = label;
            setProgress(1f);
        }

        void reset() {
            if (settle != null) {
                settle.cancel();
                settle = null;
            }
            locked = false;
            completed = false;
            settleCompletes = false;
            overrideLabel = null;
            crossed = false;
            animateTo(0f, false);
        }

        private void setProgress(float value) {
            progress = Math.max(0f, Math.min(1f, value));
            if (!crossed && progress >= 0.85f && dragging) {
                crossed = true;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            if (progress < 0.85f) {
                crossed = false;
            }
            invalidate();
        }

        private float inset() {
            return AndroidUtilities.dpf2(3);
        }

        private float thumbSize() {
            return getHeight() - inset() * 2f;
        }

        private float travel() {
            float travel = getWidth() - thumbSize() - inset() * 2f;
            return travel > 1f ? travel : 1f;
        }

        private boolean rtl() {
            return LocaleController.isRTL;
        }

        private void animateTo(float target, boolean complete) {
            if (settle != null) {
                settle.cancel();
            }
            settleCompletes = complete;
            float start = progress;
            ValueAnimator animator = ValueAnimator.ofFloat(start, target);
            animator.setDuration(180);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> setProgress((Float) a.getAnimatedValue()));
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    settleCompletes = false;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (settle == animator) {
                        settle = null;
                    }
                    if (settleCompletes) {
                        settleCompletes = false;
                        finishSwipe();
                    }
                }
            });
            settle = animator;
            animator.start();
        }

        private void finishSwipe() {
            if (completed) {
                return;
            }
            completed = true;
            locked = true;
            if (onComplete != null) {
                onComplete.run();
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            if (!locked && !completed) {
                setProgress(1f);
                finishSwipe();
                return true;
            }
            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (locked || completed || getWidth() <= 0) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (settle != null) {
                        settle.cancel();
                        settle = null;
                    }
                    dragging = true;
                    downX = event.getX();
                    downProgress = progress;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getX() - downX;
                    float delta = (rtl() ? -dx : dx) / travel();
                    setProgress(downProgress + delta);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && progress >= 0.85f) {
                        animateTo(1f, true);
                    } else {
                        animateTo(0f, false);
                    }
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(52), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float width = getWidth();
            float height = getHeight();
            float radius = height / 2f;
            float inset = inset();
            float thumb = thumbSize();
            boolean rtl = rtl();
            float thumbLeft = rtl ? (width - inset - thumb - progress * (width - thumb - inset * 2f)) : (inset + progress * (width - thumb - inset * 2f));
            float thumbRight = thumbLeft + thumb;

            canvas.drawRoundRect(0, 0, width, height, radius, radius, trackPaint);
            if (progress > 0.001f) {
                if (rtl) {
                    canvas.drawRoundRect(thumbLeft, 0, width, height, radius, radius, fillPaint);
                } else {
                    canvas.drawRoundRect(0, 0, thumbRight, height, radius, radius, fillPaint);
                }
            }
            canvas.drawCircle(thumbLeft + thumb / 2f, height / 2f, thumb / 2f, thumbPaint);

            float cx = thumbLeft + thumb / 2f;
            float cy = height / 2f;
            float arm = AndroidUtilities.dpf2(7f);
            chevron.rewind();
            if (rtl) {
                chevron.moveTo(cx + arm * 0.35f, cy - arm);
                chevron.lineTo(cx - arm * 0.55f, cy);
                chevron.lineTo(cx + arm * 0.35f, cy + arm);
            } else {
                chevron.moveTo(cx - arm * 0.35f, cy - arm);
                chevron.lineTo(cx + arm * 0.55f, cy);
                chevron.lineTo(cx - arm * 0.35f, cy + arm);
            }
            canvas.drawPath(chevron, chevronPaint);

            String label;
            float textX;
            if (overrideLabel != null) {
                label = overrideLabel;
                textX = width / 2f;
                textPaint.setColor(progress > 0.55f ? onInkColor : labelColor);
            } else if (progress > 0.04f) {
                label = activeLabel;
                float gap = AndroidUtilities.dpf2(14);
                float measured = textPaint.measureText(label);
                if (rtl) {
                    textX = thumbLeft - gap - measured / 2f;
                } else {
                    textX = thumbRight + gap + measured / 2f;
                }
                textPaint.setColor(labelColor);
            } else {
                label = slideLabel;
                float areaLeft = rtl ? inset : thumbRight;
                float areaRight = rtl ? thumbLeft : width - inset;
                textX = (areaLeft + areaRight) / 2f;
                textPaint.setColor(labelColor);
            }
            if (label != null && label.length() > 0) {
                float textWidth = textPaint.measureText(label);
                if (textX - textWidth / 2f > AndroidUtilities.dp(4) && textX + textWidth / 2f < width - AndroidUtilities.dp(4)) {
                    Paint.FontMetrics metrics = textPaint.getFontMetrics();
                    float baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f;
                    canvas.drawText(label, textX, baseline, textPaint);
                }
            }
        }
    }
}
