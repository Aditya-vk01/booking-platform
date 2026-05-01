<div align="center">

# 🏨 Booking Platform

### A production-grade scalable booking system built with Java Spring Boot, PostgreSQL, Redis, and RabbitMQ

[![Java](https://img.shields.io/badge/Java-17-orange?style=flat&logo=openjdk)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-green?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?style=flat&logo=postgresql)](https://www.postgresql.org)
[![Redis](https://img.shields.io/badge/Redis-7-red?style=flat&logo=redis)](https://redis.io)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue?style=flat&logo=docker)](https://docs.docker.com/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat)](LICENSE)

[Features](#features) · [Architecture](#architecture) · [Tech Stack](#tech-stack) · [Getting Started](#getting-started) · [API Reference](#api-reference) · [Database Schema](#database-schema)

</div>

---

## What Is This?

A fully functional booking platform (think Airbnb / Booking.com) built from scratch to demonstrate
production-grade full stack engineering. Every architectural decision is intentional and documented —
not just "what" was built, but "why" each technical choice was made.

**This project covers:**

- Designing a normalized relational schema with proper indexes and triggers
- Preventing race conditions with pessimistic database locking
- Idempotency keys to prevent double-charging on network retries
- Sub-millisecond search results with Redis distributed caching
- Asynchronous email notifications via RabbitMQ (fire-and-forget pattern)
- JWT stateless authentication enabling horizontal scaling
- Full Docker containerization for reproducible environments

---

## Features

| Feature                         | Status     | Technical Highlight                       |
| ------------------------------- | ---------- | ----------------------------------------- |
| User registration & login       | ✅ Done    | JWT + BCrypt, stateless auth              |
| Property search with filters    | ✅ Done    | Redis cache, PostgreSQL full-text         |
| Booking with availability check | ✅ Done    | SELECT FOR UPDATE prevents double-booking |
| Payment simulation              | ✅ Done    | Idempotency keys prevent double-charge    |
| Review system                   | ✅ Done    | DB trigger auto-updates avg_rating        |
| Admin dashboard                 | 🔄 Phase 3 |                                           |
| React frontend                  | 🔄 Phase 5 | SSR with Next.js                          |
| Rate limiting                   | 🔄 Phase 3 | Redis token bucket                        |
| Load testing                    | 🔄 Phase 7 | k6 scripts                                |

---

## Architecture

┌─────────────────────────────────────────────────────────┐
│ CLIENT (Browser) │
└──────────────────────────┬──────────────────────────────┘
│ HTTP / JSON
┌──────────────────────────▼──────────────────────────────┐
│ Spring Boot API (Port 8080) │
│ Controller → Service → Repository │
│ JWT Auth Filter on every request │
└────┬─────────────────┬──────────────┬────────────────────┘
│ │ │
┌────▼────┐ ┌───────▼──────┐ ┌───▼────────┐
│PostgreSQL│ │ Redis │ │ RabbitMQ │
│Port 5432 │ │ Port 6379 │ │ Port 5672 │
│Permanent │ │Search cache │ │Email queue │
│ data │ │ 5 min TTL │ │ async │

---

## Tech Stack

### Backend

| Technology      | Version | Purpose                             |
| --------------- | ------- | ----------------------------------- |
| Java            | 17 LTS  | Core language                       |
| Spring Boot     | 3.2.3   | Web framework, dependency injection |
| Spring Security | 6.x     | JWT authentication, RBAC            |
| Spring Data JPA | 3.x     | ORM, database abstraction           |
| Hibernate       | 6.x     | JPA implementation                  |
| Flyway          | 9.x     | Database schema migrations          |

### Database & Cache

| Technology | Version  | Purpose                              |
| ---------- | -------- | ------------------------------------ |
| PostgreSQL | 15       | Primary relational database          |
| Redis      | 7        | Search result caching, rate limiting |
| HikariCP   | Built-in | Connection pooling                   |

### Messaging

| Technology | Version | Purpose                   |
| ---------- | ------- | ------------------------- |
| RabbitMQ   | 3.x     | Async email notifications |
| AMQP       | —       | Message protocol          |

### DevOps

| Technology     | Purpose                       |
| -------------- | ----------------------------- |
| Docker         | Container runtime             |
| Docker Compose | Multi-container orchestration |
| GitHub Actions | CI/CD pipeline (Phase 7)      |

---

## Database Schema

> Designed with normalization, strategic denormalization, and query-optimized indexing.

![Database Schema](docs/schema.png)

**Full interactive schema:** [View on dbdiagram.io](https://dbdiagram.io/d/69f43a63ddb9320fdca7d697)

### Key Design Decisions

**UUIDs over auto-increment integers**
Prevents enumeration attacks, enables distributed ID generation,
and avoids ID conflicts when merging data.

**Denormalized `avg_rating` and `review_count` on properties**
Avoids expensive AVG + JOIN on every search query.
A PostgreSQL trigger recalculates these automatically on review change.

**`idempotency_key` on bookings**
Client generates a UUID before sending a booking request.
If the network fails and the client retries, the server returns the
existing booking instead of creating a duplicate — no double-charging.

**`SELECT FOR UPDATE` for availability checking**
Prevents two users booking the same property for the same dates.
The property row is locked for the duration of the booking transaction.

---

## Getting Started

### Prerequisites

```bash
Java 17+        https://adoptium.net
Maven 3.9+      https://maven.apache.org
Docker Desktop  https://www.docker.com/products/docker-desktop
```

### Run in 3 Commands

```bash
# 1. Clone the repository
git clone https://github.com/YOUR_USERNAME/booking-platform.git
cd booking-platform

# 2. Copy environment file
cp .env.example .env

# 3. Start everything
docker-compose up --build
```

**Services start at:**
| Service | URL | Credentials |
|---|---|---|
| Spring Boot API | http://localhost:8080 | — |
| RabbitMQ Management | http://localhost:15672 | rabbit_user / rabbit_pass |
| PostgreSQL | localhost:5432 | booking_user / booking_pass |
| Redis | localhost:6379 | — |

---

## API Reference

### Authentication

```http
POST /api/auth/register
Content-Type: application/json

{
  "fullName": "Alice Smith",
  "email": "alice@example.com",
  "password": "SecurePass123"
}
```

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "alice@example.com",
  "password": "SecurePass123"
}
```

### Properties

```http
GET /api/properties/search?city=London&checkIn=2025-06-01&checkOut=2025-06-07&page=0&size=20
Authorization: Bearer <token>
```

```http
POST /api/properties
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Luxury Chelsea Apartment",
  "city": "London",
  "country": "UK",
  "location": "123 Kings Road, Chelsea",
  "pricePerNight": 189.00,
  "maxGuests": 4
}
```

### Bookings

```http
POST /api/bookings
Authorization: Bearer <token>
Content-Type: application/json

{
  "propertyId": "550e8400-e29b-41d4-a716-446655440000",
  "checkIn": "2025-06-01",
  "checkOut": "2025-06-07",
  "guests": 2,
  "idempotencyKey": "unique-client-uuid"
}
```

---

## Project Structure

booking-platform/
├── .env.example ← Environment variable template
├── docker-compose.yml ← All 5 services defined
│
└── backend/
├── Dockerfile ← Multi-stage build (JDK → JRE)
├── pom.xml ← All dependencies
└── src/main/
├── java/com/booking/
│ ├── config/ ← Redis, RabbitMQ configuration
│ ├── controller/ ← HTTP endpoints (thin layer)
│ ├── dto/ ← Request/response shapes
│ ├── entity/ ← JPA entities (DB table blueprints)
│ ├── exception/ ← Global error handling
│ ├── repository/ ← Database queries (Spring Data JPA)
│ ├── security/ ← JWT filter, Spring Security config
│ └── service/ ← Business logic (fat layer)
└── resources/
├── application.yml
└── db/migration/
└── V1\_\_create_initial_schema.sql

---

## Engineering Decisions Log

A living document of every significant technical decision made during development.

| Decision                 | Alternatives Considered | Reason Chosen                                                |
| ------------------------ | ----------------------- | ------------------------------------------------------------ |
| UUID primary keys        | Auto-increment integers | No enumeration attacks, distributed ID generation            |
| DECIMAL for money        | FLOAT                   | Exact arithmetic — 0.1 + 0.2 = 0.3 not 0.30000000000000004   |
| Soft delete              | Hard DELETE             | Preserves financial history, prevents orphaned FK references |
| Redis TTL 5 min          | No cache / longer TTL   | Balance between freshness and performance                    |
| RabbitMQ vs direct email | Synchronous email call  | User doesn't wait, retries on failure, decoupled             |
| Pessimistic lock         | Optimistic locking      | Booking is high-contention — fail fast beats retry loops     |
| Flyway migrations        | Hibernate ddl-auto      | Schema versioned, auditable, consistent across environments  |

---

## Roadmap

- [x] Phase 1 — System design + Docker + PostgreSQL schema
- [x] Phase 2 — Spring Boot backend (entities, JWT, APIs)
- [ ] Phase 3 — Redis caching depth + rate limiting + admin dashboard
- [ ] Phase 4 — RabbitMQ consumers + email notifications
- [ ] Phase 5 — React + Next.js frontend
- [ ] Phase 6 — CI/CD with GitHub Actions
- [ ] Phase 7 — Load testing with k6 + performance tuning

---

## Author

ADITYA VENKAT KORAMATI
Full Stack Engineer | Java · Spring Boot · React · PostgreSQL

[![LinkedIn](https://img.shields.io/badge/LinkedIn-Connect-blue?style=flat&logo=linkedin)](linkedin.com/in/aditya-venkat-koramati-373ba1353)
[![GitHub](https://img.shields.io/badge/GitHub-Follow-black?style=flat&logo=github)](https://github.com/Aditya-vk01)

---

<div align="center">
Built with intention. Every line of code has a reason.
</div>
