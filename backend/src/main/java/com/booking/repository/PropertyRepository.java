package com.booking.repository;

import com.booking.entity.Booking;
import com.booking.entity.Property;

import jakarta.persistence.LockModeType;
// LockModeType.PESSIMISTIC_WRITE = SELECT FOR UPDATE in PostgreSQL

import org.springframework.data.domain.Page;
// Page<T> = a single page of results with pagination metadata
// Contains: content (the items), totalElements, totalPages, pageNumber

import org.springframework.data.domain.Pageable;
// Pageable = page number + page size + sort direction
// Passed in from the controller, Spring adds LIMIT/OFFSET automatically

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
// @Lock activates row-level locking — SELECT ... FOR UPDATE

import org.springframework.data.jpa.repository.Query;
// @Query lets you write your own JPQL or native SQL
// Used when derived method names are not expressive enough

import org.springframework.data.repository.query.Param;
// @Param("name") connects a method parameter to a :name placeholder in @Query

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID> {


    // ── CUSTOM QUERY: available property search with all filters ──────────
    //
    // This is the MOST IMPORTANT query in the entire application.
    // It powers the main search on the homepage.
    //
    // It does two things at once:
    //   1. Filters by city, price, rating, guests (optional filters)
    //   2. Excludes properties that have conflicting bookings for the dates
    //
    // WHY @Query instead of derived method name?
    // This query is too complex for a method name:
    //   - Optional filters (null means "no filter for this field")
    //   - Subquery (NOT EXISTS with date overlap check)
    //   - Multiple JOIN conditions
    // A derived method name would be 200 characters long — unreadable.

    // REPLACED LOWER(:city) with just :city because of an error
    @Query("""
        SELECT p FROM Property p
        WHERE p.active = true
          AND (:city IS NULL OR LOWER(p.city) = :city)
          AND (:minPrice IS NULL OR p.pricePerNight >= :minPrice)
          AND (:maxPrice IS NULL OR p.pricePerNight <= :maxPrice)
          AND (:minRating IS NULL OR p.avgRating >= :minRating)
          AND (:guests IS NULL OR p.maxGuests >= :guests)
          AND (
            :checkIn IS NULL
            OR NOT EXISTS (
              SELECT b FROM Booking b
              WHERE b.property = p
                AND b.status IN :statuses
                AND b.checkIn  < :checkOut
                AND b.checkOut > :checkIn
            )
          )
        """)
    // ── LINE BY LINE JPQL EXPLANATION ─────────────────────────────────────
    //
    // JPQL = Java Persistence Query Language
    // Looks like SQL but uses ENTITY CLASS names and FIELD names, not table/column names
    //   "Property p"     → the Property Java class (maps to "properties" table)
    //   "p.active"       → the "active" Java field (maps to "is_active" column)
    //   "p.pricePerNight"→ the "pricePerNight" field (maps to "price_per_night")
    //   "b.checkIn"      → the "checkIn" field (maps to "check_in")
    //
    // SELECT p FROM Property p
    //   → SELECT * FROM properties (get all columns)
    //   → "p" is an alias — like "SELECT p.* FROM properties p" in SQL
    //
    // WHERE p.active = true
    //   → AND is_active = true
    //   → Only return active (not soft-deleted) properties
    //
    // AND (:city IS NULL OR LOWER(p.city) = LOWER(:city))
    //   → "Optional filter" pattern
    //   → If :city parameter is null → the condition is always TRUE → no city filter
    //   → If :city is "London" → filter WHERE LOWER(city) = LOWER('London')
    //   → LOWER() on both sides = case-insensitive: "london" matches "London", "LONDON"
    //   → Without LOWER: "london" would NOT match "London" (case-sensitive)
    //
    // AND (:minPrice IS NULL OR p.pricePerNight >= :minPrice)
    //   → Same optional filter pattern for minimum price
    //   → No minPrice passed → no lower price limit
    //   → minPrice = 100 → only properties $100 or more
    //
    // AND (:guests IS NULL OR p.maxGuests >= :guests)
    //   → No guests param → no guest filter
    //   → guests = 4 → only properties that can hold at least 4 people
    //   → ">=" because we want properties that fit AT LEAST this many guests
    //
    // AND (:checkIn IS NULL OR NOT EXISTS (...))
    //   → If no dates provided → skip the availability check entirely
    //   → If dates provided → only include properties with NO conflicting bookings
    //
    // NOT EXISTS (SELECT b FROM Booking b WHERE ...)
    //   → In SQL: AND NOT EXISTS (SELECT 1 FROM bookings WHERE ...)
    //   → "Exclude this property IF any booking exists that overlaps our dates"
    //   → We filter on status IN :statuses (only CONFIRMED/PENDING matter — not CANCELLED)
    //
    // The date overlap condition:
    //   b.checkIn  < :checkOut   ← existing booking starts BEFORE our requested end date
    //   b.checkOut > :checkIn    ← existing booking ends AFTER our requested start date
    //
    // Why this catches ALL overlaps (it's not obvious!):
    //   Our request:    |===== June 1–7 =====|
    //   Existing A:  |===June 3–9===|          → A.checkIn(3) < our end(7) ✓ AND A.checkOut(9) > our start(1) ✓ → CONFLICT
    //   Existing B:        |===June 3–5===|    → B.checkIn(3) < our end(7) ✓ AND B.checkOut(5) > our start(1) ✓ → CONFLICT
    //   Existing C:  |==May 25–June 10===|     → C.checkIn(May25) < our end(7) ✓ AND C.checkOut(June10) > our start(1) ✓ → CONFLICT
    //   Existing D:              |July 1–5|    → D.checkIn(July1) < our end(June7)? NO ✗ → no conflict ✓
    //
    // """ = Java text block (triple quotes) — lets you write multiline strings
    // cleanly without concatenation ("SELECT " + "FROM " + ...)

    Page<Property> searchAvailable(
        @Param("city")      String city,
        @Param("checkIn")   LocalDate checkIn,
        @Param("checkOut")  LocalDate checkOut,
        @Param("guests")    Integer guests,
        @Param("minPrice")  BigDecimal minPrice,
        @Param("maxPrice")  BigDecimal maxPrice,
        @Param("minRating") BigDecimal minRating,
        @Param("statuses")  List<Booking.BookingStatus> statuses,
        Pageable pageable
        // Pageable is the LAST parameter — Spring recognizes it automatically
        // Spring adds ORDER BY, LIMIT, OFFSET based on what's in Pageable
        // No @Param needed for Pageable — Spring handles it specially
    );
    // Returns Page<Property>:
    //   page.getContent()       → List of properties for this page
    //   page.getTotalElements() → Total matching properties (e.g., 87)
    //   page.getTotalPages()    → Total pages (87 / 20 = 5 pages)
    //   page.getNumber()        → Current page index (0-based)
    //   page.hasNext()          → Whether there are more pages
    //   page.hasPrevious()      → Whether there are previous pages
    // Spring serializes all of this to JSON automatically when returned from controller


    // ── LOCK QUERY: lock property before creating a booking ───────────────
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // PESSIMISTIC_WRITE adds "FOR UPDATE" to the SQL query:
    //   SELECT * FROM properties WHERE id = ? FOR UPDATE
    //
    // "FOR UPDATE" = "I'm going to update this row — lock it"
    // PostgreSQL places a row-level exclusive lock on the property row
    //
    // What this means in practice:
    //   Transaction A calls findByIdWithLock → gets lock
    //   Transaction B calls findByIdWithLock → WAITS at this line
    //   Transaction A finishes (commit or rollback) → lock released
    //   Transaction B proceeds with fresh data
    //
    // This prevents the double-booking race condition:
    //   Both users try to book → one waits → first finishes → second checks
    //   availability → finds it booked → returns error
    //
    // IMPORTANT: This ONLY works inside a @Transactional method
    // The lock is held for the duration of the transaction
    // When the transaction commits/rollbacks, the lock is automatically released
    // BookingService.createBooking() must be @Transactional (it is)

    @Query("SELECT p FROM Property p WHERE p.id = :id")
    // We write the query explicitly because @Lock requires a @Query annotation
    // Without @Query, Spring doesn't know how to add FOR UPDATE

    Optional<Property> findByIdWithLock(@Param("id") UUID id);
    // Used in BookingService.createBooking():
    //   Property property = propertyRepository.findByIdWithLock(id)
    //     .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));
    //   // Now we hold the lock — safe to check availability and create booking


    // ── DERIVED QUERY: host's own active properties ────────────────────────
    Page<Property> findByOwnerIdAndActiveTrue(UUID ownerId, Pageable pageable);
    // Spring generates:
    //   SELECT * FROM properties
    //   WHERE owner_id = ? AND is_active = true
    //   ORDER BY ... LIMIT ? OFFSET ?
    //
    // Method name breakdown:
    //   "findBy"     → SELECT * ... WHERE
    //   "OwnerId"    → Maps to Property.owner.id
    //                  (Spring follows the relationship: owner → User → id)
    //   "And"        → AND
    //   "Active"     → Maps to Property.active field
    //   "True"       → = true
    //
    // USED IN: PropertyController's "my listings" endpoint for HOST users
    //   GET /api/properties/my → shows only this host's active properties
}