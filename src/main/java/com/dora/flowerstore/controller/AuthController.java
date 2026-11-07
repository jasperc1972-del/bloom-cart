package com.dora.flowerstore.controller;

import com.dora.flowerstore.common.ApiResponse;
import com.dora.flowerstore.dto.LoginRequest;
import com.dora.flowerstore.dto.RegisterRequest;
import com.dora.flowerstore.dto.UserView;
import com.dora.flowerstore.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<UserView> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("註冊成功，歡迎加入朵拉花店", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<UserView> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok("登入成功", authService.login(request, httpRequest));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.ok("已登出", null);
    }

    /** 目前登入者；未登入回 data = null */
    @GetMapping("/me")
    public ApiResponse<UserView> me(@RequestAttribute(value = "userId", required = false) Long userId) {
        return ApiResponse.ok(userId == null ? null : authService.findById(userId));
    }
}
