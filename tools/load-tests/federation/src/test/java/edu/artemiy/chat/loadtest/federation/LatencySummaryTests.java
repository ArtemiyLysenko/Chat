package edu.artemiy.chat.loadtest.federation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class LatencySummaryTests {

    @Test
    void summarizesLatencyPercentiles() {
        LatencySummary summary = LatencySummary.from(List.of(12L, 7L, 20L, 5L, 9L, 17L, 11L, 4L, 30L, 8L));

        assertThat(summary.minMillis()).isEqualTo(4);
        assertThat(summary.p50Millis()).isEqualTo(9);
        assertThat(summary.p95Millis()).isEqualTo(30);
        assertThat(summary.p99Millis()).isEqualTo(30);
        assertThat(summary.maxMillis()).isEqualTo(30);
        assertThat(summary.averageMillis()).isEqualTo(12.3);
    }

    @Test
    void returnsZeroSummaryForEmptyInput() {
        assertThat(LatencySummary.from(List.of()))
            .isEqualTo(new LatencySummary(0, 0, 0, 0, 0, 0));
    }
}
