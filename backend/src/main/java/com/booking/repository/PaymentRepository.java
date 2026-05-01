package com.booking.repository;

import com.booking.entity.Payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;


@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {


    // ── DERIVED QUERY: find payment for a booking ─────────────────────────
    Optional<Payment> findByBookingId(UUID bookingId);
    // Spring generates:
    //   SELECT * FROM payments WHERE booking_id = ?
    //
    // Returns Optional<Payment> because a booking might not have a payment yet
    // (e.g., if the system crashed between saving booking and saving payment)
    //
    // USED IN: Future refund processing:
    //
    //   Payment payment = paymentRepository.findByBookingId(bookingId)
    //     .orElseThrow(() -> new ResourceNotFoundException("Payment", "bookingId", bookingId));
    //   payment.setStatus(PaymentStatus.REFUNDED);
    //   paymentRepository.save(payment);
    //
    // ALSO USED IN: Showing payment status on the booking detail page:
    //   GET /api/bookings/{id} → include payment status in the response
}