package com.jpcore.labs.payment.ai;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AiObservabilityAdvisorTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesChainResultAndLogsOnlyLifecycleAndDuration(boolean fails) {
        var request = new ChatClientRequest(new Prompt("private payment data"),
                Map.of("apiKey", "private api key"));
        var response = new ChatClientResponse(new ChatResponse(List.of(
                new Generation(new AssistantMessage("private model response")))), Map.of());
        var failure = new IllegalStateException("private provider error and api key");
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(same(request))).thenAnswer(invocation -> {
            if (fails) {
                throw failure;
            }
            return response;
        });
        Logger logger = (Logger) LoggerFactory.getLogger(AiObservabilityAdvisor.class);
        Level previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);

        try {
            var advisor = new AiObservabilityAdvisor();
            if (fails) {
                assertThatThrownBy(() -> advisor.adviseCall(request, chain)).isSameAs(failure);
            } else {
                assertThat(advisor.adviseCall(request, chain)).isSameAs(response);
            }
            verify(chain).nextCall(same(request));
            verifyNoMoreInteractions(chain);
            assertThat(appender.list).hasSize(2);
            assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("AI call started");
            assertThat(appender.list.get(1).getFormattedMessage()).matches("AI call finished durationMs=\\d+");
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
