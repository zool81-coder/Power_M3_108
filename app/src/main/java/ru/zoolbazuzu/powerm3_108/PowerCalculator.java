package ru.zoolbazuzu.powerm3_108;

/**
 * Чистая бизнес-логика расчёта мощности.
 * Не зависит от Android, поэтому легко тестируется в JVM-тестах.
 */
public final class PowerCalculator {

    private PowerCalculator() {
        // утилитарный класс
    }

    /** Средняя мощность: P_ср = коэффициент * показание прибора. */
    public static double averagePower(double coefficient, double readingX) {
        return coefficient * readingX;
    }

    /** Импульсная мощность: P_имп = P_ср * скважность Q. */
    public static double pulsePower(double coefficient, double readingX, double dutyCycleQ) {
        return averagePower(coefficient, readingX) * dutyCycleQ;
    }

    /** Скважность не может быть отрицательной. */
    public static double clampDutyCycle(double q) {
        return q < 0 ? 0 : q;
    }

    /** Округление до N знаков после запятой (банковское не нужно — обычное). */
    public static double roundTo(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
