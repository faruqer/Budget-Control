package app.budgetcontrol.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.budgetcontrol.App;
import app.budgetcontrol.R;
import app.budgetcontrol.data.Repo;
import app.budgetcontrol.databinding.FragmentHistoryBinding;
import app.budgetcontrol.databinding.ItemDayHeaderBinding;
import app.budgetcontrol.databinding.ItemExpenseBinding;
import app.budgetcontrol.logic.BudgetEngine;
import app.budgetcontrol.model.Expense;
import app.budgetcontrol.ui.Ui;
import app.budgetcontrol.util.Money;

/** Every expense, newest first, under a heading for each day. */
public class HistoryFragment extends Fragment {
    private FragmentHistoryBinding b;
    private Repo repo;
    private final Adapter adapter = new Adapter();
    private final Runnable onDataChanged = this::render;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentHistoryBinding.inflate(inflater, container, false);
        repo = App.repo(requireContext());
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
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
        b.list.setAdapter(null);
        b = null;
    }

    private void render() {
        if (b == null) return;
        LocalDate today = LocalDate.now();
        Map<LocalDate, BudgetEngine.DayResult> byDate = new HashMap<>();
        for (BudgetEngine.DayResult d : repo.ledger(today)) byDate.put(d.date, d);
        List<Expense> expenses = repo.expenses(repo.firstDay(today), today);

        List<Object> rows = new ArrayList<>();
        LocalDate current = null;
        for (Expense e : expenses) {
            if (!e.day.equals(current)) {
                current = e.day;
                BudgetEngine.DayResult d = byDate.get(e.day);
                if (d != null) rows.add(d);
            }
            rows.add(e);
        }
        adapter.submit(rows, today, repo.prefs().currency());
        b.empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        b.list.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Rows are either a {@link BudgetEngine.DayResult} heading or an {@link Expense}. */
    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_DAY = 0;
        private static final int TYPE_EXPENSE = 1;

        private List<Object> rows = new ArrayList<>();
        private LocalDate today = LocalDate.now();
        private String currency = "";

        void submit(List<Object> rows, LocalDate today, String currency) {
            this.rows = rows;
            this.today = today;
            this.currency = currency;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof Expense ? TYPE_EXPENSE : TYPE_DAY;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_DAY) {
                return new DayHolder(ItemDayHeaderBinding.inflate(inflater, parent, false));
            }
            return new ExpenseHolder(ItemExpenseBinding.inflate(inflater, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (holder instanceof DayHolder) {
                bindDay(((DayHolder) holder).binding, (BudgetEngine.DayResult) row);
            } else {
                Expense e = (Expense) row;
                ItemExpenseBinding binding = ((ExpenseHolder) holder).binding;
                Ui.bindExpense(binding, e, currency);
                binding.getRoot().setOnClickListener(v -> Ui.openExpense(getParentFragmentManager(), e));
            }
        }

        private void bindDay(ItemDayHeaderBinding binding, BudgetEngine.DayResult d) {
            binding.day.setText(Ui.dayLabel(binding.getRoot().getContext(), d.date, today));
            boolean over = !d.underBudget();
            binding.total.setText(binding.getRoot().getContext().getString(
                    over ? R.string.history_day_over : R.string.history_day_total,
                    Money.format(d.spent, currency), Money.format(d.available(), currency)));
            binding.total.setTextColor(ContextCompat.getColor(binding.getRoot().getContext(),
                    over ? R.color.status_over : R.color.text_muted));
        }
    }

    private static final class DayHolder extends RecyclerView.ViewHolder {
        final ItemDayHeaderBinding binding;

        DayHolder(ItemDayHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final class ExpenseHolder extends RecyclerView.ViewHolder {
        final ItemExpenseBinding binding;

        ExpenseHolder(ItemExpenseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
