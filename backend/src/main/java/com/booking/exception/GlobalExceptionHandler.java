package com.booking.exception;

// ── IMPORTS ────────────────────────────────────────────────────────────────

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// ResponseEntity<T> wraps a response body AND lets you set the HTTP status code
// ResponseEntity.status(404).body(errorMap) → 404 status + JSON body

import org.springframework.security.access.AccessDeniedException;
// Thrown by Spring Security when @PreAuthorize("hasRole('ADMIN')") blocks access
// A logged-in GUEST trying to access /api/admin gets this → 403

import org.springframework.security.authentication.BadCredentialsException;
// Thrown by Spring Security when login email/password is wrong → 401

import org.springframework.security.authentication.DisabledException;
// Thrown when user.isEnabled() returns false (account deactivated) → 401

import org.springframework.validation.FieldError;
// Represents one validation failure: which field failed and why
// Used to extract field-level error messages from @Valid failures

import org.springframework.web.bind.MethodArgumentNotValidException;
// Thrown when @Valid on a @RequestBody DTO fails validation
// Contains ALL the field errors — one per failed annotation

import org.springframework.web.bind.annotation.ExceptionHandler;
// Marks a method as a handler for a specific exception type
// @ExceptionHandler(ResourceNotFoundException.class) = handle this exception type

import org.springframework.web.bind.annotation.RestControllerAdvice;
// @RestControllerAdvice = @ControllerAdvice + @ResponseBody
// @ControllerAdvice: this class wraps all @RestController classes globally
// @ResponseBody: all return values are serialized to JSON automatically
//
// HOW IT WORKS:
// Spring wraps ALL controllers with this class
// When ANY controller throws an exception:
//   1. Spring intercepts it
//   2. Finds the matching @ExceptionHandler method in this class
//   3. Calls that method instead of crashing
//   4. Returns the method's result as the HTTP response

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


// ── CLASS ANNOTATION ───────────────────────────────────────────────────────

@RestControllerAdvice
// This single annotation:
// 1. Tells Spring: "apply this class to ALL controllers"
// 2. Makes all return values auto-convert to JSON
// 3. Enables @ExceptionHandler methods inside

public class GlobalExceptionHandler {
// You only need ONE of these in your entire application
// It handles exceptions from ALL controllers automatically
// No need to add try-catch in any controller or service


    // ══════════════════════════════════════════════════════════════════════
    // HANDLER 1: Validation failures
    // When: @Valid on @RequestBody finds errors
    // HTTP: 400 Bad Request
    // Example trigger: POST /api/auth/register with blank email
    // ══════════════════════════════════════════════════════════════════════

    @ExceptionHandler(MethodArgumentNotValidException.class)
    // @ExceptionHandler(SomeException.class) = "when THIS exception type is thrown
    // anywhere, run THIS method"
    // Spring matches the exception type to find the right handler
    // More specific types are matched first (most specific to least specific)

    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        // "ex" is the exception object — it contains all the validation failures

        // Step 1: Collect all the field errors into a map
        Map<String, String> fieldErrors = new HashMap<>();
        // HashMap = a key-value store (like a dictionary in Python)
        // Key = field name ("email"), Value = error message ("Email is required")

        ex.getBindingResult()
        // getBindingResult() = the object containing all validation results
          .getAllErrors()
        // getAllErrors() = list of ALL failed validations
          .forEach(error -> {
            // forEach = loop through each error
            // "error -> { ... }" = lambda (anonymous function) that runs for each error

            String fieldName = ((FieldError) error).getField();
            // Cast to FieldError to access the field name
            // ((FieldError) error) = "treat this error as a FieldError type"
            // .getField() = "email", "password", "pricePerNight", etc.

            String errorMessage = error.getDefaultMessage();
            // The message from the annotation:
            //   @NotBlank(message = "Email is required") → "Email is required"
            //   @Size(min=8, message = "Min 8 chars")    → "Min 8 chars"

            fieldErrors.put(fieldName, errorMessage);
            // Store: "email" → "Email is required"
        });

        // Step 2: Build and return the error response
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            // Set HTTP status to 400
            .body(buildError(
                "Validation failed",
                HttpStatus.BAD_REQUEST,
                fieldErrors
                // Pass fieldErrors map as the "details" — it shows per-field errors
            ));
        // Client receives:
        // {
        //   "timestamp": "2025-01-15T10:30:45",
        //   "status": 400,
        //   "error": "Bad Request",
        //   "message": "Validation failed",
        //   "details": {
        //     "email": "Please provide a valid email address",
        //     "password": "Password must be at least 8 characters",
        //     "fullName": "Full name is required"
        //   }
        // }
    }


    // ══════════════════════════════════════════════════════════════════════
    // HANDLER 2: Resource not found (404)
    // When: Service calls findById().orElseThrow() and the entity doesn't exist
    // HTTP: 404 Not Found
    // Example trigger: GET /api/properties/nonexistent-uuid
    // ══════════════════════════════════════════════════════════════════════

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex) {

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            // 404
            .body(buildError(ex.getMessage(), HttpStatus.NOT_FOUND, null));
            // ex.getMessage() = the message we passed to the constructor:
            //   "Property not found with id: '550e8400...'"
            // null = no "details" field (only one message needed for 404)
        // Client receives:
        // {
        //   "timestamp": "2025-01-15T10:30:45",
        //   "status": 404,
        //   "error": "Not Found",
        //   "message": "Property not found with id: '550e8400-e29b-41d4-...'"
        // }
    }


    // ══════════════════════════════════════════════════════════════════════
    // HANDLER 3: Business rule violations (400)
    // When: Service throws BusinessException for rule violations
    // HTTP: 400 Bad Request
    // Example trigger: booking dates conflict, email already registered
    // ══════════════════════════════════════════════════════════════════════

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(
            BusinessException ex) {

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            // 400
            .body(buildError(ex.getMessage(), HttpStatus.BAD_REQUEST, null));
        // Client receives:
        // {
        //   "timestamp": "2025-01-15T10:30:45",
        //   "status": 400,
        //   "error": "Bad Request",
        //   "message": "This property is not available for the selected dates."
        // }
    }


    // ══════════════════════════════════════════════════════════════════════
    // HANDLER 4: Wrong email or password (401)
    // When: Spring Security's authenticate() fails during login
    // HTTP: 401 Unauthorized
    // Example trigger: POST /api/auth/login with wrong password
    // ══════════════════════════════════════════════════════════════════════

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(
            BadCredentialsException ex) {

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            // 401 — not authenticated
            .body(buildError(
                "Invalid email or password",
                // IMPORTANT: we say "email or password" — not WHICH one is wrong
                // WHY? Security best practice.
                // If we say "email not found", an attacker learns valid emails
                // If we say "wrong password", they know the email is correct
                // Vague message prevents "username enumeration" attacks
                HttpStatus.UNAUTHORIZED,
                null
            ));
    }


    // ══════════════════════════════════════════════════════════════════════
    // HANDLER 5: Deactivated account (401)
    // When: User tries to log in but their account is disabled
    // HTTP: 401 Unauthorized
    // Example trigger: admin set user.active = false
    // ══════════════════════════════════════════════════════════════════════

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDisabled(
            DisabledException ex) {

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(buildError(
                "Your account has been deactivated. Please contact support.",
                HttpStatus.UNAUTHORIZED,
                null
            ));
    }


    // ══════════════════════════════════════════════════════════════════════
    // HANDLER 6: Logged in but wrong role (403)
    // When: @PreAuthorize("hasRole('ADMIN')") blocks a non-admin
    // HTTP: 403 Forbidden
    // Example trigger: GUEST tries to access /api/admin/dashboard
    // ══════════════════════════════════════════════════════════════════════

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex) {

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            // 403 — authenticated but not authorized
            // KEY DIFFERENCE:
            //   401 = "Who are you?" — not authenticated
            //   403 = "I know who you are, but you can't do this." — authenticated but unauthorized
            .body(buildError(
                "You do not have permission to perform this action.",
                HttpStatus.FORBIDDEN,
                null
            ));
    }


    // ══════════════════════════════════════════════════════════════════════
    // HANDLER 7: Catch-all for unexpected errors (500)
    // When: Anything not caught by the handlers above
    // HTTP: 500 Internal Server Error
    // Example trigger: database is down, null pointer, out of memory
    //
    // THIS IS THE MOST IMPORTANT HANDLER FOR SECURITY
    // Without this, unexpected errors would leak stack traces to the client
    // ══════════════════════════════════════════════════════════════════════

    @ExceptionHandler(Exception.class)
    // Exception.class = the ROOT of all exceptions in Java
    // This catches EVERYTHING that wasn't caught by a more specific handler above
    // Spring matches handlers from most specific to least specific
    // So ResourceNotFoundException is matched FIRST, not here
    // Only truly unexpected exceptions reach here

    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {

        // CRITICAL: Log the real error for developers to investigate
        // In production, you would use a proper logger like:
        //   log.error("Unexpected error occurred", ex);
        // And it would go to your logging system (Datadog, CloudWatch, etc.)
        // For now, we print to console:
        System.err.println(
            "[ERROR] Unexpected exception: " + ex.getClass().getSimpleName()
            + " - " + ex.getMessage()
        );
        ex.printStackTrace();
        // ex.printStackTrace() prints the full stack trace to the server console
        // ONLY the server can see this — the client sees the generic message below

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            // 500 — server broke, not the client's fault
            .body(buildError(
                "An unexpected error occurred. Please try again later.",
                // NEVER send the real error message to the client
                // ex.getMessage() might say: "Connection to postgres refused"
                // That tells an attacker your database is down — security risk
                // Instead: generic message to client, real message in server logs
                HttpStatus.INTERNAL_SERVER_ERROR,
                null
            ));
    }


    // ══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPER: Build the consistent error response structure
    // Every error response in our API has the EXACT same shape
    // This method ensures that — called by all handlers above
    // ══════════════════════════════════════════════════════════════════════

    private Map<String, Object> buildError(
            String message,
            HttpStatus status,
            Object details) {
        // Parameters:
        //   message  = human-readable error description
        //   status   = the HttpStatus enum value (HttpStatus.NOT_FOUND, etc.)
        //   details  = optional extra info (field errors map, or null)

        Map<String, Object> errorBody = new HashMap<>();
        // HashMap<String, Object>:
        //   Key type = String  (the JSON field names)
        //   Value type = Object (can hold String, Integer, Map, etc.)

        errorBody.put("timestamp", LocalDateTime.now().toString());
        // "timestamp": "2025-01-15T10:30:45.123"
        // Useful for correlating errors in logs with client reports
        // "The error happened at 10:30 on Jan 15 — check the server logs for that time"

        errorBody.put("status", status.value());
        // "status": 404
        // status.value() converts HttpStatus.NOT_FOUND → the integer 404
        // Putting the number in the body is convenient for clients
        // (they can check response.body.status instead of response.status)

        errorBody.put("error", status.getReasonPhrase());
        // "error": "Not Found"
        // status.getReasonPhrase() converts HttpStatus.NOT_FOUND → "Not Found"
        // HttpStatus.BAD_REQUEST → "Bad Request"
        // HttpStatus.UNAUTHORIZED → "Unauthorized"
        // HttpStatus.INTERNAL_SERVER_ERROR → "Internal Server Error"

        errorBody.put("message", message);
        // "message": "Property not found with id: '550e8400...'"
        // The human-readable explanation

        if (details != null) {
            errorBody.put("details", details);
            // "details": { "email": "Email is required", "password": "Min 8 chars" }
            // Only added when there is extra information (validation errors)
            // For simple 404/500 errors, details is null and this field is omitted
        }

        return errorBody;
        // This map is converted to JSON by Spring automatically:
        // {
        //   "timestamp": "2025-01-15T10:30:45.123",
        //   "status": 404,
        //   "error": "Not Found",
        //   "message": "Property not found with id: '550e8400-e29b-41d4-a716-446655440000'"
        // }
    }
}