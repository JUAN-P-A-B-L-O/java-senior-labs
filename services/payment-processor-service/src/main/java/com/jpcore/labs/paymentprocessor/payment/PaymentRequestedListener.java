package com.jpcore.labs.paymentprocessor.payment;

import com.jpcore.labs.paymentprocessor.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentRequestedListener {

    private final PaymentAuthorizationService paymentAuthorizationService;
    private final ProcessedEventService processedEventService;

    public PaymentRequestedListener(
            PaymentAuthorizationService paymentAuthorizationService,
            ProcessedEventService processedEventService
    ) {
        this.paymentAuthorizationService = paymentAuthorizationService;
        this.processedEventService = processedEventService;
    }

    @RabbitListener(queues = RabbitMqConfig.PAYMENT_PROCESS_QUEUE)
    public void listen(PaymentRequestedMessage message) throws InterruptedException {
        if (processedEventService.isProcessed(message.eventId())) {
            System.out.println("paymentRequested duplicated ignored: " + message.eventId());
            return;
        }

        System.out.println("paymentRequested received: " + message);
        boolean authorized = paymentAuthorizationService.authorize(message);
        processedEventService.markProcessed(message.eventId());
        Thread.sleep(15000);

        System.out.println("paymentRequested authorization result: " + authorized);


    }
}
