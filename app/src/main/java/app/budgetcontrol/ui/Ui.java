package app.budgetcontrol.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.color.MaterialColors;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

import app.budgetcontrol.R;
import app.budgetcontrol.databinding.ItemExpenseBinding;
import app.budgetcontrol.model.Category;
import app.budgetcontrol.model.Expense;
import app.budgetcontrol.ui.add.AddExpenseSheet;
import app.budgetcontrol.ui.loan.LoanSheet;
import app.budgetcontrol.util.Money;

/** Formatting and view binding shared by the screens. */
public final class Ui {

    private Ui() {}

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM");
    private static final DateTimeFormatter DAY_LONG = DateTimeFormatter.ofPattern("EEEE, d MMMM");

    /** "Today", "Yesterday", or a short date. */
    public static String dayLabel(Context context, LocalDate day, LocalDate today) {
        if (day.equals(today)) return context.getString(R.string.today);
        if (day.equals(today.minusDays(1))) return context.getString(R.string.yesterday);
        return day.format(DAY);
    }

    public static String longDate(LocalDate day) {
        return day.format(DAY_LONG);
    }

    /** Clock time in the phone's 12 or 24 hour style. */
    public static String time(Context context, int minuteOfDay) {
        LocalTime t = LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60);
        String pattern = DateFormat.is24HourFormat(context) ? "HH:mm" : "h:mm a";
        return t.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()));
    }

    public static int minuteNow() {
        LocalTime now = LocalTime.now();
        return now.getHour() * 60 + now.getMinute();
    }

    public static DayOfWeek firstDayOfWeek() {
        return WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** One of the colour theme's colours, e.g. R.attr.accentInkColor, as the current theme defines it. */
    @ColorInt
    public static int themeColor(Context context, @AttrRes int attr) {
        return MaterialColors.getColor(context, attr, Color.MAGENTA);
    }

    /** The same, read from a theme that is not the one in use. */
    @ColorInt
    public static int themeColor(Context context, @StyleRes int theme, @AttrRes int attr) {
        TypedArray a = context.obtainStyledAttributes(theme, new int[]{attr});
        try {
            return a.getColor(0, Color.MAGENTA);
        } finally {
            a.recycle();
        }
    }

    /** An icon sized and coloured for use beside text, ready for setCompoundDrawablesRelative. */
    @Nullable
    public static Drawable icon(Context context, @DrawableRes int res, float sizeDp, @ColorInt int color) {
        Drawable source = AppCompatResources.getDrawable(context, res);
        if (source == null) return null;
        Drawable d = source.mutate();
        int size = dp(context, sizeDp);
        d.setBounds(0, 0, size, size);
        d.setTint(color);
        return d;
    }

    /** Shows the category's icon in the category's colour. */
    public static void bindCategoryIcon(ImageView icon, Category category) {
        icon.setImageResource(CategoryIcons.res(category.icon));
        icon.setImageTintList(ColorStateList.valueOf(category.color));
    }

    /** Brings a screen's sections in one after another: a short rise and fade. */
    public static void enter(ViewGroup sections) {
        float rise = dp(sections.getContext(), 14);
        for (int i = 0; i < sections.getChildCount(); i++) {
            View v = sections.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(rise);
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(Math.min(i, 8) * 45L)
                    .setDuration(320)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
        }
    }

    public static void bindExpense(ItemExpenseBinding row, Expense e, String currency) {
        Context context = row.getRoot().getContext();
        bindCategoryIcon(row.icon, e.category);
        boolean repayment = e.isLoan() && e.amount < 0;
        String tag;
        if (e.isLoan()) {
            // The note on a loan row is the person's name.
            row.title.setText(context.getString(
                    repayment ? R.string.loan_repaid_by : R.string.loan_lent_to, e.note));
            tag = context.getString(R.string.loan);
        } else {
            row.title.setText(e.category.name);
            tag = context.getString(e.planned ? R.string.planned : R.string.impulse);
            if (e.bulk) tag += " \u00B7 " + context.getString(R.string.big_shop);
        }
        row.subtitle.setText(time(context, e.minuteOfDay) + " \u00B7 " + tag);
        // Rows are recycled, so the bolt and the colour have to be cleared as well as set.
        Drawable bolt = e.planned ? null
                : icon(context, R.drawable.ic_zap, 13, row.subtitle.getCurrentTextColor());
        row.subtitle.setCompoundDrawablesRelative(null, null, bolt, null);
        row.subtitle.setCompoundDrawablePadding(dp(context, 4));
        // Money coming back reads as a plus, in the colour for good news.
        row.amount.setText(repayment ? "+" + Money.format(-e.amount, currency) : Money.format(e.amount, currency));
        row.amount.setTextColor(ContextCompat.getColor(context,
                repayment ? R.color.status_good : R.color.text_primary));
    }

    /** Opens whatever edits this row: the loan it belongs to, or the expense itself. */
    public static void openExpense(FragmentManager fragments, Expense e) {
        if (e.isLoan()) {
            LoanSheet.open(e.loanId).show(fragments, "loan");
        } else {
            AddExpenseSheet.newEdit(e.id).show(fragments, "edit");
        }
    }
}