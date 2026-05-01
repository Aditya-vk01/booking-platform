package com.booking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
// 400 Bad Request = the client's request was valid in format
// but violates a business rule
//
// DIFFERENCE between 400 causes:
//   @Valid fails → 400 via MethodArgumentNotValidException (format invalid)
//   BusinessException → 400 (format valid, but rule violated)
//
// Example:
//   "email": "not-an-email"          → @Valid catches this → 400
//   "email": "alice@example.com"     → valid format ✓
//     but this email is already registered → BusinessException → 400

public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
        // Simple: just pass a descriptive message
        //
        // USAGE EXAMPLES in services:
        //
        // In AuthService.register():
        //   if (userRepository.existsByEmail(email)) {
        //     throw new BusinessException("An account with this email already exists.");
        //   }
        //
        // In BookingService.createBooking():
        //   if (hasConflict) {
        //     throw new BusinessException(
        //       "This property is not available for the selected dates."
        //     );
        //   }
        //
        // In BookingService.cancelBooking():
        //   if (!booking.isCancellable()) {
        //     throw new BusinessException(
        //       "Only PENDING or CONFIRMED bookings can be cancelled."
        //     );
        //   }
        //
        // In PropertyService.createProperty():
        //   if (user.getRole() != Role.HOST && user.getRole() != Role.ADMIN) {
        //     throw new BusinessException(
        //       "Only HOST or ADMIN users can create property listings."
        //     );
        //   }
    }
}