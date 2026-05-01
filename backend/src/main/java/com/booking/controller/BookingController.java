package com.booking.controller;

import com.booking.dto.request.BookingRequest;
import com.booking.dto.response.BookingResponse;
import com.booking.entity.User;
import com.booking.service.BookingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // POST /api/bookings
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    // isAuthenticated() = any logged-in user (GUEST, HOST, or ADMIN)
    public BookingResponse createBooking(
            @RequestBody @Valid BookingRequest request,
            @AuthenticationPrincipal User currentUser) {
        return bookingService.createBooking(request, currentUser);
    }

    // GET /api/bookings/my?page=0&size=10
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public Page<BookingResponse> getMyBookings(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal User currentUser) {
        return bookingService.getUserBookings(
            currentUser.getId(), page, size);
    }

    // DELETE /api/bookings/{id}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void cancelBooking(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        bookingService.cancelBooking(id, currentUser.getId());
    }
}