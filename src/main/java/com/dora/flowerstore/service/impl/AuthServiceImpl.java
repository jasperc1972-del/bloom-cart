package com.dora.flowerstore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dora.flowerstore.common.BusinessException;
import com.dora.flowerstore.common.SessionKeys;
import com.dora.flowerstore.dto.LoginRequest;
import com.dora.flowerstore.dto.RegisterRequest;
import com.dora.flowerstore.dto.UserView;
import com.dora.flowerstore.entity.User;
import com.dora.flowerstore.mapper.UserMapper;
import com.dora.flowerstore.service.AuthService;
import com.dora.flowerstore.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CartService cartService;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, CartService cartService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.cartService = cartService;
    }

    @Override
    public UserView register(RegisterRequest req) {
        String username = req.username().trim();
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, username))) {
            throw new BusinessException("此帳號已被註冊");
        }
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(req.password()));
        u.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim());
        u.setRole("MEMBER");
        try {
            userMapper.insert(u);
        } catch (DuplicateKeyException e) {
            // 兩個人同時註冊同一帳號時，由資料庫 UNIQUE 擋下
            throw new BusinessException("此帳號已被註冊");
        }
        return toView(u);
    }

    @Override
    public UserView login(LoginRequest req, HttpServletRequest httpRequest) {
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.username().trim()));
        // 帳號不存在與密碼錯誤回同一句話，避免被拿來探測帳號
        if (u == null || !passwordEncoder.matches(req.password(), u.getPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "帳號或密碼錯誤");
        }
        HttpSession session = httpRequest.getSession(true);
        // 登入後更換 Session ID，防止 Session Fixation；購物車等屬性會保留
        httpRequest.changeSessionId();
        session.setAttribute(SessionKeys.USER_ID, u.getId());
        session.setAttribute(SessionKeys.USER_ROLE, u.getRole());
        session.setAttribute(SessionKeys.USERNAME, u.getUsername());

        cartService.mergeGuestCart(session, u.getId());
        return toView(u);
    }

    @Override
    public UserView findById(Long userId) {
        User u = userMapper.selectById(userId);
        return u == null ? null : toView(u);
    }

    private static UserView toView(User u) {
        return new UserView(u.getId(), u.getUsername(), u.getEmail(), u.getRole());
    }
}
