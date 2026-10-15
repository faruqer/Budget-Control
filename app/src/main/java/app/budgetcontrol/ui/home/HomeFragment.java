package app.budgetcontrol.ui.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import app.budgetcontrol.App;
import app.budgetcontrol.MainActivity;
import app.budgetcontrol.R;
import app.budgetcontrol.data.Repo;
import app.budgetcontrol.databinding.FragmentHomeBinding;
import app.budgetcontrol.databinding.ItemExpenseBinding;
import app.budgetcontrol.logic.BudgetEngine;
import app.budgetcontrol.logic.Insights;
import app.budgetcontrol.logic.Periods;
import app.budgetcontrol.model.Expense;
import app.budgetcontrol.model.Loan;
import app.budgetcontrol.model.RolloverMode;
import app.budgetcontrol.ui.CategoryIcons;
import app.budgetcontrol.ui.Ui;
import app.budgetcontrol.ui.loan.LoansSheet;
import app.budgetcontrol.util.Money;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding b;
    private Repo repo;
    private static final DateTimeFormatter BANNER_DAY = DateTimeFormatter.ofPattern("d MMM");

    private final Runnable onDataChanged = this::render;
    /** What the big number currently shows, so a change can count from it. */
    private long shownAmount;
    @Nullable private ValueAnimator counter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentHomeBinding.inflate(inflater, container, false);
        repo = App.repo(requireContext());
        shownAmount = 0;
        Ui.enter(b.content);
        b.periodRow.setOnClickListener(v -> ((MainActivity) requireActivity()).openReport(0));
        b.loansRow.setOnClickListener(v -> new LoansSheet().show(getParentFragmentManager(), "loans"));
        return b.getRoot();
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
        if (counter != null) counter.cancel();
        b = null;
    }

    private void render() {
        if (b == null) return;
        LocalDate today = LocalDate.now();
        String currency = repo.prefs().currency();
        List<BudgetEngine.DayResult> ledger = repo.ledger(today);
        BudgetEngine.Summary summary = BudgetEngine.summarize(ledger, today);
        List<Expense> todays = repo.expenses(today, today);

        b.date.setText(Ui.longDate(today));
        renderHero(summary.today, currency);
        renderTiles(summary, ledger, todays, today, currency);
        renderPeriod(today, currency);
        renderLoans(currency);
        renderQuickAdd(today, currency);
        renderToday(todays, currency);
    }

    private void renderHero(BudgetEngine.DayResult d, String currency) {
        Context context = requireContext();
        int warnPercent = repo.prefs().warnPercent();
        BudgetEngine.Status status = BudgetEngine.status(d.spent, d.available(), warnPercent);
        boolean over = status == BudgetEngine.Status.OVER;

        // The number stays in plain ink until the day goes over. The status word and the
        // meter carry the colour.
        int amountColor = ContextCompat.getColor(context, over ? R.color.status_over : R.color.text_primary);
        int statusColor;
        int meterColor;
        int pill;
        if (over) {
            statusColor = ContextCompat.getColor(context, R.color.status_over);
            meterColor = statusColor;
            pill = R.string.pill_over;
        } else if (status == BudgetEngine.Status.WARNING) {
            statusColor = ContextCompat.getColor(context, R.color.status_warn);
            meterColor = ContextCompat.getColor(context, R.color.meter_warn);
            pill = R.string.pill_warn;
        } else {
            statusColor = ContextCompat.getColor(context, R.color.status_good);
            meterColor = Ui.themeColor(context, R.attr.accentInkColor);
            pill = R.string.pill_ok;
        }
        b.heroAmount.setTextColor(amountColor);
        b.heroPill.setTextColor(statusColor);
        b.heroPill.setText(pill);
        b.heroLabel.setText(over ? R.string.over_today : R.string.left_today);
        countTo(Math.abs(d.left()), currency);

        float used = d.available() > 0 ? d.spent / (float) d.available() : (d.spent > 0 ? 1f : 0f);
        b.heroMeter.setColors(ContextCompat.getColor(context, R.color.surface_2), meterColor);
        b.heroMeter.animateTo(used);
        b.heroSpent.setText(getString(R.string.spent_amount, Money.format(d.spent, currency)));
        b.heroOf.setText(getString(R.string.of_amount, Money.format(d.available(), currency)));

        String message = null;
        if (over) {
            message = getString(R.string.hero_msg_over);
        } else if (status == BudgetEngine.Status.WARNING) {
            message = getString(R.string.hero_msg_warn, Insights.percent(d.spent, d.available()));
        } else if (d.carryIn > 0) {
            message = getString(R.string.hero_msg_carry, Money.format(d.carryIn, currency));
        }
        b.heroMessage.setVisibility(message == null ? View.GONE : View.VISIBLE);
        b.heroMessage.setText(message);

        b.hero.setContentDescription(getString(over ? R.string.over_today : R.string.left_today) + " "
                + Money.format(Math.abs(d.left()), currency) + ". " + getString(pill) + ". "
                + b.heroSpent.getText() + " " + b.heroOf.getText());
    }

    /** Rolls the big number from what it shows now to the new amount. */
    private void countTo(long target, String currency) {
        if (counter != null) counter.cancel();
        long from = shownAmount;
        if (from == target) {
            b.heroAmount.setText(Money.format(target, currency));
            return;
        }
        counter = ValueAnimator.ofFloat(0f, 1f);
        counter.setDuration(600);
        counter.setInterpolator(new DecelerateInterpolator(2f));
        counter.addUpdateListener(a -> {
            if (b == null) return;
            // Whole units while it moves, so the cents do not flicker; the exact amount at the end.
            float t = a.getAnimatedFraction();
            long value = t >= 1f ? target : Math.round((from + (target - from) * t) / 100.0) * 100;
            shownAmount = value;
            b.heroAmount.setText(Money.format(value, currency));
        });
        counter.start();
    }
    private void renderTiles(BudgetEngine.Summary summary, List<BudgetEngine.DayResult> ledger,
                             List<Expense> todays, LocalDate today, String currency) {
        labelIcon(b.tileStreakLabel, R.drawable.ic_flame);
        b.tileStreakValue.setText(
                getResources().getQuantityString(R.plurals.days, summary.streak, summary.streak));
        b.tileStreakCaption.setText(summary.today.underBudget()
                ? getString(R.string.tile_streak_best, summary.bestStreak)
                : getString(R.string.tile_streak_broken));

        RolloverMode mode = summary.today.mode;
        if (mode == RolloverMode.SAVINGS) {
            b.tileMoneyLabel.setText(R.string.tile_saved);
            labelIcon(b.tileMoneyLabel, R.drawable.ic_piggy_bank);
            b.tileMoneyValue.setText(Money.format(summary.totalSaved, currency));
            b.tileMoneyCaption.setText(R.string.tile_saved_caption);
        } else if (mode == RolloverMode.CARRY) {
            b.tileMoneyLabel.setText(R.string.tile_carry);
            labelIcon(b.tileMoneyLabel, R.drawable.ic_forward);
            b.tileMoneyValue.setText(Money.format(summary.today.carryIn, currency));
            b.tileMoneyCaption.setText(R.string.tile_carry_caption);
        } else {
            LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(Ui.firstDayOfWeek()));
            long week = 0;
            for (BudgetEngine.DayResult d : ledger) {
                if (!d.date.isBefore(weekStart)) week += d.spent;
            }
            b.tileMoneyLabel.setText(R.string.tile_week);
            labelIcon(b.tileMoneyLabel, R.drawable.ic_calendar_days);
            b.tileMoneyValue.setText(Money.format(week, currency));
            b.tileMoneyCaption.setText(R.string.tile_week_caption);
        }

        labelIcon(b.tileImpulseLabel, R.drawable.ic_zap);
        long impulse = Insights.impulseTotal(todays);
        b.tileImpulseValue.setText(Money.format(impulse, currency));
        b.tileImpulseCaption.setText(impulse == 0
                ? getString(R.string.tile_impulse_none)
                : getString(R.string.tile_impulse_caption, Insights.percent(impulse, Insights.total(todays))));
    }

    /** The budget period so far, and a notice when the previous one has just ended. */
    private void renderPeriod(LocalDate today, String currency) {
        Context context = requireContext();
        Periods.Period period = repo.period(today);
        long spent = repo.spentIn(period);
        long budget = repo.periodBudget(period);

        Periods.Length length = repo.prefs().periodLength();
        b.periodTitle.setText(length == Periods.Length.WEEK ? R.string.home_period_week
                : length == Periods.Length.TWO_WEEKS ? R.string.home_period_two_weeks
                : R.string.home_period_month);
        labelIcon(b.periodTitle, R.drawable.ic_calendar_days);
        b.periodValue.setText(getString(R.string.home_period_value,
                Money.format(spent, currency), Money.format(budget, currency)));
        boolean over = spent > budget;
        b.periodMeter.setColors(ContextCompat.getColor(context, R.color.surface_2),
                over ? ContextCompat.getColor(context, R.color.status_over)
                        : Ui.themeColor(context, R.attr.accentInkColor));
        b.periodMeter.animateTo(budget > 0 ? Math.max(0, spent) / (float) budget : 0f);
        int left = period.daysLeftAfter(today);
        b.periodCaption.setText(left == 0 ? getString(R.string.home_period_last_day)
                : getResources().getQuantityString(R.plurals.home_period_left, left, left));

        // A new period has begun since the user was last told: the one before it has a finished report.
        LocalDate announced = repo.prefs().announcedPeriod();
        if (announced == null) {
            repo.prefs().setAnnouncedPeriod(period.start);
            announced = period.start;
        }
        boolean reportReady = period.start.isAfter(announced)
                && !repo.firstDay(today).isAfter(period.start.minusDays(1));
        b.reportBanner.setVisibility(reportReady ? View.VISIBLE : View.GONE);
        if (reportReady) {
            Periods.Period previous = repo.period(period.start.minusDays(1));
            b.reportBanner.setText(getString(R.string.report_ready,
                    previous.start.format(BANNER_DAY) + " \u2013 " + previous.end.format(BANNER_DAY)));
            b.reportBanner.setCompoundDrawablesRelative(
                    Ui.icon(context, R.drawable.ic_file_chart_column, 18,
                            Ui.themeColor(context, R.attr.accentInkColor)), null,
                    Ui.icon(context, R.drawable.ic_chevron_right, 18,
                            ContextCompat.getColor(context, R.color.text_muted)), null);
            b.reportBanner.setOnClickListener(v -> {
                repo.prefs().setAnnouncedPeriod(period.start);
                ((MainActivity) requireActivity()).openReport(1);
            });
        }
    }

    private void renderLoans(String currency) {
        int open = 0;
        long owed = 0;
        for (Loan l : repo.loans()) {
            if (l.isPaid()) continue;
            open++;
            owed += l.outstanding();
        }
        labelIcon(b.loansTitle, R.drawable.ic_hand_coins);
        b.loansValue.setText(Money.format(owed, currency));
        b.loansCaption.setText(open == 0 ? getString(R.string.loans_none)
                : getResources().getQuantityString(R.plurals.loans_open, open, open));
    }

    /** Puts a small icon, in the theme's colour, in front of a tile's label. */
    private void labelIcon(TextView label, @DrawableRes int icon) {
        Context context = requireContext();
        label.setCompoundDrawablesRelative(
                Ui.icon(context, icon, 15, Ui.themeColor(context, R.attr.accentInkColor)), null, null, null);
        label.setCompoundDrawablePadding(Ui.dp(context, 5));
    }

    /** Chips that log a frequent expense again in one tap. */
    private void renderQuickAdd(LocalDate today, String currency) {
        Context context = requireContext();
        List<Repo.Combo> combos = repo.topCombos(today.minusDays(30), 6);
        b.quickTitle.setVisibility(combos.isEmpty() ? View.GONE : View.VISIBLE);
        b.quickScroll.setVisibility(combos.isEmpty() ? View.GONE : View.VISIBLE);
        b.quickRow.removeAllViews();

        int muted = ContextCompat.getColor(context, R.color.text_muted);
        for (Repo.Combo combo : combos) {
            TextView chip = new TextView(context);
            chip.setText(Money.format(combo.amount, currency));
            // The category icon leads; a bolt trails when the chip logs an impulse buy.
            chip.setCompoundDrawablesRelative(
                    Ui.icon(context, CategoryIcons.res(combo.category.icon), 18, combo.category.color),
                    null,
                    combo.planned ? null : Ui.icon(context, R.drawable.ic_zap, 14, muted),
                    null);
            chip.setCompoundDrawablePadding(Ui.dp(context, 8));
            chip.setContentDescription(combo.category.name + " "
                    + Money.format(combo.amount, currency) + ", "
                    + getString(combo.planned ? R.string.planned : R.string.impulse));
            chip.setTextSize(15);
            chip.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(context, 14), 0, Ui.dp(context, 16), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 46));
            lp.setMarginEnd(Ui.dp(context, 8));
            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                ((MainActivity) requireActivity()).logExpense(combo.amount, combo.category, combo.planned, today);
            });
            b.quickRow.addView(chip, lp);
        }
    }

    private void renderToday(List<Expense> todays, String currency) {
        b.todayList.removeAllViews();
        b.todayList.setVisibility(todays.isEmpty() ? View.GONE : View.VISIBLE);
        b.todayEmpty.setVisibility(todays.isEmpty() ? View.VISIBLE : View.GONE);
        for (Expense e : todays) {
            ItemExpenseBinding row = ItemExpenseBinding.inflate(getLayoutInflater(), b.todayList, false);
            Ui.bindExpense(row, e, currency);
            row.getRoot().setOnClickListener(v -> Ui.openExpense(getParentFragmentManager(), e));
            b.todayList.addView(row.getRoot());
        }
    }
}
