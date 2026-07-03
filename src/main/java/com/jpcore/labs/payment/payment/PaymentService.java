package com.jpcore.labs.payment.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        PaymentEntity payment = new PaymentEntity(
                request.amount(),
                request.currency(),
                request.description(),
                PaymentStatus.CREATED
        );

        PaymentEntity savedPayment = paymentRepository.save(payment);

        return new PaymentResponse(
                savedPayment.getId().toString(),
                savedPayment.getAmount(),
                savedPayment.getCurrency(),
                savedPayment.getDescription(),
                savedPayment.getStatus()
        );
    }
}
