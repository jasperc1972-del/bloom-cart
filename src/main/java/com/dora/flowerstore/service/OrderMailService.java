package com.dora.flowerstore.service;

import com.dora.flowerstore.dto.OrderItemView;
import com.dora.flowerstore.dto.OrderView;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 訂單通知信（純文字）：
 * - 待付款通知：同一次結帳中，同一位付款人的訂單合併成一封
 * - 收款確認：管理員標記「已付款」時寄出
 * - 送達通知：管理員標記「已送達」時寄出
 * 沒有設定 SMTP（config/mail.yml）時只記 log、不寄信；寄信失敗也不影響訂單
 */
@Service
public class OrderMailService {

    private static final Logger log = LoggerFactory.getLogger(OrderMailService.class);
    private static final String SHOP_NAME = "朵拉花店 Dora Floral";

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String from;
    private final String siteUrl;
    private final String bankName;
    private final String bankAccount;
    private final String accountName;

    public OrderMailService(ObjectProvider<JavaMailSender> senderProvider,
                            @Value("${app.mail.from:}") String from,
                            @Value("${app.site-url:}") String siteUrl,
                            @Value("${app.payment.bank-name:}") String bankName,
                            @Value("${app.payment.bank-account:}") String bankAccount,
                            @Value("${app.payment.account-name:}") String accountName) {
        this.senderProvider = senderProvider;
        this.from = from.trim();
        this.siteUrl = siteUrl.trim().replaceAll("/+$", "");
        this.bankName = bankName.trim();
        this.bankAccount = bankAccount.trim();
        this.accountName = accountName.trim();
    }

    /** 待付款通知：依付款人 Email 分組，每位付款人一封 */
    public void sendPaymentNotice(List<OrderView> orders) {
        Map<String, List<OrderView>> byPayer = orders.stream()
                .filter(o -> o.payerEmail() != null && !o.payerEmail().isBlank())
                .collect(Collectors.groupingBy(o -> o.payerEmail().trim().toLowerCase(),
                        LinkedHashMap::new, Collectors.toList()));
        byPayer.forEach((email, list) -> {
            BigDecimal total = list.stream().map(OrderView::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            String subject = "【" + SHOP_NAME + "】訂單待付款通知（" + list.size() + " 張，共 " + money(total) + "）";
            StringBuilder body = new StringBuilder()
                    .append(list.getFirst().payerName()).append(" 您好：\n\n")
                    .append("感謝您在朵拉花店訂購花禮！以下訂單目前為「待付款」，完成付款後我們會依指定時間配送。\n\n");
            list.forEach(o -> appendOrder(body, o));
            body.append("應付總額：").append(money(total)).append("\n\n");
            appendPaymentInfo(body, list);
            send(email, subject, body.toString());
        });
    }

    /** 收款確認信 */
    public void sendPaidConfirmation(OrderView o) {
        if (o.payerEmail() == null || o.payerEmail().isBlank()) {
            return;
        }
        String subject = "【" + SHOP_NAME + "】已收到款項 #" + o.orderNo();
        StringBuilder body = new StringBuilder()
                .append(o.payerName()).append(" 您好：\n\n")
                .append("我們已收到您的款項，花禮將於指定時間送達。\n\n");
        appendOrder(body, o);
        appendFooter(body);
        send(o.payerEmail().trim(), subject, body.toString());
    }

    /** 送達通知信：附上卡片內容，讓付款人知道祝福也一起送到了 */
    public void sendDeliveredNotice(OrderView o) {
        if (o.payerEmail() == null || o.payerEmail().isBlank()) {
            return;
        }
        String subject = "【" + SHOP_NAME + "】花禮已送達 #" + o.orderNo();
        StringBuilder body = new StringBuilder()
                .append(o.payerName()).append(" 您好：\n\n")
                .append("您訂購的花禮已經送達 ").append(o.recipientName()).append("，感謝您選擇朵拉花店傳遞心意！\n\n");
        appendOrder(body, o);
        List<String> cards = o.items().stream()
                .filter(i -> i.cardMessage() != null && !i.cardMessage().isBlank())
                .map(i -> "・" + i.productName() + "：" + i.cardMessage()
                        + (i.anonymous() ? "（匿名）" : i.cardSignature() != null && !i.cardSignature().isBlank()
                        ? " — " + i.cardSignature() : ""))
                .toList();
        if (!cards.isEmpty()) {
            body.append("隨花附上的卡片：\n").append(String.join("\n", cards)).append("\n\n");
        }
        body.append("期待下次再為您服務。\n\n");
        appendFooter(body);
        send(o.payerEmail().trim(), subject, body.toString());
    }

    private void appendOrder(StringBuilder b, OrderView o) {
        b.append("━━━━━━━━━━━━━━━━━━━━\n")
                .append("訂單編號：").append(o.orderNo()).append('\n')
                .append("收件人：").append(o.recipientName()).append("（").append(o.recipientPhone()).append("）\n")
                .append("地址：").append(o.shippingAddress()).append('\n')
                .append("指定送達：").append(o.deliveryDate()).append(' ').append(o.deliveryTimeSlot()).append('\n');
        for (OrderItemView i : o.items()) {
            b.append("・").append(i.productName()).append(" × ").append(i.quantity())
                    .append("（").append(i.wrappingLabel()).append("）  ").append(money(i.subtotal())).append('\n');
        }
        b.append("小計：").append(money(o.totalAmount())).append("\n\n");
    }

    private void appendPaymentInfo(StringBuilder b, List<OrderView> list) {
        if (!bankAccount.isEmpty()) {
            b.append("【匯款資訊】\n")
                    .append("銀行：").append(bankName).append('\n')
                    .append("帳號：").append(bankAccount).append('\n')
                    .append("戶名：").append(accountName).append('\n')
                    .append("匯款備註請填寫訂單編號：")
                    .append(list.stream().map(OrderView::orderNo).collect(Collectors.joining("、"))).append("\n\n");
        } else {
            b.append("付款方式請洽朵拉花店。\n\n");
        }
        appendFooter(b);
    }

    private void appendFooter(StringBuilder b) {
        if (!siteUrl.isEmpty()) {
            b.append("查看訂單：").append(siteUrl).append("/orders.html\n\n");
        }
        b.append(SHOP_NAME).append("・用花傳遞心意\n（此信由系統自動寄出，請勿直接回覆）\n");
    }

    private void send(String to, String subject, String text) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null || from.isEmpty()) {
            log.info("未設定寄信（config/mail.yml），略過寄送：{}", subject);
            return;
        }
        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setFrom(from, SHOP_NAME);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(text, false);
            sender.send(msg);
            log.info("已寄出通知信：{}", subject);
        } catch (Exception e) {
            // 寄信失敗不影響訂單，只記錄（不印收件者 Email，避免個資進 log）
            log.warn("通知信寄送失敗：{}（{}）", subject, e.getMessage());
        }
    }

    private static String money(BigDecimal n) {
        return "NT$" + String.format("%,.0f", n);
    }
}
