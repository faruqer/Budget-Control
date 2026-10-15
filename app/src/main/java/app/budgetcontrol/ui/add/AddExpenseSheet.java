package app.budgetcontrol.ui.add;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.budgetcontrol.App;
import app.budgetcontrol.MainActivity;
import app.budgetcontrol.R;
import app.budgetcontrol.data.Repo;
import app.budgetcontrol.databinding.ItemCategoryTileBinding;
import app.budgetcontrol.databinding.ItemKeyBinding;
import app.budgetcontrol.databinding.SheetAddExpenseBinding;
import app.budgetcontrol.model.Category;
import app.budgetcontrol.model.Expense;
import app.budgetcontrol.ui.Ui;
import app.budgetcontrol.ui.category.CategoryEditSheet;
import app.budgetcontrol.util.Money;

/**
 * Logs an expense in two steps: an amount, then a category. Tapping the category saves.
 * Opened with an expense id it edits that expense instead, and saves with a button.
 */
public class AddExpenseSheet extends BottomSheetDialogFragment {
    private static final String ARG_ID = "id";
    private static final String STATE_INPUT = "input";
    private static final String STATE_PLANNED = "planned";
    private static final String STATE_DAY = "day";
    private static final String STATE_CATEGORY = "category";
    private static final String STATE_BULK = "bulk";

    private static final String BACKSPACE = "back";
    private static final String[][] KEYS = {
            {"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {".", "0", BACKSPACE}};
    private static final int MAX_WHOLE_DIGITS = 7;
    private static final int QUICK_AMOUNTS = 4;

    private SheetAddExpenseBinding b;
    private Repo repo;
    private String currency;
    private LocalDate today;

    /** What has been typed, e.g. "125" or "12.5". */
    private String input = "";
    private boolean planned = true;
    /** A big shop for the period: saved without touching the day's limit. */
    private boolean bulk;
    private LocalDate day;
    /** Only used while editing; when adding, the category tap saves straight away. */
    @Nullable private Category category;
    @Nullable private Expense editing;
    private final Map<String, View> tiles = new HashMap<>();
    private final Runnable onCategoriesChanged = this::onCategoriesChanged;

    public static AddExpenseSheet newAdd() {
        return new AddExpenseSheet();
    }

    public static AddExpenseSheet newEdit(long expenseId) {
        AddExpenseSheet sheet = new AddExpenseSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_ID, expenseId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = SheetAddExpenseBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repo = App.repo(requireContext());
        currency = repo.prefs().currency();
        today = LocalDate.now();
        day = today;

        long id = getArguments() == null ? 0 : getArguments().getLong(ARG_ID, 0);
        if (id != 0) {
            editing = repo.find(id);
            if (editing == null) {
                dismissAllowingStateLoss();
                return;
            }
        }
        if (state != null) {
            input = state.getString(STATE_INPUT, "");
            planned = state.getBoolean(STATE_PLANNED, true);
            bulk = state.getBoolean(STATE_BULK, false);
            day = LocalDate.parse(state.getString(STATE_DAY, today.toString()));
            String key = state.getString(STATE_CATEGORY);
            category = key == null ? null : repo.category(key);
        } else if (editing != null) {
            input = Money.plain(editing.amount).replace(",", "");
            planned = editing.planned;
            bulk = editing.bulk;
            day = editing.day;
            category = editing.category;
        }

        buildQuickAmounts();
        buildKeypad();
        buildCategories();

        b.segPlanned.setOnClickListener(v -> setPlanned(true));
        b.segImpulse.setOnClickListener(v -> setPlanned(false));
        b.dateChip.setOnClickListener(v -> pickDate());
        b.bulkChip.setOnClickListener(v -> {
            bulk = !bulk;
            render();
        });
        b.bulkChip.setCompoundDrawablesRelative(
                Ui.icon(requireContext(), R.drawable.ic_shopping_basket, 16, b.bulkChip.getCurrentTextColor()),
                null, null, null);
        b.dateChip.setCompoundDrawablesRelative(null, null,
                Ui.icon(requireContext(), R.drawable.ic_chevron_down, 16, b.dateChip.getCurrentTextColor()), null);
        b.dateChip.setCompoundDrawablePadding(dp(4));

        if (editing != null) {
            b.title.setText(R.string.sheet_edit_title);
            b.hint.setVisibility(View.GONE);
            b.editActions.setVisibility(View.VISIBLE);
            b.save.setOnClickListener(v -> saveEdit());
            b.delete.setOnClickListener(v -> deleteEditing());
        }
        render();
    }

    @Override
    public void onStart() {
        super.onStart();
        repo.addListener(onCategoriesChanged);
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            // Open at full height: the category row is the last step and must be on screen.
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) dialog).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public void onStop() {
        repo.removeListener(onCategoriesChanged);
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_INPUT, input);
        out.putBoolean(STATE_PLANNED, planned);
        out.putBoolean(STATE_BULK, bulk);
        if (day != null) out.putString(STATE_DAY, day.toString());
        if (category != null) out.putString(STATE_CATEGORY, category.key);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        tiles.clear();
        b = null;
    }

    // ---- building ----

    private void buildQuickAmounts() {
        // The amounts this person actually logs come first; round numbers fill the rest.
        List<Long> amounts = new ArrayList<>(repo.topAmounts(today.minusDays(60), QUICK_AMOUNTS));
        for (long suggestion : Money.suggestedAmounts(repo.prefs().dailyBudget())) {
            if (amounts.size() >= QUICK_AMOUNTS) break;
            if (!amounts.contains(suggestion)) amounts.add(suggestion);
        }
        amounts.sort(Long::compare);

        int gap = dp(8);
        for (int i = 0; i < amounts.size(); i++) {
            long amount = amounts.get(i);
            TextView chip = new TextView(requireContext());
            chip.setText(Money.format(amount, currency));
            chip.setTextSize(14);
            chip.setMaxLines(1);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            chip.setGravity(Gravity.CENTER);
            chip.setBackgroundResource(R.drawable.bg_key);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            if (i > 0) lp.setMarginStart(gap);
            chip.setOnClickListener(v -> {
                input = Money.plain(amount).replace(",", "");
                renderAmount();
            });
            b.quickAmounts.addView(chip, lp);
        }
    }

    private void buildKeypad() {
        int gap = dp(8);
        for (int r = 0; r < KEYS.length; r++) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (r > 0) rowLp.topMargin = gap;
            for (int c = 0; c < KEYS[r].length; c++) {
                String key = KEYS[r][c];
                ItemKeyBinding k = ItemKeyBinding.inflate(getLayoutInflater(), row, false);
                if (key.equals(BACKSPACE)) {
                    k.label.setVisibility(View.GONE);
                    k.glyph.setVisibility(View.VISIBLE);
                    k.glyph.setImageResource(R.drawable.ic_delete);
                    k.getRoot().setContentDescription(getString(R.string.key_backspace));
                    k.getRoot().setOnLongClickListener(v -> {
                        input = "";
                        renderAmount();
                        return true;
                    });
                } else {
                    k.label.setText(key);
                    k.getRoot().setContentDescription(key);
                }
                k.getRoot().setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    onKey(key);
                });
                if (c > 0) ((LinearLayout.LayoutParams) k.getRoot().getLayoutParams()).setMarginStart(gap);
                row.addView(k.getRoot());
            }
            b.keypad.addView(row, rowLp);
        }
    }

    /** Fills the grid with the user's categories, four to a row, and a tile for making a new one. */
    private void buildCategories() {
        b.categories.removeAllViews();
        tiles.clear();
        List<Category> all = new ArrayList<>(repo.categories());
        // An expense being edited keeps its category on offer even if that category was removed since.
        if (category != null && !all.contains(category)) all.add(category);

        int perRow = 4;
        int gap = dp(8);
        LinearLayout row = null;
        for (int i = 0; i <= all.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setBaselineAligned(false);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowLp.topMargin = gap;
                b.categories.addView(row, rowLp);
            }
            ItemCategoryTileBinding tile = ItemCategoryTileBinding.inflate(getLayoutInflater(), row, false);
            if (i < all.size()) {
                Category c = all.get(i);
                Ui.bindCategoryIcon(tile.icon, c);
                tile.name.setText(c.name);
                tile.getRoot().setContentDescription(c.name);
                tile.getRoot().setOnClickListener(v -> onCategory(c));
                tiles.put(c.key, tile.getRoot());
            } else {
                tile.icon.setImageResource(R.drawable.ic_plus);
                tile.icon.setImageTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.text_muted)));
                tile.name.setText(R.string.category_new);
                tile.getRoot().setContentDescription(getString(R.string.category_new_title));
                tile.getRoot().setOnClickListener(v ->
                        CategoryEditSheet.newCategory().show(getParentFragmentManager(), "category"));
            }
            if (i % perRow > 0) ((LinearLayout.LayoutParams) tile.getRoot().getLayoutParams()).setMarginStart(gap);
            row.addView(tile.getRoot());
        }
        // Pad the last row so its tiles keep the width of the ones above.
        int filled = (all.size() + 1) % perRow;
        for (int i = filled; filled != 0 && i < perRow; i++) {
            View spacer = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
            lp.setMarginStart(gap);
            row.addView(spacer, lp);
        }
    }

    /** A category was added, renamed or removed while the sheet was open. */
    private void onCategoriesChanged() {
        if (b == null) return;
        if (category != null) category = repo.category(category.key);
        buildCategories();
        render();
    }
    // ---- input ----

    private void onKey(String key) {
        if (key.equals(BACKSPACE)) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
        } else if (key.equals(".")) {
            if (!input.contains(".")) input = input.isEmpty() ? "0." : input + ".";
        } else {
            int dot = input.indexOf('.');
            if (dot >= 0) {
                if (input.length() - dot - 1 < 2) input += key;
            } else if (input.equals("0")) {
                input = key;
            } else if (input.length() < MAX_WHOLE_DIGITS) {
                input += key;
            }
        }
        renderAmount();
    }

    private void setPlanned(boolean value) {
        planned = value;
        render();
    }

    private void onCategory(Category chosen) {
        if (editing != null) {
            category = chosen;
            render();
            return;
        }
        long amount = Money.parse(input);
        if (amount <= 0) {
            nudge(R.string.sheet_need_amount);
            return;
        }
        ((MainActivity) requireActivity()).logExpense(amount, chosen, planned, day, bulk);
        dismiss();
    }

    private void saveEdit() {
        long amount = Money.parse(input);
        if (amount <= 0) {
            nudge(R.string.sheet_need_amount);
            return;
        }
        if (editing == null || category == null) return;
        repo.update(new Expense(editing.id, amount, category, planned, day, editing.minuteOfDay,
                bulk, 0, editing.note));
        dismiss();
    }

    private void deleteEditing() {
        if (editing == null) return;
        Expense removed = editing;
        Repo r = repo;
        r.delete(removed.id);
        ((MainActivity) requireActivity()).snack(getString(R.string.snack_deleted), () ->
                r.insert(removed.amount, removed.category, removed.planned, removed.day,
                        removed.minuteOfDay, removed.bulk));
        dismiss();
    }

    private void pickDate() {
        long todayUtc = utcMillis(today);
        long firstUtc = utcMillis(repo.firstDay(today));
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(firstUtc)
                .setEnd(todayUtc)
                .setOpenAt(utcMillis(day))
                .setValidator(CompositeDateValidator.allOf(Arrays.asList(
                        DateValidatorPointForward.from(firstUtc),
                        DateValidatorPointBackward.before(todayUtc))))
                .build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.sheet_pick_date)
                .setSelection(utcMillis(day))
                .setCalendarConstraints(constraints)
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            day = Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate();
            if (b != null) render();
        });
        picker.show(getChildFragmentManager(), "date");
    }

    /** The date picker works in UTC midnights. */
    private static long utcMillis(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    // ---- rendering ----

    private void render() {
        renderAmount();
        b.segPlanned.setSelected(planned);
        b.segImpulse.setSelected(!planned);
        b.bulkChip.setSelected(bulk);
        b.dateChip.setText(Ui.dayLabel(requireContext(), day, today));
        for (Map.Entry<String, View> e : tiles.entrySet()) {
            e.getValue().setSelected(category != null && e.getKey().equals(category.key));
        }
    }

    private void renderAmount() {
        String typed = input.isEmpty() ? "0" : input;
        int dot = typed.indexOf('.');
        String whole = dot < 0 ? typed : typed.substring(0, dot);
        String decimals = dot < 0 ? "" : typed.substring(dot);
        String grouped = String.format(Locale.US, "%,d", Long.parseLong(whole));
        b.amount.setText(Money.prefix(currency) + grouped + decimals);
        b.amount.setTextColor(ContextCompat.getColor(requireContext(),
                Money.parse(input) > 0 ? R.color.text_primary : R.color.text_muted));
        // Typing clears whatever nudge() last complained about.
        if (editing == null) {
            b.hint.setText(bulk ? R.string.big_shop_hint : R.string.sheet_pick_category);
            b.hint.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
        } else {
            b.hint.setVisibility(View.GONE);
        }
    }

    /** Points at what is missing: shakes the amount and says so. */
    private void nudge(int message) {
        b.hint.setVisibility(View.VISIBLE);
        b.hint.setText(message);
        b.hint.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_over));
        float d = dp(10);
        ObjectAnimator.ofFloat(b.amount, View.TRANSLATION_X, 0, d, -d, d / 2, -d / 2, 0)
                .setDuration(320)
                .start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
