package com.jpcore.labs.payment.payment;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PaymentCacheInvalidationService {

    public static final String PAYMENT_CACHE = "payment";
    public static final String PAYMENT_CACHE_INVALIDATION_TOPIC = "payment.cache.invalidate";

    private final CacheManager cacheManager;
    private final ObjectProvider<StringRedisTemplate> redisTemplate;
    private final String cacheType;

    public PaymentCacheInvalidationService(
            CacheManager cacheManager,
            ObjectProvider<StringRedisTemplate> redisTemplate,
            @Value("${spring.cache.type:}") String cacheType
    ) {
        this.cacheManager = cacheManager;
        this.redisTemplate = redisTemplate;
        this.cacheType = cacheType;
    }

    public void invalidatePaymentAfterCommit(String paymentId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidatePayment(paymentId);
                }
            });
            return;
        }

        invalidatePayment(paymentId);
    }

    public void evictPayment(String paymentId) {
        Cache cache = cacheManager.getCache(PAYMENT_CACHE);
        if (cache != null) {
            cache.evict(paymentId);
        }
    }

    private void invalidatePayment(String paymentId) {
        evictPayment(paymentId);
        if ("redis".equalsIgnoreCase(cacheType)) {
            redisTemplate.ifAvailable(template ->
                    template.convertAndSend(PAYMENT_CACHE_INVALIDATION_TOPIC, paymentId)
            );
        }
    }
}
