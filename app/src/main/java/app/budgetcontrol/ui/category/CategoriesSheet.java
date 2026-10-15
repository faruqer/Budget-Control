package app.budgetcontrol.ui.category;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import app.budgetcontrol.App;
import app.budgetcontrol.data.Repo;
import app.budgetcontrol.databinding.ItemCategoryRowBinding;
import app.budgetcontrol.databinding.SheetCategoriesBinding;
import app.budgetcontrol.model.Category;
import app.budgetcontrol.ui.Ui;

/** The list of categories. Tapping one opens it for editing. */
public class CategoriesSheet extends BottomSheetDialogFragment {
    private SheetCategoriesBinding b;
    private Repo repo;
    private final Runnable onDataChanged = this::render;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = SheetCategoriesBinding.inflate(inflater, container, false);
        repo = App.repo(requireContext());
        b.add.setOnClickListener(v ->
                CategoryEditSheet.newCategory().show(getParentFragmentManager(), "category"));
        return b.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        repo.addListener(onDataChanged);
        render();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) dialog).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
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
        b.rows.removeAllViews();
        for (Category c : repo.categories()) {
            ItemCategoryRowBinding row = ItemCategoryRowBinding.inflate(getLayoutInflater(), b.rows, false);
            Ui.bindCategoryIcon(row.icon, c);
            row.name.setText(c.name);
            row.getRoot().setOnClickListener(v ->
                    CategoryEditSheet.edit(c.key).show(getParentFragmentManager(), "category"));
            b.rows.addView(row.getRoot());
        }
    }
}
