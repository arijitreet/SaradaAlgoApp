package com.sarada.trading.auth.api;

import com.sarada.trading.auth.application.AuthService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    @PostMapping("/login")
    public AuthService.LoginResult login(@RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }
}
