package com.booking.security;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.core.userdetails.UserDetailsService;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    // JwtAuthFilter depends on UserDetailsService.
    // UserDetailsService is now provided by UserDetailsServiceImpl (@Service).
    // UserDetailsServiceImpl has NO dependency on SecurityConfig.
    // RESULT: no cycle. Spring can create all three independently.

    private final UserDetailsService userDetailsService;
    // Spring sees this field needs a UserDetailsService bean.
    // It finds UserDetailsServiceImpl (@Service implements UserDetailsService).
    // It injects UserDetailsServiceImpl here automatically.
    // You do NOT need to import UserDetailsServiceImpl directly.
    // Spring's dependency injection figures it out by type.


    // ── BEAN 1: PasswordEncoder ───────────────────────────────────────────
    // BCrypt for hashing and comparing passwords.
    // No change from before.

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    // ── BEAN 2: AuthenticationProvider ────────────────────────────────────
    // Wires together UserDetailsService + PasswordEncoder.
    // Used during login to load user and compare password.

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();

        provider.setUserDetailsService(userDetailsService);
        // Uses UserDetailsServiceImpl (injected above) to load users.
        // No longer calls userDetailsService() method on this class.

        provider.setPasswordEncoder(passwordEncoder());
        // Uses BCrypt to compare passwords.

        return provider;
    }


    // ── BEAN 3: AuthenticationManager ─────────────────────────────────────
    // Used by AuthService.login() to trigger the authentication process.

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }


    // ── BEAN 4: SecurityFilterChain ───────────────────────────────────────
    // Defines all security rules: which endpoints are public,
    // which require auth, how sessions work, where JWT filter goes.

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)
            // Disable CSRF — we use JWT in headers, not cookies.
            // CSRF attacks cannot work against header-based auth.

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // No HTTP sessions — JWT is the session.
            // Any server can handle any request without shared state.

            .authorizeHttpRequests(auth -> auth

                .requestMatchers("/api/auth/**").permitAll()
                // Register and login: completely public — no token needed.

                .requestMatchers(HttpMethod.GET, "/api/properties/**").permitAll()
                // Browsing properties: public.
                // Creating/updating/deleting properties still requires auth.

                .requestMatchers(HttpMethod.GET, "/api/reviews/**").permitAll()
                // Reading reviews: public.

                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Admin dashboard: ROLE_ADMIN only.
                // GUEST or HOST → 403 Forbidden.

                .anyRequest().authenticated()
                // Everything else requires a valid JWT token.
                // No token → 403. Invalid token → 403.
            )

            .authenticationProvider(authenticationProvider())
            // Register our DaoAuthenticationProvider.

            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
            // Our JwtAuthFilter runs before Spring's default login filter.
            // This ensures JWT authentication happens first on every request.

        return http.build();
    }
}