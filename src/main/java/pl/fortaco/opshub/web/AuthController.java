package pl.fortaco.opshub.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AuthController {
    @GetMapping("/api/auth/me")
    public Map<String, Object> me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

        return Map.of(
            "username", authentication.getName(),
            "displayName", displayName(authentication.getName()),
            "roles", roles
        );
    }

    private static String displayName(String username) {
        return switch (username) {
            case "lider" -> "Lider zmiany";
            case "operator" -> "Operator";
            default -> username;
        };
    }
}
