package app.budgetcontrol.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class MoneyTest {

    @Test
    public void wholeAmountsHaveNoDecimals() {
        assertEquals("$12", Money.format(1200, "$"));
        assertEquals("$1,250", Money.format(125000, "$"));
        assertEquals("$0", Money.format(0, "$"));
    }

    @Test
    public void centsAreShownWhenPresent() {
        assertEquals("$12.50", Money.format(1250, "$"));
        assertEquals("$0.05", Money.format(5, "$"));
    }

    @Test
    public void wordSymbolsGetASpace() {
        assertEquals("Rs 800", Money.format(80000, "Rs"));
        assertEquals("₹800", Money.format(80000, "₹"));
        assertEquals("800", Money.format(80000, ""));
    }

    @Test
    public void negativeAmountsPutTheSignFirst() {
        assertEquals("-$80", Money.format(-8000, "$"));
        assertEquals("-Rs 12.50", Money.format(-1250, "Rs"));
    }

    @Test
    public void parsesKeypadInput() {
        assertEquals(12500, Money.parse("125"));
        assertEquals(1250, Money.parse("12.5"));
        assertEquals(1255, Money.parse("12.55"));
        assertEquals(75, Money.parse("0.75"));
        assertEquals(1200, Money.parse("12."));
        assertEquals(50, Money.parse(".5"));
        assertEquals(125000, Money.parse("1,250"));
    }

    @Test
    public void extraDecimalsAreDropped() {
        assertEquals(1299, Money.parse("12.999"));
    }

    @Test
    public void unreadableInputIsZero() {
        assertEquals(0, Money.parse(""));
        assertEquals(0, Money.parse("."));
        assertEquals(0, Money.parse(null));
        assertEquals(0, Money.parse("abc"));
    }

    @Test
    public void formatAndParseRoundTrip() {
        for (long amount : new long[]{0, 5, 100, 1250, 99999, 125000}) {
            assertEquals(amount, Money.parse(Money.plain(amount)));
        }
    }

    @Test
    public void suggestedAmountsScaleWithTheBudget() {
        assertEquals(Arrays.asList(5000L, 10000L, 20000L, 50000L), Money.suggestedAmounts(80000));
        assertEquals(Arrays.asList(100L, 200L, 500L, 1000L), Money.suggestedAmounts(2000));
    }

    @Test
    public void suggestedAmountsNeverRepeat() {
        // A tiny budget would round several shares to the same amount.
        assertEquals(Arrays.asList(100L, 200L), Money.suggestedAmounts(300));
    }
}
