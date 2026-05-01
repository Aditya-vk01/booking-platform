package com.booking.service;

import com.booking.dto.request.PropertyRequest;
import com.booking.dto.response.PropertyResponse;
import com.booking.entity.Booking;
import com.booking.entity.Property;
import com.booking.entity.User;
import com.booking.exception.BusinessException;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.PropertyRepository;
import com.booking.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
// readOnly = true at class level applies to ALL methods by default.
// Methods that write data override this with @Transactional (without readOnly).
// This is best practice — reads are the majority; writes explicitly opt in.
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    // ── SEARCH ────────────────────────────────────────────────────────────

    @Cacheable(
        value = "property-search",
        key   = "#city + '_' + #checkIn + '_' + #checkOut + '_' + #page + '_' + #size"
    )
    // Spring checks Redis BEFORE running this method body.
    // Cache key example: "property-search::London_2025-06-01_2025-06-07_0_20"
    // Cache HIT  → return stored result immediately (< 1ms, no DB query)
    // Cache MISS → run method, store result in Redis for 5 min, return result
    @Transactional(readOnly = true)
    public Page<PropertyResponse> searchProperties(
            String city,
            LocalDate checkIn,
            LocalDate checkOut,
            Integer guests,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal minRating,
            int page,
            int size,
            String sortBy) {

        // ── Fix: pre-lowercase city in Java instead of calling LOWER() in SQL ──
        // When city is null  → cityParam = null   → SQL: (:city IS NULL) = TRUE → no filter
        // When city = "London" → cityParam = "london" → SQL: LOWER(p.city) = "london" ✓
        // This avoids Hibernate passing null as bytea to PostgreSQL's LOWER() function
        String cityParam = (city == null || city.isBlank())
        ? null
        : city.toLowerCase().trim();

        // Only allow known sort fields — prevents injection attacks
        List<String> allowed =
            List.of("avgRating", "pricePerNight", "reviewCount", "createdAt");
        if (!allowed.contains(sortBy)) {
            sortBy = "avgRating";
        }

        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, sortBy)
        );

        // Always pass non-null statuses list to the repository query
        List<Booking.BookingStatus> activeStatuses = List.of(
            Booking.BookingStatus.CONFIRMED,
            Booking.BookingStatus.PENDING
        );

        Page<Property> results = propertyRepository.searchAvailable(
            cityParam,  // ← pass pre-lowercased city, not the raw city string
            checkIn,
            checkOut,
            guests,
            minPrice,
            maxPrice,
            minRating,
            activeStatuses,
            pageable
        );

        // Page.map() converts each Property entity to PropertyResponse DTO
        // preserving all pagination metadata (totalPages, totalElements, etc.)
        return results.map(this::toResponse);
    }

    // ── GET ONE PROPERTY ──────────────────────────────────────────────────

    public PropertyResponse getById(UUID id) {
        Property property = propertyRepository.findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException("Property", "id", id));
        return toResponse(property);
    }

    // ── CREATE ────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "property-search", allEntries = true)
    // Clear all search cache entries when a new property is created.
    // The new property must appear in future searches — stale cache would hide it.
    public PropertyResponse createProperty(PropertyRequest request, UUID ownerId) {

        User owner = userRepository.findById(ownerId)
            .orElseThrow(() ->
                new ResourceNotFoundException("User", "id", ownerId));

        if (owner.getRole() != User.Role.HOST
                && owner.getRole() != User.Role.ADMIN) {
            throw new BusinessException(
                "Only HOST or ADMIN users can create property listings.");
        }

        Property property = Property.builder()
            .owner(owner)
            .name(request.getName().trim())
            .description(request.getDescription())
            .location(request.getLocation().trim())
            .city(request.getCity().trim())
            .country(request.getCountry().trim())
            .pricePerNight(request.getPricePerNight())
            .maxGuests(request.getMaxGuests())
            .avgRating(BigDecimal.ZERO)
            .reviewCount(0)
            .active(true)
            .build();

        Property saved = propertyRepository.save(property);
        return toResponse(saved);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "property-search", allEntries = true)
    public PropertyResponse updateProperty(
            UUID id, PropertyRequest request, UUID requestingUserId) {

        Property property = propertyRepository.findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException("Property", "id", id));

        if (!property.getOwner().getId().equals(requestingUserId)) {
            throw new BusinessException("You can only update your own properties.");
        }

        property.setName(request.getName().trim());
        property.setDescription(request.getDescription());
        property.setLocation(request.getLocation().trim());
        property.setCity(request.getCity().trim());
        property.setCountry(request.getCountry().trim());
        property.setPricePerNight(request.getPricePerNight());
        property.setMaxGuests(request.getMaxGuests());

        return toResponse(propertyRepository.save(property));
    }

    // ── DEACTIVATE (soft delete) ──────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "property-search", allEntries = true)
    public void deactivateProperty(UUID id, UUID requestingUserId) {

        Property property = propertyRepository.findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException("Property", "id", id));

        if (!property.getOwner().getId().equals(requestingUserId)) {
            throw new BusinessException(
                "You can only deactivate your own properties.");
        }

        property.setActive(false);
        propertyRepository.save(property);
    }

    // ── MAPPER: Entity → Response DTO ─────────────────────────────────────
    // Private helper — converts one Property entity into one PropertyResponse.
    // Called by: searchProperties (via Page.map), getById, createProperty, updateProperty.

    private PropertyResponse toResponse(Property p) {
        return PropertyResponse.builder()
            .id(p.getId())
            .name(p.getName())
            .description(p.getDescription())
            .location(p.getLocation())
            .city(p.getCity())
            .country(p.getCountry())
            .pricePerNight(p.getPricePerNight())
            .maxGuests(p.getMaxGuests())
            .avgRating(p.getAvgRating())
            .reviewCount(p.getReviewCount())
            .active(p.isActive())
            .createdAt(p.getCreatedAt())
            // getOwner() triggers LAZY load of the owner User from DB.
            // Safe here because we are inside a @Transactional method —
            // the Hibernate session is still open.
            .ownerId(p.getOwner().getId())
            .ownerName(p.getOwner().getFullName())
            .build();
    }
}