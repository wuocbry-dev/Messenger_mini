package com.team503.messenger.auth.controller;

import com.team503.messenger.auth.dto.AuthResponse;
import com.team503.messenger.auth.dto.LoginRequest;
import com.team503.messenger.auth.dto.RefreshTokenRequest;
import com.team503.messenger.auth.dto.RegisterRequest;
import com.team503.messenger.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Account registration, login, JWT refresh and logout.")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Register a new account",
            description = "Creates a user with the provided credentials and returns a JWT access/refresh pair."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "409", description = "Username or email already taken")
    })
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(
            summary = "Log in with username and password",
            description = "Returns a JWT access token and a refresh token."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Exchange a refresh token for a new access token",
            description = "Rotates the refresh token; the old refresh token becomes invalid."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens refreshed",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token invalid or expired")
    })
    AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Log out",
            description = "Revokes the provided refresh token. Idempotent."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out")
    })
    void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }

}
