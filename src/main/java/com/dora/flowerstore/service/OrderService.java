package com.dora.flowerstore.service;

import com.dora.flowerstore.dto.CheckoutRequest;
import com.dora.flowerstore.dto.OrderView;

import java.util.List;

public interface OrderService {

    /** 結帳：依收件人拆成多張訂單、扣庫存、清空購物車，全部在同一個交易內 */
    List<OrderView> checkout(Long userId, CheckoutRequest request);

    List<OrderView> listMyOrders(Long userId);

    OrderView getMyOrder(Long userId, String orderNo);

    /** 後台：全部訂單，可依狀態篩選 */
    List<OrderView> listAll(String status);

    OrderView updateStatus(Long orderId, String status);

    /** 後台刪除訂單；尚未出貨（待付款 / 已付款）會把庫存加回去 */
    void deleteOrder(Long orderId);

    /** 會員取消自己「待付款」的訂單（刪除並加回庫存） */
    void cancelMyOrder(Long userId, String orderNo);
}
