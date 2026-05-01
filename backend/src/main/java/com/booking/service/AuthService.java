package com.booking.service;

import com.booking.dto.request.LoginRequest;
import com.booking.dto.request.RegisterRequest;
import com.booking.dto.response.AuthResponse;
import com.booking.entity.User;
import com.booking.exception.BusinessException;
import com.booking.repository.UserRepository;
import com.booking.security.JwtUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    // ── REGISTER ──────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        // Step 1: Normalise email (lowercase + no leading/trailing spaces)
        String email = request.getEmail().toLowerCase().trim();

        // Step 2: Check email not already taken
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("An account with this email already exists.");
        }

        // Step 3: Build User entity — password is hashed, never stored plain
        User user = User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .fullName(request.getFullName().trim())
            .role(User.Role.GUEST)
            .active(true)
            .build();

        // Step 4: Save to PostgreSQL
        // userRepository.save() runs:
        //   INSERT INTO users (id, email, password_hash, full_name, role, is_active, ...)
        //   VALUES (gen_random_uuid(), ?, ?, ?, 'GUEST', true, NOW(), NOW())
        User saved = userRepository.save(user);

        // Step 5: Generate JWT so user is logged in immediately after registering
        String token = jwtUtil.generateToken(
            saved.getEmail(),
            saved.getRole().name()
        );

        return AuthResponse.builder()
            .token(token)
            .tokenType("Bearer")
            .expiresIn(86400000L)
            .role(saved.getRole().name())
            .email(saved.getEmail())
            .fullName(saved.getFullName())
            .build();
    }

    // ── LOGIN ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        String email = request.getEmail().toLowerCase().trim();

        // authenticationManager.authenticate() does ALL of this in one call:
        //   1. Loads user from DB by email (via UserDetailsServiceImpl)
        //   2. BCrypt-hashes the submitted password
        //   3. Compares with stored hash
        //   4. Checks isEnabled() and isAccountNonLocked()
        //   5. Throws BadCredentialsException if anything fails → 401
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                email,
                request.getPassword()
            )
        );

        // Authentication passed — load user for the response
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BusinessException("User not found."));

        String token = jwtUtil.generateToken(
            user.getEmail(),
            user.getRole().name()
        );

        return AuthResponse.builder()
            .token(token)
            .tokenType("Bearer")
            .expiresIn(86400000L)
            .role(user.getRole().name())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .build();
    }
}