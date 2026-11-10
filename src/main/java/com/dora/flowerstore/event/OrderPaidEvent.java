package com.dora.flowerstore.event;

import com.dora.flowerstore.dto.OrderView;

/**
 * 管理員把訂單標記為「已付款」事件（寄收款確認信）
 */
public record OrderPaidEvent(OrderView order) {
}
