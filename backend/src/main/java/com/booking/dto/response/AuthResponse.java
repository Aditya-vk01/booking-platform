package com.booking.dto.response;
// Response DTOs have no validation annotations
// They are outgoing — WE control the data, not the client

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
// @Builder because we create AuthResponse in AuthService like this:
//   return AuthResponse.builder()
//       .token(token)
//       .tokenType("Bearer")
//       .expiresIn(86400000L)
//       .role(user.getRole().name())
//       .email(user.getEmail())
//       .fullName(user.getFullName())
//       .build();
// This is the Builder pattern — creates objects cleanly without a
// constructor with 6 positional parameters (easy to mix up the order)

@NoArgsConstructor
// Required by Jackson (the JSON library) to deserialize responses
// Jackson calls the empty constructor first, then sets fields

@AllArgsConstructor
// Required by @Builder to work correctly
public class AuthResponse {

    private String token;
    // The JWT token — the most important field
    // Client stores this (usually in localStorage or a secure cookie)
    // Looks like: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZUBleGFtcGxlLmNvbSJ9.xyz"
    // Three parts separated by dots: header.payload.signature


    private String tokenType;
    // Always "Bearer"
    // This is an HTTP standard convention for JWT tokens
    // Client uses it like:
    //   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...


    private long expiresIn;
    // How many milliseconds until the token expires
    // We set 86400000 = 24 hours (24 × 60 × 60 × 1000)
    // The client can use this to know WHEN to re-authenticate
    // After expiry, the client sends the token, server rejects it,
    // client must send the user to the login page


    private String role;
    // "GUEST", "HOST", or "ADMIN"
    // The frontend uses this to show or hide UI elements:
    //   if (role === 'HOST') showCreateListingButton()
    //   if (role === 'ADMIN') showAdminDashboard()


    private String email;
    // Shown in the UI as the logged-in user's email


    private String fullName;
    // Shown as the display name in the navbar/profile
}