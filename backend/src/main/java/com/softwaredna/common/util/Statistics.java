package com.softwaredna.common.util;

import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntFunction;

/** Small statistical helpers used by the scoring modules. */
public final class Statistics {

    private Statistics() {
    }

    public static double median(int[] values) {
        if (values.length == 0) return Double.NaN;
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0
                ? (sorted[middle - 1] + sorted[middle]) / 2.0
                : sorted[middle];
    }

    /** The value below which {@code percentile}% of observations fall. */
    public static double percentile(int[] values, double percentile) {
        if (values.length == 0) return Double.NaN;
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        int index = (int) Math.ceil((percentile / 100.0) * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    public static <T> int[] toIntArray(List<T> items, ToIntFunction<T> extractor) {
        int[] values = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            values[i] = extractor.applyAsInt(items.get(i));
        }
        return values;
    }

    /** Share of values at or above a threshold, as a percentage. */
    public static double shareAtOrAbove(int[] values, int threshold) {
        if (values.length == 0) return 0;
        long count = Arrays.stream(values).filter(value -> value >= threshold).count();
        return (count * 100.0) / values.length;
    }

    public static double safeDivide(double numerator, double denominator) {
        return denominator == 0 ? Double.NaN : numerator / denominator;
    }
}
