package com.booking.repository;

// ── IMPORTS ────────────────────────────────────────────────────────────────

import com.booking.entity.User;
// Import our User entity — this is the type this repository manages

import org.springframework.data.jpa.repository.JpaRepository;
// JpaRepository<EntityType, PrimaryKeyType> is the interface we extend
// It provides all the free CRUD methods (save, findById, findAll, delete, etc.)

import org.springframework.stereotype.Repository;
// @Repository marks this as a Spring-managed bean for database access
// Also enables Spring to translate raw SQL exceptions into Spring exceptions
// (e.g., DataIntegrityViolationException instead of raw PSQLException)

import java.util.Optional;
// Optional<T> is a container that may or may not hold a value
// Much safer than returning null — forces you to handle the "not found" case

import java.util.UUID;
// Our primary key type


// ── INTERFACE DEFINITION ──────────────────────────────────────────────────

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
// "interface" = contract with no implementation
// "extends JpaRepository<User, UUID>" means:
//   - This repository works with User entities
//   - User's primary key type is UUID
//   - You instantly get: save(), findById(), findAll(), deleteById(), count()...
//
// Spring sees this interface at startup and creates a real class that
// implements it — you never write the SQL yourself


    // ── DERIVED QUERY: find user by email ─────────────────────────────────
    Optional<User> findByEmail(String email);
    // Spring reads this method name letter by letter:
    //   "find"   → SELECT *
    //   "By"     → WHERE
    //   "Email"  → email = ?  (maps to the "email" field in User entity)
    //
    // Generated SQL:
    //   SELECT * FROM users WHERE email = ?
    //
    // Returns Optional<User> because:
    //   - The user might not exist (new email, typo)
    //   - Optional forces the caller to handle both cases:
    //     userRepo.findByEmail("alice@example.com")
    //       .orElseThrow(() -> new ResourceNotFoundException("User", "email", email))
    //   - Much safer than returning null (null causes NullPointerException)
    //
    // USED IN: AuthService.login() to load the user after authentication


    // ── DERIVED QUERY: check if email is already registered ───────────────
    boolean existsByEmail(String email);
    // Spring generates: SELECT COUNT(*) > 0 FROM users WHERE email = ?
    // Returns true if any user has this email, false otherwise
    //
    // WHY use existsByEmail instead of findByEmail for this check?
    // existsByEmail is more efficient:
    //   - findByEmail loads the entire User object into memory (wasted)
    //   - existsByEmail just counts rows (no data loaded)
    //
    // USED IN: AuthService.register() to check for duplicate emails:
    //   if (userRepository.existsByEmail(email)) {
    //     throw new BusinessException("An account with this email already exists");
    //   }


    // ── DERIVED QUERY: find active user by email ──────────────────────────
    Optional<User> findByEmailAndActiveTrue(String email);
    // Spring generates:
    //   SELECT * FROM users WHERE email = ? AND is_active = true
    //
    // Method name breakdown:
    //   "findBy"   → SELECT * ... WHERE
    //   "Email"    → email = ?
    //   "And"      → AND
    //   "Active"   → is_active = ?  (maps to "active" field in User entity)
    //   "True"     → = true
    //
    // IMPORTANT NAMING NOTE:
    // The Java field is "active" (boolean)
    // The DB column is "is_active"
    // Spring knows "active" in the method name maps to "is_active" in the DB
    // because of the @Column(name = "is_active") annotation on the field
    //
    // USED IN: could be used in custom UserDetailsService if we want to
    // prevent deactivated users from loading (as extra safety)
}