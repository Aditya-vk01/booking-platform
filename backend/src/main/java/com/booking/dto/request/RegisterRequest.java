// dto.request sub-package to keep request DTOs seperate from response DTOs
package com.booking.dto.request;

// IMPORTS
import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


// @Data is a Lombok annotation - a shortcut that gives you:
// @Getter = getFullName(), getEmail(), getPassword()
// @Setter = setFullName(), setEmail(), setPassword()
// @ToString = "RegisterRequest(fullName=Alice, email=alice@gmail.com,...)"
// @EqualsAndHashCode = compare two objects by their field values
// @RequiredArgsConstructor = constructor for final fields
// For DTOs, @Data is perfect - we need all of these

// One annotation replaces ~50 lines of boilerplate code
// DTOs use @Data instead of the @Getter @Setter @Builder @NoArgsConstructor .... combination we used in entities.
// Why the difference?
// Entities: use @Builder because we can create them with Builder pattern in services
// DTOs: Spring/Jackson creates them by calling setters (no Builder needed)
@Data
// This class represents the JSON body of POST /api/auth/register when the browser sends: {"fullName": "Alice Smith", "email": "alice@gmail.com", "password": "SecurePass123"}
// Spring reads that JSON and calls:
// RegisterRequest req = new RegisterRequest()
// req.setFullName("Alice Smith")
// req.setEmail("alice@gmail.com")
// req.setPassword("SecurePass123")
// Then @Valid validates each field against the annotations below
public class RegisterRequest {
  // @NotBlank checks THREE things in one annotation:
  //   1. Not null              → req.fullName != null
  //   2. Not empty string      → req.fullName != ""
  //   3. Not only whitespace   → req.fullName != "   "
  // If any of these are violated: validation fails
  // message = "..." is what gets returned to the client in the error response
  // Without message: Spring returns a generic default message
  @NotBlank(message = "Full name is required")
  // @Size validates the length of a String
  // min = 2: "Al" is the shortest valid name
  // max = 100: prevents someone from submitting a 10,000 character name
  // Both @NotBlank and @Size run together — both must pass
  @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
  private String fullName;

  // First check: is it provided at all?
  @NotBlank(message = "Email is required")
  // @Email checks that the string matches the email pattern:x@y.z
  // IMPORTANT: @Email does NOT check if the email actually exists
  // It only validates the FORMAT
  // "alice@example.com"    → VALID (correct format)
  // "alice@"               → INVALID (no domain)
  // "aliceexample.com"     → INVALID (no @ symbol)
  // "alice@example"        → technically valid by most implementations
  @Email(message = "Please provide a valid email address")
  private String email;

  @NotBlank(message = "Password is required")
  // Minimum 8 characters — basic password strength requirement
  // For stronger validation you could add:
  //   @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9]).{8,}$",
  //            message = "Password must contain at least one uppercase letter and one number")
  // We keep it simple for now
  @Size(min = 8, message = "Password must be atleast 8 characters")
  // This is the PLAIN TEXT password from the client
  // NEVER log this field
  // The service immediately hashes it with BCrypt before doing anything else
  private String password;
}
