package edu.artemiy.chat.identity.domain;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordResetSecret {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordResetSecret() {
    }

    public static IssuedPasswordResetSecret issue() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedPasswordResetSecret(rawToken, hash(rawToken));
    }

    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for password reset hashing.", exception);
        }
    }

    public record IssuedPasswordResetSecret(String rawToken, String tokenHash) {
    }
}
