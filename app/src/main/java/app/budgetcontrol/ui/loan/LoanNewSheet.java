package app.budgetcontrol.ui.loan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;

import app.budgetcontrol.App;
import app.budgetcontrol.R;
import app.budgetcontrol.databinding.SheetLoanNewBinding;
import app.budgetcontrol.ui.ExpandedSheet;
import app.budgetcontrol.ui.Ui;
import app.budgetcontrol.util.Money;

/** Records money lent today. */
public class LoanNewSheet extends ExpandedSheet {
    private SheetLoanNewBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = SheetLoanNewBinding.inflate(inflater, container, false);
        b.save.setOnClickListener(v -> save());
        return b.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private void save() {
        String person = b.person.getText().toString().trim();
        long amount = Money.parse(b.amount.getText().toString());
        if (person.isEmpty()) {
            b.person.setError(getString(R.string.loan_need_person));
            b.person.requestFocus();
            return;
        }
        if (amount <= 0) {
            b.amount.setError(getString(R.string.loan_need_amount));
            b.amount.requestFocus();
            return;
        }
        String note = b.note.getText().toString().trim();
        App.repo(requireContext()).addLoan(person, amount, LocalDate.now(),
                note.isEmpty() ? null : note, Ui.minuteNow());
        dismiss();
    }
}
