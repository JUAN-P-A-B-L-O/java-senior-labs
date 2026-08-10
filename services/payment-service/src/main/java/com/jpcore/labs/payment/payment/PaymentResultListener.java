package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RabbitListener(queues = RabbitMqConfig.PAYMENT_RESULT_QUEUE)
public class PaymentResultListener {

    private final PaymentService paymentService;
    private final PaymentResultProcessedEventService processedEventService;

    public PaymentResultListener(PaymentService paymentService, PaymentResultProcessedEventService processedEventService) {
        this.paymentService = paymentService;
        this.processedEventService = processedEventService;
    }

    @RabbitHandler
    public void listen(PaymentProcessedMessage message) {
        if (processedEventService.isProcessed(message.eventId())) {
            return;
        }

        paymentService.completePayment(message.paymentId());
        processedEventService.markProcessed(message.eventId());
    }

    @RabbitHandler
    public void listen(PaymentProcessingFailedMessage message) {
        if (processedEventService.isProcessed(message.eventId())) {
            return;
        }

        paymentService.failPayment(message.paymentId());
        processedEventService.markProcessed(message.eventId());
    }
}
