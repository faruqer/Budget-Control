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

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import app.budgetcontrol.data.Prefs;
import app.budgetcontrol.data.Repo;
import app.budgetcontrol.databinding.ActivityOnboardingBinding;
import app.budgetcontrol.databinding.ItemOptionBinding;
import app.budgetcontrol.model.RolloverMode;
import app.budgetcontrol.ui.settings.SettingsFragment;
import app.budgetcontrol.util.Money;

/** First run: a daily budget, a currency symbol and what to do with leftovers. */
public class OnboardingActivity extends AppCompatActivity {
    private static final String STATE_ROLLOVER = "rollover";
    private static final RolloverMode[] OPTIONS = {
            RolloverMode.CARRY, RolloverMode.SAVINGS, RolloverMode.NONE};

    private ActivityOnboardingBinding b;
    private RolloverMode rollover = RolloverMode.CARRY;
    private final Map<RolloverMode, View> optionViews = new EnumMap<>(RolloverMode.class);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(App.repo(this).prefs().palette().themeRes);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        b = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(b.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        if (savedInstanceState != null) {
            rollover = RolloverMode.fromKey(savedInstanceState.getString(STATE_ROLLOVER));
        } else {
            b.currency.setText(Prefs.deviceCurrencySymbol());
        }

        for (RolloverMode mode : OPTIONS) {
            ItemOptionBinding option = ItemOptionBinding.inflate(getLayoutInflater(), b.rolloverOptions, false);
            option.title.setText(SettingsFragment.rolloverTitle(mode));
            option.desc.setText(SettingsFragment.rolloverDescription(mode));
            option.getRoot().setOnClickListener(v -> select(mode));
            b.rolloverOptions.addView(option.getRoot());
            optionViews.put(mode, option.getRoot());
        }
        select(rollover);

        b.start.setOnClickListener(v -> start());
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_ROLLOVER, rollover.key);
    }

    private void select(RolloverMode mode) {
        rollover = mode;
        for (Map.Entry<RolloverMode, View> e : optionViews.entrySet()) {
            e.getValue().setSelected(e.getKey() == mode);
        }
    }

    private void start() {
        long budget = Money.parse(b.budget.getText().toString());
        if (budget <= 0) {
            b.budget.setError(getString(R.string.invalid_budget));
            b.budget.requestFocus();
            return;
        }
        Repo repo = App.repo(this);
        Prefs prefs = repo.prefs();
        prefs.setCurrency(b.currency.getText().toString().trim());
        prefs.setDailyBudget(budget);
        prefs.setRollover(rollover);
        prefs.setOnboarded();
        repo.ensureDays(LocalDate.now());

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
