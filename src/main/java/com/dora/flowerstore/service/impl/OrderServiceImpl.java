package com.dora.flowerstore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dora.flowerstore.common.BusinessException;
import com.dora.flowerstore.dto.CheckoutRequest;
import com.dora.flowerstore.dto.OrderItemView;
import com.dora.flowerstore.dto.OrderView;
import com.dora.flowerstore.dto.ShipmentRequest;
import com.dora.flowerstore.entity.CartItem;
import com.dora.flowerstore.entity.Order;
import com.dora.flowerstore.entity.OrderItem;
import com.dora.flowerstore.entity.Product;
import com.dora.flowerstore.entity.User;
import com.dora.flowerstore.enums.DeliveryTimeSlot;
import com.dora.flowerstore.event.OrderDeliveredEvent;
import com.dora.flowerstore.event.OrderPaidEvent;
import com.dora.flowerstore.event.OrdersPlacedEvent;
import com.dora.flowerstore.enums.OrderStatus;
import com.dora.flowerstore.enums.WrappingOption;
import com.dora.flowerstore.mapper.CartItemMapper;
import com.dora.flowerstore.mapper.OrderItemMapper;
import com.dora.flowerstore.mapper.OrderMapper;
import com.dora.flowerstore.mapper.ProductMapper;
import com.dora.flowerstore.mapper.UserMapper;
import com.dora.flowerstore.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    /** 訂單號流水號（起始值隨機，降低重啟後撞號機率；order_no 另有 UNIQUE 保護） */
    private static final AtomicInteger SEQ = new AtomicInteger(ThreadLocalRandom.current().nextInt(10000));

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;
    private final int minDaysAhead;
    private final int maxDaysAhead;

    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper, CartItemMapper cartItemMapper,
                            ProductMapper productMapper, UserMapper userMapper, ApplicationEventPublisher events,
                            @Value("${app.delivery.min-days-ahead}") int minDaysAhead,
                            @Value("${app.delivery.max-days-ahead}") int maxDaysAhead) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.events = events;
        this.minDaysAhead = minDaysAhead;
        this.maxDaysAhead = maxDaysAhead;
    }

    // ==================== 結帳 ====================

    /**
     * 結帳交易（PRD 4.3 / 5.1），支援多位收件人拆單：
     * 1. 驗證每位收件人的配送日期與時段
     * 2. 檢查購物車每一項都剛好分配給一位收件人
     * 3. 依商品 id 排序、合併數量後原子扣庫存（UPDATE ... WHERE stock >= qty），任一項不足就拋例外 → 整筆回滾
     * 4. 每位收件人寫入一張訂單（共用結帳批號）與明細（單價、包裝、卡片皆為快照）
     * 5. 清空購物車
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OrderView> checkout(Long userId, CheckoutRequest req) {
        for (ShipmentRequest s : req.shipments()) {
            validateDelivery(s.deliveryDate(), s.deliveryTimeSlot());
        }

        List<CartItem> cart = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId));
        if (cart.isEmpty()) {
            throw new BusinessException("購物車是空的");
        }
        Map<Long, CartItem> cartById = cart.stream()
                .collect(Collectors.toMap(CartItem::getId, c -> c, (a, b) -> a, LinkedHashMap::new));
        validateAssignment(req.shipments(), cartById);

        // 每個商品只查一次，並確認都還在上架
        Map<Long, Product> products = new HashMap<>();
        for (CartItem c : cart) {
            Product p = products.computeIfAbsent(c.getProductId(), productMapper::selectById);
            if (p == null || p.getStatus() == null || p.getStatus() != 1) {
                throw new BusinessException("購物車中有已下架的商品，請先移除");
            }
        }

        // 同商品可能分給不同收件人：先合併數量，再依商品 id 排序扣庫存，讓多筆交易鎖定順序一致，避免死結
        Map<Long, Integer> qtyByProduct = new TreeMap<>();
        cart.forEach(c -> qtyByProduct.merge(c.getProductId(), c.getQuantity(), Integer::sum));
        qtyByProduct.forEach((productId, qty) -> {
            if (productMapper.decreaseStock(productId, qty) == 0) {
                // 用鎖定讀取得最新已提交的庫存（一般 SELECT 會讀到交易開始時的舊快照）
                Integer latest = productMapper.selectCurrentStock(productId);
                throw new BusinessException(HttpStatus.CONFLICT,
                        "「" + products.get(productId).getName() + "」庫存不足，目前剩 " + latest + " 件，請調整數量");
            }
        });

        String batchNo = "B" + nextOrderNo();
        List<Long> orderIds = new ArrayList<>();
        for (ShipmentRequest s : req.shipments()) {
            List<OrderItem> orderItems = s.cartItemIds().stream()
                    .map(cartById::get)
                    .sorted(Comparator.comparing(CartItem::getProductId))
                    .map(c -> toOrderItem(c, products.get(c.getProductId())))
                    .toList();
            // 單項總價 = (花卉基礎單價 + 包裝加價) × 數量
            BigDecimal total = orderItems.stream()
                    .map(oi -> oi.getPrice().add(oi.getWrappingFee()).multiply(BigDecimal.valueOf(oi.getQuantity())))
                    .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

            Order order = new Order();
            order.setOrderNo(nextOrderNo());
            order.setBatchNo(batchNo);
            order.setUserId(userId);
            order.setTotalAmount(total);
            order.setRecipientName(s.recipientName().trim());
            order.setRecipientPhone(s.recipientPhone().trim());
            order.setShippingAddress(s.shippingAddress().trim());
            order.setPayerName(s.payerName().trim());
            order.setPayerPhone(s.payerPhone().trim());
            order.setPayerEmail(s.payerEmail().trim());
            order.setDeliveryDate(s.deliveryDate());
            order.setDeliveryTimeSlot(s.deliveryTimeSlot());
            order.setCardMessage(summarizeCards(orderItems));
            order.setStatus(OrderStatus.PENDING.name());
            orderMapper.insert(order);
            orderIds.add(order.getId());

            for (OrderItem oi : orderItems) {
                oi.setOrderId(order.getId());
                orderItemMapper.insert(oi);
            }
        }

        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));

        // 重新讀回，帶出資料庫產生的建立時間
        List<OrderView> views = toViews(orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getId, orderIds).orderByAsc(Order::getId)));
        // 交易提交後才寄待付款通知（見 OrderMailListener）
        events.publishEvent(new OrdersPlacedEvent(views));
        return views;
    }

    /** 購物車每一項都要剛好分配給一位收件人：不能漏、不能重複、不能是別人的或已刪除的項目 */
    private static void validateAssignment(List<ShipmentRequest> shipments, Map<Long, CartItem> cartById) {
        Set<Long> assigned = new HashSet<>();
        for (ShipmentRequest s : shipments) {
            for (Long id : s.cartItemIds()) {
                if (!cartById.containsKey(id)) {
                    throw new BusinessException("購物車內容已變更，請重新整理結帳頁");
                }
                if (!assigned.add(id)) {
                    throw new BusinessException("同一項商品不能同時分給兩位收件人");
                }
            }
        }
        if (assigned.size() != cartById.size()) {
            throw new BusinessException("還有商品沒有指定收件人");
        }
    }

    /** 購物車項目 → 訂單明細快照 */
    private static OrderItem toOrderItem(CartItem c, Product p) {
        WrappingOption w = WrappingOption.from(c.getWrappingOption());
        OrderItem oi = new OrderItem();
        oi.setProductId(p.getId());
        oi.setProductName(p.getName());
        oi.setPrice(p.getBasePrice());
        oi.setWrappingFee(w.getFee());
        oi.setWrappingOption(w.name());
        oi.setQuantity(c.getQuantity());
        oi.setCardMessage(c.getCardMessage());
        oi.setCardSignature(c.getCardSignature());
        oi.setAnonymous(Boolean.TRUE.equals(c.getAnonymous()));
        return oi;
    }

    private void validateDelivery(LocalDate date, String slot) {
        LocalDate today = LocalDate.now(TAIPEI);
        LocalDate min = today.plusDays(minDaysAhead);
        LocalDate max = today.plusDays(maxDaysAhead);
        if (date.isBefore(min) || date.isAfter(max)) {
            throw new BusinessException("指定送達日期需在 " + min + " 到 " + max + " 之間");
        }
        if (!DeliveryTimeSlot.isValid(slot)) {
            throw new BusinessException("配送時段只能選 " + String.join(" / ", DeliveryTimeSlot.labels()));
        }
    }

    /** 訂單號：yyyyMMddHHmm + 4 位流水號，例如 2026031509300001 */
    private static String nextOrderNo() {
        int seq = Math.floorMod(SEQ.incrementAndGet(), 10000);
        return LocalDateTime.now(TAIPEI).format(ORDER_NO_TIME) + String.format("%04d", seq);
    }

    /** 把各品項卡片彙整成訂單層級的快照，超過欄位長度就截斷 */
    private static String summarizeCards(List<OrderItem> items) {
        String joined = items.stream()
                .filter(i -> i.getCardMessage() != null)
                .map(i -> i.getProductName() + "：" + i.getCardMessage()
                        + (Boolean.TRUE.equals(i.getAnonymous()) ? "（匿名）"
                        : i.getCardSignature() != null ? "—" + i.getCardSignature() : ""))
                .collect(Collectors.joining(" | "));
        if (joined.isEmpty()) {
            return null;
        }
        return joined.length() > 255 ? joined.substring(0, 252) + "..." : joined;
    }

    // ==================== 查詢 ====================

    @Override
    public List<OrderView> listMyOrders(Long userId) {
        return toViews(orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId).orderByDesc(Order::getId)));
    }

    @Override
    public OrderView getMyOrder(Long userId, String orderNo) {
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo).eq(Order::getUserId, userId));
        if (o == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "訂單不存在");
        }
        return toViews(List.of(o)).getFirst();
    }

    @Override
    public List<OrderView> listAll(String status) {
        LambdaQueryWrapper<Order> q = new LambdaQueryWrapper<Order>().orderByDesc(Order::getId);
        if (status != null && !status.isBlank()) {
            q.eq(Order::getStatus, OrderStatus.from(status).name());
        }
        return toViews(orderMapper.selectList(q));
    }

    @Override
    public OrderView updateStatus(Long orderId, String status) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "訂單不存在");
        }
        OrderStatus current = OrderStatus.from(o.getStatus());
        OrderStatus next = OrderStatus.from(status);
        // 只能往後推進：待付款 → 已付款 → 配送中 → 已送達
        if (next.ordinal() <= current.ordinal()) {
            throw new BusinessException("訂單狀態只能往後推進，目前為「" + current.getLabel() + "」");
        }
        o.setStatus(next.name());
        orderMapper.updateById(o);
        OrderView view = toViews(List.of(o)).getFirst();
        if (next == OrderStatus.PAID) {
            events.publishEvent(new OrderPaidEvent(view));
        } else if (next == OrderStatus.COMPLETED) {
            events.publishEvent(new OrderDeliveredEvent(view));
        }
        return view;
    }

    // ==================== 刪除 / 取消 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "訂單不存在");
        }
        OrderStatus s = OrderStatus.from(o.getStatus());
        // 配送中 / 已送達的花已經出貨，不加回庫存
        boolean restoreStock = s == OrderStatus.PENDING || s == OrderStatus.PAID;
        removeOrder(o, restoreStock);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelMyOrder(Long userId, String orderNo) {
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo).eq(Order::getUserId, userId));
        if (o == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "訂單不存在");
        }
        if (OrderStatus.from(o.getStatus()) != OrderStatus.PENDING) {
            throw new BusinessException("只有「待付款」的訂單可以取消，已付款請聯絡花店");
        }
        removeOrder(o, true);
    }

    /** 同一交易內：（選擇性）加回庫存 → 刪明細 → 刪主檔（外鍵限制，必須先刪明細） */
    private void removeOrder(Order o, boolean restoreStock) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, o.getId()));
        if (restoreStock) {
            // 依商品 id 排序後加回，與結帳扣庫存的鎖定順序一致，避免死結
            items.stream()
                    .sorted(Comparator.comparing(OrderItem::getProductId))
                    .forEach(i -> productMapper.increaseStock(i.getProductId(), i.getQuantity()));
        }
        orderItemMapper.delete(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, o.getId()));
        orderMapper.deleteById(o.getId());
    }

    /** 批次組裝訂單顯示資料（一次查明細與會員，避免 N+1 查詢） */
    private List<OrderView> toViews(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrder = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, orderIds).orderByAsc(OrderItem::getId))
                .stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        List<Long> userIds = orders.stream().map(Order::getUserId).distinct().toList();
        Map<Long, String> usernames = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        return orders.stream().map(o -> {
            List<OrderItemView> items = itemsByOrder.getOrDefault(o.getId(), List.of()).stream()
                    .map(this::toItemView).toList();
            OrderStatus s = OrderStatus.from(o.getStatus());
            return new OrderView(o.getId(), o.getOrderNo(), o.getBatchNo(), usernames.get(o.getUserId()),
                    o.getTotalAmount(), o.getRecipientName(), o.getRecipientPhone(), o.getShippingAddress(),
                    o.getPayerName(), o.getPayerPhone(), o.getPayerEmail(),
                    o.getDeliveryDate(), o.getDeliveryTimeSlot(), o.getCardMessage(),
                    s.name(), s.getLabel(), o.getCreatedAt(), items);
        }).toList();
    }

    private OrderItemView toItemView(OrderItem i) {
        WrappingOption w = WrappingOption.from(i.getWrappingOption());
        BigDecimal fee = i.getWrappingFee() == null ? BigDecimal.ZERO : i.getWrappingFee();
        BigDecimal subtotal = i.getPrice().add(fee).multiply(BigDecimal.valueOf(i.getQuantity()));
        return new OrderItemView(i.getProductId(), i.getProductName(), i.getPrice(), fee, w.name(), w.getLabel(),
                i.getQuantity(), i.getCardMessage(), i.getCardSignature(), Boolean.TRUE.equals(i.getAnonymous()),
                subtotal);
    }
}
