package app.budgetcontrol.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Predicate;

import app.budgetcontrol.App;
import app.budgetcontrol.OnboardingActivity;
import app.budgetcontrol.R;
import app.budgetcontrol.data.Prefs;
import app.budgetcontrol.data.Repo;
import app.budgetcontrol.databinding.FragmentSettingsBinding;
import app.budgetcontrol.logic.Periods;
import app.budgetcontrol.model.Palette;
import app.budgetcontrol.model.RolloverMode;
import app.budgetcontrol.ui.Ui;
import app.budgetcontrol.ui.category.CategoriesSheet;
import app.budgetcontrol.util.Money;

public class SettingsFragment extends Fragment {
    private static final RolloverMode[] ROLLOVER_ORDER = {
            RolloverMode.CARRY, RolloverMode.SAVINGS, RolloverMode.NONE};
    private static final int[] WARN_CHOICES = {60, 70, 80, 90};

    private FragmentSettingsBinding b;
    private static final DateTimeFormatter PERIOD_DAY = DateTimeFormatter.ofPattern("d MMM");

    private Repo repo;
    private final Runnable onDataChanged = this::render;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentSettingsBinding.inflate(inflater, container, false);
        repo = App.repo(requireContext());
        b.rowCategories.setOnClickListener(v ->
                new CategoriesSheet().show(getParentFragmentManager(), "categories"));
        b.rowBudget.setOnClickListener(v -> editBudget());
        b.rowPeriod.setOnClickListener(v -> choosePeriod());
        b.rowPeriodBudget.setOnClickListener(v -> editPeriodBudget());
        b.rowCurrency.setOnClickListener(v -> editCurrency());
        b.rowRollover.setOnClickListener(v -> chooseRollover());
        b.rowWarn.setOnClickListener(v -> chooseWarn());
        b.rowErase.setOnClickListener(v -> confirmErase());
        buildPaletteRow();
        render();
        return b.getRoot();
    }

    /** One round swatch per colour theme, showing its gradient, with a ring around the one in use. */
    private void buildPaletteRow() {
        Context context = requireContext();
        Palette current = repo.prefs().palette();
        b.paletteName.setText(current.nameRes);
        for (Palette palette : Palette.values()) {
            GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                    Ui.themeColor(context, palette.themeRes, R.attr.accentStartColor),
                    Ui.themeColor(context, palette.themeRes, R.attr.accentEndColor)});
            fill.setShape(GradientDrawable.OVAL);
            View dot = new View(context);
            dot.setBackground(fill);

            FrameLayout swatch = new FrameLayout(context);
            int size = Ui.dp(context, 36);
            swatch.addView(dot, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            if (palette == current) {
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setColor(Color.TRANSPARENT);
                ring.setStroke(Ui.dp(context, 2), ContextCompat.getColor(context, R.color.text_primary));
                swatch.setBackground(ring);
            }
            swatch.setContentDescription(getString(palette.nameRes));
            swatch.setOnClickListener(v -> {
                if (palette == repo.prefs().palette()) return;
                repo.prefs().setPalette(palette);
                // Colours come from the activity's theme, so the screen is rebuilt to pick them up.
                requireActivity().recreate();
            });
            int box = Ui.dp(context, 48);
            b.paletteRow.addView(swatch, new LinearLayout.LayoutParams(box, box));
        }
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

    private void render() {
        if (b == null) return;
        Prefs prefs = repo.prefs();
        b.budgetValue.setText(Money.format(prefs.dailyBudget(), prefs.currency()));
        b.currencyValue.setText(prefs.currency());
        b.rolloverDesc.setText(rolloverTitle(prefs.rollover()));
        b.warnDesc.setText(getString(R.string.warn_value, prefs.warnPercent()));
        LocalDate today = LocalDate.now();
        Periods.Length length = prefs.periodLength();
        Periods.Period period = repo.period(today);
        b.periodValue.setText(periodName(length));
        b.periodDesc.setText(length == Periods.Length.MONTH
                ? getString(R.string.setting_period_desc_month)
                : getString(R.string.setting_period_desc_days,
                        getString(periodName(length)).toLowerCase(Locale.getDefault()),
                        period.start.format(PERIOD_DAY)));
        String periodBudget = Money.format(repo.periodBudget(period), prefs.currency());
        b.periodBudgetDesc.setText(prefs.periodBudget() > 0 ? periodBudget
                : getString(R.string.setting_period_budget_auto, periodBudget));
        int categories = repo.categories().size();
        b.categoriesDesc.setText(getResources().getQuantityString(R.plurals.categories_count, categories, categories));
    }

    public static int rolloverTitle(RolloverMode mode) {
        switch (mode) {
            case CARRY: return R.string.rollover_carry;
            case SAVINGS: return R.string.rollover_savings;
            default: return R.string.rollover_none;
        }
    }

    public static int rolloverDescription(RolloverMode mode) {
        switch (mode) {
            case CARRY: return R.string.rollover_carry_desc;
            case SAVINGS: return R.string.rollover_savings_desc;
            default: return R.string.rollover_none_desc;
        }
    }

    private void editBudget() {
        String current = Money.plain(repo.prefs().dailyBudget()).replace(",", "");
        prompt(R.string.setting_budget, current,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, 10, text -> {
                    long amount = Money.parse(text);
                    if (amount <= 0) return false;
                    repo.setDailyBudget(amount, LocalDate.now());
                    return true;
                });
    }

    private static int periodName(Periods.Length length) {
        switch (length) {
            case WEEK: return R.string.period_week;
            case TWO_WEEKS: return R.string.period_two_weeks;
            default: return R.string.period_month;
        }
    }

    private void choosePeriod() {
        Periods.Length[] lengths = Periods.Length.values();
        String[] labels = new String[lengths.length];
        int checked = 0;
        for (int i = 0; i < lengths.length; i++) {
            labels[i] = getString(periodName(lengths[i]));
            if (lengths[i] == repo.prefs().periodLength()) checked = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_period)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    // Picking the length that is already set would restart the count for nothing.
                    if (lengths[which] != repo.prefs().periodLength()) {
                        repo.setPeriod(lengths[which], LocalDate.now());
                    }
                    dialog.dismiss();
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void editPeriodBudget() {
        long set = repo.prefs().periodBudget();
        String current = set > 0 ? Money.plain(set).replace(",", "") : "";
        prompt(R.string.setting_period_budget, current,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, 10, text -> {
                    // Empty goes back to the daily budget for each day.
                    repo.setPeriodBudget(Money.parse(text));
                    return true;
                });
    }

    private void editCurrency() {
        prompt(R.string.setting_currency, repo.prefs().currency(), InputType.TYPE_CLASS_TEXT, 4, text -> {
            repo.setCurrency(text.trim());
            return true;
        });
    }

    /**
     * Asks for one line of text.
     *
     * @param onSave returns false to reject the text and keep the dialog open
     */
    private void prompt(int title, String initial, int inputType, int maxLength, Predicate<String> onSave) {
        float dp = getResources().getDisplayMetrics().density;
        EditText field = new EditText(requireContext());
        field.setInputType(inputType);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        field.setText(initial);
        field.setSelection(field.getText().length());
        field.setSingleLine(true);
        FrameLayout holder = new FrameLayout(requireContext());
        holder.setPadding((int) (24 * dp), (int) (8 * dp), (int) (24 * dp), 0);
        holder.addView(field);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(holder)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        // Set after show() so a rejected value does not close the dialog.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (onSave.test(field.getText().toString())) {
                dialog.dismiss();
                render();
            } else {
                field.setError(getString(R.string.invalid_budget));
            }
        }));
        dialog.show();
        field.requestFocus();
    }

    private void chooseRollover() {
        String[] labels = new String[ROLLOVER_ORDER.length];
        int checked = 0;
        for (int i = 0; i < ROLLOVER_ORDER.length; i++) {
            labels[i] = getString(rolloverTitle(ROLLOVER_ORDER[i]));
            if (ROLLOVER_ORDER[i] == repo.prefs().rollover()) checked = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_rollover)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    repo.setRollover(ROLLOVER_ORDER[which], LocalDate.now());
                    dialog.dismiss();
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void chooseWarn() {
        String[] labels = new String[WARN_CHOICES.length];
        int checked = 0;
        for (int i = 0; i < WARN_CHOICES.length; i++) {
            labels[i] = getString(R.string.warn_value, WARN_CHOICES[i]);
            if (WARN_CHOICES[i] == repo.prefs().warnPercent()) checked = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_warn)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    repo.setWarnPercent(WARN_CHOICES[which]);
                    dialog.dismiss();
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmErase() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.erase_confirm_title)
                .setMessage(R.string.erase_confirm_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.erase_confirm_button, (dialog, which) -> {
                    repo.eraseAll();
                    // Back to the first-run screen, with nothing left behind it.
                    Intent restart = new Intent(requireContext(), OnboardingActivity.class);
                    restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(restart);
                })
                .show();
    }
}
