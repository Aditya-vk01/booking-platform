package com.booking.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
// BigDecimal = precise decimal number, perfect for money
// Never use double or float for prices — they have rounding errors:
// 0.1 + 0.2 = 0.30000000000000004 in floating point!
// BigDecimal: 0.1 + 0.2 = 0.3 exactly

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;


    // ── RELATIONSHIP: Many Properties → One User (owner) ──────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    // @ManyToOne = "many properties can belong to ONE user"
    // Read it as: "Many [this entity] to One [target entity]"
    // Many Properties → One User (the host/owner)
    // This creates a foreign key column in the "properties" table

    @JoinColumn(name = "owner_id", nullable = false)
    // @JoinColumn = specifies the foreign key column name
    // name = "owner_id" → column in "properties" table that stores the owner's UUID
    // This column references: users.id
    // SQL equivalent: owner_id UUID NOT NULL REFERENCES users(id)

    private User owner;
    // In Java, we work with a full User object
    // JPA handles translating this to/from the owner_id UUID in the database
    // When you call property.getOwner(), JPA fetches the User from DB (because LAZY)


    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    // columnDefinition = "TEXT" overrides the default column type
    // By default, JPA maps String to VARCHAR(255)
    // TEXT in PostgreSQL has unlimited length — good for long descriptions
    private String description;

    @Column(name = "location", nullable = false, columnDefinition = "TEXT")
    private String location;

    @Column(name = "city", nullable = false, length = 100)
    private String city;
    // Stored separately from location for fast city-based searches:
    // WHERE city = 'London'  ← uses the city index we created in SQL

    @Column(name = "country", nullable = false, length = 100)
    @Builder.Default
    private String country = "US";


    // ── PRICE ──────────────────────────────────────────────────────────────

    @Column(name = "price_per_night", nullable = false, precision = 10, scale = 2)
    // precision = 10 → total maximum digits: up to 99,999,999.99
    // scale = 2      → digits after decimal point: exactly 2
    // Maps to DECIMAL(10, 2) in PostgreSQL
    private BigDecimal pricePerNight;


    @Column(name = "max_guests", nullable = false)
    private Integer maxGuests;
    // Integer (uppercase) can be null. int (lowercase) cannot.
    // We use Integer here because it can participate in @NotNull validation


    // ── DENORMALIZED RATING FIELDS ─────────────────────────────────────────
    // These are calculated values stored directly on the property
    // for fast read performance during search.
    //
    // WHY DENORMALIZE?
    // The alternative is: every search query joins reviews and calculates AVG
    //   SELECT p.*, AVG(r.rating) FROM properties p
    //   LEFT JOIN reviews r ON r.property_id = p.id
    //   GROUP BY p.id
    // This gets slower as reviews grow. With denormalization:
    //   SELECT * FROM properties WHERE city = 'London'  ← just one table, instant!
    //
    // HOW IS IT KEPT ACCURATE?
    // Our PostgreSQL trigger (trg_update_property_rating in V1 SQL)
    // automatically updates avg_rating and review_count whenever a review
    // is inserted, updated, or deleted. No manual maintenance needed.

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    // precision=3, scale=2 → values like 4.85, 3.20, 5.00 (max 9.99)
    @Builder.Default
    private BigDecimal avgRating = BigDecimal.ZERO;
    // BigDecimal.ZERO = 0.00 — starts at zero, trigger updates it


    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;


    // ── IS ACTIVE ──────────────────────────────────────────────────────────

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
    // Soft delete pattern: instead of removing the row from the database,
    // we set active = false. Why? Because:
    // 1. Old bookings still reference this property — deleting it breaks them
    // 2. We keep historical data for reporting and audit
    // 3. A property can be reactivated without losing any data


    // ── TIMESTAMPS ─────────────────────────────────────────────────────────

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    // ── RELATIONSHIP: One Property → Many Bookings ─────────────────────────

    @OneToMany(
        mappedBy = "property",
        // Refers to the field named "property" in Booking.java
        // That is where the @JoinColumn(name="property_id") lives
        // This side just provides a convenient list

        cascade = CascadeType.ALL,
        // CascadeType.ALL = any operation on Property is cascaded to its Bookings
        // If you delete a Property → all its Bookings are also deleted
        // Options:
        //   PERSIST  = save cascades (when you save property, save its bookings too)
        //   MERGE    = updates cascade
        //   REMOVE   = deletes cascade
        //   ALL      = all of the above

        fetch = FetchType.LAZY,
        // Don't load all bookings when loading a property
        // A popular property might have thousands of bookings!

        orphanRemoval = true
        // If you remove a Booking from this list:
        //   property.getBookings().remove(booking);
        // JPA will DELETE that booking from the database
    )
    @Builder.Default
    private List<Booking> bookings = new ArrayList<>();


    // ── RELATIONSHIP: One Property → Many Reviews ──────────────────────────

    @OneToMany(
        mappedBy = "property",
        cascade = CascadeType.ALL,
        fetch = FetchType.LAZY,
        orphanRemoval = true
    )
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();
}