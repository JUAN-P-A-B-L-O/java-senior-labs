package com.jpcore.labs.paymentprocessor.outbox;

import com.jpcore.labs.paymentprocessor.payment.PaymentLogContext;
import com.jpcore.labs.paymentprocessor.payment.PaymentProcessedMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@ConditionalOnProperty(name = "payment-processor.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaOutboxEventPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxEventPublisherJob.class);
    private final OutboxEventService outboxEventService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final Duration publishTimeout;

    public KafkaOutboxEventPublisherJob(
            OutboxEventService outboxEventService,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${payment-processor.kafka.payment-processed-topic:payment.processed}") String topic,
            @Value("${payment-processor.kafka.publish-timeout:15s}") Duration publishTimeout
    ) {
        this.outboxEventService = outboxEventService;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishTimeout = publishTimeout;
    }

    @Scheduled(fixedDelayString = "${outbox.kafka-publisher.fixed-delay:30000}", scheduler = "kafkaOutboxScheduler")
    public void publishWaitingEvents() {
        for (OutboxEventEntity event : outboxEventService.findWaitingKafkaPublish()) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            publish(event);
        }
    }

    private void publish(OutboxEventEntity event) {
        try (PaymentLogContext ignored = PaymentLogContext.with(null, event.getAggregateId())) {
            try {
                PaymentProcessedMessage message = (PaymentProcessedMessage) outboxEventService.toMessage(event);
                ignored.requestedBy(message.requestedBy());
                try (PaymentLogContext ignoredWithTrace = PaymentLogContext.with(message.traceId(), event.getAggregateId())) {
                    ProducerRecord<String, String> record = new ProducerRecord<>(
                            topic, event.getAggregateId().toString(), event.getPayload());
                    record.headers().add("eventType", OutboxEventService.PAYMENT_PROCESSED.getBytes(StandardCharsets.UTF_8));
                    if (message.traceParent() != null && !message.traceParent().isBlank()) {
                        record.headers().add("traceparent", message.traceParent().getBytes(StandardCharsets.UTF_8));
                    }
                    kafkaTemplate.send(record).get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    outboxEventService.markPublished(event);
                    log.info("Kafka payment event published. eventId={} topic={}", event.getEventId(), topic);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("Kafka publication interrupted. eventId={}", event.getEventId());
            } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                log.error("Could not publish Kafka outbox event. eventId={} topic={}",
                        event.getEventId(), topic, exception);
            }
        }
    }
}
