package com.booking.dto.response;

import com.booking.entity.Booking.BookingStatus;
// Importing the enum from the Booking entity
// We reuse the same enum in both entity and DTO
// This is fine — the enum is just a type definition, not database logic

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
// Returned by:
//   POST   /api/bookings        → confirmation after creating a booking
//   GET    /api/bookings/my     → list of all user's bookings
//   DELETE /api/bookings/{id}   → the cancelled booking
//
// Real example response (what Alice sees after booking):
// {
//   "id":             "booking-uuid-here",
//   "propertyName":   "Luxury Chelsea Apartment",
//   "propertyCity":   "London",
//   "userFullName":   "Alice Smith",
//   "checkIn":        "2025-06-01",
//   "checkOut":       "2025-06-07",
//   "nights":         6,
//   "guests":         2,
//   "totalPrice":     1134.00,
//   "status":         "CONFIRMED",
//   "createdAt":      "2025-01-15T10:30:45"
// }


    private UUID id;
    // The booking reference ID — "Your booking reference is: abc-123"


    private UUID propertyId;
    // Used if the frontend needs to link back to the property page


    private String propertyName;
    // "Luxury Chelsea Apartment" — shown in booking confirmation email
    // Pulled from: booking.getProperty().getName()


    private String propertyCity;
    // "London" — shown as "Your trip to London"


    private UUID userId;
    // The guest's ID — useful for admin views


    private String userFullName;
    // "Alice Smith" — shown in "Booked by: Alice Smith"


    private LocalDate checkIn;
    // 2025-06-01 — serialized in JSON as "2025-06-01" (ISO format)


    private LocalDate checkOut;
    // 2025-06-07


    private long nights;
    // 6 nights
    // Calculated in BookingService.toResponse() by calling booking.getNights()
    // NOT stored in the database — computed fresh each time
    // booking.getNights() = ChronoUnit.DAYS.between(checkIn, checkOut)


    private Integer guests;
    // 2 guests


    private BigDecimal totalPrice;
    // 1134.00 — pricePerNight ($189) × nights (6)


    private BookingStatus status;
    // CONFIRMED
    // Serializes to JSON as the string "CONFIRMED" (because @Enumerated(STRING))


    private LocalDateTime createdAt;
    // "2025-01-15T10:30:45" — when the booking was made
}