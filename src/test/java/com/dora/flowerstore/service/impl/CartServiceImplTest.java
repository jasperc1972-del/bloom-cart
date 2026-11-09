package com.dora.flowerstore.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dora.flowerstore.common.BusinessException;
import com.dora.flowerstore.common.SessionKeys;
import com.dora.flowerstore.dto.AddToCartRequest;
import com.dora.flowerstore.dto.CartItemView;
import com.dora.flowerstore.dto.CartView;
import com.dora.flowerstore.dto.SessionCartItem;
import com.dora.flowerstore.dto.UpdateCartItemRequest;
import com.dora.flowerstore.entity.CartItem;
import com.dora.flowerstore.entity.Product;
import com.dora.flowerstore.mapper.CartItemMapper;
import com.dora.flowerstore.mapper.ProductMapper;
import com.dora.flowerstore.service.ProductImageService;
import com.dora.flowerstore.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CartServiceImpl 購物車")
class CartServiceImplTest {

    @Mock
    CartItemMapper cartItemMapper;
    @Mock
    ProductMapper productMapper;
    @Mock
    ProductImageService imageService;
    @InjectMocks
    CartServiceImpl service;

    MockHttpSession session;
    Product rose;

    @BeforeAll
    static void initMybatis() {
        MybatisPlusTestSupport.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        rose = product(1L, "經典紅玫瑰花束", "1680.00", 10, 1);
        when(productMapper.selectById(1L)).thenReturn(rose);
        when(imageService.resolveMain(any())).thenReturn("/images/x.webp");
    }

    // ---------- 測試資料工具 ----------

    static Product product(Long id, String name, String price, int stock, int status) {
        Product p = new Product();
        p.setId(id);
        p.setProductCode("FLOWER-" + id);
        p.setName(name);
        p.setBasePrice(new BigDecimal(price));
        p.setStock(stock);
        p.setStatus(status);
        return p;
    }

    static AddToCartRequest add(long productId, int qty, String wrapping, String msg, String sig, Boolean anon) {
        return new AddToCartRequest(productId, qty, wrapping, msg, sig, anon);
    }

    @SuppressWarnings("unchecked")
    List<SessionCartItem> guestCart() {
        return (List<SessionCartItem>) session.getAttribute(SessionKeys.GUEST_CART);
    }

    // ======================================================

    @Nested
    @DisplayName("訪客購物車（存在 Session）")
    class Guest {

        @Test
        @DisplayName("加入商品 → Session 出現一筆，流水號從 1 開始")
        void addFirst() {
            service.addItem(null, session, add(1, 2, null, null, null, null));

            assertThat(guestCart()).hasSize(1);
            SessionCartItem it = guestCart().getFirst();
            assertThat(it.id()).isEqualTo(1L);
            assertThat(it.quantity()).isEqualTo(2);
            assertThat(it.wrappingOption()).isEqualTo("STANDARD");
            verify(cartItemMapper, never()).insert(any(CartItem.class));
        }

        @Test
        @DisplayName("同商品 + 同包裝 + 同卡片 → 合併數量")
        void mergeSameOptions() {
            service.addItem(null, session, add(1, 2, "FRENCH", "生日快樂", "小明", false));
            service.addItem(null, session, add(1, 3, "french", " 生日快樂 ", "小明", null));

            assertThat(guestCart()).hasSize(1);
            assertThat(guestCart().getFirst().quantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("同商品但包裝不同 → 分成兩筆")
        void differentOptionsSeparate() {
            service.addItem(null, session, add(1, 1, "STANDARD", null, null, null));
            service.addItem(null, session, add(1, 1, "FRENCH", null, null, null));

            assertThat(guestCart()).hasSize(2);
            assertThat(guestCart()).extracting(SessionCartItem::id).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("合併後超過庫存 → 擋下，購物車維持原狀")
        void mergeExceedsStock() {
            service.addItem(null, session, add(1, 8, null, null, null, null));

            assertThatThrownBy(() -> service.addItem(null, session, add(1, 3, null, null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("庫存不足")
                    .hasMessageContaining("10");
            assertThat(guestCart().getFirst().quantity()).isEqualTo(8);
        }

        @Test
        @DisplayName("匿名送花 → 不保留署名")
        void anonymousDropsSignature() {
            service.addItem(null, session, add(1, 1, null, "加油", "小明", true));

            SessionCartItem it = guestCart().getFirst();
            assertThat(it.anonymous()).isTrue();
            assertThat(it.cardSignature()).isNull();
        }

        @Test
        @DisplayName("卡片附言超過 100 字 → 擋下（中文字也按字數算）")
        void cardMessageTooLong() {
            String ok = "花".repeat(100);
            String tooLong = "花".repeat(101);

            service.addItem(null, session, add(1, 1, null, ok, null, null));
            assertThatThrownBy(() -> service.addItem(null, session, add(1, 1, null, tooLong, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("商品已下架或不存在 → 404")
        void offlineProduct() {
            when(productMapper.selectById(2L)).thenReturn(product(2L, "下架花", "100", 5, 0));

            assertThatThrownBy(() -> service.addItem(null, session, add(2, 1, null, null, null, null)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
            assertThatThrownBy(() -> service.addItem(null, session, add(99, 1, null, null, null, null)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("不支援的包裝選項 → 擋下")
        void badWrapping() {
            assertThatThrownBy(() -> service.addItem(null, session, add(1, 1, "GOLD", null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("包裝");
        }

        @Test
        @DisplayName("修改數量與移除項目")
        void updateAndRemove() {
            service.addItem(null, session, add(1, 1, null, null, null, null));

            service.updateItem(null, session, 1L, new UpdateCartItemRequest(4, "FRENCH", "謝謝", null, false));
            assertThat(guestCart().getFirst().quantity()).isEqualTo(4);
            assertThat(guestCart().getFirst().wrappingOption()).isEqualTo("FRENCH");

            service.removeItem(null, session, 1L);
            assertThat(guestCart()).isEmpty();
        }

        @Test
        @DisplayName("修改 / 移除不存在的項目 → 404")
        void itemNotFound() {
            assertThatThrownBy(() -> service.removeItem(null, session, 999L))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    // ======================================================

    @Nested
    @DisplayName("購物車金額計算")
    class Totals {

        @Test
        @DisplayName("單項小計 = (單價 + 包裝加價) × 數量；總額與總數量正確")
        void subtotalAndTotal() {
            Product tulip = product(2L, "粉鬱金香", "1280.00", 10, 1);
            when(productMapper.selectById(2L)).thenReturn(tulip);
            when(productMapper.selectBatchIds(anyCollection())).thenReturn(List.of(rose, tulip));

            service.addItem(null, session, add(1, 2, "FRENCH", null, null, null)); // (1680+150)×2 = 3660
            service.addItem(null, session, add(2, 1, "STANDARD", null, null, null)); // 1280×1 = 1280

            CartView cart = service.getCart(null, session);

            assertThat(cart.items()).hasSize(2);
            assertThat(cart.items().get(0).subtotal()).isEqualByComparingTo("3660");
            assertThat(cart.items().get(1).subtotal()).isEqualByComparingTo("1280");
            assertThat(cart.totalAmount()).isEqualByComparingTo("4940");
            assertThat(cart.totalQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("空購物車 → 0 件、0 元")
        void empty() {
            CartView cart = service.getCart(null, session);
            assertThat(cart.items()).isEmpty();
            assertThat(cart.totalAmount()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("加入後商品被下架或庫存變少 → 標示為不可購買")
        void unavailableFlag() {
            service.addItem(null, session, add(1, 5, null, null, null, null));
            rose.setStock(3);
            when(productMapper.selectBatchIds(anyCollection())).thenReturn(List.of(rose));

            CartItemView v = service.getCart(null, session).items().getFirst();
            assertThat(v.available()).isFalse();
        }
    }

    // ======================================================

    @Nested
    @DisplayName("會員購物車（存在資料庫）")
    class Member {

        static final Long USER = 7L;

        CartItem dbItem(Long id, Long userId, int qty, String wrapping) {
            CartItem c = new CartItem();
            c.setId(id);
            c.setUserId(userId);
            c.setProductId(1L);
            c.setQuantity(qty);
            c.setWrappingOption(wrapping);
            c.setAnonymous(false);
            return c;
        }

        @Test
        @DisplayName("新商品 → insert 一筆")
        void insertNew() {
            when(cartItemMapper.selectList(any())).thenReturn(List.of());

            service.addItem(USER, session, add(1, 2, "FRENCH", "愛你", "阿華", false));

            ArgumentCaptor<CartItem> cap = ArgumentCaptor.forClass(CartItem.class);
            verify(cartItemMapper).insert(cap.capture());
            assertThat(cap.getValue().getUserId()).isEqualTo(USER);
            assertThat(cap.getValue().getQuantity()).isEqualTo(2);
            assertThat(cap.getValue().getWrappingOption()).isEqualTo("FRENCH");
            assertThat(session.getAttribute(SessionKeys.GUEST_CART)).isNull();
        }

        @Test
        @DisplayName("已有相同選項的項目 → updateById 累加數量")
        void mergeExisting() {
            CartItem existing = dbItem(100L, USER, 3, "STANDARD");
            when(cartItemMapper.selectList(any())).thenReturn(List.of(existing));

            service.addItem(USER, session, add(1, 2, null, null, null, null));

            verify(cartItemMapper).updateById(existing);
            assertThat(existing.getQuantity()).isEqualTo(5);
            verify(cartItemMapper, never()).insert(any(CartItem.class));
        }

        @Test
        @DisplayName("不能修改 / 刪除別人的購物車項目 → 404")
        void cannotTouchOthersItem() {
            when(cartItemMapper.selectById(100L)).thenReturn(dbItem(100L, 999L, 1, "STANDARD"));

            assertThatThrownBy(() -> service.removeItem(USER, session, 100L))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
            verify(cartItemMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("刪除自己的項目 → deleteById")
        void removeOwn() {
            when(cartItemMapper.selectById(100L)).thenReturn(dbItem(100L, USER, 1, "STANDARD"));

            service.removeItem(USER, session, 100L);

            verify(cartItemMapper).deleteById(100L);
        }
    }

    // ======================================================

    @Nested
    @DisplayName("登入時合併訪客購物車")
    class MergeOnLogin {

        @Test
        @DisplayName("合併後超過庫存 → 自動調降到庫存上限，不報錯；並清掉 Session 購物車")
        void capToStock() {
            service.addItem(null, session, add(1, 6, null, null, null, null));
            CartItem existing = new CartItem();
            existing.setId(100L);
            existing.setUserId(7L);
            existing.setProductId(1L);
            existing.setQuantity(7);
            existing.setWrappingOption("STANDARD");
            existing.setAnonymous(false);
            when(cartItemMapper.selectList(any())).thenReturn(List.of(existing));

            service.mergeGuestCart(session, 7L);

            assertThat(existing.getQuantity()).isEqualTo(10); // 6 + 7 = 13，調降為庫存 10
            verify(cartItemMapper).updateById(existing);
            assertThat(session.getAttribute(SessionKeys.GUEST_CART)).isNull();
            assertThat(session.getAttribute(SessionKeys.GUEST_CART_SEQ)).isNull();
        }

        @Test
        @DisplayName("訪客期間商品被下架 → 跳過不合併")
        void skipOffline() {
            service.addItem(null, session, add(1, 1, null, null, null, null));
            rose.setStatus(0);

            service.mergeGuestCart(session, 7L);

            verify(cartItemMapper, never()).insert(any(CartItem.class));
            verify(cartItemMapper, never()).selectList(any(Wrapper.class));
        }
    }
}
