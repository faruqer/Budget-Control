package app.budgetcontrol.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Amounts are stored as hundredths of the currency unit, so no floating point touches the totals. */
public final class Money {

    private Money() {}

    /** "Rs 1,250", "$12.50". Decimals appear only when the amount has them. */
    public static String format(long amount, String symbol) {
        return (amount < 0 ? "-" : "") + prefix(symbol) + plain(Math.abs(amount));
    }

    /** The symbol as it goes in front of a number: "$", or "Rs " with a space when it is a word. */
    public static String prefix(String symbol) {
        return endsWithLetter(symbol) ? symbol + ' ' : symbol;
    }

    /** The number alone, with thousands separators. */
    public static String plain(long amount) {
        long whole = amount / 100;
        long cents = amount % 100;
        String s = String.format(Locale.US, "%,d", whole);
        return cents == 0 ? s : s + String.format(Locale.US, ".%02d", cents);
    }

    /** Reads what the keypad produced, e.g. "125", "12.5", "0.75". Anything unreadable is 0. */
    public static long parse(String input) {
        if (input == null) return 0;
        String s = input.trim().replace(",", "");
        if (s.isEmpty() || s.equals(".")) return 0;
        int dot = s.indexOf('.');
        String wholePart = dot < 0 ? s : s.substring(0, dot);
        String centPart = dot < 0 ? "" : s.substring(dot + 1);
        if (centPart.length() > 2) centPart = centPart.substring(0, 2);
        while (centPart.length() < 2) centPart += "0";
        try {
            long whole = wholePart.isEmpty() ? 0 : Long.parseLong(wholePart);
            return whole * 100 + Long.parseLong(centPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Round one-tap amounts sized to the budget, e.g. 800 a day gives 50, 100, 200, 500. */
    public static List<Long> suggestedAmounts(long dailyBudget) {
        double[] shares = {0.05, 0.1, 0.25, 0.5};
        List<Long> out = new ArrayList<>();
        for (double share : shares) {
            long nice = niceUnits(dailyBudget / 100.0 * share) * 100;
            if (!out.contains(nice)) out.add(nice);
        }
        return out;
    }

    /** Nearest of 1, 2, 5 times a power of ten, at least 1. */
    private static long niceUnits(double units) {
        if (units <= 1) return 1;
        double magnitude = Math.pow(10, Math.floor(Math.log10(units)));
        double lead = units / magnitude;
        double niceLead = lead < 1.5 ? 1 : lead < 3.5 ? 2 : lead < 7.5 ? 5 : 10;
        return Math.round(niceLead * magnitude);
    }

    private static boolean endsWithLetter(String symbol) {
        return !symbol.isEmpty() && Character.isLetter(symbol.charAt(symbol.length() - 1));
    }
}
