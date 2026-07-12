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
        boolean leader = roles.contains("ROLE_LEADER");
        boolean operator = roles.contains("ROLE_OPERATOR");

        return Map.of(
            "username", authentication.getName(),
            "displayName", displayName(authentication.getName()),
            "roles", roles,
            "capabilities", Map.of(
                "canCreateIssue", operator || leader,
                "canComment", operator || leader,
                "canAttachEvidence", operator || leader,
                "canStartWork", operator || leader,
                "canResolveIssue", leader,
                "canVerifyIssue", leader,
                "canDeleteIssue", leader,
                "canSyncDowntime", leader
            )
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
