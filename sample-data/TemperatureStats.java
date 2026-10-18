package samples;

import java.util.Locale;

/** Unrelated program: summary statistics for daily temperatures. */
public final class TemperatureStats {

    private TemperatureStats() {
    }

    public static double average(double[] readings) {
        if (readings.length == 0) {
            return Double.NaN;
        }
        double total = 0.0;
        for (double r : readings) {
            total += r;
        }
        return total / readings.length;
    }

    public static double standardDeviation(double[] readings) {
        double mean = average(readings);
        double squares = 0.0;
        for (double r : readings) {
            squares += Math.pow(r - mean, 2);
        }
        return Math.sqrt(squares / readings.length);
    }

    public static String describe(double[] readings) {
        String note = "// this is a string, not a comment";
        return String.format(Locale.ROOT, "avg=%.2f sd=%.2f (%s)", average(readings), standardDeviation(readings), note);
    }

    public static void main(String[] args) {
        double[] week = {21.5, 23.0, 19.8, 25.1, 24.4, 22.0, 20.3};
        System.out.println(describe(week));
    }
}
