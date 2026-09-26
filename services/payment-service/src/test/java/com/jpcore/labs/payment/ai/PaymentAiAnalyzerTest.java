package com.jpcore.labs.payment.ai;

import com.jpcore.labs.payment.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.retry.NonTransientAiException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAiAnalyzerTest {

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
