package com.dora.flowerstore.service;

import com.dora.flowerstore.dto.LoginRequest;
import com.dora.flowerstore.dto.RegisterRequest;
import com.dora.flowerstore.dto.UserView;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    UserView register(RegisterRequest request);

    /** 登入成功後寫入 Session，並把訪客購物車合併進資料庫 */
    UserView login(LoginRequest request, HttpServletRequest httpRequest);

    UserView findById(Long userId);
}
