package com.jpcore.labs.paymentprocessor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PaymentProcessorApplicationTests {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext context;

    @Test
    void contextLoads() {
        org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(
                com.jpcore.labs.paymentprocessor.outbox.OutboxEventPublisherJob.class)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(
                com.jpcore.labs.paymentprocessor.outbox.KafkaOutboxEventPublisherJob.class)).isEmpty();
    }
}
