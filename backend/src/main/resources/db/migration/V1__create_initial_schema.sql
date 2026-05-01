-- A SQL statement used in PostgreSQL to enable "pgcrypto" module.
-- This module provides crytpographic functions for hashing, encryption, and random number generation.
-- Purpose: It enables functions like gen_random_uuid() (often used for primary keys), crypt() for password hashing, and encrypt()/decrypt() for raw data.
-- Permenance: Once run, the extension is installed permanently in that specific database; it survives database restarts and does not need to be re-run unless you drop the extension.
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- TABLE 1: users 
-- gen_random_uuid() function to automatically generated 128-bit Version 4 UUIDs, which are safer for distributed systems than sequential integers. This function is built-in for PostgreSQL 13 or later. For versions prior to 13, you must first enable it.
-- CONSTRAINTS AT TABLE CREATION LEVEL
-- PRIMARY KEY: Uniquely identifies each row in a table. It is effectively a combination of NOT NULL and UNIQUE. Each table can have only one PK.
-- NOT NULL: Ensures that a column cannot have a NULL value, mandating that every row must contain data for that specific field.
-- DEFUALT: To assign a predefined value to a column when no value is specified during a data insertion.
CREATE TABLE users (
    -- Option 1: id SERIAL PRIMARY KEY
    -- generates 1, 2, 3, 4, 5,....
    -- Problem: an attacker can enumerate your data. not secure
    id UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
    -- UNIQUE: A constraint to ensure that all values in a column or a group of columns are distinct across all rows in a table.
    -- When you define a unique constraint, PostgreSQL automatically creates a unique B-tree index to enforce it.
    -- This is single column application during table creation.
    -- For mulitple columns (composite) - used when the combination of values must be unique. UNIQUE(col1,col2)
    -- In PostgreSQl, by default, two NULL values are not considered equal. This means a unique column can contain multiple rows with NULL values. 
    -- But in PostgreSQL(15+) version, you can force the database to treat all NULL values as equal, effectively allowing only one NULL value in the column with the help of constraint: NULLS NOT DISTINCT. e.g., email VARCHAR(255) UNIQUE NULLS NOT DISTINCT
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    -- CHECK constraint: An integrity rule that ensures values in one or more columns satisfy a Boolean expression before being saved. If a value causes the expression to evaluate to FALSE, the operation (INSERT or UPDATE) is rejected with an error.
    role VARCHAR(20) NOT NULL DEFAULT 'GUEST' CHECK (role IN ('GUEST','HOST','ADMIN')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    -- In PostgreSQL, the TIMESTAMP WITH TIME ZONE data type (abbreviated as TIMESTAMPTZ) is the recommended way to store absolute points in time.
    -- To get current time: Use the now() or CURRENT_TIMESTAMP functions, which both return a TIMESTAMPTZ.
    -- DEFAULT NOW(): automatically assigns the current system date and time to the column if no value is provided during an INSERT operation.
    -- It is widely considered a best practice in PostgreSQL to use TIMESTAMP WITH TIME ZONE rather than the plain TIMESTAMP to avoid ambiguity across different server and client locations.
    -- CURRENT_TIMESTAMP is a standard SQL synonym for NOW() and can be used interchangeably in this context.
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    -- This default value only triggers during a record's initial creation. It will not automatically update if the row is modified later; for tracking modifications, you would typically use an additional updated_at column with a database trigger.
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- TABLE 2: properties 

CREATE TABLE properties (
    id UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
    -- FOREIGN KEY: A constraint that links a column or group of columns in one table to a PRIMARY KEY or UNIQUE constraint in another table.
    -- This key ensures referential integrity, meaning any value added to the foreign key column must already exist in the reference parent table.
    -- ON DELETE CASCADE is a foreign key constraint clause that ensures referential integrity by automatically deleting rows in a child table when the corresponding parent row in 'users' table is deleted. 
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    location TEXT NOT NULL,
    city VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL DEFAULT 'US',
    price_per_night DECIMAL(10,2) NOT NULL CHECK (price_per_night > 0),
    max_guests INTEGER NOT NULL CHECK (max_guests > 0),
    avg_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00 CHECK (avg_rating >= 0 AND avg_rating <= 5),
    review_count INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- TABLE 3: bookings

CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFUALT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    property_id UUID NOT NULL REFERENCES properties(id),
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    guests INTEGER NOT NULL CHECK (guests > 0),
    total_price DECIMAL(10,2) NOT NULL CHECK (total_price > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECKK (status IN ('PENDING','CONFIRMED','COMPLETED','CANCELLED')),
    idempotency_key VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFUALT NOW(),
    CONSTRAINT uk_bookings_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_valid_dates CHECK (check_out > check_in)
);

CREATE TABLE payments (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id   UUID          NOT NULL REFERENCES bookings(id),
    amount       DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    method       VARCHAR(50)   NOT NULL DEFAULT 'CREDIT_CARD',
    processed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE reviews (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id),
    property_id UUID        NOT NULL REFERENCES properties(id),
    booking_id  UUID        NOT NULL REFERENCES bookings(id),
    rating      INTEGER     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_review_per_booking UNIQUE (user_id, booking_id)
);

CREATE INDEX idx_properties_city   ON properties(city);
CREATE INDEX idx_properties_price  ON properties(price_per_night);
CREATE INDEX idx_properties_rating ON properties(avg_rating DESC);
CREATE INDEX idx_properties_active ON properties(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_bookings_property_dates ON bookings(property_id, check_in, check_out);
CREATE INDEX idx_bookings_user     ON bookings(user_id);
CREATE INDEX idx_bookings_status   ON bookings(status);
CREATE INDEX idx_reviews_property  ON reviews(property_id);

CREATE OR REPLACE FUNCTION update_property_rating()
RETURNS TRIGGER AS $$
DECLARE target_id UUID;
BEGIN
    IF TG_OP = 'DELETE' THEN target_id := OLD.property_id;
    ELSE target_id := NEW.property_id;
    END IF;
    UPDATE properties SET
        avg_rating   = (SELECT COALESCE(ROUND(AVG(rating)::NUMERIC, 2), 0.00) FROM reviews WHERE property_id = target_id),
        review_count = (SELECT COUNT(*) FROM reviews WHERE property_id = target_id),
        updated_at   = NOW()
    WHERE id = target_id;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_property_rating
    AFTER INSERT OR UPDATE OR DELETE ON reviews
    FOR EACH ROW EXECUTE FUNCTION update_property_rating();

INSERT INTO users (email, password_hash, full_name, role)
VALUES ('admin@booking.com',
        '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
        'Admin User', 'ADMIN');