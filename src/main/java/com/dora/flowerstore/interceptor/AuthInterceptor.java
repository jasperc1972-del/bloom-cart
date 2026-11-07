package com.dora.flowerstore.interceptor;

import com.dora.flowerstore.common.ApiResponse;
import com.dora.flowerstore.common.SessionKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 登入攔截器
 * 1. 已登入：把 userId 放進 request attribute（Controller 用 @RequestAttribute("userId") 取得）
 * 2. /api/orders/**：必須登入
 * 3. /api/admin/**：必須是管理員
 * 購物車 /api/cart/** 訪客也能用，所以不擋
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "userId";

    private final ObjectMapper objectMapper;

    public AuthInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        HttpSession session = request.getSession(false);
        Long userId = session == null ? null : (Long) session.getAttribute(SessionKeys.USER_ID);
        String role = session == null ? null : (String) session.getAttribute(SessionKeys.USER_ROLE);
        if (userId != null) {
            request.setAttribute(ATTR_USER_ID, userId);
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/admin/")) {
            if (userId == null) {
                return reject(response, HttpStatus.UNAUTHORIZED, "請先登入");
            }
            if (!"ADMIN".equals(role)) {
                return reject(response, HttpStatus.FORBIDDEN, "需要管理員權限");
            }
        } else if (path.startsWith("/api/orders")) {
            if (userId == null) {
                return reject(response, HttpStatus.UNAUTHORIZED, "請先登入會員才能結帳或查詢訂單");
            }
        }
        return true;
    }

    private boolean reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(message)));
        return false;
    }
}
