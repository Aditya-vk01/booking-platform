package com.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyResponse {
// Returned by:
//   GET  /api/properties/{id}          → one property
//   GET  /api/properties/search?city=X → page of properties
//   POST /api/properties               → the newly created property
//   PUT  /api/properties/{id}          → the updated property
//
// What it DOES include: all display data
// What it does NOT include: owner's password, owner's email,
// internal JPA state, the full list of bookings (which could be thousands)


    private UUID id;


    private String name;


    private String description;


    private String location;
    // Full address string


    private String city;
    // Separate from location for easy display in search results


    private String country;


    private BigDecimal pricePerNight;


    private Integer maxGuests;


    private BigDecimal avgRating;
    // This comes from the denormalized column on the Property entity
    // Kept up-to-date by the PostgreSQL trigger
    // 0.00 when no reviews yet


    private Integer reviewCount;
    // Shows "42 reviews" in the UI


    private boolean active;
    // Frontend can hide inactive properties from the UI


    private LocalDateTime createdAt;
    // "Listed since June 2024"


    // ── OWNER INFO ─────────────────────────────────────────────────────────
    // Instead of returning a nested User object, we "flatten" the owner data
    // WHY flatten?
    //   Nested:  { "owner": { "id": "...", "name": "Bob", "email": "bob@...", "passwordHash": "!" } }
    //   Flat:    { "ownerId": "...", "ownerName": "Bob" }
    //
    // The flat approach:
    //   1. Only exposes what we want (name and id — not email or password)
    //   2. Simpler for the frontend to use
    //   3. Cleaner JSON structure

    private UUID ownerId;
    // Useful if the frontend needs to navigate to the owner's profile

    private String ownerName;
    // "Listed by Bob Jones" displayed on the property page
    //
    // HOW we get these from the entity in PropertyService.toResponse():
    //   .ownerId(property.getOwner().getId())
    //   .ownerName(property.getOwner().getFullName())
    // Calling getOwner() triggers LAZY loading — JPA fetches the User
    // from the database at that moment (one SQL SELECT)
}