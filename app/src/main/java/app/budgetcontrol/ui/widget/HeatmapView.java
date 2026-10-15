package app.budgetcontrol.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import app.budgetcontrol.R;
import app.budgetcontrol.ui.Ui;

/**
 * A grid of rounded cells, one hue, stronger where the value is higher.
 * Rows and columns are whatever the caller labels them as.
 */
public class HeatmapView extends View {

    public interface OnSelect {
        void onSelect(int row, int column);
    }

    private static final float MAX_CELL_DP = 44f;

    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rowText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint columnText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint columnTextSelected = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private final float gap;
    @ColorInt private final int emptyColor;
    @ColorInt private final int rampColor;
    @ColorInt private final int surfaceColor;

    private String[] rowLabels = new String[0];
    private String[] columnLabels = new String[0];
    private long[][] values = new long[0][0];
    private long max;
    private int selectedRow = -1;
    private int selectedColumn = -1;
    @Nullable private OnSelect onSelect;

    // Worked out in onMeasure.
    private float labelWidth;
    private float cellSize;

    public HeatmapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        float labelSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics());
        gap = 4 * dp;
        emptyColor = ContextCompat.getColor(context, R.color.surface_2);
        rampColor = Ui.themeColor(context, R.attr.accentInkColor);
        surfaceColor = ContextCompat.getColor(context, R.color.surface);

        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2 * dp);
        ring.setColor(ContextCompat.getColor(context, R.color.text_primary));
        rowText.setColor(ContextCompat.getColor(context, R.color.text_muted));
        rowText.setTextSize(labelSize);
        columnText.set(rowText);
        columnText.setTextAlign(Paint.Align.CENTER);
        columnTextSelected.set(columnText);
        columnTextSelected.setColor(ContextCompat.getColor(context, R.color.text_primary));
        columnTextSelected.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    /** @param values indexed [row][column] */
    public void setData(String[] rowLabels, String[] columnLabels, long[][] values) {
        this.rowLabels = rowLabels;
        this.columnLabels = columnLabels;
        this.values = values;
        max = 0;
        for (long[] row : values) {
            for (long v : row) max = Math.max(max, v);
        }
        selectedRow = -1;
        selectedColumn = -1;
        requestLayout();
        invalidate();
    }

    public void setOnSelect(@Nullable OnSelect onSelect) {
        this.onSelect = onSelect;
    }

    public void select(int row, int column) {
        selectedRow = row;
        selectedColumn = column;
        invalidate();
    }

    /**
     * The fill for a share of the maximum. The lightest step still stands clear of an empty cell,
     * so "a little" and "nothing" never look the same.
     */
    @ColorInt
    public static int rampColor(@ColorInt int hue, @ColorInt int surface, float share) {
        float alpha = 0.22f + 0.78f * Math.max(0f, Math.min(1f, share));
        return ColorUtils.blendARGB(surface, hue, alpha);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int columns = Math.max(1, columnLabels.length);
        int rows = Math.max(1, rowLabels.length);
        labelWidth = 0;
        for (String s : rowLabels) labelWidth = Math.max(labelWidth, rowText.measureText(s));
        labelWidth += 10 * dp;
        cellSize = Math.min(MAX_CELL_DP * dp, (width - labelWidth - gap * (columns - 1)) / columns);
        float height = rows * cellSize + (rows - 1) * gap + columnText.getTextSize() + 10 * dp;
        setMeasuredDimension(width, (int) Math.ceil(height));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (rowLabels.length == 0 || columnLabels.length == 0) return;
        float radius = Math.min(8 * dp, cellSize / 4f);
        float rowCenterOffset = cellSize / 2f - (rowText.descent() + rowText.ascent()) / 2f;

        for (int r = 0; r < rowLabels.length; r++) {
            float y = r * (cellSize + gap);
            canvas.drawText(rowLabels[r], 0, y + rowCenterOffset, rowText);
            for (int c = 0; c < columnLabels.length; c++) {
                float x = labelWidth + c * (cellSize + gap);
                long v = values[r][c];
                cell.setColor(v <= 0 ? emptyColor : rampColor(rampColor, surfaceColor, v / (float) max));
                canvas.drawRoundRect(x, y, x + cellSize, y + cellSize, radius, radius, cell);
                if (r == selectedRow && c == selectedColumn) {
                    float inset = dp;
                    canvas.drawRoundRect(x + inset, y + inset, x + cellSize - inset, y + cellSize - inset,
                            radius, radius, ring);
                }
            }
        }

        float labelY = getHeight() - 2 * dp;
        for (int c = 0; c < columnLabels.length; c++) {
            float cx = labelWidth + c * (cellSize + gap) + cellSize / 2f;
            canvas.drawText(columnLabels[c], cx, labelY, c == selectedColumn ? columnTextSelected : columnText);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (onSelect == null || rowLabels.length == 0) return super.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            int c = (int) ((event.getX() - labelWidth + gap / 2f) / (cellSize + gap));
            int r = (int) ((event.getY() + gap / 2f) / (cellSize + gap));
            if (event.getX() >= labelWidth && c >= 0 && c < columnLabels.length && r >= 0 && r < rowLabels.length
                    && (r != selectedRow || c != selectedColumn)) {
                selectedRow = r;
                selectedColumn = c;
                invalidate();
                onSelect.onSelect(r, c);
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
