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
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

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
    void bothEndpointsReturnSafeProblemDetailsForProviderFailures() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new TransientAiException(
                "HTTP 529 - Anthropic overloaded_error x-api-key: test-secret"
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
                    .andExpect(content().string(not(containsString("x-api-key"))))
                    .andExpect(content().string(not(containsString("overloaded_error"))));
        }
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void bothEndpointsReturnSafeProblemDetailsForRateLimits() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new NonTransientAiException(
                "HTTP 429 - Anthropic rate_limit_error x-api-key: test-secret"
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
                    .andExpect(status().isTooManyRequests())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.detail").value("AI service rate limit exceeded. Please try again later."))
                    .andExpect(content().string(not(containsString("test-secret"))))
                    .andExpect(content().string(not(containsString("Anthropic"))))
                    .andExpect(content().string(not(containsString("x-api-key"))))
                    .andExpect(content().string(not(containsString("rate_limit_error"))));
        }
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void bothEndpointsReturnSafeProblemDetailsForTimeouts() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new ResourceAccessException(
                "Anthropic x-api-key: test-secret", new SocketTimeoutException("provider details")
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
                    .andExpect(status().isGatewayTimeout())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(504))
                    .andExpect(jsonPath("$.detail").value("AI service request timed out."))
                    .andExpect(content().string(not(containsString("test-secret"))))
                    .andExpect(content().string(not(containsString("Anthropic"))))
                    .andExpect(content().string(not(containsString("x-api-key"))))
                    .andExpect(content().string(not(containsString("provider details"))));
        }
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

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
