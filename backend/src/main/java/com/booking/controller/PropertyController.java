package com.booking.controller;

import com.booking.dto.request.PropertyRequest;
import com.booking.dto.response.PropertyResponse;
import com.booking.entity.User;
import com.booking.service.PropertyService;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    // GET /api/properties/search
    // ?city=London&checkIn=2025-06-01&checkOut=2025-06-07&page=0&size=20
    @GetMapping("/search")
    public Page<PropertyResponse> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) LocalDate checkIn,
            @RequestParam(required = false) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(defaultValue = "0")         int page,
            @RequestParam(defaultValue = "20")        int size,
            @RequestParam(defaultValue = "avgRating") String sortBy) {
        // required = false: all filters are optional
        // defaultValue: page=0 (first page), size=20, sort by rating

        return propertyService.searchProperties(
            city, checkIn, checkOut, guests,
            minPrice, maxPrice, minRating,
            page, size, sortBy
        );
    }

    // GET /api/properties/{id}
    @GetMapping("/{id}")
    public PropertyResponse getById(@PathVariable UUID id) {
        // @PathVariable reads from the URL: /api/properties/550e8400-...
        // Spring converts the String automatically to UUID
        return propertyService.getById(id);
    }

    // POST /api/properties
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    // @PreAuthorize runs BEFORE the method body
    // GUEST → 403 Forbidden immediately, method never runs
    public PropertyResponse create(
            @RequestBody @Valid PropertyRequest request,
            @AuthenticationPrincipal User currentUser) {
        // @AuthenticationPrincipal: Spring Security injects the logged-in User object
        // This is the User entity stored in SecurityContextHolder by JwtAuthFilter
        return propertyService.createProperty(request, currentUser.getId());
    }

    // PUT /api/properties/{id}
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    public PropertyResponse update(
            @PathVariable UUID id,
            @RequestBody @Valid PropertyRequest request,
            @AuthenticationPrincipal User currentUser) {
        return propertyService.updateProperty(id, request, currentUser.getId());
    }

    // DELETE /api/properties/{id}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)   // 204 — success, no body returned
    @PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
    public void deactivate(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        propertyService.deactivateProperty(id, currentUser.getId());
    }
}