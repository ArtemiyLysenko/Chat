package edu.artemiy.chat.loadtest.federation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record LatencySummary(
    long minMillis,
    long p50Millis,
    long p95Millis,
    long p99Millis,
    long maxMillis,
    double averageMillis
) {

    public static LatencySummary from(List<Long> latenciesMillis) {
        if (latenciesMillis.isEmpty()) {
            return new LatencySummary(0, 0, 0, 0, 0, 0);
        }

        List<Long> sorted = new ArrayList<>(latenciesMillis);
        sorted.sort(Comparator.naturalOrder());
        long min = sorted.getFirst();
        long max = sorted.getLast();
        long p50 = percentile(sorted, 0.50);
        long p95 = percentile(sorted, 0.95);
        long p99 = percentile(sorted, 0.99);
        double average = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
        return new LatencySummary(min, p50, p95, p99, max, average);
    }

    private static long percentile(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
