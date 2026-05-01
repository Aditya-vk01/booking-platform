package com.booking.repository;

import com.booking.entity.Booking;
import com.booking.entity.Booking.BookingStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {


    // ── DERIVED QUERY: user's booking history ─────────────────────────────
    Page<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    // Spring generates:
    //   SELECT * FROM bookings
    //   WHERE user_id = ?
    //   ORDER BY created_at DESC
    //   LIMIT ? OFFSET ?
    //
    // Method name breakdown:
    //   "findBy"          → SELECT * ... WHERE
    //   "UserId"          → WHERE user_id = ?
    //                       (Booking.user.id → follows the @ManyToOne relationship)
    //   "OrderBy"         → ORDER BY
    //   "CreatedAt"       → created_at column
    //   "Desc"            → DESC (newest first)
    //
    // Returns Page<Booking> for pagination
    // The controller passes Pageable from URL query params:
    //   GET /api/bookings/my?page=0&size=10
    //
    // USED IN: BookingService.getUserBookings(userId, page, size)


    // ── DERIVED QUERY: all bookings for a property ─────────────────────────
    Page<Booking> findByPropertyIdOrderByCreatedAtDesc(
            UUID propertyId, Pageable pageable);
    // Spring generates:
    //   SELECT * FROM bookings WHERE property_id = ? ORDER BY created_at DESC
    //
    // USED IN: Future admin/host dashboard to see who has booked their property


    // ── DERIVED QUERY: idempotency check ──────────────────────────────────
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
    // Spring generates:
    //   SELECT * FROM bookings WHERE idempotency_key = ?
    //
    // USED IN: BookingService.createBooking() for duplicate detection:
    //
    //   if (request.getIdempotencyKey() != null) {
    //     Optional<Booking> existing =
    //       bookingRepository.findByIdempotencyKey(request.getIdempotencyKey());
    //     if (existing.isPresent()) {
    //       return toResponse(existing.get()); // return same result — no duplicate!
    //     }
    //   }
    //
    // Scenario where this saves the day:
    //   1. Alice clicks "Book Now" — request sent with key "abc-123"
    //   2. Server processes booking — saves successfully
    //   3. Network drops BEFORE response reaches Alice
    //   4. Alice's app retries with the SAME key "abc-123"
    //   5. findByIdempotencyKey("abc-123") finds the existing booking
    //   6. Returns it immediately — NO new booking created
    //   7. Alice is NOT charged twice


    // ── CUSTOM QUERY: conflict detection ──────────────────────────────────
    //
    // The most critical business logic query.
    // Called AFTER acquiring the property lock to safely check availability.
    //
    // Returns true if there IS a conflict (property is NOT available)
    // Returns false if there is NO conflict (property IS available)

    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.property.id = :propertyId
          AND b.status IN :statuses
          AND b.checkIn  < :checkOut
          AND b.checkOut > :checkIn
        """)
    // ── LINE BY LINE EXPLANATION ──────────────────────────────────────────
    //
    // SELECT COUNT(b) > 0
    //   → We don't need the actual bookings — just whether ANY exist
    //   → COUNT > 0 returns a boolean directly in JPQL
    //   → true = at least one conflicting booking exists = NOT available
    //   → false = no conflicts = available
    //
    // FROM Booking b
    //   → Look in the bookings table
    //
    // WHERE b.property.id = :propertyId
    //   → "b.property.id" navigates the @ManyToOne relationship:
    //     Booking → property field (Property entity) → id field
    //   → In SQL: WHERE property_id = ?
    //   → We check THIS specific property only
    //
    // AND b.status IN :statuses
    //   → :statuses will be: [CONFIRMED, PENDING]
    //   → We only care about ACTIVE bookings
    //   → CANCELLED bookings don't block availability
    //   → Example: if June 1-7 was booked but cancelled → still available!
    //
    // AND b.checkIn < :checkOut
    //   → Existing booking starts BEFORE our requested end date
    //
    // AND b.checkOut > :checkIn
    //   → Existing booking ends AFTER our requested start date
    //
    // BOTH conditions must be true for an overlap:
    //   Our dates:     |===== June 1–7 =====|
    //   Their booking: starts(June 3) < our end(June 7)? YES ✓
    //                  ends(June 9) > our start(June 1)? YES ✓
    //   → CONFLICT → property is NOT available
    //
    // A booking ending exactly when ours starts is NOT a conflict:
    //   Their booking: ends June 1 > our start June 1? NO ✗ (equal, not greater)
    //   → NOT a conflict → previous guest checked out, we can check in same day
    //   → This is correct: checkout and checkin on the same day is allowed

    boolean existsConflictingBooking(
        @Param("propertyId") UUID propertyId,
        @Param("checkIn")    LocalDate checkIn,
        @Param("checkOut")   LocalDate checkOut,
        @Param("statuses")   List<BookingStatus> statuses
    );
    // USED IN: BookingService.processNewBooking():
    //
    //   boolean hasConflict = bookingRepository.existsConflictingBooking(
    //     property.getId(),
    //     request.getCheckIn(),
    //     request.getCheckOut(),
    //     List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING)
    //   );
    //
    //   if (hasConflict) {
    //     throw new BusinessException(
    //       "This property is not available for the selected dates."
    //     );
    //   }
    //
    //   // If we reach here, property is available → create booking
}