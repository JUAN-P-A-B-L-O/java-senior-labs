package com.jpcore.labs.payment.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "spring.cache", name = "type", havingValue = "redis")
public class PaymentCacheInvalidationListener implements MessageListener {

    private final PaymentCacheInvalidationService paymentCacheInvalidationService;

    public PaymentCacheInvalidationListener(PaymentCacheInvalidationService paymentCacheInvalidationService) {
        this.paymentCacheInvalidationService = paymentCacheInvalidationService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        paymentCacheInvalidationService.evictPayment(new String(message.getBody(), StandardCharsets.UTF_8));
    }
}
