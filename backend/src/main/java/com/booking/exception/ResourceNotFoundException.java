package com.booking.exception;
// All exception classes go in the "exception" package

// ── IMPORTS ────────────────────────────────────────────────────────────────

import org.springframework.http.HttpStatus;
// HttpStatus is an enum of all HTTP status codes
// HttpStatus.NOT_FOUND = 404
// HttpStatus.BAD_REQUEST = 400
// HttpStatus.UNAUTHORIZED = 401
// etc.

import org.springframework.web.bind.annotation.ResponseStatus;
// @ResponseStatus tells Spring: "if this exception is not caught anywhere,
// use THIS HTTP status code for the response"
// We don't rely on this because GlobalExceptionHandler catches it first
// But it is good documentation and a safety net


// ── CLASS DEFINITION ───────────────────────────────────────────────────────

@ResponseStatus(HttpStatus.NOT_FOUND)
// Documents: this exception represents a 404 situation
// If GlobalExceptionHandler somehow misses it, Spring uses 404 as fallback

public class ResourceNotFoundException extends RuntimeException {
// "extends RuntimeException" = this is an UNCHECKED exception
//
// Java has two kinds of exceptions:
//
// CHECKED exceptions (extend Exception):
//   - Java FORCES you to handle them with try-catch or declare throws
//   - Example: IOException, SQLException
//   - Spring does NOT use these — they pollute every method signature
//
// UNCHECKED exceptions (extend RuntimeException):
//   - You are NOT forced to handle them — they propagate automatically
//   - Example: NullPointerException, IllegalArgumentException
//   - Spring uses these — they travel up the call stack to @RestControllerAdvice
//   - MUCH cleaner code — services don't need try-catch everywhere
//
// This is why all our custom exceptions extend RuntimeException


    // ── CONSTRUCTOR 1: structured message ─────────────────────────────────
    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s not found with %s: '%s'", resource, field, value));
        // "super(...)" calls the RuntimeException constructor with our message
        // String.format works like a template:
        //   %s = insert a String here
        //   resource = "Property", field = "id", value = UUID
        //   Result: "Property not found with id: '550e8400-e29b-41d4-a716-446655440000'"
        //
        // USAGE EXAMPLES:
        //   throw new ResourceNotFoundException("Property", "id", propertyId)
        //   → "Property not found with id: '550e8400...'"
        //
        //   throw new ResourceNotFoundException("User", "email", email)
        //   → "User not found with email: 'alice@example.com'"
        //
        //   throw new ResourceNotFoundException("Booking", "id", bookingId)
        //   → "Booking not found with id: 'abc-123'"
    }


    // ── CONSTRUCTOR 2: free-form message ──────────────────────────────────
    public ResourceNotFoundException(String message) {
        super(message);
        // Sometimes you want a custom message that doesn't fit the template
        // Example: throw new ResourceNotFoundException("No active properties found")
    }
}