package com.jpcore.labs.paymentprocessor.payment;

import com.jpcore.labs.paymentprocessor.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentRequestedListener {

    @RabbitListener(queues = RabbitMqConfig.PAYMENT_PROCESS_QUEUE)
    public void listen(PaymentRequestedMessage message) {
        System.out.println("paymentRequested received: " + message);
    }
}
