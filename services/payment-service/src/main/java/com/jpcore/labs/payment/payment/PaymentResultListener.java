package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RabbitListener(queues = RabbitMqConfig.PAYMENT_RESULT_QUEUE)
public class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final PaymentService paymentService;
    private final PaymentResultProcessedEventService processedEventService;

    public PaymentResultListener(PaymentService paymentService, PaymentResultProcessedEventService processedEventService) {
        this.paymentService = paymentService;
        this.processedEventService = processedEventService;
    }

    @RabbitHandler
    public void listen(PaymentProcessedMessage message) {
        try (PaymentLogContext ignored = PaymentLogContext.with(message.traceId(), message.paymentId(), message.requestedBy())) {
            if (processedEventService.isProcessed(message.eventId())) {
                log.info("PaymentProcessed duplicate ignored. eventId={}", message.eventId());
                return;
            }

            log.info("PaymentProcessed received. eventId={} requestedEventId={}",
                    message.eventId(),
                    message.requestedEventId()
            );
            try {
                paymentService.completePayment(message.paymentId(), message.traceId());
                processedEventService.markProcessed(message.eventId());
            } catch (RuntimeException exception) {
                log.error("PaymentProcessed handling failed. eventId={}", message.eventId(), exception);
                throw exception;
            }
        }
    }

    @RabbitHandler
    public void listen(PaymentProcessingFailedMessage message) {
        try (PaymentLogContext ignored = PaymentLogContext.with(message.traceId(), message.paymentId(), message.requestedBy())) {
            if (processedEventService.isProcessed(message.eventId())) {
                log.info("PaymentProcessingFailed duplicate ignored. eventId={}", message.eventId());
                return;
            }

            log.info("PaymentProcessingFailed received. eventId={} requestedEventId={}",
                    message.eventId(),
                    message.requestedEventId()
            );
            try {
                paymentService.failPayment(message.paymentId(), message.traceId());
                processedEventService.markProcessed(message.eventId());
            } catch (RuntimeException exception) {
                log.error("PaymentProcessingFailed handling failed. eventId={}", message.eventId(), exception);
                throw exception;
            }
        }
    }
}
