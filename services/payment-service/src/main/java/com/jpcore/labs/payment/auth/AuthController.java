package com.jpcore.labs.payment.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    public AuthService.TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }

    @PostMapping("/service-token")
    public AuthService.TokenResponse serviceToken(@Valid @RequestBody ServiceRequest request) {
        return auth.serviceToken(request.clientId(), request.clientSecret());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return auth.createUser(request.username(), request.password(), request.role());
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ProblemDetail authenticationError(
            org.springframework.web.server.ResponseStatusException exception) {
        return org.springframework.http.ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    public record LoginRequest(@NotBlank @Size(max = 100) String username,
                               @NotBlank @Size(max = 72) String password) { }
    public record ServiceRequest(@NotBlank String clientId, @NotBlank @Size(max = 72) String clientSecret) { }
    public record CreateUserRequest(@NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{1,100}") String username,
                                    @NotBlank @Size(min = 12, max = 72) String password,
                                    @NotNull UserRole role) { }
}
