package com.booking.security;

// ── IMPORTS ────────────────────────────────────────────────────────────────

import io.jsonwebtoken.Claims;
// Claims = the "payload" part of a JWT
// Contains all the data we embedded: email, role, expiry
// Like the information printed on the wristband

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
// Exception types thrown during token validation
// Each represents a different way a token can be invalid

import io.jsonwebtoken.Jwts;
// The main JWT builder/parser class from the jjwt library
// Jwts.builder() = start building a new token
// Jwts.parserBuilder() = start parsing/validating an existing token

import io.jsonwebtoken.SignatureAlgorithm;
// Enum of signing algorithms
// SignatureAlgorithm.HS256 = HMAC-SHA256
// HMAC = Hash-based Message Authentication Code
// SHA256 = 256-bit hash function
// Together: signs the token with our secret key

import io.jsonwebtoken.security.Keys;
// Helper class to create cryptographic keys from our secret string

import org.springframework.beans.factory.annotation.Value;
// @Value("${property.name}") injects a value from application.yml
// This is how we read config values — NO hardcoding

import org.springframework.stereotype.Component;
// @Component = Spring manages this class as a Bean
// Not @Service (business logic) or @Repository (database) — just a utility

import java.security.Key;
// Java's standard Key interface for cryptographic keys

import java.util.Date;
// Used for token issued-at and expiry timestamps
// Note: Java has newer java.time.LocalDateTime but JWT library uses java.util.Date

import java.util.HashMap;
import java.util.Map;
// HashMap for building the JWT claims (key-value pairs embedded in token)


// ── CLASS ANNOTATION ───────────────────────────────────────────────────────

@Component
// Spring creates ONE instance of JwtUtil at startup
// The same instance is injected everywhere it is needed:
//   AuthService uses it to generate tokens
//   JwtAuthFilter uses it to validate tokens
// "One instance" = Singleton pattern

public class JwtUtil {


    // ── READ CONFIG VALUES ────────────────────────────────────────────────

    @Value("${jwt.secret}")
    // @Value reads from application.yml:
    //   jwt:
    //     secret: ThisIsAVeryLongSecretKey...
    // Spring injects that value into this field at startup
    // If the property is missing → Spring fails to start (intentional — no silent defaults for secrets)
    private String jwtSecret;
    // This is the SECRET KEY used to sign all tokens
    // MUST be at least 256 bits (32 characters) for HS256 algorithm
    // MUST be kept secret — if leaked, anyone can forge tokens
    // In production: stored in AWS Secrets Manager, not hardcoded


    @Value("${jwt.expiration-ms}")
    // Reads:
    //   jwt:
    //     expiration-ms: 86400000   (24 hours in milliseconds)
    private long jwtExpirationMs;
    // How long tokens are valid
    // 86400000 ms = 86400 seconds = 1440 minutes = 24 hours
    // After this time, validateToken() will return false → user must log in again


    // ── PUBLIC METHOD: Generate a new token ───────────────────────────────
    //
    // Called after successful login/registration
    // Returns the token string: "eyJhbGci...eyJzdWIi...SflKx..."

    public String generateToken(String email, String role) {
        // Parameters:
        //   email = "alice@example.com" (the user's identity)
        //   role  = "GUEST", "HOST", or "ADMIN" (for authorization checks)

        Map<String, Object> claims = new HashMap<>();
        // "claims" = the data we embed in the token payload
        // Standard JWT claims: sub (subject), iat (issued at), exp (expiry)
        // Custom claims: anything we want (we add "role")
        claims.put("role", role);
        // Add role as a custom claim
        // WHY: so we can read the role from the token without a DB call
        // Every request can check "does this token's role allow this action?"
        // Without this: every request would need SELECT * FROM users WHERE email=?

        return Jwts.builder()
            // Start building the JWT

            .setClaims(claims)
            // Set our custom claims first (role)
            // IMPORTANT: setClaims REPLACES all claims
            // So we must call it BEFORE setSubject, or setSubject would be overwritten
            // setSubject adds to claims, but setClaims({}) would clear it

            .setSubject(email)
            // "sub" (subject) claim = who this token represents
            // This is the user's email — their unique identifier in our system
            // Retrieved later with: claims.getSubject()

            .setIssuedAt(new Date())
            // "iat" (issued at) claim = when was this token created
            // new Date() = current timestamp
            // Useful for audit trails: "this token was issued at 10:30 AM"

            .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
            // "exp" (expiration) claim = when does this token expire
            // System.currentTimeMillis() = current time in milliseconds
            // + jwtExpirationMs (86400000) = 24 hours from now
            // After this timestamp: validateToken() throws ExpiredJwtException

            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            // SIGN the token with our secret key using HS256 algorithm
            // This creates the SIGNATURE part (third segment of the JWT)
            // The signature = HMACSHA256(base64(header) + "." + base64(payload), secretKey)
            // Any change to header or payload → signature mismatch → invalid token
            // Only someone who knows the secret key can create a valid signature

            .compact();
            // Assemble the final token string: "xxxxx.yyyyy.zzzzz"
            // x = base64(header), y = base64(payload), z = signature
    }


    // ── PUBLIC METHOD: Extract email from token ───────────────────────────
    //
    // Used by JwtAuthFilter to find out WHO sent the request

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
        // parseClaims() decodes and verifies the token
        // getSubject() returns the "sub" claim we set in generateToken()
        // = "alice@example.com"
    }


    // ── PUBLIC METHOD: Extract role from token ────────────────────────────
    //
    // Used if we need to check role without loading from DB

    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
        // claims.get("role", String.class):
        //   First arg: the key we want ("role")
        //   Second arg: the type to return (String.class)
        // Returns "GUEST", "HOST", or "ADMIN"
    }


    // ── PUBLIC METHOD: Validate a token ──────────────────────────────────
    //
    // Called by JwtAuthFilter on every request
    // Returns true = valid, false = invalid (any reason)

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            // If parseClaims() runs without throwing → token is valid
            return true;

        } catch (ExpiredJwtException e) {
            // Token has passed its expiry date
            // User needs to log in again to get a new token
            System.err.println("JWT expired: " + e.getMessage());

        } catch (UnsupportedJwtException e) {
            // Token uses an unsupported format or algorithm
            System.err.println("JWT unsupported: " + e.getMessage());

        } catch (MalformedJwtException e) {
            // Token is not properly formatted (corrupted, truncated, not a JWT)
            System.err.println("JWT malformed: " + e.getMessage());

        } catch (io.jsonwebtoken.security.SignatureException e) {
            // MOST IMPORTANT: signature does not match
            // Either:
            //   - Token was tampered with (hacker changed the payload)
            //   - Token was signed with a different secret key (wrong server)
            // Always reject these — potential security attack
            System.err.println("JWT signature invalid: " + e.getMessage());

        } catch (IllegalArgumentException e) {
            // Token string is null, empty, or only whitespace
            System.err.println("JWT string empty: " + e.getMessage());
        }

        return false;
        // Any exception above → return false → JwtAuthFilter skips authentication
        // The request continues as "unauthenticated"
        // If the endpoint requires auth → AuthorizationFilter rejects with 401
    }


    // ── PRIVATE HELPER: Parse and verify token ────────────────────────────
    //
    // Shared by getEmailFromToken, getRoleFromToken, and validateToken

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
            // Create a JWT parser

            .setSigningKey(getSigningKey())
            // Tell the parser WHICH key was used to sign the token
            // The parser will:
            //   1. Decode the header and payload from base64
            //   2. Recalculate what the signature SHOULD be using our key
            //   3. Compare with the actual signature in the token
            //   4. If mismatch → throw SignatureException
            //   5. If expiry has passed → throw ExpiredJwtException
            //   6. If all good → return the Claims object

            .build()
            // Finalize the parser configuration

            .parseClaimsJws(token)
            // Parse the token string
            // "Jws" = JSON Web Signature (a signed JWT)
            // Throws exceptions if anything is wrong (signature, expiry, format)

            .getBody();
            // Returns the Claims (payload) object
            // Contains: getSubject() (email), get("role") (role), getExpiration() (expiry)
    }


    // ── PRIVATE HELPER: Build the signing key ─────────────────────────────
    //
    // Converts our secret string into a proper cryptographic Key object

    private Key getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes();
        // Convert the secret string to bytes
        // "ThisIsAVeryLong..." → byte array [84, 104, 105, 115, ...]
        // Must be at least 32 bytes (256 bits) for HS256

        return Keys.hmacShaKeyFor(keyBytes);
        // Creates a proper HMAC-SHA key from the bytes
        // This Key object is what the JWT library uses for signing/verification
        // We rebuild this on every call — that is fine, it is cheap to create
    }
}