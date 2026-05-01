package com.booking.service;

import com.booking.config.RabbitMQConfig;
import com.booking.dto.request.BookingRequest;
import com.booking.dto.response.BookingResponse;
import com.booking.entity.Booking;
import com.booking.entity.Booking.BookingStatus;
import com.booking.entity.Payment;
import com.booking.entity.Property;
import com.booking.entity.User;
import com.booking.exception.BusinessException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.BookingRepository;
import com.booking.repository.PaymentRepository;
import com.booking.repository.PropertyRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository  bookingRepository;
    private final PropertyRepository propertyRepository;
    private final PaymentRepository  paymentRepository;
    private final RabbitTemplate     rabbitTemplate;
    private final CacheManager       cacheManager;

    // ── CREATE BOOKING ────────────────────────────────────────────────────

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    // REPEATABLE_READ: this transaction sees a consistent snapshot.
    // Combined with SELECT FOR UPDATE (in findByIdWithLock),
    // this prevents the double-booking race condition completely.
    public BookingResponse createBooking(BookingRequest request, User currentUser) {

        // Step 1: Idempotency — return existing booking if same key was sent before
        if (request.getIdempotencyKey() != null
                && !request.getIdempotencyKey().isBlank()) {

            return bookingRepository
                .findByIdempotencyKey(request.getIdempotencyKey())
                .map(this::toResponse)
                // If found → return it immediately, no duplicate created
                .orElseGet(() -> processNewBooking(request, currentUser));
                // If not found → process as a new booking
        }

        return processNewBooking(request, currentUser);
    }

    private BookingResponse processNewBooking(
            BookingRequest request, User currentUser) {

        // Step 2: Validate dates at service level (belt-and-suspenders)
        if (!request.getCheckOut().isAfter(request.getCheckIn())) {
            throw new BusinessException(
                "Check-out date must be after check-in date.");
        }

        // Step 3: Lock the property row with SELECT FOR UPDATE.
        // Any other transaction trying to book this property will WAIT here.
        // This is the key line that prevents double-booking.
        Property property = propertyRepository
            .findByIdWithLock(request.getPropertyId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Property", "id", request.getPropertyId()));

        if (!property.isActive()) {
            throw new BusinessException(
                "This property is not available for booking.");
        }

        if (property.getMaxGuests() < request.getGuests()) {
            throw new BusinessException(String.format(
                "This property accommodates a maximum of %d guests.",
                property.getMaxGuests()));
        }

        // Step 4: Check for date conflicts (safe — we hold the lock)
        boolean hasConflict = bookingRepository.existsConflictingBooking(
            property.getId(),
            request.getCheckIn(),
            request.getCheckOut(),
            List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING)
        );

        if (hasConflict) {
            throw new BusinessException(
                "This property is not available for the selected dates.");
        }

        // Step 5: Calculate total price
        long nights = ChronoUnit.DAYS.between(
            request.getCheckIn(), request.getCheckOut());

        BigDecimal total = property.getPricePerNight()
            .multiply(BigDecimal.valueOf(nights));

        // Step 6: Save the booking
        Booking booking = Booking.builder()
            .user(currentUser)
            .property(property)
            .checkIn(request.getCheckIn())
            .checkOut(request.getCheckOut())
            .guests(request.getGuests())
            .totalPrice(total)
            .status(BookingStatus.CONFIRMED)
            .idempotencyKey(request.getIdempotencyKey())
            .build();

        Booking saved = bookingRepository.save(booking);

        // Step 7: Simulate payment (real system would call Stripe/PayPal here)
        Payment payment = Payment.builder()
            .booking(saved)
            .amount(total)
            .status(Payment.PaymentStatus.COMPLETED)
            .method("CREDIT_CARD")
            .processedAt(LocalDateTime.now())
            .build();

        paymentRepository.save(payment);

        // Step 8: Clear search cache — this property is now booked for these dates.
        // Next search will re-query PostgreSQL and exclude this property correctly.
        var cache = cacheManager.getCache("property-search");
        if (cache != null) cache.clear();

        // Step 9: Publish async message to RabbitMQ.
        // Email notification is sent by a consumer in the background.
        // This returns immediately — user does not wait for the email.
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.BOOKING_EXCHANGE,
            RabbitMQConfig.ROUTING_KEY_CONFIRMED,
            saved.getId().toString()
        );

        return toResponse(saved);
        // @Transactional commits here → FOR UPDATE lock on property is released.
    }

    // ── GET USER BOOKINGS ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<BookingResponse> getUserBookings(
            UUID userId, int page, int size) {

        Pageable pageable = PageRequest.of(
            page, size,
            Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return bookingRepository
            .findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(this::toResponse);
    }

    // ── CANCEL BOOKING ────────────────────────────────────────────────────

    @Transactional
    public BookingResponse cancelBooking(UUID bookingId, UUID requestingUserId) {

        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() ->
                new ResourceNotFoundException("Booking", "id", bookingId));

        if (!booking.getUser().getId().equals(requestingUserId)) {
            throw new BusinessException(
                "You can only cancel your own bookings.");
        }

        if (!booking.isCancellable()) {
            throw new BusinessException(
                "Only PENDING or CONFIRMED bookings can be cancelled.");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);

        // Property is available again — clear the cache
        var cache = cacheManager.getCache("property-search");
        if (cache != null) cache.clear();

        rabbitTemplate.convertAndSend(
            RabbitMQConfig.BOOKING_EXCHANGE,
            RabbitMQConfig.ROUTING_KEY_CANCELLED,
            saved.getId().toString()
        );

        return toResponse(saved);
    }

    // ── MAPPER: Entity → Response DTO ─────────────────────────────────────

    private BookingResponse toResponse(Booking b) {
        return BookingResponse.builder()
            .id(b.getId())
            .propertyId(b.getProperty().getId())
            .propertyName(b.getProperty().getName())
            .propertyCity(b.getProperty().getCity())
            .userId(b.getUser().getId())
            .userFullName(b.getUser().getFullName())
            .checkIn(b.getCheckIn())
            .checkOut(b.getCheckOut())
            .nights(b.getNights())
            .guests(b.getGuests())
            .totalPrice(b.getTotalPrice())
            .status(b.getStatus())
            .createdAt(b.getCreatedAt())
            .build();
    }
}