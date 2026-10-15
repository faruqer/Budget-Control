package app.budgetcontrol;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;

import app.budgetcontrol.data.Repo;
import app.budgetcontrol.databinding.ActivityMainBinding;
import app.budgetcontrol.logic.BudgetEngine;
import app.budgetcontrol.model.Category;
import app.budgetcontrol.ui.Ui;
import app.budgetcontrol.ui.add.AddExpenseSheet;
import app.budgetcontrol.ui.history.HistoryFragment;
import app.budgetcontrol.ui.home.HomeFragment;
import app.budgetcontrol.ui.insights.InsightsFragment;
import app.budgetcontrol.ui.settings.SettingsFragment;
import app.budgetcontrol.util.Money;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding b;
    private Repo repo;
    /** Which period the Insights tab should open on next; set just before switching to it. */
    private int reportPeriodsBack;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        repo = App.repo(this);
        // The colour theme has to be in place before anything is inflated.
        setTheme(repo.prefs().palette().themeRes);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        if (!repo.prefs().isOnboarded()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(b.contentFrame, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        b.nav.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            return true;
        });
        // Without this, tapping the current tab would rebuild its screen and lose the scroll position.
        b.nav.setOnItemReselectedListener(item -> { });
        b.fab.setOnClickListener(v -> AddExpenseSheet.newAdd().show(getSupportFragmentManager(), "add"));

        if (savedInstanceState == null) show(R.id.nav_today);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (b == null) return;
        repo.ensureDays(LocalDate.now());
        updateFab(b.nav.getSelectedItemId());
    }

    private void show(int itemId) {
        Fragment screen;
        if (itemId == R.id.nav_insights) {
            screen = InsightsFragment.report(reportPeriodsBack);
            reportPeriodsBack = 0;
        } else if (itemId == R.id.nav_history) {
            screen = new HistoryFragment();
        } else if (itemId == R.id.nav_settings) {
            screen = new SettingsFragment();
        } else {
            screen = new HomeFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.content, screen)
                .commit();
        updateFab(itemId);
    }

    /** Switches to the report for the budget period that many periods ago (0 is the current one). */
    public void openReport(int periodsBack) {
        reportPeriodsBack = periodsBack;
        b.nav.setSelectedItemId(R.id.nav_insights);
    }

    private void updateFab(int itemId) {
        boolean wanted = itemId == R.id.nav_today || itemId == R.id.nav_history;
        b.fab.setVisibility(wanted ? View.VISIBLE : View.GONE);
    }

    /**
     * Saves an expense and confirms it. When the expense is the one that pushes today past the
     * warning threshold or over the limit, the confirmation says that instead.
     */
    public void logExpense(long amount, Category category, boolean planned, LocalDate day) {
        logExpense(amount, category, planned, day, false);
    }

    /** @param bulk a big shop for the period; it leaves today's limit alone, so it cannot trip a warning */
    public void logExpense(long amount, Category category, boolean planned, LocalDate day, boolean bulk) {
        LocalDate today = LocalDate.now();
        String currency = repo.prefs().currency();
        BudgetEngine.Status before = todayStatus(today);
        long id = repo.insert(amount, category, planned, day, Ui.minuteNow(), bulk);

        String message = getString(R.string.snack_added,
                Money.format(amount, currency), category.name);
        if (day.equals(today)) {
            BudgetEngine.DayResult d = BudgetEngine.summarize(repo.ledger(today), today).today;
            BudgetEngine.Status after = todayStatus(today);
            if (after != before && after == BudgetEngine.Status.OVER) {
                message = getString(R.string.snack_over, Money.format(-d.left(), currency));
            } else if (after != before && after == BudgetEngine.Status.WARNING) {
                message = getString(R.string.snack_warn, Money.format(d.left(), currency));
            }
        }
        snack(message, () -> repo.delete(id));
    }

    private BudgetEngine.Status todayStatus(LocalDate today) {
        BudgetEngine.DayResult d = BudgetEngine.summarize(repo.ledger(today), today).today;
        return BudgetEngine.status(d.spent, d.available(), repo.prefs().warnPercent());
    }

    /** Shows a message above the bottom bar, with an Undo action when one is given. */
    public void snack(String message, @Nullable Runnable undo) {
        Snackbar bar = Snackbar.make(b.contentFrame, message, Snackbar.LENGTH_LONG);
        bar.setAnchorView(b.fab.getVisibility() == View.VISIBLE ? b.fab : b.nav);
        if (undo != null) bar.setAction(R.string.undo, v -> undo.run());
        bar.show();
    }
}
