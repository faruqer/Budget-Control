package app.budgetcontrol.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Collections;
import java.util.List;

import app.budgetcontrol.R;
import app.budgetcontrol.ui.Ui;

/**
 * Columns on one baseline with an optional reference line. One bar can be selected by touch;
 * only that bar carries its value, so the chart never fills up with numbers.
 */
public class BarChartView extends View {

    public static final class Bar {
        /** Shown under the bar; empty to skip it when there are too many bars to label. */
        public final String label;
        public final long value;
        /** Printed above the bar while it is selected. */
        public final String valueLabel;
        public final boolean over;
        public final boolean emphasized;

        public Bar(String label, long value, String valueLabel, boolean over, boolean emphasized) {
            this.label = label;
            this.value = value;
            this.valueLabel = valueLabel;
            this.over = over;
            this.emphasized = emphasized;
        }
    }

    public interface OnSelect {
        void onSelect(int index);
    }

    private static final float MAX_BAR_DP = 24f;

    private final Paint muted = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emphasis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint over = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint limitLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisTextSelected = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valueText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path barPath = new Path();
    private final float[] corners = new float[8];
    private final float dp;

    private List<Bar> bars = Collections.emptyList();
    private long limit;
    private int selected = -1;
    /** 0 to 1 while the bars are growing in, 1 once they have settled. */
    private float growth = 1f;
    @Nullable private ValueAnimator grow;
    @Nullable private OnSelect onSelect;

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        float labelSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics());

        muted.setColor(ContextCompat.getColor(context, R.color.chart_muted));
        emphasis.setColor(Ui.themeColor(context, R.attr.accentInkColor));
        over.setColor(ContextCompat.getColor(context, R.color.chart_over));
        grid.setColor(ContextCompat.getColor(context, R.color.chart_grid));
        grid.setStrokeWidth(dp);
        limitLine.setColor(ContextCompat.getColor(context, R.color.text_muted));
        limitLine.setStrokeWidth(dp);

        axisText.setColor(ContextCompat.getColor(context, R.color.text_muted));
        axisText.setTextSize(labelSize);
        axisText.setTextAlign(Paint.Align.CENTER);
        axisTextSelected.set(axisText);
        axisTextSelected.setColor(ContextCompat.getColor(context, R.color.text_primary));
        axisTextSelected.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        valueText.setColor(ContextCompat.getColor(context, R.color.text_primary));
        valueText.setTextSize(labelSize);
        valueText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        valueText.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * @param limit    where to draw the reference line, or 0 for none
     * @param selected index of the bar to select, or -1
     */
    public void setData(List<Bar> bars, long limit, int selected) {
        this.bars = bars;
        this.limit = limit;
        this.selected = selected >= 0 && selected < bars.size() ? selected : -1;
        if (grow != null) grow.cancel();
        grow = ValueAnimator.ofFloat(0f, 1f);
        grow.setDuration(550);
        grow.setInterpolator(new DecelerateInterpolator(1.8f));
        grow.addUpdateListener(a -> {
            growth = (float) a.getAnimatedValue();
            invalidate();
        });
        grow.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (grow != null) grow.cancel();
        growth = 1f;
        super.onDetachedFromWindow();
    }

    public void setOnSelect(@Nullable OnSelect onSelect) {
        this.onSelect = onSelect;
    }

    public int getSelected() {
        return selected;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = bars.size();
        if (n == 0) return;

        float w = getWidth();
        float top = valueText.getTextSize() + 8 * dp;
        float baseline = getHeight() - (axisText.getTextSize() + 10 * dp);
        float plotH = baseline - top;

        long max = limit;
        for (Bar b : bars) max = Math.max(max, b.value);
        if (max <= 0) max = 1;

        float slot = w / n;
        // Leave at least a 2dp gap between neighbours, and never fill the slot.
        float barW = Math.min(MAX_BAR_DP * dp, Math.max(2 * dp, Math.min(slot * 0.6f, slot - 2 * dp)));
        float radius = Math.min(4 * dp, barW / 2f);

        canvas.drawLine(0, baseline, w, baseline, grid);
        if (limit > 0) {
            float y = baseline - plotH * limit / max;
            canvas.drawLine(0, y, w, y, limitLine);
        }

        for (int i = 0; i < n; i++) {
            Bar b = bars.get(i);
            float cx = slot * i + slot / 2f;
            boolean isSelected = i == selected;

            if (b.value > 0) {
                float h = Math.max(2 * dp, plotH * b.value / max * growth);
                Paint p = b.over ? over : (b.emphasized || isSelected) ? emphasis : muted;
                float r = Math.min(radius, h);
                // Rounded at the data end, square on the baseline.
                corners[0] = corners[1] = corners[2] = corners[3] = r;
                barPath.reset();
                barPath.addRoundRect(cx - barW / 2f, baseline - h, cx + barW / 2f, baseline,
                        corners, Path.Direction.CW);
                canvas.drawPath(barPath, p);

                if (isSelected && !b.valueLabel.isEmpty()) {
                    float half = valueText.measureText(b.valueLabel) / 2f;
                    float tx = Math.max(half, Math.min(w - half, cx));
                    canvas.drawText(b.valueLabel, tx, baseline - h - 6 * dp, valueText);
                }
            }

            if (!b.label.isEmpty()) {
                canvas.drawText(b.label, cx, getHeight() - 2 * dp,
                        b.emphasized || isSelected ? axisTextSelected : axisText);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bars.isEmpty() || onSelect == null) return super.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            // The whole slot is the target, not just the bar.
            int index = (int) (event.getX() / (getWidth() / (float) bars.size()));
            index = Math.max(0, Math.min(bars.size() - 1, index));
            if (index != selected) {
                selected = index;
                invalidate();
                onSelect.onSelect(index);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
