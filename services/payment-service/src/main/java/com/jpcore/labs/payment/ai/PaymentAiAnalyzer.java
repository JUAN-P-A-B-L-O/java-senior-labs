package com.jpcore.labs.payment.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class PaymentAiAnalyzer {

    private static final Pattern SERVER_ERROR_STATUS = Pattern.compile("^(?:HTTP )?5[0-9]{2} - ");

    private static final Resource SYSTEM_PROMPT = new ClassPathResource("prompts/payment-analysis-system.st");
    private static final Resource USER_PROMPT = new ClassPathResource("prompts/payment-analysis-user.st");

    private final ChatClient chatClient;

    public PaymentAiAnalyzer(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String testCall() {
        return call(() -> chatClient.prompt("Say hello in one short sentence.").call().content());
    }

    public PaymentAnalysisResponse analyze(PaymentAnalysisInput input) {
        return call(() -> chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(user -> user.text(USER_PROMPT).params(Map.of(
                        "amount", String.valueOf(input.amount()),
                        "currency", String.valueOf(input.currency()),
                        "status", String.valueOf(input.status()),
                        "description", String.valueOf(input.description())
                )))
                .call()
                .entity(PaymentAnalysisResponse.class));
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RuntimeException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SocketTimeoutException
                        || cause instanceof HttpTimeoutException
                        || cause instanceof TimeoutException) {
                    throw new AiTimeoutException();
                }
                if (cause instanceof RestClientResponseException responseException
                        && responseException.getStatusCode().value() == 429) {
                    throw new AiRateLimitException();
                }
                if (cause instanceof RestClientResponseException responseException
                        && responseException.getStatusCode().is5xxServerError()) {
                    throw new AiProviderException();
                }
                // Spring AI 1.0.9 exposes HTTP status only in the error message.
                String causeMessage = cause.getMessage();
                if ((cause instanceof NonTransientAiException || cause instanceof TransientAiException)
                        && causeMessage != null) {
                    if (causeMessage.startsWith("HTTP 429 - ") || causeMessage.startsWith("429 - ")) {
                        throw new AiRateLimitException();
                    }
                    if (SERVER_ERROR_STATUS.matcher(causeMessage).find()) {
                        throw new AiProviderException();
                    }
                }
            }
            // Spring AI 1.0.9 exposes HTTP status only in the error message.
            String message = exception.getMessage();
            if (exception instanceof NonTransientAiException && message != null
                    && (message.startsWith("HTTP 401 - ") || message.startsWith("401 - "))) {
                throw new AiAuthenticationException();
            }
            throw exception;
        }
    }
}
