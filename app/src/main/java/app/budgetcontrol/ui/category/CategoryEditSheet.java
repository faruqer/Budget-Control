package app.budgetcontrol.ui.category;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

import app.budgetcontrol.App;
import app.budgetcontrol.MainActivity;
import app.budgetcontrol.R;
import app.budgetcontrol.data.Repo;
import app.budgetcontrol.databinding.SheetCategoryEditBinding;
import app.budgetcontrol.model.Category;
import app.budgetcontrol.ui.CategoryIcons;
import app.budgetcontrol.ui.Ui;

/** Makes a new category, or changes the name, icon and colour of an existing one. */
public class CategoryEditSheet extends BottomSheetDialogFragment {
    private static final String ARG_KEY = "key";
    private static final String STATE_ICON = "icon";
    private static final String STATE_COLOR = "color";
    private static final int ICONS_PER_ROW = 6;
    private static final int COLORS_PER_ROW = 5;

    private SheetCategoryEditBinding b;
    private Repo repo;
    /** Null while making a new category. */
    @Nullable private Category editing;
    private String icon;
    private int color;
    private final List<View> iconCells = new ArrayList<>();
    private final List<View> colorCells = new ArrayList<>();

    public static CategoryEditSheet newCategory() {
        return new CategoryEditSheet();
    }

    public static CategoryEditSheet edit(String key) {
        CategoryEditSheet sheet = new CategoryEditSheet();
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = SheetCategoryEditBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repo = App.repo(requireContext());
        String key = getArguments() == null ? null : getArguments().getString(ARG_KEY);
        if (key != null) editing = repo.category(key);

        List<String> icons = CategoryIcons.names();
        if (editing != null) {
            icon = editing.icon;
            color = editing.color;
            b.title.setText(R.string.category_edit_title);
            if (state == null) b.name.setText(editing.name);
            // "Other" is where unknown categories land, so it can be restyled but not removed.
            if (!Category.OTHER_KEY.equals(editing.key)) {
                b.remove.setVisibility(View.VISIBLE);
                b.remove.setOnClickListener(v -> remove());
            }
        } else {
            // Start a new one on a colour and icon the existing categories are not already using.
            int count = repo.categories().size();
            icon = icons.get(Math.min(count, icons.size() - 1));
            color = CategoryIcons.COLORS[count % CategoryIcons.COLORS.length];
        }
        if (state != null) {
            icon = state.getString(STATE_ICON, icon);
            color = state.getInt(STATE_COLOR, color);
        }

        buildColors();
        buildIcons(icons);
        b.save.setOnClickListener(v -> save());
        render();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) dialog).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_ICON, icon);
        out.putInt(STATE_COLOR, color);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        iconCells.clear();
        colorCells.clear();
        b = null;
    }

    // ---- building ----

    private void buildColors() {
        int[] colors = CategoryIcons.COLORS;
        LinearLayout row = null;
        for (int i = 0; i < colors.length; i++) {
            if (i % COLORS_PER_ROW == 0) row = addRow(b.colors, i > 0);
            int value = colors[i];
            View dot = new View(requireContext());
            dot.setBackgroundResource(R.drawable.bg_swatch);
            dot.setBackgroundTintList(ColorStateList.valueOf(value));
            FrameLayout cell = cell(row, 52);
            int size = Ui.dp(requireContext(), 34);
            cell.addView(dot, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            cell.setTag(value);
            cell.setOnClickListener(v -> {
                color = value;
                render();
            });
            colorCells.add(cell);
        }
    }

    private void buildIcons(List<String> icons) {
        LinearLayout row = null;
        for (int i = 0; i < icons.size(); i++) {
            if (i % ICONS_PER_ROW == 0) row = addRow(b.icons, i > 0);
            String name = icons.get(i);
            ImageView image = new ImageView(requireContext());
            image.setImageResource(CategoryIcons.res(name));
            FrameLayout cell = cell(row, 48);
            int size = Ui.dp(requireContext(), 24);
            cell.addView(image, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            cell.setTag(name);
            cell.setContentDescription(name.replace('-', ' '));
            cell.setOnClickListener(v -> {
                icon = name;
                render();
            });
            iconCells.add(cell);
        }
        // Pad the last row so its icons line up under the ones above.
        int filled = icons.size() % ICONS_PER_ROW;
        for (int i = filled; filled != 0 && i < ICONS_PER_ROW; i++) cell(row, 48);
    }

    private LinearLayout addRow(LinearLayout parent, boolean spaced) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (spaced) lp.topMargin = Ui.dp(requireContext(), 4);
        parent.addView(row, lp);
        return row;
    }

    /** An equal-width slot in a row. */
    private FrameLayout cell(LinearLayout row, int heightDp) {
        FrameLayout cell = new FrameLayout(requireContext());
        row.addView(cell, new LinearLayout.LayoutParams(0, Ui.dp(requireContext(), heightDp), 1f));
        return cell;
    }

    // ---- rendering ----

    private void render() {
        int ink = ContextCompat.getColor(requireContext(), R.color.text_primary);
        int muted = ContextCompat.getColor(requireContext(), R.color.text_muted);

        b.preview.setImageResource(CategoryIcons.res(icon));
        b.preview.setImageTintList(ColorStateList.valueOf(color));

        for (View cell : colorCells) {
            cell.setBackground((int) cell.getTag() == color ? ring(ink, true) : null);
        }
        for (View cell : iconCells) {
            boolean chosen = icon.equals(cell.getTag());
            cell.setBackground(chosen ? ring(ink, false) : null);
            ImageView image = (ImageView) ((FrameLayout) cell).getChildAt(0);
            image.setImageTintList(ColorStateList.valueOf(chosen ? color : muted));
        }
    }

    /** The outline that marks the chosen colour (round) or icon (rounded square). */
    private GradientDrawable ring(int ink, boolean round) {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(Ui.dp(requireContext(), 2), ink);
        if (round) {
            ring.setShape(GradientDrawable.OVAL);
        } else {
            ring.setCornerRadius(Ui.dp(requireContext(), 14));
        }
        return ring;
    }

    // ---- actions ----

    private void save() {
        String name = b.name.getText().toString().trim();
        if (name.isEmpty()) {
            b.name.setError(getString(R.string.category_need_name));
            b.name.requestFocus();
            return;
        }
        if (editing == null) {
            repo.addCategory(name, icon, color);
        } else {
            repo.updateCategory(editing.key, name, icon, color);
        }
        dismiss();
    }

    private void remove() {
        if (editing == null) return;
        repo.removeCategory(editing.key);
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).snack(getString(R.string.category_removed, editing.name), null);
        }
        dismiss();
    }
}
