package com.dora.flowerstore.service.impl;

import com.dora.flowerstore.common.BusinessException;
import com.dora.flowerstore.common.SessionKeys;
import com.dora.flowerstore.dto.LoginRequest;
import com.dora.flowerstore.dto.RegisterRequest;
import com.dora.flowerstore.dto.UserView;
import com.dora.flowerstore.entity.User;
import com.dora.flowerstore.mapper.UserMapper;
import com.dora.flowerstore.service.CartService;
import com.dora.flowerstore.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthServiceImpl 會員註冊與登入")
class AuthServiceImplTest {

    @Mock
    UserMapper userMapper;
    @Mock
    CartService cartService;

    /** 用真的 BCrypt，順便驗證密碼有被雜湊（cost 4 加快測試速度） */
    final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    AuthServiceImpl service;

    @BeforeAll
    static void initMybatis() {
        MybatisPlusTestSupport.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userMapper, encoder, cartService);
    }

    User storedUser(String rawPassword) {
        User u = new User();
        u.setId(7L);
        u.setUsername("dora_fan");
        u.setPassword(encoder.encode(rawPassword));
        u.setRole("MEMBER");
        return u;
    }

    @Test
    @DisplayName("註冊成功：密碼以 BCrypt 雜湊儲存、角色為 MEMBER、空白 email 存成 null")
    void registerSuccess() {
        when(userMapper.exists(any())).thenReturn(false);

        UserView v = service.register(new RegisterRequest(" dora_fan ", "flower1234", "  "));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(cap.capture());
        User saved = cap.getValue();
        assertThat(saved.getUsername()).isEqualTo("dora_fan");
        assertThat(saved.getPassword()).isNotEqualTo("flower1234").startsWith("$2");
        assertThat(encoder.matches("flower1234", saved.getPassword())).isTrue();
        assertThat(saved.getEmail()).isNull();
        assertThat(v.role()).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("帳號已存在 → 擋下，不寫入資料庫")
    void registerDuplicate() {
        when(userMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("dora_fan", "flower1234", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("此帳號已被註冊");
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("兩人同時註冊同帳號（資料庫 UNIQUE 擋下）→ 轉成友善訊息")
    void registerRaceCondition() {
        when(userMapper.exists(any())).thenReturn(false);
        doThrow(new DuplicateKeyException("dup")).when(userMapper).insert(any(User.class));

        assertThatThrownBy(() -> service.register(new RegisterRequest("dora_fan", "flower1234", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("此帳號已被註冊");
    }

    @Test
    @DisplayName("登入成功：Session 寫入會員資訊、更換 Session ID、合併訪客購物車")
    void loginSuccess() {
        when(userMapper.selectOne(any())).thenReturn(storedUser("flower1234"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        req.setSession(session);
        String oldId = session.getId();

        UserView v = service.login(new LoginRequest("dora_fan", "flower1234"), req);

        assertThat(v.username()).isEqualTo("dora_fan");
        assertThat(session.getAttribute(SessionKeys.USER_ID)).isEqualTo(7L);
        assertThat(session.getAttribute(SessionKeys.USER_ROLE)).isEqualTo("MEMBER");
        assertThat(session.getId()).isNotEqualTo(oldId);
        verify(cartService).mergeGuestCart(session, 7L);
    }

    @Test
    @DisplayName("密碼錯誤與帳號不存在 → 同樣回 401「帳號或密碼錯誤」，不洩漏帳號是否存在")
    void loginFail() {
        when(userMapper.selectOne(any())).thenReturn(storedUser("flower1234"));
        assertThatThrownBy(() -> service.login(new LoginRequest("dora_fan", "wrong-pass"), new MockHttpServletRequest()))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getMessage()).isEqualTo("帳號或密碼錯誤");
                });

        when(userMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.login(new LoginRequest("nobody", "flower1234"), new MockHttpServletRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("帳號或密碼錯誤"));

        verify(cartService, never()).mergeGuestCart(any(), anyLong());
    }
}
