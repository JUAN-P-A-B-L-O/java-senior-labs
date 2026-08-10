package com.jpcore.labs.payment.outbox;

import com.jpcore.labs.payment.config.RabbitMqConfig;
import com.jpcore.labs.payment.payment.PaymentRequestedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OutboxEventPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisherJob.class);

    private final OutboxEventService outboxEventService;
    private final RabbitTemplate rabbitTemplate;

    public OutboxEventPublisherJob(OutboxEventService outboxEventService, RabbitTemplate rabbitTemplate) {
        this.outboxEventService = outboxEventService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay:30000}")
    public void publishWaitingEvents() {
        outboxEventService.findWaitingPublish()
                .forEach(this::publish);
    }

    private void publish(OutboxEventEntity outboxEvent) {
        try {
            PaymentRequestedMessage message = outboxEventService.toPaymentRequestedMessage(outboxEvent);
            CorrelationData correlationData = new CorrelationData(outboxEvent.getEventId().toString());

            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.PAYMENT_EXCHANGE,
                    RabbitMqConfig.PAYMENT_PROCESS_ROUTING_KEY,
                    message,
                    correlationData
            );
            outboxEventService.markPublished(outboxEvent);
        } catch (RuntimeException exception) {
            log.error("Could not publish outbox event. eventId={}", outboxEvent.getEventId(), exception);
        }
    }
}
