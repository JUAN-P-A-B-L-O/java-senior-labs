package com.jpcore.labs.payment.config;

import org.junit.jupiter.api.Test;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatClientConfigTest {

    @Test
    void exportsNativeClientAndAdvisorMetricsWithoutContent() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("private completion")))));
        var meters = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try {
            var observations = ObservationRegistry.create();
            observations.observationConfig().observationHandler(new DefaultMeterObservationHandler(meters));
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ChatClientAutoConfiguration.class))
                    .withBean(ChatModel.class, () -> model)
                    .withBean(ObservationRegistry.class, () -> observations)
                    .withUserConfiguration(ChatClientConfig.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ChatClient.class);
                        assertThat(context.getBean(ChatClient.class).prompt("private payment data").call().content())
                                .isEqualTo("private completion");
                    });

            var timer = meters.get("spring.ai.chat.client").timer();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isPositive();
            assertThat(meters.get("spring.ai.advisor").timers()).isNotEmpty();
            assertThat(meters.scrape()).contains("spring_ai_chat_client_seconds_count", "spring_ai_advisor_seconds_count")
                    .doesNotContain("private payment data", "private completion");
        } finally {
            meters.close();
        }
    }
}
