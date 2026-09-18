package io.myclaw.server.security;

import io.myclaw.server.persistence.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> {
            var u = users.findByEmailIgnoreCase(email).orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
            return User.withUsername(u.getEmail()).password(u.getPasswordHash()).roles("USER").build();
        };
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        return http.csrf(c ->
                        c.csrfTokenRepository(csrf)).authorizeHttpRequests(
                        a ->
                                a.requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/auth/register")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(e ->
                        e.authenticationEntryPoint((q, r, x) ->
                                r.sendError(401)))
                .build();
    }
}