package com.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
// LocalDate = date ONLY, no time (2024-06-01)
// Different from LocalDateTime which includes time (2024-06-01T10:30:00)
// For check-in/check-out, we only care about the date — not what hour

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
// ChronoUnit lets us calculate the difference between two dates
import java.util.UUID;

@Entity
@Table(
    name = "bookings",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_bookings_idempotency",
        columnNames = "idempotency_key"
    )
    // Unique constraint on idempotency_key:
    // If the same request is sent twice (network retry),
    // the second INSERT will fail with a unique violation
    // We catch this in the service and return the existing booking
    // This prevents double-charging the customer
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;


    // ── RELATIONSHIP: Many Bookings → One User ─────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    // Creates a foreign key column "user_id" in the bookings table
    // SQL: user_id UUID NOT NULL REFERENCES users(id)
    private User user;
    // The guest who made this booking


    // ── RELATIONSHIP: Many Bookings → One Property ─────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    // Creates "property_id UUID NOT NULL REFERENCES properties(id)"
    private Property property;
    // The property being booked


    // ── DATES ──────────────────────────────────────────────────────────────

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;
    // 2024-06-01
    // LocalDate is perfect here: check-in is a date, not a moment in time
    // No timezone issues — June 1st is June 1st everywhere

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;
    // Must be AFTER checkIn — enforced by CHECK constraint in our SQL:
    // CONSTRAINT chk_valid_dates CHECK (check_out > check_in)


    @Column(name = "guests", nullable = false)
    private Integer guests;
    // Number of guests for this booking
    // Validated in service: guests <= property.maxGuests


    // ── TOTAL PRICE ────────────────────────────────────────────────────────

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;
    // Calculated and locked in at booking time:
    //   totalPrice = pricePerNight × number of nights
    // Stored here so the price is permanent even if the property's
    // current price changes later


    // ── STATUS ─────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;
    // A booking moves through a lifecycle:
    //
    //   PENDING → CONFIRMED → COMPLETED
    //                ↓
    //            CANCELLED
    //
    // New booking: PENDING (payment not yet processed)
    // After payment: CONFIRMED
    // After guest checks out: COMPLETED
    // If cancelled before stay: CANCELLED


    // ── IDEMPOTENCY KEY ────────────────────────────────────────────────────

    @Column(name = "idempotency_key", length = 100)
    // This column is nullable (no "nullable = false") — it's optional
    // A unique constraint is on this column (see @Table above)
    private String idempotencyKey;
    // Scenario where this saves the day:
    // 1. Alice clicks "Book Now" — request sent
    // 2. Alice's phone loses WiFi
    // 3. Alice never got a response — did it work?
    // 4. Alice clicks "Book Now" again — same idempotencyKey sent
    // 5. Server finds existing booking by idempotencyKey
    // 6. Returns the existing booking instead of creating a duplicate
    // 7. Alice is not charged twice


    // ── TIMESTAMPS ─────────────────────────────────────────────────────────

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    // ── ENUM: Booking lifecycle states ────────────────────────────────────

    public enum BookingStatus {
        PENDING,    // just created, waiting for payment confirmation
        CONFIRMED,  // payment successful, booking is active
        COMPLETED,  // the stay has ended
        CANCELLED   // booking was cancelled (by guest, host, or system)
    }


    // ── HELPER METHODS ────────────────────────────────────────────────────
    // These are NOT stored in the database — they are calculated on the fly
    // by calling methods on the Java object

    public long getNights() {
        // Calculate how many nights between check-in and check-out
        return ChronoUnit.DAYS.between(checkIn, checkOut);
        // ChronoUnit.DAYS.between(date1, date2) = date2 - date1 in days
        // Example: June 1 to June 7 → 6 nights
    }

    public boolean isCancellable() {
        // A booking can only be cancelled if it hasn't ended or been cancelled
        return status == BookingStatus.PENDING
            || status == BookingStatus.CONFIRMED;
        // COMPLETED and CANCELLED bookings cannot be cancelled again
        // Used in BookingService.cancelBooking() to reject invalid requests
    }
}