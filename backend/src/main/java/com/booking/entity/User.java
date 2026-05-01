package com.booking.entity;

// A blueprint for every person who uses our platform. One User object = one row in the users table.

// IMPORTS
// Imports bring in code from other libraries so we can use them.
// Without importing, Java does not know what @Entity or UUID means.

// All jakarta.persistence.* = JPA annotations (Java Persistence API)
// IMPORTANT: Must be "jakarta" not "javax" — Spring Boot 3 uses jakarta
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.EnumType;
import jakarta.persistence.OneToMany;

// Hibernate-specific annotations that auto-set timestamp fields
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

// Spring Security classes — needed because User implements UserDetails
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

// All lombok.* = Lombok annotations that generate boilerplate code for us
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import org.springframework.security.core.userdetails.UserDetails;

import java.lang.Override;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

// LocalDateTime = date + time without timezone (e.g., 2024-06-15T10:30:00)
import java.time.LocalDateTime;

// CLASS LEVEL ANNOTATIONS
// Tells JPA: "this Java class is a database entity"
// JPA will now map this class to a table in PostgreSQL
// Without this: JPA completely ignores this class
@Entity
@Table(
  // Maps to the table named "users" in PostgreSQL
  // Our Flyway SQL created this table in V1__create_initial_schema.sql
  name = "users",
  // This enforces: no two users can have the same email
  // If you try to save two users with alice@example.com -> error
  // Mirrors: CONSTRAINT uk_users_email UNIQUE (email) in SQL
  uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
// Lombok generates getter methods for all the fields declared in the class during compiler time. Like getId(), getEmail(), etc.
// Without this we would write each getter method manually. Generates Boilerplate code.
@Getter
// Lombok generates setter methods for all the fields declared in the class during compiler time. Like setEmail(String email), setFullName(String name), etc.
// Without this we would write each setter method manually. Generates Boilerplate code.
@Setter
// Lombok generates No Argument constructor - public User() {}
// JPA requires this when it reads a row from DB, it creates an empty User() and then fills in the fields one by one
@NoArgsConstructor
// Lombok: generates a constructor with ALL fields as parameters
// Used internally by @Builder
@AllArgsConstructor
// Lombok: enables the Builder pattern for creating objects cleanly:
// User.builder().email("x").fullName("y").role(GUEST).build()
// Far cleaner than: new User(null, "x", "y", null, GUEST, true, null, null)
@Builder
// "implements UserDetails" = this class agrees to fulfill Spring Security's contract
// Spring Security needs to know: what is the username? what is the password?
// UserDetails is an "interface" — a contract that lists methods you MUST implement
// We implement those methods below (getUsername, getPassword, etc.)
public class User implements UserDetails {
  // Marks this field as the PRIMARY KEY of the table
  // There can only be one @Id per entity
  // PRIMARY KEY - ID
  @Id
  // Automatically generates a UUID value when you save a new User
    // You never set this manually — Hibernate generates it
    // GenerationType.UUID = use UUID (Spring Boot 3 / Hibernate 6 feature)
    // Alternative: GenerationType.IDENTITY = auto-increment integer (1, 2, 3...)
  @GeneratedValue(strategy = GenerationType.UUID)
  // name = "id"         → the database column is called "id"
  // updatable = false   → once saved, the ID can NEVER be changed
  // nullable = false    → this column cannot be NULL in the database
  @Column(name = "id", updatable = false, nullable = false)
  // UUID type in Java maps to UUID type in PostgreSQL
  // Example value: 550e8400-e29b-41d4-a716-446655440000
  private UUID id;

  // EMAIL
  // name = "email"       → maps to the "email" column in DB
  // nullable = false     → maps to NOT NULL in PostgreSQL
  // length = 255         → maps to VARCHAR(255) in PostgreSQL
  @Column(name = "email",nullable = false,length = 255)
  private String email;

  // PASSWORD HASH
  // The column is called "password_hash" in DB
  // Notice the Java field name is "passwordHash" (camelCase)
  // Spring's naming strategy would convert "passwordHash" → "password_hash" automatically
  // But we use @Column(name=...) to be explicit and clear
  @Column(name = "password_hash",nullable = false,length = 255)
  private String passwordHash;

  // FULL NAME
  @Column(name = "full_name",nullable = false, length = 255)
  private String fullName;

  // ROLE
  // @Enumerated tells JPA how to store a Java enum in the database
  // EnumType.STRING → store the name as text: "GUEST", "HOST", "ADMIN"
  // EnumType.ORDINAL(NEVER use this) → store as a number: 0, 1, 2
  // Problem with ORDINAL: if you add a role in the middle,
  // all existing numbers shift and your data becomes corrupted
  @Enumerated(EnumType.STRING)
  @Column(name = "role",nullable = false,length = 20)
  // @Builder.Default = when using User.builder().build(),
  // use GUEST as the default value if role is not specified
  // Without @Builder.Default, Builder would set role = null
  @Builder.Default
  // New users are always GUEST by default
  // Admin can manually change role in the database if needed
  private Role role = Role.GUEST;

  // ENUM - User roles
  public enum Role {
    GUEST, // can search properties and make bookings
    HOST, // can create and manage property listings
    ADMIN // full access to everything
    // "enum" = a fixed set of named constants
    // This is safer than using String "GUEST" everywhere
    // If you typo it (e.g., "GUETS"), Java catches it at compile time
    // With String, it would fail silently at runtime
    }

    // IS ACTIVE
    // IMPORTANT: the Java field is "active" (not "isActive")
    // But the DB column is "is_active" — so we need @Column(name="is_active")
    // If we named the field "isActive", Lombok would generate isIsActive() — ugly!
    // Naming it "active" means Lombok generates isActive() — clean!
    @Column(name = "is_active",nullable = false)
    @Builder.Default
    // boolean (lowercase) = primitive type, cannot be null
    // Boolean (uppercase) = object, can be null — avoid for simple flags
    // Default = true: new users are active
    private boolean active = true;

    // CREATED AT
    // Hibernate annotation: automatically sets this to the current timestamp
    // when the entity is FIRST saved (INSERT)
    // You never set this yourself — Hibernate handles it
    @CreationTimestamp
    // updatable = false: once set, created_at never changes
    @Column(name = "created_at",updatable = false, nullable = false)
    private LocalDateTime createdAt;

    // UPDATED AT
    // Hibernate: automatically updates to current timestamp
    // every time the entity is saved (INSERT or UPDATE)
    @UpdateTimestamp
    @Column(name = "updated_at",updatable = false,nullable = false)
    private LocalDateTime updatedAt;

    // RELATIONSHIP: One User has Many Bookings
    @OneToMany(
      // "mappedBy = 'user'" tells JPA:
      // "The relationship is already defined on the OTHER side"
      // Look in Booking.java — you will find a field called "user"
      // with a @ManyToOne and @JoinColumn there
      // This side (User) just provides a convenient list — no extra DB column
      mappedBy = "user",
      // LAZY = DO NOT load bookings from DB when loading a User
      // Only fetch them IF and WHEN you call user.getBookings()
      // WHY: a user might have 500 bookings. Loading them all every
      // time you load a user would be extremely slow and wasteful
      // EAGER = always load them immediately (almost always wrong for collections)
      fetch = FetchType.LAZY
    )
    @Builder.Default
    // ArrayList is initialized to empty list (not null)
    // IMPORTANT: never leave a collection as null — null causes NullPointerException
    // when something tries to iterate over the list
    private List<Booking> bookings = new ArrayList<>();

    // RELATIONSHIP: One User has Many Reviews
    @OneToMany(mappedBy = "user",fetch = FetchType.LAZY)
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();

  // SPRING SECURITY : UserDetails interface methods implementation
  // Spring Security calls these methods to understand who the user is?
  // Because User implements UserDetails, we MUST provide concrete code to all these inherited abstract methods. If unable to do it then we can declare the class as Abstract.
  // The @Override annotation means "this method exists in UserDetails interface and I am providing my implementation of it"

  @Override
  // Spring Security asks: "what roles/permissions does this user have?"
  // We return a list with one item: their role (e.g., "ROLE_GUEST")
  // Spring Security convention: roles must be prefixed with "ROLE_"
  // This is used by: @PreAuthorize("hasRole('ADMIN')") in controllers
  public Collection<? extends GrantedAuthority> getAuthorities() {
    // role.name() returns "GUEST", "HOST", or "ADMIN"
    // So getAuthorities() returns: [ROLE_GUEST] or [ROLE_HOST] or [ROLE_ADMIN]
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getUsername() {
    // Spring Security uses this as the unique identifier
    // In our system, the "username" is the email address
    return email;
  }

  @Override
  public String getPassword() {
    // Spring Security calls this during login
    // It takes the password the user typed, re-hashes it,
    // then compares with this stored hash
    return passwordHash;
  }

  @Override
  public boolean isAccountNonExpired() {
    // Return false to mark accounts as expired (e.g., trial accounts)
    // We don't use expiry, so always return true
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    // If active = false → account is "locked" → login rejected
    // Admin can deactivate a user by setting active = false
    return active;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    // Return false to force password resets
    // We don't implement this feature, so always return true
    return true;
  }

  @Override
  public boolean isEnabled() {
    // Same as isAccountNonLocked in our case
    // Spring Security checks both — both must return true to allow login
    return active;
  }
  
}
