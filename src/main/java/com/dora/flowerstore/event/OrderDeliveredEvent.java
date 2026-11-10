package com.dora.flowerstore.event;

import com.dora.flowerstore.dto.OrderView;

/**
 * 管理員把訂單標記為「已送達」事件（寄送達通知信）
 */
public record OrderDeliveredEvent(OrderView order) {
}
