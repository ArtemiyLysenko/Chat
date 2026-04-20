package edu.artemiy.chat.adapters.persistence.jpa.identity;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import edu.artemiy.chat.identity.spi.PasswordHasher;

@Component
class SpringSecurityPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return rawPassword != null && passwordHash != null && passwordEncoder.matches(rawPassword, passwordHash);
    }
}
