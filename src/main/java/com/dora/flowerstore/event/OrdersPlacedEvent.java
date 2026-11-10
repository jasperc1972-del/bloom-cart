package com.dora.flowerstore.event;

import com.dora.flowerstore.dto.OrderView;

import java.util.List;

/**
 * 結帳成功事件：同一次結帳拆出的所有訂單（交易提交後才寄待付款通知）
 */
public record OrdersPlacedEvent(List<OrderView> orders) {
}
