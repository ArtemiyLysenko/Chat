package edu.artemiy.chat.testing;

import java.time.Instant;

import edu.artemiy.chat.core.kernel.ClockPort;

public final class FixedClock implements ClockPort {

    private final Instant instant;

    public FixedClock(Instant instant) {
        this.instant = instant;
    }

    @Override
    public Instant now() {
        return instant;
    }
}
