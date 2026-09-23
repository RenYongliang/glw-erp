package com.glw.backend.user.controller;

import com.glw.backend.auth.AuthPrincipal;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.LoginRequest;
import com.glw.backend.model.vo.LoginResponse;
import com.glw.backend.model.entity.UserView;
import com.glw.backend.user.service.UserService;

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
