package app.budgetcontrol.ui.loan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.util.List;

import app.budgetcontrol.App;
import app.budgetcontrol.R;
import app.budgetcontrol.data.Repo;
import app.budgetcontrol.databinding.ItemRecordRowBinding;
import app.budgetcontrol.databinding.SheetLoanBinding;
import app.budgetcontrol.model.Expense;
import app.budgetcontrol.model.Loan;
import app.budgetcontrol.ui.ExpandedSheet;
import app.budgetcontrol.ui.Ui;
import app.budgetcontrol.util.Money;

/** One loan: what is still owed, the repayments so far, and a place to record the next one. */
public class LoanSheet extends ExpandedSheet {
    private static final String ARG_ID = "id";

    private SheetLoanBinding b;
    private Repo repo;
    private long loanId;
    private final Runnable onDataChanged = this::render;

    public static LoanSheet open(long loanId) {
        LoanSheet sheet = new LoanSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_ID, loanId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = SheetLoanBinding.inflate(inflater, container, false);
        repo = App.repo(requireContext());
        loanId = requireArguments().getLong(ARG_ID);
        b.addRepayment.setOnClickListener(v -> addRepayment());
        b.markPaid.setOnClickListener(v -> markPaid());
        b.delete.setOnClickListener(v -> confirmDelete());
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
        Loan loan = repo.loan(loanId);
        if (loan == null) {
            // Deleted, here or from another screen.
            dismissAllowingStateLoss();
            return;
        }
        String currency = repo.prefs().currency();
        LocalDate today = LocalDate.now();

        b.person.setText(loan.person);
        b.outstanding.setText(Money.format(loan.outstanding(), currency));
        b.status.setText(loan.isPaid() ? R.string.loan_paid_back : R.string.loan_still_owed);
        b.status.setTextColor(ContextCompat.getColor(requireContext(),
                loan.isPaid() ? R.color.status_good : R.color.text_secondary));
        b.meter.animateTo(loan.amount > 0 ? loan.repaid / (float) loan.amount : 0f);
        String summary = getString(R.string.loan_summary, Money.format(loan.amount, currency),
                Ui.dayLabel(requireContext(), loan.day, today));
        b.summary.setText(loan.note == null ? summary : summary + " · " + loan.note);
        b.repayBox.setVisibility(loan.isPaid() ? View.GONE : View.VISIBLE);

        List<Expense> repayments = repo.repayments(loanId);
        b.noRepayments.setVisibility(repayments.isEmpty() ? View.VISIBLE : View.GONE);
        b.repayments.removeAllViews();
        for (Expense r : repayments) {
            ItemRecordRowBinding row = ItemRecordRowBinding.inflate(getLayoutInflater(), b.repayments, false);
            String day = Ui.dayLabel(requireContext(), r.day, today);
            String amount = Money.format(-r.amount, currency);
            row.label.setText(day);
            row.value.setText(amount);
            int side = Ui.dp(requireContext(), 24);
            row.getRoot().setPadding(side, row.getRoot().getPaddingTop(), side, row.getRoot().getPaddingBottom());
            row.getRoot().setBackgroundResource(R.drawable.bg_row);
            // Tapping a repayment offers to take it back, for one entered by mistake.
            row.getRoot().setOnClickListener(v -> confirmRemoveRepayment(r.id, amount, day));
            b.repayments.addView(row.getRoot());
        }
    }

    private void addRepayment() {
        Loan loan = repo.loan(loanId);
        if (loan == null) return;
        long amount = Money.parse(b.repayment.getText().toString());
        if (amount <= 0) {
            b.repayment.setError(getString(R.string.loan_need_amount));
            return;
        }
        if (amount > loan.outstanding()) {
            b.repayment.setError(getString(R.string.loan_too_much,
                    Money.format(loan.outstanding(), repo.prefs().currency())));
            return;
        }
        b.repayment.setText("");
        repo.addRepayment(loan, amount, LocalDate.now(), Ui.minuteNow());
    }

    private void markPaid() {
        Loan loan = repo.loan(loanId);
        if (loan == null || loan.isPaid()) return;
        b.repayment.setText("");
        repo.addRepayment(loan, loan.outstanding(), LocalDate.now(), Ui.minuteNow());
    }

    private void confirmRemoveRepayment(long expenseId, String amount, String day) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.loan_repayment_delete_title)
                .setMessage(getString(R.string.loan_repayment_delete_body, amount, day))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.remove, (d, w) -> repo.deleteRepayment(expenseId))
                .show();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.loan_delete_title)
                .setMessage(R.string.loan_delete_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> repo.deleteLoan(loanId))
                .show();
    }
}
