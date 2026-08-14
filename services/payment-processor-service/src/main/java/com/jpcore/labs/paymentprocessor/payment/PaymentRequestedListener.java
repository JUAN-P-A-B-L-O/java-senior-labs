package com.jpcore.labs.paymentprocessor.payment;

import com.jpcore.labs.paymentprocessor.config.RabbitMqConfig;
import com.jpcore.labs.paymentprocessor.outbox.OutboxEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestedListener.class);

    private final PaymentAuthorizationService paymentAuthorizationService;
    private final ProcessedEventService processedEventService;
    private final OutboxEventService outboxEventService;

    public PaymentRequestedListener(
            PaymentAuthorizationService paymentAuthorizationService,
            ProcessedEventService processedEventService,
            OutboxEventService outboxEventService
    ) {
        this.paymentAuthorizationService = paymentAuthorizationService;
        this.processedEventService = processedEventService;
        this.outboxEventService = outboxEventService;
    }

    @RabbitListener(queues = RabbitMqConfig.PAYMENT_PROCESS_QUEUE)
    public void listen(PaymentRequestedMessage message) {
        if (processedEventService.isProcessed(message.eventId())) {
            log.info("PaymentRequested duplicate ignored. paymentId={} eventId={}",
                    message.paymentId(),
                    message.eventId()
            );
            return;
        }

        log.info("PaymentRequested received. paymentId={} eventId={}", message.paymentId(), message.eventId());
        try {
            try {
                paymentAuthorizationService.authorize(message);
                outboxEventService.savePaymentProcessed(message.eventId(), message.paymentId());
            } catch (PaymentAuthorizationException exception) {
                outboxEventService.savePaymentProcessingFailed(message.eventId(), message.paymentId(), exception.getMessage());
            }
            processedEventService.markProcessed(message.eventId());
        } catch (RuntimeException exception) {
            log.error("PaymentRequested processing failed. paymentId={} eventId={}",
                    message.paymentId(),
                    message.eventId(),
                    exception
            );
            throw exception;
        }
    }
}
