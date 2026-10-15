package app.budgetcontrol.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import app.budgetcontrol.R;
import app.budgetcontrol.ui.Ui;

/** One bar split in two: the highlighted share first, the rest after a small gap. */
public class SplitBarView extends View {
    private final Paint first = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rest = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final float gap;
    private float fraction;

    public SplitBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        first.setColor(Ui.themeColor(context, R.attr.accentInkColor));
        rest.setColor(ContextCompat.getColor(context, R.color.chart_muted));
        gap = 2 * getResources().getDisplayMetrics().density;
    }

    /** @param fraction share of the first segment, 0 to 1 */
    public void setFraction(float fraction) {
        this.fraction = Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        clip.reset();
        clip.addRoundRect(0, 0, w, h, h / 2f, h / 2f, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        canvas.save();
        canvas.clipPath(clip);
        if (fraction <= 0f) {
            canvas.drawRect(0, 0, w, h, rest);
        } else if (fraction >= 1f) {
            canvas.drawRect(0, 0, w, h, first);
        } else {
            // Keep both segments wide enough to see, whatever the split.
            float split = Math.max(h, Math.min(w - h, w * fraction));
            canvas.drawRect(0, 0, split - gap / 2f, h, first);
            canvas.drawRect(split + gap / 2f, 0, w, h, rest);
        }
        canvas.restore();
    }
}
