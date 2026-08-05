package com.jpcore.labs.paymentprocessor.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RabbitMqConfigTest {

    @Autowired
    private Environment environment;

    @Test
    void paymentProcessConsumerRetryIsConfigured() {
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.enabled", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.max-attempts", Integer.class))
                .isEqualTo(3);
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.initial-interval"))
                .isEqualTo("2s");
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.multiplier", Integer.class))
                .isEqualTo(1);
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.max-interval"))
                .isEqualTo("2s");
    }
}
