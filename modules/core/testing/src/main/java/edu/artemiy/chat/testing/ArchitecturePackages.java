package edu.artemiy.chat.testing;

import java.util.List;

public final class ArchitecturePackages {

    public static final String BASE_PACKAGE = "edu.artemiy.chat";
    public static final List<String> FEATURES = List.of(
        "identity",
        "rooms",
        "contacts",
        "messaging",
        "attachments",
        "presence",
        "federation",
        "admin"
    );

    private ArchitecturePackages() {
    }

    public static List<String> appAllowedPackages() {
        return List.of(
            "edu.artemiy.chat.app.bootstrap..",
            "edu.artemiy.chat.app.config..",
            "edu.artemiy.chat.app.http..",
            "edu.artemiy.chat.app.websocket..",
            "edu.artemiy.chat.app.ui.."
        );
    }

    public static String featureRoot(String feature) {
        return BASE_PACKAGE + "." + feature + "..";
    }

    public static List<String> nonApiPackages(String feature) {
        return List.of(
            BASE_PACKAGE + "." + feature + ".domain..",
            BASE_PACKAGE + "." + feature + ".application..",
            BASE_PACKAGE + "." + feature + ".spi..",
            BASE_PACKAGE + "." + feature + ".internal.."
        );
    }
}
