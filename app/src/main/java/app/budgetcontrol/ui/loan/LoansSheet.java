package app.budgetcontrol.ui.loan;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import app.budgetcontrol.App;
import app.budgetcontrol.R;
import app.budgetcontrol.data.Repo;
import app.budgetcontrol.databinding.ItemLoanRowBinding;
import app.budgetcontrol.databinding.SheetLoansBinding;
import app.budgetcontrol.model.Loan;
import app.budgetcontrol.ui.ExpandedSheet;
import app.budgetcontrol.ui.Ui;
import app.budgetcontrol.util.Money;

/** Everyone who has borrowed money: those still owing first, then the ones who have paid. */
public class LoansSheet extends ExpandedSheet {
    private SheetLoansBinding b;
    private Repo repo;
    private final Runnable onDataChanged = this::render;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = SheetLoansBinding.inflate(inflater, container, false);
        repo = App.repo(requireContext());
        b.lend.setOnClickListener(v -> new LoanNewSheet().show(getParentFragmentManager(), "loan_new"));
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
        b = null;
    }

    private void render() {
        if (b == null) return;
        String currency = repo.prefs().currency();
        LocalDate today = LocalDate.now();
        List<Loan> open = new ArrayList<>();
        List<Loan> paid = new ArrayList<>();
        long owed = 0;
        for (Loan l : repo.loans()) {
            (l.isPaid() ? paid : open).add(l);
            owed += l.outstanding();
        }
        b.owed.setText(Money.format(owed, currency));

        b.rows.removeAllViews();
        int ink = ContextCompat.getColor(requireContext(), R.color.text_primary);
        int muted = ContextCompat.getColor(requireContext(), R.color.text_muted);
        List<Loan> ordered = new ArrayList<>(open);
        ordered.addAll(paid);
        for (Loan l : ordered) {
            ItemLoanRowBinding row = ItemLoanRowBinding.inflate(getLayoutInflater(), b.rows, false);
            row.person.setText(l.person);
            String day = Ui.dayLabel(requireContext(), l.day, today);
            row.detail.setText(l.isPaid()
                    ? getString(R.string.loan_row_paid, Money.format(l.amount, currency))
                    : getString(R.string.loan_row_open, Money.format(l.outstanding(), currency),
                            Money.format(l.amount, currency), day));
            // Settled loans step back so the ones still owing stand out.
            row.person.setTextColor(l.isPaid() ? muted : ink);
            row.icon.setImageTintList(ColorStateList.valueOf(l.isPaid() ? muted : ink));
            row.getRoot().setOnClickListener(v ->
                    LoanSheet.open(l.id).show(getParentFragmentManager(), "loan"));
            b.rows.addView(row.getRoot());
        }
    }
}
