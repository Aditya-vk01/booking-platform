package com.booking.config;
// This file lives in the "config" package
// Config files contain Spring Bean definitions — setup code that runs once at startup

// ── IMPORTS ────────────────────────────────────────────────────────────────

import org.springframework.cache.CacheManager;
// CacheManager = the object that powers @Cacheable/@CacheEvict annotations
// Spring checks this manager when deciding "is this result cached?"

import org.springframework.context.annotation.Bean;
// @Bean marks a method whose return value Spring should store and manage

import org.springframework.context.annotation.Configuration;
// @Configuration marks this class as a source of Spring Bean definitions
// Spring reads this class at startup and calls all @Bean methods

import org.springframework.data.redis.cache.RedisCacheConfiguration;
// Configuration builder for Redis-backed caching (TTL, serialization rules)

import org.springframework.data.redis.cache.RedisCacheManager;
// The actual CacheManager implementation that uses Redis as the storage backend

import org.springframework.data.redis.connection.RedisConnectionFactory;
// Spring creates this automatically based on application.yml settings
// (host: redis, port: 6379)
// We receive it as a parameter — Spring injects it

import org.springframework.data.redis.core.RedisTemplate;
// The main class for manually interacting with Redis
// Like a "connection object" with helper methods for get/set/delete

import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
// Converts Java objects ↔ JSON when storing/reading from Redis
// "Jackson" is a popular Java JSON library — used throughout Spring Boot

import org.springframework.data.redis.serializer.RedisSerializationContext;
// Wrapper that tells Spring HOW to serialize keys and values

import org.springframework.data.redis.serializer.StringRedisSerializer;
// Converts Strings to UTF-8 bytes (for cache keys like "property-search::London_2024-06-01")

import java.time.Duration;
// Duration represents a length of time (like "5 minutes")
// Used to set the TTL (Time To Live) on cached entries


// ── CLASS ANNOTATIONS ──────────────────────────────────────────────────────

@Configuration
// Tells Spring: "read this class at startup and process all @Bean methods"
// Spring will call redisTemplate() and cacheManager() once during startup
// and store the returned objects in its container

public class RedisConfig {
// No @Service, no @Repository — just @Configuration
// This class doesn't handle HTTP requests or database queries
// Its only job is to create and configure objects for Spring's container


    // ── BEAN 1: RedisTemplate ─────────────────────────────────────────────
    // This bean is used for MANUAL Redis operations
    // Example: manually storing a value or checking if a key exists

    @Bean
    // This annotation tells Spring:
    // "call this method at startup, take the object it returns,
    //  and make it available for injection in other classes"

    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory
            // Spring automatically injects this parameter
            // It reads application.yml to find Redis host/port
            // and creates the connection for us
            // We don't create it — we just receive it
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        // RedisTemplate<KeyType, ValueType>
        // KeyType = String (cache keys are strings like "property-search::London")
        // ValueType = Object (values can be any Java object — lists, maps, etc.)

        template.setConnectionFactory(connectionFactory);
        // Give the template the connection to Redis
        // Without this, the template doesn't know WHERE Redis is

        // ── Configure how KEYS are serialized ─────────────────────────────
        template.setKeySerializer(new StringRedisSerializer());
        // Keys are stored as plain readable strings in Redis
        // Without this: Spring would use Java binary serialization for keys
        // Binary keys look like: "\xac\xed\x00\x05t\x00\x05hello"
        // String keys look like: "property-search::London_2024-06-01"
        // String keys are readable and debuggable in the Redis CLI

        template.setHashKeySerializer(new StringRedisSerializer());
        // Same but for hash keys (Redis hashes are like nested maps)
        // We use simple key-value storage, but setting this prevents
        // unexpected issues if hash operations are ever used

        // ── Configure how VALUES are serialized ───────────────────────────
        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer();
        // This serializer converts Java objects to JSON and back
        // PropertyResponse → {"id":"abc","name":"Chelsea Apt","city":"London",...}
        // When reading back: JSON → PropertyResponse object automatically

        template.setValueSerializer(jsonSerializer);
        // Use JSON for values
        // WHY JSON instead of Java binary?
        // 1. Readable in Redis CLI and management tools
        // 2. Works across Java versions (binary format can break between versions)
        // 3. Easier to debug — you can see exactly what is cached

        template.setHashValueSerializer(jsonSerializer);
        // Same but for hash values

        template.afterPropertiesSet();
        // Finalizes the template configuration
        // Validates that all required properties are set correctly
        // Must call this after configuring the template

        return template;
        // Spring stores this configured RedisTemplate object
        // Any class that needs it can declare:
        //   private final RedisTemplate<String, Object> redisTemplate;
        // And Spring will inject this exact configured instance
    }


    // ── BEAN 2: CacheManager ──────────────────────────────────────────────
    // This bean powers the @Cacheable and @CacheEvict annotations
    // Spring calls this manager automatically when it sees those annotations

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Again, Spring injects the RedisConnectionFactory parameter for us

        // ── Step 1: Build the default cache configuration ─────────────────
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration
            .defaultCacheConfig()
            // Start with sensible defaults, then customize

            .entryTtl(Duration.ofMinutes(5))
            // TTL = Time To Live
            // After 5 minutes, a cached entry is automatically deleted from Redis
            // The NEXT request after expiry will:
            //   1. Get a cache MISS (entry is gone)
            //   2. Query PostgreSQL for fresh data
            //   3. Store the fresh result in Redis for another 5 minutes
            //
            // WHY 5 minutes?
            // Properties don't change every second. 5 minutes means:
            //   - Most searches hit the cache (fast)
            //   - Cache is never more than 5 minutes stale (acceptable)
            // For real-time data (stock prices), you'd use 10-30 seconds
            // For rarely-changing data (city list), you'd use 1 hour

            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer())
                // Cache keys are stored as readable strings
                // Key format: "cacheName::keyExpression"
                // Example: "property-search::London_2024-06-01_2024-06-07_0"
                //           ^cache name^     ^generated from @Cacheable key=#city+...^
            )

            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer())
                // Cache values (the actual search results) are stored as JSON
            )

            .disableCachingNullValues();
            // Don't cache null results
            // If a search returns empty results, don't cache that null
            // WHY: a new property might be added to London 1 second later
            // Caching "London has no properties" would hide new listings
            // for 5 minutes — bad user experience


        // ── Step 2: Build the CacheManager with our configuration ─────────
        return RedisCacheManager.builder(connectionFactory)
            // Use Redis as the backing store for the cache

            .cacheDefaults(cacheConfig)
            // Apply our configuration (TTL, serializers) to ALL caches
            // Every @Cacheable in the application uses these settings

            .build();

        // After this bean is created, @Cacheable annotations work:
        //
        // @Cacheable(value = "property-search", key = "#city + '_' + #checkIn")
        // public Page<PropertyResponse> searchProperties(String city, ...) {
        //     // Spring asks CacheManager: "is 'property-search::London_2024-06-01' cached?"
        //     // YES → return cached result, skip method body entirely
        //     // NO  → run method, then ask CacheManager to store the result
        // }
    }
}