package com.jpcore.labs.paymentprocessor.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class PaymentAuthorizationService {

    private final RestClient restClient;
    private final String authorizationUrl;

    public PaymentAuthorizationService(
            RestClient.Builder restClientBuilder,
            @Value("${payment-processor.authorization-url}") String authorizationUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.authorizationUrl = authorizationUrl;
    }

    public boolean authorize(PaymentRequestedMessage message) {
        try {
            AuthorizationResponse response = restClient.get()
                    .uri(authorizationUrl)
                    .retrieve()
                    .body(AuthorizationResponse.class);

            if (response != null && response.isAuthorized()) {
                return true;
            }

            throw new PaymentAuthorizationException("Payment authorization failed for paymentId=" + message.paymentId());
        } catch (RestClientException exception) {
            throw new PaymentAuthorizationException("Payment authorization failed for paymentId=" + message.paymentId(), exception);
        }
    }
}
