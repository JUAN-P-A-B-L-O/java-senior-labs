package com.jpcore.labs.payment.config;

import com.jpcore.labs.payment.payment.PaymentCacheInvalidationListener;
import com.jpcore.labs.payment.payment.PaymentCacheInvalidationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnProperty(prefix = "spring.cache", name = "type", havingValue = "redis")
public class PaymentCachePubSubConfig {

    @Bean
    ChannelTopic paymentCacheInvalidationTopic() {
        return new ChannelTopic(PaymentCacheInvalidationService.PAYMENT_CACHE_INVALIDATION_TOPIC);
    }

    @Bean
    RedisMessageListenerContainer paymentCacheInvalidationListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            PaymentCacheInvalidationListener listener,
            ChannelTopic paymentCacheInvalidationTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(listener, paymentCacheInvalidationTopic);
        return container;
    }
}
