package com.booking.repository;

import com.booking.entity.Review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;


@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {


    // ── DERIVED QUERY: all reviews for a property ─────────────────────────
    Page<Review> findByPropertyIdOrderByCreatedAtDesc(
            UUID propertyId, Pageable pageable);
    // Spring generates:
    //   SELECT * FROM reviews
    //   WHERE property_id = ?
    //   ORDER BY created_at DESC
    //   LIMIT ? OFFSET ?
    //
    // Shows reviews for a property listing page, newest first
    // Paginated so we don't load all 500 reviews at once
    //
    // USED IN: ReviewService.getPropertyReviews(propertyId, page, size)
    // Future endpoint: GET /api/reviews/property/{propertyId}


    // ── DERIVED QUERY: has user already reviewed this booking? ────────────
    boolean existsByUserIdAndBookingId(UUID userId, UUID bookingId);
    // Spring generates:
    //   SELECT COUNT(*) > 0 FROM reviews
    //   WHERE user_id = ? AND booking_id = ?
    //
    // Method name breakdown:
    //   "existsBy"   → SELECT COUNT(*) > 0
    //   "UserId"     → WHERE user_id = ?
    //   "And"        → AND
    //   "BookingId"  → booking_id = ?
    //
    // USED IN: Future ReviewService.createReview() to prevent duplicate reviews:
    //
    //   if (reviewRepository.existsByUserIdAndBookingId(userId, bookingId)) {
    //     throw new BusinessException(
    //       "You have already reviewed this booking."
    //     );
    //   }
    //
    // The DB also enforces this with UNIQUE constraint:
    //   CONSTRAINT uk_review_per_booking UNIQUE (user_id, booking_id)
    // But checking first in code gives a better error message
}