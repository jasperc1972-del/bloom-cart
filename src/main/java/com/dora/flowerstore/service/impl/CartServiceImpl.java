package com.dora.flowerstore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dora.flowerstore.common.BusinessException;
import com.dora.flowerstore.common.SessionKeys;
import com.dora.flowerstore.dto.AddToCartRequest;
import com.dora.flowerstore.dto.CartItemView;
import com.dora.flowerstore.dto.CartView;
import com.dora.flowerstore.dto.SessionCartItem;
import com.dora.flowerstore.dto.UpdateCartItemRequest;
import com.dora.flowerstore.entity.CartItem;
import com.dora.flowerstore.entity.Product;
import com.dora.flowerstore.enums.WrappingOption;
import com.dora.flowerstore.mapper.CartItemMapper;
import com.dora.flowerstore.mapper.ProductMapper;
import com.dora.flowerstore.service.CartService;
import com.dora.flowerstore.service.ProductImageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.WebUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    /** 卡片附言字數上限（PRD 4.2） */
    private static final int CARD_MESSAGE_LIMIT = 100;

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final ProductImageService imageService;

    public CartServiceImpl(CartItemMapper cartItemMapper, ProductMapper productMapper, ProductImageService imageService) {
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
        this.imageService = imageService;
    }

    /** 客製化選項（正規化後），用來判斷兩筆項目是否可以合併 */
    private record Options(WrappingOption wrapping, String cardMessage, String cardSignature, boolean anonymous) {

        static Options of(String wrapping, String cardMessage, String cardSignature, Boolean anonymous) {
            boolean anon = Boolean.TRUE.equals(anonymous);
            String msg = trimToNull(cardMessage);
            if (msg != null && msg.codePointCount(0, msg.length()) > CARD_MESSAGE_LIMIT) {
                throw new BusinessException("卡片附言上限 " + CARD_MESSAGE_LIMIT + " 字");
            }
            // 匿名送花就不保留署名
            String sig = anon ? null : trimToNull(cardSignature);
            return new Options(WrappingOption.from(wrapping), msg, sig, anon);
        }

        boolean sameAs(String w, String msg, String sig, Boolean anon) {
            return wrapping.name().equals(w)
                    && Objects.equals(cardMessage, msg)
                    && Objects.equals(cardSignature, sig)
                    && anonymous == Boolean.TRUE.equals(anon);
        }
    }

    // ==================== 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addItemToCart(Long userId, AddToCartRequest req) {
        Product product = requireOnSale(req.productId());
        Options opt = Options.of(req.wrappingOption(), req.cardMessage(), req.cardSignature(), req.anonymous());
        addToDb(userId, product, req.quantity(), opt, false);
    }

    @Override
    public void addItem(Long userId, HttpSession session, AddToCartRequest req) {
        if (userId != null) {
            addItemToCart(userId, req);
            return;
        }
        Product product = requireOnSale(req.productId());
        Options opt = Options.of(req.wrappingOption(), req.cardMessage(), req.cardSignature(), req.anonymous());
        synchronized (WebUtils.getSessionMutex(session)) {
            List<SessionCartItem> cart = guestCart(session);
            // 相同商品 + 相同客製化選項 → 合併數量
            int idx = indexOfSame(cart, product.getId(), opt);
            int newQty = req.quantity() + (idx >= 0 ? cart.get(idx).quantity() : 0);
            checkStock(product, newQty);
            if (idx >= 0) {
                cart.set(idx, cart.get(idx).withQuantity(newQty));
            } else {
                cart.add(new SessionCartItem(nextGuestId(session), product.getId(), newQty,
                        opt.wrapping().name(), opt.cardMessage(), opt.cardSignature(), opt.anonymous()));
            }
            session.setAttribute(SessionKeys.GUEST_CART, cart);
        }
    }

    /**
     * 寫入資料庫購物車；capToStock = true 時（登入合併）數量超過庫存就自動調降而不是報錯
     */
    private void addToDb(Long userId, Product product, int quantity, Options opt, boolean capToStock) {
        List<CartItem> sameProduct = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, product.getId()));
        CartItem existing = sameProduct.stream()
                .filter(c -> opt.sameAs(c.getWrappingOption(), c.getCardMessage(), c.getCardSignature(), c.getAnonymous()))
                .findFirst().orElse(null);

        int newQty = quantity + (existing != null ? existing.getQuantity() : 0);
        if (capToStock) {
            newQty = Math.min(newQty, product.getStock());
            if (newQty <= 0) {
                return;
            }
        } else {
            checkStock(product, newQty);
        }

        if (existing != null) {
            existing.setQuantity(newQty);
            cartItemMapper.updateById(existing);
        } else {
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setProductId(product.getId());
            item.setQuantity(newQty);
            item.setWrappingOption(opt.wrapping().name());
            item.setCardMessage(opt.cardMessage());
            item.setCardSignature(opt.cardSignature());
            item.setAnonymous(opt.anonymous());
            cartItemMapper.insert(item);
        }
    }

    // ==================== 查詢 ====================

    @Override
    public CartView getCart(Long userId, HttpSession session) {
        List<SessionCartItem> raw;
        if (userId != null) {
            raw = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                            .eq(CartItem::getUserId, userId).orderByAsc(CartItem::getId))
                    .stream()
                    .map(c -> new SessionCartItem(c.getId(), c.getProductId(), c.getQuantity(), c.getWrappingOption(),
                            c.getCardMessage(), c.getCardSignature(), Boolean.TRUE.equals(c.getAnonymous())))
                    .toList();
        } else {
            raw = session == null ? List.of() : List.copyOf(guestCart(session));
        }
        if (raw.isEmpty()) {
            return new CartView(List.of(), 0, BigDecimal.ZERO.setScale(2));
        }

        // 一次撈出所有相關商品
        List<Long> ids = raw.stream().map(SessionCartItem::productId).distinct().toList();
        Map<Long, Product> products = productMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<CartItemView> views = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        int totalQty = 0;
        for (SessionCartItem it : raw) {
            Product p = products.get(it.productId());
            if (p == null) {
                continue;
            }
            WrappingOption w = WrappingOption.from(it.wrappingOption());
            // 單項總價 = (花卉基礎單價 + 包裝加價) × 數量
            BigDecimal subtotal = p.getBasePrice().add(w.getFee()).multiply(BigDecimal.valueOf(it.quantity()));
            boolean available = p.getStatus() != null && p.getStatus() == 1 && p.getStock() >= it.quantity();
            views.add(new CartItemView(it.id(), p.getId(), p.getProductCode(), p.getName(), imageService.resolveMain(p),
                    p.getBasePrice(), w.name(), w.getLabel(), w.getFee(), it.quantity(),
                    it.cardMessage(), it.cardSignature(), it.anonymous(), subtotal, p.getStock(), available));
            total = total.add(subtotal);
            totalQty += it.quantity();
        }
        return new CartView(views, totalQty, total);
    }

    // ==================== 修改 / 刪除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long userId, HttpSession session, Long itemId, UpdateCartItemRequest req) {
        Options opt = Options.of(req.wrappingOption(), req.cardMessage(), req.cardSignature(), req.anonymous());
        if (userId != null) {
            CartItem item = requireOwnedItem(userId, itemId);
            checkStock(productMapper.selectById(item.getProductId()), req.quantity());
            item.setQuantity(req.quantity());
            item.setWrappingOption(opt.wrapping().name());
            item.setCardMessage(opt.cardMessage());
            item.setCardSignature(opt.cardSignature());
            item.setAnonymous(opt.anonymous());
            cartItemMapper.updateById(item);
            return;
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            List<SessionCartItem> cart = guestCart(session);
            int idx = indexOfId(cart, itemId);
            SessionCartItem old = cart.get(idx);
            checkStock(productMapper.selectById(old.productId()), req.quantity());
            cart.set(idx, new SessionCartItem(old.id(), old.productId(), req.quantity(), opt.wrapping().name(),
                    opt.cardMessage(), opt.cardSignature(), opt.anonymous()));
            session.setAttribute(SessionKeys.GUEST_CART, cart);
        }
    }

    @Override
    public void removeItem(Long userId, HttpSession session, Long itemId) {
        if (userId != null) {
            requireOwnedItem(userId, itemId);
            cartItemMapper.deleteById(itemId);
            return;
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            List<SessionCartItem> cart = guestCart(session);
            cart.remove(indexOfId(cart, itemId));
            session.setAttribute(SessionKeys.GUEST_CART, cart);
        }
    }

    // ==================== 登入合併 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mergeGuestCart(HttpSession session, Long userId) {
        @SuppressWarnings("unchecked")
        List<SessionCartItem> cart = (List<SessionCartItem>) session.getAttribute(SessionKeys.GUEST_CART);
        if (cart == null || cart.isEmpty()) {
            return;
        }
        for (SessionCartItem it : cart) {
            Product p = productMapper.selectById(it.productId());
            if (p == null || p.getStatus() == null || p.getStatus() != 1) {
                continue; // 已下架的商品不合併
            }
            Options opt = Options.of(it.wrappingOption(), it.cardMessage(), it.cardSignature(), it.anonymous());
            addToDb(userId, p, it.quantity(), opt, true);
        }
        session.removeAttribute(SessionKeys.GUEST_CART);
        session.removeAttribute(SessionKeys.GUEST_CART_SEQ);
    }

    // ==================== 工具方法 ====================

    private Product requireOnSale(Long productId) {
        Product p = productMapper.selectById(productId);
        if (p == null || p.getStatus() == null || p.getStatus() != 1) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "找不到這款花卉，可能已下架");
        }
        return p;
    }

    private static void checkStock(Product p, int qty) {
        if (p == null || p.getStatus() == null || p.getStatus() != 1) {
            throw new BusinessException("此花卉已下架");
        }
        if (qty > p.getStock()) {
            throw new BusinessException("「" + p.getName() + "」庫存不足，目前剩 " + p.getStock() + " 件");
        }
    }

    private CartItem requireOwnedItem(Long userId, Long itemId) {
        CartItem item = cartItemMapper.selectById(itemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "購物車項目不存在");
        }
        return item;
    }

    @SuppressWarnings("unchecked")
    private static List<SessionCartItem> guestCart(HttpSession session) {
        Object o = session.getAttribute(SessionKeys.GUEST_CART);
        return o == null ? new ArrayList<>() : new ArrayList<>((List<SessionCartItem>) o);
    }

    private static long nextGuestId(HttpSession session) {
        Long seq = (Long) session.getAttribute(SessionKeys.GUEST_CART_SEQ);
        long next = seq == null ? 1L : seq + 1;
        session.setAttribute(SessionKeys.GUEST_CART_SEQ, next);
        return next;
    }

    private static int indexOfSame(List<SessionCartItem> cart, Long productId, Options opt) {
        for (int i = 0; i < cart.size(); i++) {
            SessionCartItem c = cart.get(i);
            if (c.productId().equals(productId)
                    && opt.sameAs(c.wrappingOption(), c.cardMessage(), c.cardSignature(), c.anonymous())) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfId(List<SessionCartItem> cart, Long id) {
        for (int i = 0; i < cart.size(); i++) {
            if (cart.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new BusinessException(HttpStatus.NOT_FOUND, "購物車項目不存在");
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
