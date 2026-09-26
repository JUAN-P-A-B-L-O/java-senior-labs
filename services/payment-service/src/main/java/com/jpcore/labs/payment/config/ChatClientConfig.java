package com.jpcore.labs.payment.config;

import com.jpcore.labs.payment.ai.AiObservabilityAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new AiObservabilityAdvisor())
                .build();
    }
}
