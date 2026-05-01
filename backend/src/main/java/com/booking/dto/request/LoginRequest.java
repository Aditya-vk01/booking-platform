package com.booking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// The JSON body for POST /api/auth/login
// Simplest DTO in the project - just two fields
@Data
public class LoginRequest {
  @NotBlank(message = "Email is required")
  @Email(message = "Please provide a valid email address")
  // The user's registered email address
  private String email;

  @NotBlank(message = "Password is required")
  // Plain text password typed by the user
  //
  // What happens to it in AuthService.login():
  //   1. Spring Security loads the stored User by email
  //   2. Calls passwordEncoder.matches(request.getPassword(), user.getPasswordHash())
  //   3. BCrypt internally:
  //      - Extracts the salt from the stored hash
  //      - Hashes the plain text password WITH that same salt
  //      - Compares the result with the stored hash
  //   4. Returns true/false
  // The plain text password is NEVER stored — it only lives in memory for the duration of this comparison
  private String password;
}
