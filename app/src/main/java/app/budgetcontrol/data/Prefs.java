package app.budgetcontrol.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.util.Currency;
import java.util.Locale;

import app.budgetcontrol.logic.Periods;
import app.budgetcontrol.model.Palette;
import app.budgetcontrol.model.RolloverMode;

public final class Prefs {
    private static final String ONBOARDED = "onboarded";
    private static final String DAILY_BUDGET = "daily_budget";
    private static final String CURRENCY = "currency";
    private static final String ROLLOVER = "rollover";
    private static final String WARN_PERCENT = "warn_percent";
    private static final String PALETTE = "palette";
    private static final String PERIOD_LENGTH = "period_length";
    private static final String PERIOD_ANCHOR = "period_anchor";
    private static final String PERIOD_BUDGET = "period_budget";
    private static final String ANNOUNCED_PERIOD = "announced_period";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);
    }

    public boolean isOnboarded() {
        return sp.getBoolean(ONBOARDED, false);
    }

    public void setOnboarded() {
        sp.edit().putBoolean(ONBOARDED, true).apply();
    }

    public long dailyBudget() {
        return sp.getLong(DAILY_BUDGET, 0);
    }

    public void setDailyBudget(long amount) {
        sp.edit().putLong(DAILY_BUDGET, amount).apply();
    }

    public String currency() {
        String saved = sp.getString(CURRENCY, null);
        return saved != null ? saved : deviceCurrencySymbol();
    }

    public void setCurrency(String symbol) {
        sp.edit().putString(CURRENCY, symbol).apply();
    }

    public RolloverMode rollover() {
        return RolloverMode.fromKey(sp.getString(ROLLOVER, RolloverMode.CARRY.key));
    }

    public void setRollover(RolloverMode mode) {
        sp.edit().putString(ROLLOVER, mode.key).apply();
    }

    public int warnPercent() {
        return sp.getInt(WARN_PERCENT, 80);
    }

    public void setWarnPercent(int percent) {
        sp.edit().putInt(WARN_PERCENT, percent).apply();
    }

    public Palette palette() {
        return Palette.fromKey(sp.getString(PALETTE, null));
    }

    public void setPalette(Palette palette) {
        sp.edit().putString(PALETTE, palette.key).apply();
    }

    public Periods.Length periodLength() {
        return Periods.Length.fromKey(sp.getString(PERIOD_LENGTH, null));
    }

    public void setPeriodLength(Periods.Length length) {
        sp.edit().putString(PERIOD_LENGTH, length.key).apply();
    }

    /** The day weekly and two-weekly periods are counted from, or null if never set. */
    @Nullable
    public LocalDate periodAnchor() {
        String saved = sp.getString(PERIOD_ANCHOR, null);
        return saved == null ? null : LocalDate.parse(saved);
    }

    public void setPeriodAnchor(LocalDate day) {
        sp.edit().putString(PERIOD_ANCHOR, day.toString()).apply();
    }

    /** What the user set aside for a whole period, or 0 to use the daily budget times the days. */
    public long periodBudget() {
        return sp.getLong(PERIOD_BUDGET, 0);
    }

    public void setPeriodBudget(long amount) {
        sp.edit().putLong(PERIOD_BUDGET, amount).apply();
    }

    /** The start of the newest period whose arrival the user has been told about. */
    @Nullable
    public LocalDate announcedPeriod() {
        String saved = sp.getString(ANNOUNCED_PERIOD, null);
        return saved == null ? null : LocalDate.parse(saved);
    }

    public void setAnnouncedPeriod(LocalDate start) {
        sp.edit().putString(ANNOUNCED_PERIOD, start.toString()).apply();
    }

    public void clear() {
        sp.edit().clear().apply();
    }

    public static String deviceCurrencySymbol() {
        try {
            return Currency.getInstance(Locale.getDefault()).getSymbol();
        } catch (IllegalArgumentException | NullPointerException e) {
            // Locales without a country have no currency.
            return "$";
        }
    }
}
