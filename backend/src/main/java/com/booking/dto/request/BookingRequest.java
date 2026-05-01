package com.booking.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class BookingRequest {
// The JSON body for POST /api/bookings
//
// What the browser sends:
// {
//   "propertyId":     "550e8400-e29b-41d4-a716-446655440000",
//   "checkIn":        "2025-06-01",
//   "checkOut":       "2025-06-07",
//   "guests":         2,
//   "idempotencyKey": "client-generated-uuid-here"
// }


    @NotNull(message = "Property ID is required")
    private UUID propertyId;
    // UUID type: Spring automatically converts the string "550e8400-..."
    // from the JSON body into a proper UUID object
    // If the string is not a valid UUID format → 400 Bad Request automatically


    @NotNull(message = "Check-in date is required")
    @FutureOrPresent(message = "Check-in date cannot be in the past")
    // @FutureOrPresent: the date must be TODAY or any future date
    // "2020-01-01" → fails (past date)
    // "2025-06-01" → passes (future date)
    // Today's date → passes (present = OK)
    // Spring converts the JSON string "2025-06-01" to LocalDate automatically
    private LocalDate checkIn;
    // LocalDate = date only (no time): 2025-06-01
    // Perfect for check-in — we care about the DATE, not the hour


    @NotNull(message = "Check-out date is required")
    @Future(message = "Check-out date must be in the future")
    // @Future: the date must be STRICTLY after today (today is not allowed)
    // WHY stricter than checkIn?
    //   - You CAN check in today (same-day booking)
    //   - You CANNOT check out today (that would be 0 nights)
    // We also validate checkOut > checkIn in BookingService as extra safety
    private LocalDate checkOut;


    @NotNull(message = "Number of guests is required")
    @Min(value = 1, message = "At least 1 guest is required")
    @Max(value = 50, message = "Cannot exceed 50 guests")
    private Integer guests;


    private String idempotencyKey;
    // NO validation annotations = this field is completely optional
    // The client SHOULD send this but is not required to
    //
    // HOW TO USE IT (client side):
    //   1. Before clicking "Book Now", generate a UUID:
    //      const key = crypto.randomUUID() // generates "550e8400-e29b-..."
    //   2. Include it in the request body
    //   3. If the request fails (network error), retry with the SAME key
    //   4. The server finds the existing booking by this key and returns it
    //   5. Customer is not charged twice
    //
    // IN BookingService:
    //   if (idempotencyKey != null) {
    //     Optional<Booking> existing = bookingRepo.findByIdempotencyKey(key);
    //     if (existing.isPresent()) return toResponse(existing.get()); // no duplicate
    //   }
}