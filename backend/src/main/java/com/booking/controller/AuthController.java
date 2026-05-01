package com.booking.controller;

import com.booking.dto.request.LoginRequest;
import com.booking.dto.request.RegisterRequest;
import com.booking.dto.response.AuthResponse;
import com.booking.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
// @RestController = @Controller + @ResponseBody
// Every method return value is automatically converted to JSON.
@RequestMapping("/api/auth")
// All endpoints in this controller are prefixed with /api/auth
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // POST /api/auth/register
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)   // returns 201 on success (not default 200)
    public AuthResponse register(
            @RequestBody @Valid RegisterRequest request) {
        // @RequestBody: deserialise JSON body into RegisterRequest object
        // @Valid: run all validation annotations (@NotBlank, @Email, @Size)
        //         If any fail → 400 returned immediately, method never runs
        return authService.register(request);
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody @Valid LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);   // 200 OK
    }
}