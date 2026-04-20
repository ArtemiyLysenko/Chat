package edu.artemiy.chat.identity.api;

import java.time.Duration;

public record IdentitySettings(
    Duration sessionTtl,
    Duration passwordResetTtl
) {
}
