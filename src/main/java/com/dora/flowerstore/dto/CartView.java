package com.dora.flowerstore.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 整台購物車
 */
public record CartView(List<CartItemView> items, int totalQuantity, BigDecimal totalAmount) {
}
