package com.booking.security;

// This class's ONLY job is to load a User from the database by their email.
// It implements Spring Security's UserDetailsService interface.
// Moving this out of SecurityConfig breaks the circular dependency completely.

import com.booking.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.stereotype.Service;

@Service
// @Service registers this class as a Spring Bean automatically.
// Spring will find it during component scanning at startup.
// Any class that needs UserDetailsService will get THIS class injected.
// Because it is @Service (not defined in SecurityConfig),
// it has no dependency on SecurityConfig — cycle broken.
public class UserDetailsServiceImpl implements UserDetailsService {
// "implements UserDetailsService" means this class fulfills the contract:
//   it MUST provide: loadUserByUsername(String username)

    private final UserRepository userRepository;
    // UserRepository has no dependency on SecurityConfig or JwtAuthFilter.
    // This is a clean, one-directional dependency chain:
    //   UserDetailsServiceImpl → UserRepository → PostgreSQL

    // @RequiredArgsConstructor generates:
    // public UserDetailsServiceImpl(UserRepository userRepository) {
    //     this.userRepository = userRepository;
    // }
    // Spring injects UserRepository automatically.
    // No circular references possible here.
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {
        // Spring Security calls this method in TWO situations:
        //
        // Situation 1 — During login:
        //   AuthenticationManager.authenticate() is called
        //   → internally calls loadUserByUsername(email)
        //   → loads Alice from DB
        //   → BCrypt compares passwords
        //
        // Situation 2 — On every protected request (via JwtAuthFilter):
        //   JwtAuthFilter extracts email from the JWT token
        //   → calls loadUserByUsername(email)
        //   → loads Alice from DB to confirm she still exists and is active

        return userRepository.findByEmail(email)
            // findByEmail returns Optional<User>
            // If found: return the User (which implements UserDetails)
            // If not found: throw UsernameNotFoundException

            .orElseThrow(() ->
                new UsernameNotFoundException(
                    "User not found with email: " + email
                )
            );
            // UsernameNotFoundException is a Spring Security exception.
            // Spring Security catches it and translates it to a 401 response.
            // Our GlobalExceptionHandler's BadCredentialsException handler
            // returns: "Invalid email or password"
    }
}