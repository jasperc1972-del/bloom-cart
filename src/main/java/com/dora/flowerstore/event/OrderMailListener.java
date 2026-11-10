package com.dora.flowerstore.event;

import com.dora.flowerstore.service.OrderMailService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 訂單事件 → 寄信
 * AFTER_COMMIT：交易提交成功才寄（回滾的訂單不會寄出）；@Async：寄信慢不拖住下單回應
 * fallbackExecution：updateStatus 沒有交易時也照樣觸發
 */
@Component
public class OrderMailListener {

    private final OrderMailService mailService;

    public OrderMailListener(OrderMailService mailService) {
        this.mailService = mailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrdersPlaced(OrdersPlacedEvent event) {
        mailService.sendPaymentNotice(event.orders());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderPaid(OrderPaidEvent event) {
        mailService.sendPaidConfirmation(event.order());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderDelivered(OrderDeliveredEvent event) {
        mailService.sendDeliveredNotice(event.order());
    }
}
