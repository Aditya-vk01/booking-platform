package com.booking.security;

// ── IMPORTS ────────────────────────────────────────────────────────────────

import jakarta.servlet.FilterChain;
// FilterChain = the sequence of filters
// Calling filterChain.doFilter(req, res) passes the request to the NEXT filter
// WITHOUT calling this, the request stops here — client gets no response

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
// HttpServletRequest = the incoming HTTP request object
// Contains: headers, body, URL, method (GET/POST/etc.)

import jakarta.servlet.http.HttpServletResponse;
// HttpServletResponse = the outgoing HTTP response object
// We mostly don't use this directly — Spring handles the response

import lombok.RequiredArgsConstructor;
// Generates constructor for final fields → Spring injects them

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// The object that represents an authenticated user in Spring Security
// Contains: principal (the User object), credentials (null after auth), authorities (roles)

import org.springframework.security.core.context.SecurityContextHolder;
// A thread-local holder for the current request's authentication
// Thread-local = each HTTP request has its own SecurityContext
// Storing auth here makes it available to the entire request processing chain

import org.springframework.security.core.userdetails.UserDetails;
// Interface that our User entity implements
// getUsername(), getPassword(), getAuthorities(), isEnabled()...

import org.springframework.security.core.userdetails.UserDetailsService;
// Interface with one method: loadUserByUsername(email)
// We define the implementation in SecurityConfig as a @Bean

import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
// Helper to attach request details (IP address, session ID) to the authentication object
// Useful for audit logging and security analysis

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
// StringUtils.hasText(str) = true if str is not null, not empty, not whitespace
// Safer than str != null && !str.isEmpty()

import org.springframework.web.filter.OncePerRequestFilter;
// Parent class that guarantees this filter runs EXACTLY ONCE per request
// Without this guarantee, a filter might run twice in some edge cases


// ── CLASS DEFINITION ──────────────────────────────────────────────────────

@Component
// Spring creates one instance and registers it as a servlet filter

@RequiredArgsConstructor
// Generates:
//   public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
//     this.jwtUtil = jwtUtil;
//     this.userDetailsService = userDetailsService;
//   }
// Spring calls this constructor and injects the beans automatically

public class JwtAuthFilter extends OncePerRequestFilter {
// "extends OncePerRequestFilter" = this class IS a filter
// It plugs into the HTTP processing pipeline
// Every request: Browser → [filters including this one] → Controller


    private final JwtUtil jwtUtil;
    // Injected by Spring — used to validate tokens and extract claims

    private final UserDetailsService userDetailsService;
    // Injected by Spring — the @Bean we define in SecurityConfig
    // Used to load the User from database by email


    // ── THE MAIN METHOD: runs for every single HTTP request ────────────────

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, java.io.IOException {

        // ── Step 1: Extract the token from the Authorization header ────────
        String token = extractTokenFromRequest(request);
        // Look for: "Authorization: Bearer eyJhbGci..."
        // If found: return "eyJhbGci..." (strip "Bearer " prefix)
        // If not found: return null


        // ── Step 2: Only proceed if a token was actually provided ──────────
        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            // StringUtils.hasText(token) = token is not null and not empty
            // jwtUtil.validateToken(token) = checks signature, checks expiry

            // IF this block is skipped (no token or invalid token):
            //   The request continues as UNAUTHENTICATED
            //   Public endpoints (/api/auth/**) will still work
            //   Protected endpoints will be rejected by AuthorizationFilter (401)

            // ── Step 3: Get the user's email from the token ────────────────
            String email = jwtUtil.getEmailFromToken(token);
            // Decodes the JWT payload and returns the "sub" claim
            // = "alice@example.com"
            // NO database call here — it is just base64 decoding + verification


            // ── Step 4: Load the full User object from the database ────────
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            // This calls the @Bean we define in SecurityConfig:
            //   return email -> userRepository.findByEmail(email)
            //     .orElseThrow(() -> new UsernameNotFoundException(...));
            //
            // Why load from DB when we have the token?
            //   The token might have been issued BEFORE:
            //     - The user's account was deactivated (active = false)
            //     - The user's role changed (promoted from GUEST to HOST)
            //   Loading from DB gets the CURRENT user state
            //   If user is deactivated (isEnabled() = false) →
            //     loadUserByUsername still returns the user
            //     but Spring Security will check isEnabled() when creating auth
            //   NOTE: for even tighter security, you could invalidate tokens
            //   on account changes by storing a "token version" in the DB


            // ── Step 5: Create the authentication object ───────────────────
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userDetails,                    // principal = the User object
                    // "principal" = who is making the request
                    // Later retrieved with: @AuthenticationPrincipal User currentUser

                    null,
                    // credentials = null after authentication
                    // We don't need the password anymore — just the user object

                    userDetails.getAuthorities()
                    // authorities = the user's roles
                    // Returns: [ROLE_GUEST] or [ROLE_HOST] or [ROLE_ADMIN]
                    // Used by @PreAuthorize("hasRole('ADMIN')") to check access
                );

            authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
                // Attaches extra request info to the authentication:
                //   - Remote IP address (for audit logs)
                //   - Session ID (if sessions were enabled, which they are not)
                // Not strictly required but good practice for audit trails
            );


            // ── Step 6: Store authentication in Spring's context ──────────
            SecurityContextHolder.getContext().setAuthentication(authentication);
            // SecurityContextHolder = Spring's thread-local storage
            // "Thread-local" = each thread (each request) has its own copy
            // Storing here means: for THIS request, Alice is authenticated
            //
            // After this line:
            //   @AuthenticationPrincipal in controllers works
            //   @PreAuthorize("hasRole('ADMIN')") checks work
            //   SecurityContextHolder.getContext().getAuthentication() returns Alice
            //
            // This context is CLEARED automatically after the request completes
            // Next request starts with empty context — must authenticate again

        } // end if (valid token)


        // ── Step 7: ALWAYS pass to the next filter ─────────────────────────
        filterChain.doFilter(request, response);
        // This line MUST be called no matter what happened above
        // Without it: the request stops here, client gets no response at all
        //
        // What happens next:
        //   → More filters in the chain
        //   → AuthorizationFilter checks: is this endpoint allowed?
        //   → DispatcherServlet routes to the correct controller
        //   → Controller method executes
        //   → Response travels back through the filter chain
    }


    // ── PRIVATE HELPER: Extract JWT from Authorization header ─────────────

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        // getHeader("Authorization") reads the "Authorization" HTTP header
        //
        // Expected format: "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIi..."
        // "Bearer" is the scheme — a standard for JWT in HTTP
        //
        // If no Authorization header: returns null
        // If header is "Basic dXNlcjpwYXNz": this is Basic auth, not Bearer

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            // Two checks:
            //   1. bearerToken exists and is not blank
            //   2. It starts with "Bearer " (with a space after)
            //      "Bearer" = 6 chars + space = 7 chars to strip

            return bearerToken.substring(7);
            // substring(7) = remove the first 7 characters ("Bearer ")
            // "Bearer eyJhbGci..." → "eyJhbGci..."
            // Returns just the token string
        }

        return null;
        // No Authorization header, or doesn't start with "Bearer "
        // JwtAuthFilter skips authentication for this request
    }
}