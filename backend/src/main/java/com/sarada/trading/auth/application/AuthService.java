package com.sarada.trading.auth.application;

import com.sarada.trading.auth.domain.User;
import com.sarada.trading.auth.infra.UserRepository;
import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.error.DomainException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties props;
    private final AuditService audit;

    /** Seed/refresh the admin user from env-provided credentials on boot. */
    @org.springframework.context.event.EventListener(
            org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void seedAdmin() {
        String username = props.security().adminUsername();
        String password = props.security().adminPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD must be set");
        }
        users.findByUsername(username).ifPresentOrElse(
                existing -> {
                    if (!passwordEncoder.matches(password, existing.getPasswordHash())) {
                        existing.setPasswordHash(passwordEncoder.encode(password));
                        users.save(existing);
                    }
                },
                () -> users.save(new User(username, passwordEncoder.encode(password), "ADMIN")));
    }

    public record LoginResult(String token, String username, String role) {}

    public LoginResult login(String username, String password) {
        User user = users.findByUsername(username)
                .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> {
                    audit.log("AUTH", "LOGIN_FAILED", "username=" + username, username);
                    return new DomainException("Invalid credentials", HttpStatus.UNAUTHORIZED);
                });
        audit.log("AUTH", "LOGIN", "Dashboard login", username);
        return new LoginResult(jwtService.issue(user.getUsername(), user.getRole()),
                user.getUsername(), user.getRole());
    }
}
