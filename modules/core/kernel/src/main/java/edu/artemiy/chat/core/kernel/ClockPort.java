package edu.artemiy.chat.core.kernel;

import java.time.Instant;

@FunctionalInterface
public interface ClockPort {

    Instant now();

    static ClockPort systemUtc() {
        return Instant::now;
    }
}
