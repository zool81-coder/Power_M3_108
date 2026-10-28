package ru.zoolbazuzu.powerm3_108;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PowerCalculatorTest {

    private static final double EPS = 1e-9;

    @Test
    public void averagePower_multipliesCoeffByReading() {
        assertEquals(6.25, PowerCalculator.averagePower(2.5, 2.5), EPS);
    }

    @Test
    public void averagePower_zeroReading_givesZero() {
        assertEquals(0.0, PowerCalculator.averagePower(2.5, 0.0), EPS);
    }

    @Test
    public void pulsePower_multipliesAverageByDutyCycle() {
        assertEquals(100.0, PowerCalculator.pulsePower(2.5, 2.5, 16.0), EPS);
    }

    @Test
    public void pulsePower_zeroDutyCycle_givesZero() {
        assertEquals(0.0, PowerCalculator.pulsePower(2.5, 2.5, 0.0), EPS);
    }

    @Test
    public void clampDutyCycle_negativeBecomesZero() {
        assertEquals(0.0, PowerCalculator.clampDutyCycle(-5.0), EPS);
    }

    @Test
    public void clampDutyCycle_positiveUnchanged() {
        assertEquals(16.0, PowerCalculator.clampDutyCycle(16.0), EPS);
    }

    @Test
    public void roundTo_oneDecimal_roundsDown() {
        assertEquals(2.5, PowerCalculator.roundTo(2.54, 1), EPS);
    }

    @Test
    public void roundTo_oneDecimal_roundsUp() {
        assertEquals(2.6, PowerCalculator.roundTo(2.56, 1), EPS);
    }

    @Test
    public void roundTo_twoDecimals() {
        assertEquals(1.23, PowerCalculator.roundTo(1.234, 2), EPS);
    }
}
