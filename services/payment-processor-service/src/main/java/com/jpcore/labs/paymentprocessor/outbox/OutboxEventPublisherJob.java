package com.jpcore.labs.paymentprocessor.outbox;

import com.jpcore.labs.paymentprocessor.config.RabbitMqConfig;
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
            Object message = outboxEventService.toMessage(outboxEvent);
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.PAYMENT_EXCHANGE,
                    routingKey(outboxEvent),
                    message,
                    new CorrelationData(outboxEvent.getEventId().toString())
            );
            outboxEventService.markPublished(outboxEvent);
        } catch (RuntimeException exception) {
            log.error("Could not publish processor outbox event. eventId={}", outboxEvent.getEventId(), exception);
        }
    }

    private String routingKey(OutboxEventEntity outboxEvent) {
        if (OutboxEventService.PAYMENT_PROCESSED.equals(outboxEvent.getEventType())) {
            return RabbitMqConfig.PAYMENT_PROCESSED_ROUTING_KEY;
        }
        if (OutboxEventService.PAYMENT_PROCESSING_FAILED.equals(outboxEvent.getEventType())) {
            return RabbitMqConfig.PAYMENT_PROCESSING_FAILED_ROUTING_KEY;
        }
        throw new IllegalStateException("Unknown outbox event type: " + outboxEvent.getEventType());
    }
}
