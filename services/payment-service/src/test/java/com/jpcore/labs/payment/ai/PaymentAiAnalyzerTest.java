package com.jpcore.labs.payment.ai;

import com.jpcore.labs.payment.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAiAnalyzerTest {

    @ParameterizedTest
    @MethodSource("providerFailures")
    void translatesProviderFailures(RuntimeException failure) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(failure);

        assertThatThrownBy(() -> new PaymentAiAnalyzer(chatModel).testCall())
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI service is currently unavailable.")
                .hasNoCause();
        verify(chatModel).call(any(Prompt.class));
    }

    static Stream<RuntimeException> providerFailures() {
        return Stream.of(500, 502, 503, 504, 529, 599).flatMap(status -> Stream.of(
                new TransientAiException("HTTP " + status + " - provider details\nmore details"),
                new TransientAiException(status + " - provider details"),
                new NonTransientAiException("HTTP " + status + " - provider details"),
                new NonTransientAiException(status + " - provider details"),
                new HttpServerErrorException(HttpStatusCode.valueOf(status)),
                new RuntimeException(new TransientAiException("HTTP " + status + " - provider details")),
                new RuntimeException(new HttpServerErrorException(HttpStatusCode.valueOf(status)))
        ));
    }

    @ParameterizedTest
    @MethodSource("rateLimitFailures")
    void translatesRateLimitFailures(RuntimeException failure) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(failure);

        assertThatThrownBy(() -> new PaymentAiAnalyzer(chatModel).testCall())
                .isInstanceOf(AiRateLimitException.class)
                .hasMessage("AI service rate limit exceeded. Please try again later.")
                .hasNoCause();
        verify(chatModel).call(any(Prompt.class));
    }

    static Stream<RuntimeException> rateLimitFailures() {
        return Stream.of(
                new NonTransientAiException("HTTP 429 - provider details"),
                new NonTransientAiException("429 - provider details"),
                new TransientAiException("HTTP 429 - provider details"),
                new TransientAiException("429 - provider details"),
                new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS),
                new RuntimeException(new NonTransientAiException("HTTP 429 - provider details")),
                new RuntimeException(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))
        );
    }

    @ParameterizedTest
    @MethodSource("nonRateLimitFailures")
    void preservesFailuresThatAreNotRateLimits(RuntimeException failure) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(failure);

        assertThatThrownBy(() -> new PaymentAiAnalyzer(chatModel).testCall()).isSameAs(failure);
        verify(chatModel).call(any(Prompt.class));
    }

    static Stream<RuntimeException> nonRateLimitFailures() {
        return Stream.of(
                new NonTransientAiException("HTTP 400 - body mentions 429"),
                new NonTransientAiException("HTTP 400 - body mentions HTTP 500 - provider details"),
                new TransientAiException("HTTP 5000 - invalid status"),
                new TransientAiException("HTTP 600 - outside server error range"),
                new RuntimeException("HTTP 500 - unrelated exception"),
                new HttpClientErrorException(HttpStatus.BAD_REQUEST),
                new RuntimeException("HTTP 429 - unrelated exception"),
                new TransientAiException(null)
        );
    }

    @ParameterizedTest
    @MethodSource("timeoutFailures")
    void translatesTimeoutFailures(RuntimeException failure) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(failure);

        assertThatThrownBy(() -> new PaymentAiAnalyzer(chatModel).testCall())
                .isInstanceOf(AiTimeoutException.class)
                .hasMessage("AI service request timed out.")
                .hasNoCause();
        verify(chatModel).call(any(Prompt.class));
    }

    static Stream<RuntimeException> timeoutFailures() {
        return Stream.of(
                new ResourceAccessException("provider details", new SocketTimeoutException("Read timed out")),
                new ResourceAccessException("provider details", new HttpTimeoutException("request timed out")),
                new ResourceAccessException("provider details", new HttpConnectTimeoutException("connect timed out")),
                new RuntimeException(new RuntimeException(new TimeoutException("provider details")))
        );
    }

    @Test
    void preservesNonTimeoutConnectionFailures() {
        ChatModel chatModel = mock(ChatModel.class);
        RuntimeException failure = new ResourceAccessException("Connection failed", new ConnectException());
        when(chatModel.call(any(Prompt.class))).thenThrow(failure);

        assertThatThrownBy(() -> new PaymentAiAnalyzer(chatModel).testCall()).isSameAs(failure);
        verify(chatModel).call(any(Prompt.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP 401 - invalid x-api-key", "401 - invalid x-api-key"})
    void translatesAuthenticationFailures(String message) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new NonTransientAiException(message));

        assertThatThrownBy(() -> new PaymentAiAnalyzer(chatModel).analyze(
                new PaymentAnalysisInput(BigDecimal.ONE, "BRL", "Test", PaymentStatus.COMPLETED)
        )).isInstanceOf(AiAuthenticationException.class)
                .hasMessage("AI service is currently unavailable.")
                .hasNoCause();
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void preservesOtherProviderFailures() {
        ChatModel chatModel = mock(ChatModel.class);
        NonTransientAiException failure = new NonTransientAiException("HTTP 400 - invalid request");
        when(chatModel.call(any(Prompt.class))).thenThrow(failure);

        assertThatThrownBy(() -> new PaymentAiAnalyzer(chatModel).testCall()).isSameAs(failure);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void includesFormatInstructionsAndConvertsModelResponse() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("""
                        {
                          "summary": "Payment completed.",
                          "risk": "LOW",
                          "recommendedAction": "NONE"
                        }
                        """))
        )));

        PaymentAnalysisResponse response = new PaymentAiAnalyzer(chatModel).analyze(
                new PaymentAnalysisInput(new BigDecimal("100.50"), "BRL", "Test payment", PaymentStatus.COMPLETED)
        );

        assertThat(response).isEqualTo(new PaymentAnalysisResponse(
                "Payment completed.", PaymentRisk.LOW, RecommendedAction.NONE
        ));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getInstructions().getFirst().getText())
                .contains(new BeanOutputConverter<>(PaymentAnalysisResponse.class).getFormat())
                .contains("For risk, use only LOW, MEDIUM, or HIGH.")
                .contains("For recommendedAction, use only NONE, REVIEW, or BLOCK.");
    }
}
