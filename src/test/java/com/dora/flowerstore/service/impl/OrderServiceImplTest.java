package com.dora.flowerstore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dora.flowerstore.common.BusinessException;
import com.dora.flowerstore.dto.CheckoutRequest;
import com.dora.flowerstore.dto.OrderView;
import com.dora.flowerstore.dto.ShipmentRequest;
import com.dora.flowerstore.entity.CartItem;
import com.dora.flowerstore.entity.Order;
import com.dora.flowerstore.entity.OrderItem;
import com.dora.flowerstore.entity.Product;
import com.dora.flowerstore.entity.User;
import com.dora.flowerstore.event.OrderDeliveredEvent;
import com.dora.flowerstore.event.OrderPaidEvent;
import com.dora.flowerstore.event.OrdersPlacedEvent;
import com.dora.flowerstore.mapper.CartItemMapper;
import com.dora.flowerstore.mapper.OrderItemMapper;
import com.dora.flowerstore.mapper.OrderMapper;
import com.dora.flowerstore.mapper.ProductMapper;
import com.dora.flowerstore.mapper.UserMapper;
import com.dora.flowerstore.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderServiceImpl 結帳與訂單")
class OrderServiceImplTest {

    static final Long USER = 7L;
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Taipei"));

    @Mock
    OrderMapper orderMapper;
    @Mock
    OrderItemMapper orderItemMapper;
    @Mock
    CartItemMapper cartItemMapper;
    @Mock
    ProductMapper productMapper;
    @Mock
    UserMapper userMapper;
    @Mock
    ApplicationEventPublisher events;

    OrderServiceImpl service;

    /** 模擬資料庫裡的訂單明細（insert 時存起來，查詢時回傳） */
    final List<OrderItem> savedItems = new ArrayList<>();
    final List<Order> savedOrders = new ArrayList<>();
    Order savedOrder;

    @BeforeAll
    static void initMybatis() {
        MybatisPlusTestSupport.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        // 配送日期：最快明天，最晚 30 天後
        service = new OrderServiceImpl(orderMapper, orderItemMapper, cartItemMapper, productMapper, userMapper, events, 1, 30);

        User u = new User();
        u.setId(USER);
        u.setUsername("dora_fan");
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(u));

        // insert 訂單時依序給 id（500、501…），結帳後 selectList 讀回全部
        doAnswer(inv -> {
            savedOrder = inv.getArgument(0);
            savedOrder.setId(500L + savedOrders.size());
            savedOrders.add(savedOrder);
            return 1;
        }).when(orderMapper).insert(any(Order.class));
        when(orderMapper.selectList(any())).thenAnswer(inv -> List.copyOf(savedOrders));

        doAnswer(inv -> {
            savedItems.add(inv.getArgument(0));
            return 1;
        }).when(orderItemMapper).insert(any(OrderItem.class));
        when(orderItemMapper.selectList(any())).thenAnswer(inv -> List.copyOf(savedItems));
    }

    // ---------- 測試資料工具 ----------

    static Product product(Long id, String name, String price, int status) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setBasePrice(new BigDecimal(price));
        p.setStock(10);
        p.setStatus(status);
        return p;
    }

    static CartItem cartItem(Long productId, int qty, String wrapping, String msg, String sig, boolean anon) {
        return cartItem(productId, productId, qty, wrapping, msg, sig, anon);
    }

    static CartItem cartItem(Long id, Long productId, int qty, String wrapping, String msg, String sig, boolean anon) {
        CartItem c = new CartItem();
        c.setId(id);
        c.setUserId(USER);
        c.setProductId(productId);
        c.setQuantity(qty);
        c.setWrappingOption(wrapping);
        c.setCardMessage(msg);
        c.setCardSignature(sig);
        c.setAnonymous(anon);
        return c;
    }

    /** 單一收件人，購物車項目 id 預設 1 */
    static CheckoutRequest checkoutReq(LocalDate date, String slot, Long... cartIds) {
        List<Long> ids = cartIds.length == 0 ? List.of(1L) : List.of(cartIds);
        return new CheckoutRequest(List.of(shipment(" 王小美 ", " 測試市測試路 1 號 ", date, slot, " 陳付款 ", ids)));
    }

    static ShipmentRequest shipment(String name, String addr, LocalDate date, String slot, String payer, List<Long> ids) {
        return new ShipmentRequest(name, "0900-000-000", addr, date, slot, payer, "0900-000-009", " Payer@Example.com ", ids);
    }

    static Order order(Long id, String status) {
        Order o = new Order();
        o.setId(id);
        o.setOrderNo("2026111000000001");
        o.setUserId(USER);
        o.setStatus(status);
        o.setTotalAmount(new BigDecimal("1680.00"));
        return o;
    }

    // ======================================================

    @Nested
    @DisplayName("結帳 checkout")
    class Checkout {

        @Test
        @DisplayName("成功：扣庫存、金額正確、建立訂單與明細、清空購物車")
        void success() {
            Product rose = product(1L, "經典紅玫瑰花束", "1680.00", 1);
            Product tulip = product(2L, "粉鬱金香春日花束", "1280.00", 1);
            when(productMapper.selectById(1L)).thenReturn(rose);
            when(productMapper.selectById(2L)).thenReturn(tulip);
            when(productMapper.decreaseStock(anyLong(), anyInt())).thenReturn(1);
            // 故意把商品 2 放前面，驗證會依商品 id 排序扣庫存
            when(cartItemMapper.selectList(any())).thenReturn(List.of(
                    cartItem(2L, 1, "STANDARD", null, null, false),
                    cartItem(1L, 2, "FRENCH", "生日快樂", "阿華", false)));

            OrderView view = service.checkout(USER, checkoutReq(TODAY.plusDays(3), "09:00-12:00", 1L, 2L)).getFirst();

            // (1680 + 150) × 2 + 1280 × 1 = 4940
            assertThat(view.totalAmount()).isEqualByComparingTo("4940");
            assertThat(view.status()).isEqualTo("PENDING");
            assertThat(view.statusLabel()).isEqualTo("待付款");
            assertThat(view.username()).isEqualTo("dora_fan");
            assertThat(view.items()).hasSize(2);

            // 收件資訊去掉前後空白
            assertThat(savedOrder.getRecipientName()).isEqualTo("王小美");
            assertThat(savedOrder.getShippingAddress()).isEqualTo("測試市測試路 1 號");
            assertThat(savedOrder.getPayerName()).isEqualTo("陳付款");
            assertThat(savedOrder.getPayerEmail()).isEqualTo("Payer@Example.com");
            // 發出結帳事件（交易提交後寄待付款通知）
            ArgumentCaptor<OrdersPlacedEvent> ev = ArgumentCaptor.forClass(OrdersPlacedEvent.class);
            verify(events).publishEvent(ev.capture());
            assertThat(ev.getValue().orders()).hasSize(1);
            assertThat(view.batchNo()).startsWith("B");
            // 訂單號：12 碼時間 + 4 碼流水號
            assertThat(savedOrder.getOrderNo()).matches("\\d{16}");
            // 卡片彙整
            assertThat(savedOrder.getCardMessage()).isEqualTo("經典紅玫瑰花束：生日快樂—阿華");

            InOrder order = inOrder(productMapper);
            order.verify(productMapper).decreaseStock(1L, 2);
            order.verify(productMapper).decreaseStock(2L, 1);
            verify(cartItemMapper).delete(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("明細是下單當下的快照（單價、包裝費、品名）")
        void itemSnapshot() {
            when(productMapper.selectById(1L)).thenReturn(product(1L, "經典紅玫瑰花束", "1680.00", 1));
            when(productMapper.decreaseStock(anyLong(), anyInt())).thenReturn(1);
            when(cartItemMapper.selectList(any())).thenReturn(List.of(
                    cartItem(1L, 1, "FRENCH", "加油", "小明", true)));

            service.checkout(USER, checkoutReq(TODAY.plusDays(1), "14:00-18:00"));

            ArgumentCaptor<OrderItem> cap = ArgumentCaptor.forClass(OrderItem.class);
            verify(orderItemMapper).insert(cap.capture());
            OrderItem oi = cap.getValue();
            assertThat(oi.getOrderId()).isEqualTo(500L);
            assertThat(oi.getProductName()).isEqualTo("經典紅玫瑰花束");
            assertThat(oi.getPrice()).isEqualByComparingTo("1680");
            assertThat(oi.getWrappingFee()).isEqualByComparingTo("150");
            assertThat(oi.getAnonymous()).isTrue();
            // 匿名時卡片彙整標示（匿名），不顯示署名
            assertThat(savedOrder.getCardMessage()).isEqualTo("經典紅玫瑰花束：加油（匿名）");
        }

        @Test
        @DisplayName("庫存不足 → 409，帶出最新庫存，不建立訂單、不清購物車")
        void outOfStock() {
            when(productMapper.selectById(1L)).thenReturn(product(1L, "經典紅玫瑰花束", "1680.00", 1));
            when(productMapper.decreaseStock(1L, 5)).thenReturn(0);
            when(productMapper.selectCurrentStock(1L)).thenReturn(2);
            when(cartItemMapper.selectList(any())).thenReturn(List.of(cartItem(1L, 5, null, null, null, false)));

            assertThatThrownBy(() -> service.checkout(USER, checkoutReq(TODAY.plusDays(2), "09:00-12:00")))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getMessage()).contains("庫存不足").contains("剩 2 件");
                    });
            verify(orderMapper, never()).insert(any(Order.class));
            verify(cartItemMapper, never()).delete(any(LambdaQueryWrapper.class));
            verify(events, never()).publishEvent(any());
        }

        @Test
        @DisplayName("購物車是空的 → 擋下")
        void emptyCart() {
            when(cartItemMapper.selectList(any())).thenReturn(List.of());

            assertThatThrownBy(() -> service.checkout(USER, checkoutReq(TODAY.plusDays(2), "09:00-12:00")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("購物車是空的");
        }

        @Test
        @DisplayName("購物車內有已下架商品 → 擋下，不扣庫存")
        void offlineProduct() {
            when(productMapper.selectById(1L)).thenReturn(product(1L, "下架花", "100", 0));
            when(cartItemMapper.selectList(any())).thenReturn(List.of(cartItem(1L, 1, null, null, null, false)));

            assertThatThrownBy(() -> service.checkout(USER, checkoutReq(TODAY.plusDays(2), "09:00-12:00")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已下架");
            verify(productMapper, never()).decreaseStock(anyLong(), anyInt());
        }

        @ParameterizedTest(name = "今天 + {0} 天 → 超出可配送範圍")
        @CsvSource({"0", "-1", "31"})
        @DisplayName("配送日期不在範圍內 → 擋下")
        void dateOutOfRange(int days) {
            assertThatThrownBy(() -> service.checkout(USER, checkoutReq(TODAY.plusDays(days), "09:00-12:00")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("指定送達日期");
            verify(cartItemMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("邊界值：明天與第 30 天都可以")
        void dateBoundaryOk() {
            when(productMapper.selectById(1L)).thenReturn(product(1L, "玫瑰", "100", 1));
            when(productMapper.decreaseStock(anyLong(), anyInt())).thenReturn(1);
            when(cartItemMapper.selectList(any())).thenReturn(List.of(cartItem(1L, 1, null, null, null, false)));

            service.checkout(USER, checkoutReq(TODAY.plusDays(1), "09:00-12:00"));
            service.checkout(USER, checkoutReq(TODAY.plusDays(30), "09:00-12:00"));
        }

        @Test
        @DisplayName("兩位收件人 → 拆成兩張訂單：各自金額、地址、付款人，共用結帳批號")
        void splitToTwoRecipients() {
            when(productMapper.selectById(1L)).thenReturn(product(1L, "粉鬱金香春日花束", "1280.00", 1));
            when(productMapper.selectById(2L)).thenReturn(product(2L, "滿天星夢幻花束", "780.00", 1));
            when(productMapper.decreaseStock(anyLong(), anyInt())).thenReturn(1);
            when(cartItemMapper.selectList(any())).thenReturn(List.of(
                    cartItem(11L, 1L, 1, "STANDARD", "dear lily", "steven", false),
                    cartItem(12L, 2L, 1, "STANDARD", "congratulations", "Daddy", false)));
            LocalDate d1 = TODAY.plusDays(1);
            LocalDate d2 = TODAY.plusDays(5);

            List<OrderView> views = service.checkout(USER, new CheckoutRequest(List.of(
                    shipment("Lily", "台北市 A 路 1 號", d1, "09:00-12:00", "steven", List.of(11L)),
                    shipment("女兒", "台中市 B 路 2 號", d2, "14:00-18:00", "媽媽", List.of(12L)))));

            assertThat(views).hasSize(2);
            Order a = savedOrders.get(0);
            Order b = savedOrders.get(1);
            assertThat(a.getTotalAmount()).isEqualByComparingTo("1280");
            assertThat(b.getTotalAmount()).isEqualByComparingTo("780");
            assertThat(a.getShippingAddress()).isEqualTo("台北市 A 路 1 號");
            assertThat(b.getDeliveryDate()).isEqualTo(d2);
            assertThat(b.getDeliveryTimeSlot()).isEqualTo("14:00-18:00");
            assertThat(a.getPayerName()).isEqualTo("steven");
            assertThat(b.getPayerName()).isEqualTo("媽媽");
            assertThat(a.getBatchNo()).isNotBlank().isEqualTo(b.getBatchNo());
            assertThat(a.getOrderNo()).isNotEqualTo(b.getOrderNo());
            // 卡片只彙整該張訂單自己的品項
            assertThat(a.getCardMessage()).isEqualTo("粉鬱金香春日花束：dear lily—steven");
            // 明細各自掛在對應的訂單底下
            assertThat(savedItems).extracting(OrderItem::getOrderId).containsExactly(500L, 501L);
            verify(cartItemMapper).delete(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("同一款花分給兩位收件人 → 數量合併後只扣一次庫存")
        void sameProductTwoRecipientsMergesStock() {
            when(productMapper.selectById(1L)).thenReturn(product(1L, "玫瑰", "100.00", 1));
            when(productMapper.decreaseStock(anyLong(), anyInt())).thenReturn(1);
            when(cartItemMapper.selectList(any())).thenReturn(List.of(
                    cartItem(11L, 1L, 1, "STANDARD", null, null, false),
                    cartItem(12L, 1L, 2, "FRENCH", null, null, false)));

            service.checkout(USER, new CheckoutRequest(List.of(
                    shipment("甲", "地址甲", TODAY.plusDays(2), "09:00-12:00", "付款人", List.of(11L)),
                    shipment("乙", "地址乙", TODAY.plusDays(2), "09:00-12:00", "付款人", List.of(12L)))));

            verify(productMapper).decreaseStock(1L, 3);
            assertThat(savedOrders).hasSize(2);
            // (100 + 150) × 2 = 500
            assertThat(savedOrders.get(1).getTotalAmount()).isEqualByComparingTo("500");
        }

        @Test
        @DisplayName("有商品沒指定收件人 → 擋下，不扣庫存")
        void unassignedItem() {
            when(cartItemMapper.selectList(any())).thenReturn(List.of(
                    cartItem(11L, 1L, 1, null, null, null, false),
                    cartItem(12L, 2L, 1, null, null, null, false)));

            assertThatThrownBy(() -> service.checkout(USER, checkoutReq(TODAY.plusDays(2), "09:00-12:00", 11L)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("沒有指定收件人");
            verify(productMapper, never()).decreaseStock(anyLong(), anyInt());
            verify(orderMapper, never()).insert(any(Order.class));
        }

        @Test
        @DisplayName("同一項商品分給兩位收件人 → 擋下")
        void duplicateAssignment() {
            when(cartItemMapper.selectList(any())).thenReturn(List.of(cartItem(11L, 1L, 1, null, null, null, false)));

            assertThatThrownBy(() -> service.checkout(USER, new CheckoutRequest(List.of(
                    shipment("甲", "地址甲", TODAY.plusDays(2), "09:00-12:00", "付款人", List.of(11L)),
                    shipment("乙", "地址乙", TODAY.plusDays(2), "09:00-12:00", "付款人", List.of(11L))))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不能同時分給兩位收件人");
            verify(productMapper, never()).decreaseStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("送來不屬於自己購物車的項目 id → 擋下")
        void foreignCartItem() {
            when(cartItemMapper.selectList(any())).thenReturn(List.of(cartItem(11L, 1L, 1, null, null, null, false)));

            assertThatThrownBy(() -> service.checkout(USER, checkoutReq(TODAY.plusDays(2), "09:00-12:00", 11L, 999L)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("購物車內容已變更");
            verify(orderMapper, never()).insert(any(Order.class));
        }

        @Test
        @DisplayName("其中一位收件人的日期不合法 → 整筆擋下")
        void oneShipmentBadDate() {
            assertThatThrownBy(() -> service.checkout(USER, new CheckoutRequest(List.of(
                    shipment("甲", "地址甲", TODAY.plusDays(2), "09:00-12:00", "付款人", List.of(11L)),
                    shipment("乙", "地址乙", TODAY.plusDays(99), "09:00-12:00", "付款人", List.of(12L))))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("指定送達日期");
            verify(cartItemMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("配送時段不合法 → 擋下")
        void badSlot() {
            assertThatThrownBy(() -> service.checkout(USER, checkoutReq(TODAY.plusDays(2), "20:00-22:00")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("配送時段");
        }
    }

    // ======================================================

    @Nested
    @DisplayName("訂單狀態 updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("往後推進可以：待付款 → 已付款")
        void forward() {
            Order o = order(1L, "PENDING");
            when(orderMapper.selectById(1L)).thenReturn(o);

            OrderView v = service.updateStatus(1L, "PAID");

            assertThat(v.status()).isEqualTo("PAID");
            verify(orderMapper).updateById(o);
            // 改成已付款 → 發出收款確認事件
            verify(events).publishEvent(any(OrderPaidEvent.class));
        }

        @Test
        @DisplayName("改成已送達 → 發出送達通知事件")
        void completedPublishesDeliveredEvent() {
            when(orderMapper.selectById(1L)).thenReturn(order(1L, "DELIVERING"));

            service.updateStatus(1L, "COMPLETED");

            verify(events).publishEvent(any(OrderDeliveredEvent.class));
            verify(events, never()).publishEvent(any(OrderPaidEvent.class));
        }

        @Test
        @DisplayName("改成配送中 → 不發收款確認事件")
        void deliveringNoPaidEvent() {
            when(orderMapper.selectById(1L)).thenReturn(order(1L, "PAID"));

            service.updateStatus(1L, "DELIVERING");

            verify(events, never()).publishEvent(any());
        }

        @ParameterizedTest(name = "{0} → {1} 不允許")
        @CsvSource({"PAID,PENDING", "PAID,PAID", "COMPLETED,DELIVERING"})
        @DisplayName("倒退或維持原狀 → 擋下")
        void backward(String current, String next) {
            when(orderMapper.selectById(1L)).thenReturn(order(1L, current));

            assertThatThrownBy(() -> service.updateStatus(1L, next))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("只能往後推進");
            verify(orderMapper, never()).updateById(any(Order.class));
        }

        @Test
        @DisplayName("訂單不存在 → 404")
        void notFound() {
            assertThatThrownBy(() -> service.updateStatus(404L, "PAID"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    // ======================================================

    @Nested
    @DisplayName("取消與刪除訂單")
    class CancelAndDelete {

        void givenItems() {
            OrderItem a = new OrderItem();
            a.setOrderId(1L);
            a.setProductId(2L);
            a.setQuantity(1);
            a.setPrice(BigDecimal.TEN);
            OrderItem b = new OrderItem();
            b.setOrderId(1L);
            b.setProductId(1L);
            b.setQuantity(3);
            b.setPrice(BigDecimal.TEN);
            savedItems.addAll(List.of(a, b));
        }

        @Test
        @DisplayName("會員取消待付款訂單 → 依商品 id 順序加回庫存，再刪明細與主檔")
        void cancelPending() {
            givenItems();
            when(orderMapper.selectOne(any())).thenReturn(order(1L, "PENDING"));

            service.cancelMyOrder(USER, "2026111000000001");

            InOrder in = inOrder(productMapper, orderItemMapper, orderMapper);
            in.verify(productMapper).increaseStock(1L, 3);
            in.verify(productMapper).increaseStock(2L, 1);
            in.verify(orderItemMapper).delete(any(LambdaQueryWrapper.class));
            in.verify(orderMapper).deleteById(1L);
        }

        @Test
        @DisplayName("已付款的訂單會員不能自己取消")
        void cannotCancelPaid() {
            when(orderMapper.selectOne(any())).thenReturn(order(1L, "PAID"));

            assertThatThrownBy(() -> service.cancelMyOrder(USER, "2026111000000001"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("待付款");
            verify(orderMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("管理員刪除已付款訂單 → 加回庫存")
        void adminDeletePaidRestoresStock() {
            givenItems();
            when(orderMapper.selectById(1L)).thenReturn(order(1L, "PAID"));

            service.deleteOrder(1L);

            verify(productMapper).increaseStock(1L, 3);
            verify(productMapper).increaseStock(2L, 1);
            verify(orderMapper).deleteById(1L);
        }

        @Test
        @DisplayName("管理員刪除配送中訂單 → 花已出貨，不加回庫存")
        void adminDeleteDeliveringNoRestore() {
            givenItems();
            when(orderMapper.selectById(1L)).thenReturn(order(1L, "DELIVERING"));

            service.deleteOrder(1L);

            verify(productMapper, never()).increaseStock(anyLong(), anyInt());
            verify(orderMapper).deleteById(eq(1L));
        }
    }
}
