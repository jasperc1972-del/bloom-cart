package com.dora.flowerstore.service;

import com.dora.flowerstore.dto.OrderItemView;
import com.dora.flowerstore.dto.OrderView;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OrderMailService 訂單通知信")
class OrderMailServiceTest {

    JavaMailSender sender;
    ObjectProvider<JavaMailSender> provider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
    }

    OrderMailService service(String from, String bankAccount) {
        return new OrderMailService(provider, from, "http://shop.test/", "測試銀行", bankAccount, "朵拉花店");
    }

    static OrderView order(String no, String recipient, String payer, String email, String amount) {
        OrderItemView item = new OrderItemView(1L, "粉鬱金香春日花束", new BigDecimal(amount), BigDecimal.ZERO,
                "STANDARD", "牛皮紙經典包裝", 1, null, null, false, new BigDecimal(amount));
        return new OrderView(1L, no, "B1", "member_a", new BigDecimal(amount), recipient, "0900-000-001", "測試市 A 路 1 號",
                payer, "0900-000-002", email, LocalDate.of(2026, 11, 11), "09:00-12:00", null,
                "PENDING", "待付款", null, List.of(item));
    }

    String body(MimeMessage m) throws Exception {
        return m.getContent().toString();
    }

    @Test
    @DisplayName("同一付款人兩張訂單 → 合併成一封，列出兩張訂單、總額與匯款資訊")
    void samePayerMerged() throws Exception {
        service("shop@test.com", "123-456-789").sendPaymentNotice(List.of(
                order("N1", "mom", "陳先生", "buyer@test.com", "1730"),
                order("N2", "daughter", "陳先生", "BUYER@test.com ", "780")));

        ArgumentCaptor<MimeMessage> cap = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, times(1)).send(cap.capture());
        MimeMessage m = cap.getValue();
        assertThat(((InternetAddress) m.getAllRecipients()[0]).getAddress()).isEqualTo("buyer@test.com");
        assertThat(m.getSubject()).contains("待付款").contains("2 張").contains("NT$2,510");
        String text = body(m);
        assertThat(text).contains("N1").contains("N2").contains("mom").contains("daughter")
                .contains("應付總額：NT$2,510")
                .contains("帳號：123-456-789")
                .contains("匯款備註請填寫訂單編號：N1、N2")
                .contains("http://shop.test/orders.html");
    }

    @Test
    @DisplayName("兩位不同付款人 → 各寄一封")
    void differentPayers() {
        service("shop@test.com", "123").sendPaymentNotice(List.of(
                order("N1", "mom", "陳先生", "buyer@test.com", "1730"),
                order("N2", "daughter", "媽媽", "mama@test.com", "780")));

        verify(sender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("沒設定匯款帳號 → 信中改寫「付款方式請洽朵拉花店」")
    void noBankAccount() throws Exception {
        service("shop@test.com", "").sendPaymentNotice(List.of(order("N1", "mom", "陳先生", "a@test.com", "100")));

        ArgumentCaptor<MimeMessage> cap = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(cap.capture());
        assertThat(body(cap.getValue())).contains("付款方式請洽朵拉花店").doesNotContain("匯款資訊");
    }

    @Test
    @DisplayName("沒設定寄件人（沒有 config/mail.yml）→ 不寄信")
    void notConfigured() {
        service("", "123").sendPaymentNotice(List.of(order("N1", "mom", "陳先生", "a@test.com", "100")));

        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("SMTP 寄送失敗 → 不往外拋例外（不影響訂單）")
    void sendFailureSwallowed() {
        doThrow(new MailSendException("smtp down")).when(sender).send(any(MimeMessage.class));

        assertThatCode(() -> service("shop@test.com", "123")
                .sendPaymentNotice(List.of(order("N1", "mom", "陳先生", "a@test.com", "100"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("收款確認信：主旨含訂單編號")
    void paidConfirmation() throws Exception {
        service("shop@test.com", "123").sendPaidConfirmation(order("N9", "mom", "陳先生", "a@test.com", "100"));

        ArgumentCaptor<MimeMessage> cap = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(cap.capture());
        assertThat(cap.getValue().getSubject()).contains("已收到款項").contains("N9");
        assertThat(body(cap.getValue())).contains("已收到您的款項");
    }

    @Test
    @DisplayName("送達通知信：主旨含訂單編號，內文含收件人與卡片內容")
    void deliveredNotice() throws Exception {
        OrderItemView item = new OrderItemView(1L, "向日葵陽光花束", new BigDecimal("980"), BigDecimal.ZERO,
                "STANDARD", "牛皮紙經典包裝", 1, "dear happy birthday", "爸爸", false, new BigDecimal("980"));
        OrderView o = new OrderView(1L, "N7", "B1", "林小姐", new BigDecimal("980"), "daughter", "0900-000-003", "測試市 B 路 2 號",
                "林小姐", "0900-000-004", "a@test.com", LocalDate.of(2026, 11, 11), "09:00-12:00", null,
                "COMPLETED", "已送達", null, List.of(item));

        service("shop@test.com", "123").sendDeliveredNotice(o);

        ArgumentCaptor<MimeMessage> cap = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(cap.capture());
        assertThat(cap.getValue().getSubject()).contains("花禮已送達").contains("N7");
        assertThat(body(cap.getValue())).contains("已經送達 daughter")
                .contains("向日葵陽光花束：dear happy birthday — 爸爸");
    }

    @Test
    @DisplayName("舊訂單沒有付款人 Email → 不寄")
    void noPayerEmail() {
        service("shop@test.com", "123").sendPaidConfirmation(order("N9", "mom", "陳先生", null, "100"));
        service("shop@test.com", "123").sendDeliveredNotice(order("N9", "mom", "陳先生", null, "100"));
        service("shop@test.com", "123").sendPaymentNotice(List.of(order("N9", "mom", "陳先生", " ", "100")));

        verify(sender, never()).send(any(MimeMessage.class));
    }
}
