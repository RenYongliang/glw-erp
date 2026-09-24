package com.glw.backend.controller;

import com.glw.backend.auth.AuthPrincipal;
import com.glw.backend.auth.LoginEncryptionService;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.EncryptedLoginRequest;
import com.glw.backend.model.dto.RefreshTokenRequest;
import com.glw.backend.model.entity.UserView;
import com.glw.backend.model.vo.LoginResponse;
import com.glw.backend.service.UserService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    private final LoginEncryptionService encryption;

    public UserController(UserService service, LoginEncryptionService encryption) {
        this.service = service;
        this.encryption = encryption;
    }

    @GetMapping("/login-key")
    public ApiResponse<LoginEncryptionService.LoginKey> loginKey(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.ok(encryption.issue());
    }

    @PostMapping({"/login", "/token"})
    public ApiResponse<LoginResponse> login(@Valid @RequestBody EncryptedLoginRequest request) {
        return ApiResponse.ok(service.login(encryption.decrypt(request)));
    }

    @PostMapping("/refresh-token")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(service.refresh(request.refreshToken()));
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
