package com.jpcore.labs.payment.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import com.jpcore.labs.payment.ai.AiObservabilityAdvisor;
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
    void logsAdvisorExecutionWithoutPromptOrCompletionContent() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("private completion")))));
        Logger logger = (Logger) LoggerFactory.getLogger(AiObservabilityAdvisor.class);
        Level previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);

        try {
            String response = new ChatClientConfig().chatClient(model).prompt()
                    .system("private system instructions")
                    .user("private payment data")
                    .call().content();

            assertThat(response).isEqualTo("private completion");
            assertThat(appender.list).hasSize(2);
            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                assertThat(event.getFormattedMessage()).doesNotContain(
                        "private system instructions", "private payment data", "private completion");
            });
            assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("AI call started");
            assertThat(appender.list.get(1).getFormattedMessage()).matches("AI call finished durationMs=\\d+");
        }
        finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
