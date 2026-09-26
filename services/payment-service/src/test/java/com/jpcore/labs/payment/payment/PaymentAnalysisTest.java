package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.ai.PaymentAiAnalyzer;
import com.jpcore.labs.payment.ai.PaymentAnalysisInput;
import com.jpcore.labs.payment.ai.PaymentAnalysisResponse;
import com.jpcore.labs.payment.ai.PaymentRisk;
import com.jpcore.labs.payment.ai.RecommendedAction;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentAnalysisTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentAiAnalyzer paymentAiAnalyzer = mock(PaymentAiAnalyzer.class);
    private final PaymentAnalysisService analysisService = new PaymentAnalysisService(paymentService, paymentAiAnalyzer);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new PaymentController(paymentService, analysisService)
    ).build();

    @Test
    void analyzesPaymentUsingOnlyAnalysisFieldsAndReturnsJson() throws Exception {
        String id = "11111111-1111-1111-1111-111111111111";
        BigDecimal amount = new BigDecimal("100.50");
        when(paymentService.findById(id)).thenReturn(
                new PaymentResponse(id, amount, "BRL", "Test payment", PaymentStatus.COMPLETED)
        );
        PaymentAnalysisInput input = new PaymentAnalysisInput(
                amount, "BRL", "Test payment", PaymentStatus.COMPLETED
        );
        when(paymentAiAnalyzer.analyze(input)).thenReturn(new PaymentAnalysisResponse(
                "The payment of 100.50 BRL is completed.", PaymentRisk.LOW, RecommendedAction.NONE
        ));

        mockMvc.perform(post("/api/payments/{id}/ai-analysis", id))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "summary": "The payment of 100.50 BRL is completed.",
                          "risk": "LOW",
                          "recommendedAction": "NONE"
                        }
                        """));

        verify(paymentService).findById(id);
        verify(paymentAiAnalyzer).analyze(input);
    }

    @Test
    void missingPaymentReturnsNotFoundWithoutCallingAi() throws Exception {
        String id = "11111111-1111-1111-1111-111111111111";
        when(paymentService.findById(id)).thenThrow(new PaymentNotFoundException(id));

        mockMvc.perform(post("/api/payments/{id}/ai-analysis", id))
                .andExpect(status().isNotFound());

        verifyNoInteractions(paymentAiAnalyzer);
    }
}
