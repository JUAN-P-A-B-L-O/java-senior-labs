package com.jpcore.labs.payment.ai;

import com.jpcore.labs.payment.payment.PaymentAnalysisService;
import com.jpcore.labs.payment.payment.PaymentController;
import com.jpcore.labs.payment.payment.PaymentResponse;
import com.jpcore.labs.payment.payment.PaymentService;
import com.jpcore.labs.payment.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiExceptionHandlerTest {

    @Test
    void bothEndpointsReturnSafeProblemDetailsForInvalidCredentials() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new NonTransientAiException(
                "HTTP 401 - Anthropic invalid x-api-key: test-secret"
        ));
        PaymentAiAnalyzer analyzer = new PaymentAiAnalyzer(chatModel);
        PaymentService paymentService = mock(PaymentService.class);
        when(paymentService.findById("payment-id")).thenReturn(new PaymentResponse(
                "payment-id", BigDecimal.ONE, "BRL", "Test", PaymentStatus.COMPLETED
        ));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new AiController(analyzer),
                new PaymentController(paymentService, new PaymentAnalysisService(paymentService, analyzer))
        ).setControllerAdvice(new AiExceptionHandler()).build();

        for (var request : java.util.List.of(get("/api/ai/test"), post("/api/payments/payment-id/ai-analysis"))) {
            mvc.perform(request)
                    .andExpect(status().isBadGateway())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(502))
                    .andExpect(jsonPath("$.detail").value("AI service is currently unavailable."))
                    .andExpect(content().string(not(containsString("test-secret"))))
                    .andExpect(content().string(not(containsString("Anthropic"))))
                    .andExpect(content().string(not(containsString("x-api-key"))));
        }
        verify(chatModel, times(2)).call(any(Prompt.class));
    }
}
