package com.gyw.backend.user.controller;

import com.gyw.backend.auth.AuthPrincipal;
import com.gyw.backend.common.ApiResponse;
import com.gyw.backend.user.dto.*;
import com.gyw.backend.user.model.UserView;
import com.gyw.backend.user.service.UserService;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(service.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        service.logout(principal);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(principal.user());
    }
}
