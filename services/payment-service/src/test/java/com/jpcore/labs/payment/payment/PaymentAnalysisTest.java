package com.jpcore.labs.payment.payment;

import com.jpcore.labs.payment.ai.AiService;
import com.jpcore.labs.payment.ai.PaymentAnalysisInput;
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
    private final AiService aiService = mock(AiService.class);
    private final PaymentAnalysisService analysisService = new PaymentAnalysisService(paymentService, aiService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new PaymentController(paymentService, analysisService)
    ).build();

    @Test
    void analyzesPaymentUsingOnlyAnalysisFieldsAndReturnsText() throws Exception {
        String id = "11111111-1111-1111-1111-111111111111";
        BigDecimal amount = new BigDecimal("100.50");
        when(paymentService.findById(id)).thenReturn(
                new PaymentResponse(id, amount, "BRL", "Test payment", PaymentStatus.COMPLETED)
        );
        PaymentAnalysisInput input = new PaymentAnalysisInput(
                amount, "BRL", "Test payment", PaymentStatus.COMPLETED
        );
        when(aiService.analyze(input)).thenReturn("The payment of 100.50 BRL is completed.");

        mockMvc.perform(post("/api/payments/{id}/ai-analysis", id))
                .andExpect(status().isOk())
                .andExpect(content().string("The payment of 100.50 BRL is completed."));

        verify(paymentService).findById(id);
        verify(aiService).analyze(input);
    }

    @Test
    void missingPaymentReturnsNotFoundWithoutCallingAi() throws Exception {
        String id = "11111111-1111-1111-1111-111111111111";
        when(paymentService.findById(id)).thenThrow(new PaymentNotFoundException(id));

        mockMvc.perform(post("/api/payments/{id}/ai-analysis", id))
                .andExpect(status().isNotFound());

        verifyNoInteractions(aiService);
    }
}
