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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;


    // ── RELATIONSHIP: One Payment → One Booking ────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;
    // WHY a separate Payment table?
    //
    // Option 1: Store payment info directly on Booking
    //   - Simple but inflexible
    //   - What if payment fails and needs to be retried?
    //   - What if a refund is processed later?
    //   - You lose the history of what happened
    //
    // Option 2: Separate Payment table (what we do)
    //   - Full audit trail of every transaction
    //   - One booking can have multiple payment records
    //     (e.g., initial charge FAILED, retry COMPLETED)
    //   - Matches how real payment systems work (Stripe, PayPal)


    // ── AMOUNT ─────────────────────────────────────────────────────────────

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    // Same amount as booking.totalPrice at time of payment
    // Stored separately in case the booking total is ever adjusted


    // ── STATUS ─────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;
    // Lifecycle:
    //   PENDING   → payment record created, processing not started
    //   COMPLETED → money successfully taken
    //   FAILED    → payment declined (wrong card, insufficient funds)
    //   REFUNDED  → money returned to customer (after cancellation)


    // ── PAYMENT METHOD ─────────────────────────────────────────────────────

    @Column(name = "method", nullable = false, length = 50)
    @Builder.Default
    private String method = "CREDIT_CARD";
    // In our platform this is simulated (no real payment gateway)
    // In production: would be CREDIT_CARD, DEBIT_CARD, PAYPAL, etc.
    // Could also store last 4 digits of card for receipt purposes


    // ── PROCESSED AT ───────────────────────────────────────────────────────

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    // NO "nullable = false" — this field starts as null
    // It only gets set when the payment actually completes or fails
    //
    // Timeline:
    //   10:00:00 → Payment created, processedAt = null, status = PENDING
    //   10:00:02 → Payment gateway responds, processedAt = 10:00:02, status = COMPLETED
    //
    // In our BookingService, we set this immediately (simulated payment):
    //   .processedAt(LocalDateTime.now())


    // ── CREATED AT ─────────────────────────────────────────────────────────

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
    // When the payment record was created (before processing)
    // Different from processedAt (when it was processed)


    // ── ENUM ───────────────────────────────────────────────────────────────

    public enum PaymentStatus {
        PENDING,    // record created, awaiting processing
        COMPLETED,  // successfully charged
        FAILED,     // charge declined or error occurred
        REFUNDED    // money returned to the customer
    }
}