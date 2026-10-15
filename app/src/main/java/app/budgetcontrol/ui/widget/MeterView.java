package app.budgetcontrol.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import app.budgetcontrol.R;
import app.budgetcontrol.ui.Ui;

/** A rounded track with a fill: how much of a limit has been used. */
public class MeterView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float fraction;
    @Nullable private ValueAnimator animator;

    public MeterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        track.setColor(ContextCompat.getColor(context, R.color.surface_2));
        fill.setColor(Ui.themeColor(context, R.attr.accentInkColor));
    }

    public void setColors(@ColorInt int trackColor, @ColorInt int fillColor) {
        track.setColor(trackColor);
        fill.setColor(fillColor);
        invalidate();
    }

    /** @param fraction 0 to 1; values outside are clamped */
    public void setFraction(float fraction) {
        if (animator != null) animator.cancel();
        this.fraction = Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    /** As {@link #setFraction}, but the fill slides from where it is now. */
    public void animateTo(float target) {
        float to = Math.max(0f, Math.min(1f, target));
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(fraction, to);
        animator.setDuration(650);
        animator.setInterpolator(new DecelerateInterpolator(1.8f));
        animator.addUpdateListener(a -> {
            fraction = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;
        canvas.drawRoundRect(0, 0, w, h, r, r, track);
        if (fraction > 0) {
            // Never narrower than a dot, so a small amount is still visible.
            float fw = Math.max(h, w * fraction);
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            if (rtl) {
                canvas.drawRoundRect(w - fw, 0, w, h, r, r, fill);
            } else {
                canvas.drawRoundRect(0, 0, fw, h, r, r, fill);
            }
        }
    }
}
