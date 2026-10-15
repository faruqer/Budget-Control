package app.budgetcontrol.ui.insights;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.budgetcontrol.App;
import app.budgetcontrol.R;
import app.budgetcontrol.data.Repo;
import app.budgetcontrol.databinding.FragmentInsightsBinding;
import app.budgetcontrol.databinding.ItemCategoryBarBinding;
import app.budgetcontrol.databinding.ItemRecordRowBinding;
import app.budgetcontrol.logic.BudgetEngine;
import app.budgetcontrol.logic.Insights;
import app.budgetcontrol.logic.Periods;
import app.budgetcontrol.model.Category;
import app.budgetcontrol.model.Expense;
import app.budgetcontrol.ui.Ui;
import app.budgetcontrol.ui.widget.BarChartView;
import app.budgetcontrol.ui.widget.HeatmapView;
import app.budgetcontrol.util.Money;

public class InsightsFragment extends Fragment {
    private static final String ARG_BACK = "periods_back";
    private static final String STATE_MODE = "mode";
    private static final String STATE_ANCHOR = "anchor";
    /** How far back the "patterns" cards look. */
    private static final int PATTERN_DAYS = 56;

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM");
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter BAR_DAY = DateTimeFormatter.ofPattern("EEE d MMM");

    private static final int MODE_PERIOD = 0;
    private static final int MODE_WEEK = 1;
    private static final int MODE_MONTH = 2;

    private FragmentInsightsBinding b;
    private Repo repo;
    /** Which stretch the report covers: the user's own budget period, a calendar week or a calendar month. */
    private int mode = MODE_PERIOD;
    /** Any day inside the stretch being shown. */
    private LocalDate anchor;
    private final Runnable onDataChanged = this::render;

    /** Opens on the user's budget period, {@code periodsBack} periods before the current one. */
    public static InsightsFragment report(int periodsBack) {
        InsightsFragment f = new InsightsFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_BACK, periodsBack);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        b = FragmentInsightsBinding.inflate(inflater, container, false);
        repo = App.repo(requireContext());
        anchor = LocalDate.now();
        if (state != null) {
            mode = state.getInt(STATE_MODE, MODE_PERIOD);
            anchor = LocalDate.parse(state.getString(STATE_ANCHOR, anchor.toString()));
        } else if (getArguments() != null) {
            for (int i = getArguments().getInt(ARG_BACK, 0); i > 0; i--) {
                anchor = range(anchor).start.minusDays(1);
            }
        }

        b.segPeriod.setOnClickListener(v -> setMode(MODE_PERIOD));
        b.segWeek.setOnClickListener(v -> setMode(MODE_WEEK));
        b.segMonth.setOnClickListener(v -> setMode(MODE_MONTH));
        b.prev.setOnClickListener(v -> step(-1));
        b.next.setOnClickListener(v -> step(1));
        return b.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_MODE, mode);
        if (anchor != null) out.putString(STATE_ANCHOR, anchor.toString());
    }

    @Override
    public void onStart() {
        super.onStart();
        repo.addListener(onDataChanged);
        render();
    }

    @Override
    public void onStop() {
        repo.removeListener(onDataChanged);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private void setMode(int newMode) {
        if (mode == newMode) return;
        mode = newMode;
        anchor = LocalDate.now();
        render();
    }

    private void step(int direction) {
        Periods.Period current = range(anchor);
        anchor = direction < 0 ? current.start.minusDays(1) : current.end.plusDays(1);
        render();
    }

    /** The stretch of days holding {@code inside}, in the current mode. */
    private Periods.Period range(LocalDate inside) {
        if (mode == MODE_PERIOD) return repo.period(inside);
        if (mode == MODE_MONTH) return Periods.containing(Periods.Length.MONTH, inside, inside);
        LocalDate start = inside.with(TemporalAdjusters.previousOrSame(Ui.firstDayOfWeek()));
        return Periods.containing(Periods.Length.WEEK, start, inside);
    }

    private void render() {
        if (b == null) return;
        LocalDate today = LocalDate.now();
        String currency = repo.prefs().currency();
        List<BudgetEngine.DayResult> ledger = repo.ledger(today);
        Map<LocalDate, BudgetEngine.DayResult> byDate = new HashMap<>();
        for (BudgetEngine.DayResult d : ledger) byDate.put(d.date, d);

        Periods.Period period = range(anchor);
        List<Expense> expenses = repo.expenses(period.start, period.end);

        renderFilter(period, today);
        renderTotals(period, today, expenses, byDate, currency);
        renderReport(period, today, expenses, byDate, currency);
        renderCategories(expenses, currency);
        renderImpulse(expenses, currency);

        LocalDate patternStart = today.minusDays(PATTERN_DAYS - 1);
        List<BudgetEngine.DayResult> patternDays = new ArrayList<>();
        for (BudgetEngine.DayResult d : ledger) {
            if (!d.date.isBefore(patternStart)) patternDays.add(d);
        }
        renderWeekdays(patternDays, currency);
        renderTimes(repo.expenses(patternStart, today), currency);
        renderRecord(BudgetEngine.summarize(ledger, today), currency);
    }

    private void renderFilter(Periods.Period period, LocalDate today) {
        b.segPeriod.setSelected(mode == MODE_PERIOD);
        b.segWeek.setSelected(mode == MODE_WEEK);
        b.segMonth.setSelected(mode == MODE_MONTH);

        Periods.Period current = range(today);
        String label;
        if (period.start.equals(current.start)) {
            label = getString(mode == MODE_PERIOD ? R.string.this_period
                    : mode == MODE_MONTH ? R.string.this_month : R.string.this_week);
        } else if (mode == MODE_MONTH) {
            label = period.start.format(MONTH_YEAR);
        } else {
            label = period.start.format(DAY_MONTH) + " \u2013 " + period.end.format(DAY_MONTH);
        }
        b.period.setText(label);

        // There is nothing to see in the future.
        boolean canGoForward = period.start.isBefore(current.start);
        b.next.setEnabled(canGoForward);
        b.next.setAlpha(canGoForward ? 1f : 0.3f);
    }

    /** What the stretch had to spend: the period budget for the user's period, the daily budgets otherwise. */
    private long budgetFor(Periods.Period period, Map<LocalDate, BudgetEngine.DayResult> byDate) {
        if (mode == MODE_PERIOD) return repo.periodBudget(period);
        long budgeted = 0;
        for (LocalDate d = period.start; !d.isAfter(period.end); d = d.plusDays(1)) {
            BudgetEngine.DayResult r = byDate.get(d);
            if (r != null) budgeted += r.budget;
        }
        return budgeted;
    }

    private void renderTotals(Periods.Period period, LocalDate today, List<Expense> expenses,
                              Map<LocalDate, BudgetEngine.DayResult> byDate, String currency) {
        long total = Insights.total(expenses);
        b.total.setText(Money.format(total, currency));

        long budgeted = budgetFor(period, byDate);
        b.totalBudget.setVisibility(budgeted > 0 ? View.VISIBLE : View.GONE);
        b.totalBudget.setText(getString(R.string.total_of_budget, Money.format(budgeted, currency)));

        renderDelta(period, total);

        // The bars are the day-to-day spending the daily limit applies to, so big shops stay out.
        List<Expense> daily = new ArrayList<>();
        for (Expense e : expenses) {
            if (!e.bulk) daily.add(e);
        }
        Map<LocalDate, Long> spentByDay = Insights.byDay(daily);
        boolean manyBars = period.days() > 7;
        List<BarChartView.Bar> bars = new ArrayList<>();
        List<LocalDate> barDates = new ArrayList<>();
        int todayIndex = -1;
        int maxIndex = -1;
        long max = 0;
        boolean anyOver = false;
        for (LocalDate d = period.start; !d.isAfter(period.end); d = d.plusDays(1)) {
            Long spentOrNull = spentByDay.get(d);
            // A day that got more back than it spent has nothing to draw.
            long spent = spentOrNull == null ? 0 : Math.max(0, spentOrNull);
            BudgetEngine.DayResult r = byDate.get(d);
            boolean over = r != null && !r.underBudget();
            anyOver |= over;
            boolean isToday = d.equals(today);
            if (isToday) todayIndex = bars.size();
            if (spent > max) {
                max = spent;
                maxIndex = bars.size();
            }
            bars.add(new BarChartView.Bar(axisLabel(d, period, manyBars), spent,
                    Money.format(spent, currency), over, isToday));
            barDates.add(d);
        }

        // Start on today when it is in view, otherwise on the biggest day.
        int selected = todayIndex >= 0 && bars.get(todayIndex).value > 0 ? todayIndex : maxIndex;
        long dailyBudget = repo.prefs().dailyBudget();
        b.chart.setData(bars, dailyBudget, selected);
        b.chart.setOnSelect(i -> showBarCaption(bars.get(i), barDates.get(i), currency));
        if (selected >= 0) {
            showBarCaption(bars.get(selected), barDates.get(selected), currency);
        } else {
            b.chartCaption.setText(R.string.empty_period);
        }

        b.legendBudget.setText(getString(R.string.legend_budget, Money.format(dailyBudget, currency)));
        b.legendOverSwatch.setVisibility(anyOver ? View.VISIBLE : View.GONE);
        b.legendOver.setVisibility(anyOver ? View.VISIBLE : View.GONE);
        b.chart.setContentDescription(getString(R.string.total_spent) + " " + b.total.getText());
    }

    private String axisLabel(LocalDate d, Periods.Period period, boolean manyBars) {
        if (!manyBars) return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
        // Too many bars to label each one: the first, then every few days.
        long index = ChronoUnit.DAYS.between(period.start, d);
        int every = period.days() > 16 ? 5 : 3;
        return index % every == 0 ? String.valueOf(d.getDayOfMonth()) : "";
    }

    private void showBarCaption(BarChartView.Bar bar, LocalDate date, String currency) {
        b.chartCaption.setText(getString(
                bar.over ? R.string.chart_day_value_over : R.string.chart_day_value,
                date.format(BAR_DAY), Money.format(bar.value, currency)));
    }

    /** The figures of the report, as plain rows. Rows that would only say "nothing" are left out. */
    private void renderReport(Periods.Period period, LocalDate today, List<Expense> expenses,
                              Map<LocalDate, BudgetEngine.DayResult> byDate, String currency) {
        long total = Insights.total(expenses);
        long bigShops = 0;
        long lent = 0;
        long repaid = 0;
        Expense biggest = null;
        for (Expense e : expenses) {
            if (e.isLoan()) {
                if (e.amount > 0) lent += e.amount; else repaid -= e.amount;
                continue;
            }
            if (e.bulk) bigShops += e.amount;
            if (biggest == null || e.amount > biggest.amount) biggest = e;
        }
        int tracked = 0;
        int under = 0;
        for (LocalDate d = period.start; !d.isAfter(period.end) && !d.isAfter(today); d = d.plusDays(1)) {
            BudgetEngine.DayResult r = byDate.get(d);
            if (r == null) continue;
            tracked++;
            if (r.underBudget()) under++;
        }
        long budget = budgetFor(period, byDate);

        b.reportRows.removeAllViews();
        if (budget > 0) {
            addRow(b.reportRows, R.string.report_budget, Money.format(budget, currency), 0);
            long left = budget - total;
            addRow(b.reportRows, left >= 0 ? R.string.report_left : R.string.report_over,
                    Money.format(Math.abs(left), currency), left >= 0 ? R.color.status_good : R.color.status_over);
        }
        addRow(b.reportRows, R.string.report_daily, Money.format(total - bigShops - lent + repaid, currency), 0);
        if (bigShops > 0) addRow(b.reportRows, R.string.report_big_shops, Money.format(bigShops, currency), 0);
        if (lent > 0) addRow(b.reportRows, R.string.report_lent, Money.format(lent, currency), 0);
        if (repaid > 0) addRow(b.reportRows, R.string.report_repaid, Money.format(repaid, currency), 0);
        if (tracked > 0) {
            addRow(b.reportRows, R.string.report_average, Money.format(Math.round(total / (double) tracked / 100) * 100, currency), 0);
            addRow(b.reportRows, R.string.record_under, getString(R.string.record_under_value, under, tracked), 0);
        }
        if (biggest != null) {
            addRow(b.reportRows, R.string.report_biggest,
                    Money.format(biggest.amount, currency) + " \u00B7 " + biggest.category.name, 0);
        }
        if (period.contains(today) && period.daysLeftAfter(today) > 0) {
            int left = period.daysLeftAfter(today);
            addRow(b.reportRows, R.string.report_days_left,
                    getResources().getQuantityString(R.plurals.days, left, left), 0);
        }
    }

    /** How this stretch compares with the one before it. Spending less is the good direction. */
    private void renderDelta(Periods.Period period, long total) {
        Periods.Period before = range(period.start.minusDays(1));
        long previous = Insights.total(repo.expenses(before.start, before.end));
        if (previous <= 0) {
            b.delta.setVisibility(View.GONE);
            return;
        }
        b.delta.setVisibility(View.VISIBLE);
        int change = (int) Math.round((total - previous) * 100.0 / previous);
        int text;
        int color;
        int arrow;
        if (change > 0) {
            text = R.string.delta_more;
            color = R.color.status_over;
            arrow = R.drawable.ic_trending_up;
        } else if (change < 0) {
            text = R.string.delta_less;
            color = R.color.status_good;
            arrow = R.drawable.ic_trending_down;
        } else {
            text = R.string.delta_same;
            color = R.color.text_muted;
            arrow = 0;
        }
        int ink = ContextCompat.getColor(requireContext(), color);
        b.delta.setText(getString(text, Math.abs(change)));
        b.delta.setTextColor(ink);
        b.delta.setCompoundDrawablesRelative(
                arrow == 0 ? null : Ui.icon(requireContext(), arrow, 16, ink), null, null, null);
    }
    private void renderCategories(List<Expense> expenses, String currency) {
        Map<Category, Long> sums = Insights.byCategory(expenses, false);
        long total = Insights.total(expenses);
        b.categoryRows.removeAllViews();
        b.categoryEmpty.setVisibility(sums.isEmpty() ? View.VISIBLE : View.GONE);

        long largest = 0;
        for (long v : sums.values()) largest = Math.max(largest, v);
        int track = ContextCompat.getColor(requireContext(), R.color.surface_2);
        int fill = Ui.themeColor(requireContext(), R.attr.accentInkColor);
        for (Map.Entry<Category, Long> e : sums.entrySet()) {
            ItemCategoryBarBinding row = ItemCategoryBarBinding.inflate(getLayoutInflater(), b.categoryRows, false);
            Ui.bindCategoryIcon(row.icon, e.getKey());
            row.name.setText(e.getKey().name);
            row.amount.setText(Money.format(e.getValue(), currency));
            row.share.setText(getString(R.string.category_share, Insights.percent(e.getValue(), total)));
            // One series, so one colour: the bar length is the comparison, the emoji is the identity.
            row.bar.setColors(track, fill);
            row.bar.animateTo(e.getValue() / (float) largest);
            b.categoryRows.addView(row.getRoot());
        }
    }

    private void renderImpulse(List<Expense> expenses, String currency) {
        long total = Insights.total(expenses);
        b.impulseCard.setVisibility(total > 0 ? View.VISIBLE : View.GONE);
        if (total <= 0) return;

        long impulse = Insights.impulseTotal(expenses);
        b.impulseHeadline.setText(getString(R.string.impulse_headline, Insights.percent(impulse, total)));
        b.impulseBar.setFraction(impulse / (float) total);
        b.impulseValue.setText(getString(R.string.impulse_impulse_value, Money.format(impulse, currency)));
        b.plannedValue.setText(getString(R.string.impulse_planned_value, Money.format(total - impulse, currency)));

        Map<Category, Long> impulseByCategory = Insights.byCategory(expenses, true);
        if (impulseByCategory.isEmpty()) {
            b.impulseNote.setText(R.string.impulse_none);
        } else {
            Map.Entry<Category, Long> top = impulseByCategory.entrySet().iterator().next();
            b.impulseNote.setText(getString(R.string.impulse_top,
                    top.getKey().name, Money.format(top.getValue(), currency)));
        }
    }

    private void renderWeekdays(List<BudgetEngine.DayResult> days, String currency) {
        List<Insights.WeekdayStat> stats = Insights.weekdayStats(days, Ui.firstDayOfWeek());
        Insights.WeekdayStat worst = null;
        for (Insights.WeekdayStat s : stats) {
            if (s.average() > 0 && (worst == null || s.average() > worst.average())) worst = s;
        }

        List<BarChartView.Bar> bars = new ArrayList<>();
        int worstIndex = -1;
        for (Insights.WeekdayStat s : stats) {
            if (s == worst) worstIndex = bars.size();
            bars.add(new BarChartView.Bar(
                    s.day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    s.average(), Money.format(roundToUnit(s.average()), currency), false, s == worst));
        }
        b.weekdayChart.setData(bars, 0, worstIndex);
        b.weekdayChart.setOnSelect(i -> { });

        if (worst == null) {
            b.weekdayInsight.setText(R.string.not_enough_data);
            return;
        }
        String name = getString(R.string.weekday_plural,
                worst.day.getDisplayName(TextStyle.FULL, Locale.getDefault()));
        String average = Money.format(roundToUnit(worst.average()), currency);
        b.weekdayInsight.setText(worst.overDays > 0
                ? getString(R.string.weekday_insight_over, name, average, worst.overDays, worst.days)
                : getString(R.string.weekday_insight, name, average));
    }

    private void renderTimes(List<Expense> expenses, String currency) {
        Insights.TimeGrid grid = Insights.timeGrid(expenses);
        Insights.TimeBucket[] buckets = Insights.TimeBucket.values();
        DayOfWeek first = Ui.firstDayOfWeek();

        String[] rows = new String[buckets.length];
        for (int r = 0; r < buckets.length; r++) rows[r] = getString(bucketName(buckets[r]));
        String[] columns = new String[7];
        DayOfWeek[] columnDays = new DayOfWeek[7];
        for (int c = 0; c < 7; c++) {
            columnDays[c] = first.plus(c);
            columns[c] = columnDays[c].getDisplayName(TextStyle.NARROW, Locale.getDefault());
        }
        long[][] values = new long[buckets.length][7];
        for (int r = 0; r < buckets.length; r++) {
            for (int c = 0; c < 7; c++) values[r][c] = grid.amount[columnDays[c].ordinal()][r];
        }

        b.heatmap.setData(rows, columns, values);
        b.heatmap.setOnSelect((r, c) -> {
            int count = grid.count[columnDays[c].ordinal()][r];
            String amount = getResources().getQuantityString(R.plurals.times_cell_count, count,
                    Money.format(values[r][c], currency), count);
            b.timesCaption.setText(getString(R.string.times_cell,
                    columnDays[c].getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    getString(bucketName(buckets[r])).toLowerCase(Locale.getDefault()),
                    getString(bucketRange(buckets[r])), amount));
        });
        b.timesCaption.setText(R.string.times_hint);
        buildScale();

        boolean hasData = grid.max > 0;
        b.heatmapLegend.setVisibility(hasData ? View.VISIBLE : View.GONE);
        b.timesCaption.setVisibility(hasData ? View.VISIBLE : View.GONE);
        if (!hasData) {
            b.timesInsight.setText(R.string.not_enough_data);
            return;
        }
        b.timesInsight.setText(getString(R.string.times_insight,
                grid.peakDay.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                getString(bucketPlural(grid.peakBucket)),
                Money.format(grid.max, currency), Insights.percent(grid.max, grid.total)));
        for (int c = 0; c < 7; c++) {
            if (columnDays[c] == grid.peakDay) b.heatmap.select(grid.peakBucket.ordinal(), c);
        }
    }

    /** The "less to more" key under the heatmap, drawn with the same ramp as the cells. */
    private void buildScale() {
        if (b.heatmapScale.getChildCount() > 0) return;
        int hue = Ui.themeColor(requireContext(), R.attr.accentInkColor);
        int surface = ContextCompat.getColor(requireContext(), R.color.surface);
        float dp = getResources().getDisplayMetrics().density;
        float[] steps = {0f, 0.33f, 0.66f, 1f};
        for (float step : steps) {
            View swatch = new View(requireContext());
            swatch.setBackgroundColor(HeatmapView.rampColor(hue, surface, step));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (14 * dp), (int) (14 * dp));
            lp.setMarginEnd((int) (2 * dp));
            b.heatmapScale.addView(swatch, lp);
        }
    }

    private void renderRecord(BudgetEngine.Summary s, String currency) {
        b.recordRows.removeAllViews();
        addRecord(R.string.record_streak, getResources().getQuantityString(R.plurals.days, s.streak, s.streak));
        addRecord(R.string.record_best,
                getResources().getQuantityString(R.plurals.days, s.bestStreak, s.bestStreak));
        addRecord(R.string.record_under,
                getString(R.string.record_under_value, s.finishedDaysUnderBudget, s.finishedDays));
        addRecord(R.string.record_saved, Money.format(s.totalSaved, currency));
    }

    private void addRecord(int label, String value) {
        addRow(b.recordRows, label, value, 0);
    }

    /** @param valueColor a colour resource for the value, or 0 for the usual ink */
    private void addRow(LinearLayout rows, int label, String value, int valueColor) {
        ItemRecordRowBinding row = ItemRecordRowBinding.inflate(getLayoutInflater(), rows, false);
        row.label.setText(label);
        row.value.setText(value);
        if (valueColor != 0) row.value.setTextColor(ContextCompat.getColor(requireContext(), valueColor));
        rows.addView(row.getRoot());
    }

    /** Averages are shown to the whole unit; the cents are noise there. */
    private static long roundToUnit(long amount) {
        return Math.round(amount / 100.0) * 100;
    }

    private static int bucketName(Insights.TimeBucket bucket) {
        switch (bucket) {
            case MORNING: return R.string.bucket_morning;
            case AFTERNOON: return R.string.bucket_afternoon;
            case EVENING: return R.string.bucket_evening;
            default: return R.string.bucket_night;
        }
    }

    private static int bucketPlural(Insights.TimeBucket bucket) {
        switch (bucket) {
            case MORNING: return R.string.bucket_morning_lc;
            case AFTERNOON: return R.string.bucket_afternoon_lc;
            case EVENING: return R.string.bucket_evening_lc;
            default: return R.string.bucket_night_lc;
        }
    }

    private static int bucketRange(Insights.TimeBucket bucket) {
        switch (bucket) {
            case MORNING: return R.string.bucket_morning_range;
            case AFTERNOON: return R.string.bucket_afternoon_range;
            case EVENING: return R.string.bucket_evening_range;
            default: return R.string.bucket_night_range;
        }
    }
}
