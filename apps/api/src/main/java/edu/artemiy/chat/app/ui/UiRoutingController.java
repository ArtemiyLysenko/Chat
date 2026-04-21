package edu.artemiy.chat.app.ui;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class UiRoutingController {

    @GetMapping("/")
    String root(Authentication authentication) {
        return isAuthenticated(authentication) ? "redirect:/app" : "forward:/index.html";
    }

    @GetMapping("/login")
    String login(Authentication authentication) {
        return authPage(authentication, "forward:/login.html");
    }

    @GetMapping("/register")
    String register(Authentication authentication) {
        return authPage(authentication, "forward:/register.html");
    }

    @GetMapping("/password-reset/request")
    String resetRequest(Authentication authentication) {
        return authPage(authentication, "forward:/password-reset-request.html");
    }

    @GetMapping("/password-reset/consume")
    String resetConsume(Authentication authentication) {
        return authPage(authentication, "forward:/password-reset-consume.html");
    }

    @GetMapping("/app")
    String appShell(Authentication authentication) {
        return protectedPage(authentication, "forward:/app.html");
    }

    @GetMapping("/app/contacts")
    String contacts(Authentication authentication) {
        return protectedPage(authentication, "forward:/contacts.html");
    }

    @GetMapping("/app/sessions")
    String sessions(Authentication authentication) {
        return protectedPage(authentication, "forward:/sessions.html");
    }

    @GetMapping("/app/direct-dialogs/{userId}")
    String directDialog(Authentication authentication) {
        return protectedPage(authentication, "forward:/direct-dialog.html");
    }

    private static String authPage(Authentication authentication, String view) {
        return isAuthenticated(authentication) ? "redirect:/app" : view;
    }

    private static String protectedPage(Authentication authentication, String view) {
        return isAuthenticated(authentication) ? view : "redirect:/login";
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
