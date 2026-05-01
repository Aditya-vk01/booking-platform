package com.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "reviews",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_review_per_booking",
        columnNames = {"user_id", "booking_id"}
        // COMPOSITE unique constraint: both columns together must be unique
        // A user can only leave ONE review per booking
        // This prevents a user spamming multiple reviews for the same stay
        // They CAN review different bookings at different properties
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;


    // ── THREE RELATIONSHIPS ────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    // Who wrote this review


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
    // Which property is being reviewed
    // When a review is saved, the PostgreSQL trigger automatically
    // recalculates property.avgRating and property.reviewCount


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;
    // Links the review to the specific stay
    // WHY: this proves the reviewer actually stayed at the property
    // In ReviewService (Phase 7), we check:
    //   - booking.user == current user (they made this booking)
    //   - booking.status == COMPLETED (the stay happened)
    // If either check fails, the review is rejected


    // ── RATING ─────────────────────────────────────────────────────────────

    @Column(name = "rating", nullable = false)
    private Integer rating;
    // 1 to 5 stars
    // DB enforcement: CHECK (rating BETWEEN 1 AND 5) in our SQL
    // DTO enforcement: @Min(1) @Max(5) on the request DTO
    // Both layers protect against invalid data


    // ── COMMENT ────────────────────────────────────────────────────────────

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;
    // Optional — a user can leave just a star rating without writing text
    // No "nullable = false" here means this column CAN be null in the DB


    // ── TIMESTAMP ──────────────────────────────────────────────────────────

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
    // Reviews cannot be updated after submission (no @UpdateTimestamp)
    // If a user wants to change their review, they would need to
    // delete and resubmit — but that is a product decision
}