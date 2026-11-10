package com.dora.flowerstore.controller;

import com.dora.flowerstore.common.BusinessException;
import com.dora.flowerstore.common.GlobalExceptionHandler;
import com.dora.flowerstore.common.SessionKeys;
import com.dora.flowerstore.dto.CartView;
import com.dora.flowerstore.interceptor.AuthInterceptor;
import com.dora.flowerstore.service.AuthService;
import com.dora.flowerstore.service.CartService;
import com.dora.flowerstore.service.OrderService;
import com.dora.flowerstore.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 層測試：用 standalone MockMvc 組裝 Controller + 登入攔截器 + 全域例外處理，
 * Service 全部用 Mockito 模擬，不啟動整個 Spring、不連資料庫。
 */
@DisplayName("API：登入權限、欄位驗證、錯誤回應格式")
class ApiControllerTest {

    CartService cartService = mock(CartService.class);
    OrderService orderService = mock(OrderService.class);
    ProductService productService = mock(ProductService.class);
    AuthService authService = mock(AuthService.class);

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        mvc = MockMvcBuilders
                .standaloneSetup(
                        new CartController(cartService),
                        new OrderController(orderService),
                        new ProductController(productService, 1, 30),
                        new AdminController(productService, orderService),
                        new AuthController(authService))
                .addMappedInterceptors(new String[]{"/api/**"}, new AuthInterceptor(om))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(cartService.getCart(any(), any())).thenReturn(new CartView(List.of(), 0, BigDecimal.ZERO));
    }

    /** 模擬已登入的 Session */
    static MockHttpSession login(long userId, String role) {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(SessionKeys.USER_ID, userId);
        s.setAttribute(SessionKeys.USER_ROLE, role);
        return s;
    }

    // ======================================================

    @Nested
    @DisplayName("登入攔截器")
    class Interceptor {

        @Test
        @DisplayName("未登入查訂單 → 401")
        void ordersNeedLogin() throws Exception {
            mvc.perform(get("/api/orders"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(containsString("請先登入")));
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("一般會員進管理後台 → 403")
        void adminNeedsAdminRole() throws Exception {
            mvc.perform(get("/api/admin/orders").session(login(7L, "MEMBER")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("需要管理員權限"));
        }

        @Test
        @DisplayName("未登入進管理後台 → 401")
        void adminNeedsLogin() throws Exception {
            mvc.perform(get("/api/admin/products"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("管理員可以進後台")
        void adminOk() throws Exception {
            when(orderService.listAll(null)).thenReturn(List.of());
            mvc.perform(get("/api/admin/orders").session(login(1L, "ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("已登入會員查訂單 → 用 Session 裡的 userId 查")
        void memberOrders() throws Exception {
            when(orderService.listMyOrders(7L)).thenReturn(List.of());
            mvc.perform(get("/api/orders").session(login(7L, "MEMBER")))
                    .andExpect(status().isOk());
            verify(orderService).listMyOrders(7L);
        }

        @Test
        @DisplayName("訪客可以使用購物車（userId 為 null）")
        void guestCanUseCart() throws Exception {
            mvc.perform(get("/api/cart"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalQuantity").value(0));
            verify(cartService).getCart(isNull(), any());
        }
    }

    // ======================================================

    @Nested
    @DisplayName("欄位驗證（@Valid）")
    class Validation {

        @Test
        @DisplayName("加入購物車數量為 0 → 400「數量至少 1」")
        void quantityZero() throws Exception {
            mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":1,\"quantity\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("數量至少 1")));
            verify(cartService, never()).addItem(any(), any(), any());
        }

        @Test
        @DisplayName("註冊帳號太短、密碼太短 → 400，訊息合併回傳")
        void registerInvalid() throws Exception {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"ab\",\"password\":\"123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("帳號需為")))
                    .andExpect(jsonPath("$.message").value(containsString("密碼長度")));
        }

        @Test
        @DisplayName("結帳電話格式錯誤 → 400")
        void badPhone() throws Exception {
            mvc.perform(post("/api/orders/checkout").session(login(7L, "MEMBER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"shipments":[{"recipientName":"王小美","recipientPhone":"abc",
                                     "shippingAddress":"台北市","deliveryDate":"2099-01-01",
                                     "deliveryTimeSlot":"09:00-12:00","payerName":"陳付款",
                                     "payerPhone":"0900-000-009","payerEmail":"a@b.com","cartItemIds":[1]}]}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("收件人電話格式不正確")));
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("結帳沒填付款人、沒分配商品 → 400")
        void missingPayerAndItems() throws Exception {
            mvc.perform(post("/api/orders/checkout").session(login(7L, "MEMBER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"shipments":[{"recipientName":"王小美","recipientPhone":"0900-000-000",
                                     "shippingAddress":"台北市","deliveryDate":"2099-01-01",
                                     "deliveryTimeSlot":"09:00-12:00","payerName":"","payerPhone":"0900-000-009",
                                     "payerEmail":"not-an-email","cartItemIds":[]}]}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("付款人姓名")))
                    .andExpect(jsonPath("$.message").value(containsString("Email 格式不正確")))
                    .andExpect(jsonPath("$.message").value(containsString("至少要分配一項商品")));
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("結帳沒有任何收件人 → 400")
        void noShipments() throws Exception {
            mvc.perform(post("/api/orders/checkout").session(login(7L, "MEMBER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"shipments\":[]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("至少填寫一位收件人")));
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("JSON 格式壞掉 → 400「請求格式錯誤」")
        void malformedJson() throws Exception {
            mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("請求格式錯誤"));
        }
    }

    // ======================================================

    @Nested
    @DisplayName("回應格式與錯誤處理")
    class Responses {

        @Test
        @DisplayName("加入購物車成功 → 回傳成功訊息與最新購物車")
        void addToCartOk() throws Exception {
            mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":1,\"quantity\":2,\"wrappingOption\":\"FRENCH\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("成功將花卉加入朵拉花店購物車"));
        }

        @Test
        @DisplayName("業務例外 → 依例外帶的狀態碼回應（例如 404）")
        void businessException() throws Exception {
            when(productService.getOnSale(99L))
                    .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "找不到這款花卉，可能已下架"));

            mvc.perform(get("/api/products/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("找不到這款花卉，可能已下架"));
        }

        @Test
        @DisplayName("未預期的錯誤 → 500，不把內部錯誤訊息洩漏給前端")
        void unexpectedError() throws Exception {
            when(productService.filterOptions()).thenThrow(new IllegalStateException("DB password wrong"));

            mvc.perform(get("/api/products/filters"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("系統忙碌中，請稍後再試"));
        }

        @Test
        @DisplayName("/api/meta 回傳包裝選項、配送時段與日期範圍")
        void meta() throws Exception {
            mvc.perform(get("/api/meta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.wrappingOptions[1].code").value("FRENCH"))
                    .andExpect(jsonPath("$.data.wrappingOptions[1].fee").value(150.00))
                    .andExpect(jsonPath("$.data.timeSlots[0]").value("09:00-12:00"))
                    .andExpect(jsonPath("$.data.deliveryMaxDaysAhead").value(30));
        }

        @Test
        @DisplayName("會員取消訂單 → 呼叫 service 並帶入自己的 userId")
        void cancelOrder() throws Exception {
            mvc.perform(delete("/api/orders/2026111000000001").session(login(7L, "MEMBER")))
                    .andExpect(status().isOk());
            verify(orderService).cancelMyOrder(eq(7L), eq("2026111000000001"));
            verify(orderService, never()).deleteOrder(anyLong());
        }
    }
}
