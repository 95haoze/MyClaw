package io.myclaw.server.controller;

import io.myclaw.server.annotation.RepeatSubmit;
import io.myclaw.server.dto.ApiResponse;
import io.myclaw.server.persistence.entity.UserEntity;
import io.myclaw.server.persistence.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager manager;

    public AuthController(UserRepository users, PasswordEncoder encoder,
                          AuthenticationConfiguration configuration) throws Exception {
        this.users = users;
        this.encoder = encoder;
        this.manager = configuration.getAuthenticationManager();
    }

    public record Credentials(String email, String password, String displayName) {
    }

    public record UserView(Long id, String email, String displayName) {
    }

    public record CsrfView(String headerName, String token) {
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfView> csrf(CsrfToken token) {
        return ApiResponse.ok(new CsrfView(token.getHeaderName(), token.getToken()));
    }

    @RepeatSubmit(interval = 1000)
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserView> register(@RequestBody Credentials credentials) {
        String email = normalize(credentials.email());
        if (credentials.password() == null || credentials.password().length() < 8)
            throw new IllegalArgumentException("密码至少需要 8 位");
        if (users.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("邮箱已注册");
        String name = credentials.displayName() == null || credentials.displayName().isBlank()
                ? email : credentials.displayName().strip();
        return ApiResponse.ok(view(users.save(new UserEntity(email, name, encoder.encode(credentials.password())))));
    }

    @RepeatSubmit(interval = 1000)
    @PostMapping("/login")
    public ApiResponse<UserView> login(@RequestBody Credentials credentials, HttpServletRequest request) {
        Authentication auth = manager.authenticate(
                new UsernamePasswordAuthenticationToken(normalize(credentials.email()), credentials.password()));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", context);
        return ApiResponse.ok(view(users.findByEmailIgnoreCase(auth.getName()).orElseThrow()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(Authentication auth) {
        return ApiResponse.ok(view(users.findByEmailIgnoreCase(auth.getName()).orElseThrow()));
    }

    private static String normalize(String email) {
        if (email == null || !email.strip().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new IllegalArgumentException("邮箱格式不正确");
        return email.strip().toLowerCase();
    }

    private static UserView view(UserEntity user) {
        return new UserView(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
