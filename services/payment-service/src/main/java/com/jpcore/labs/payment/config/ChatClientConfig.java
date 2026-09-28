package com.jpcore.labs.payment.config;

import com.jpcore.labs.payment.ai.AiObservabilityAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(new AiObservabilityAdvisor())
                .build();
    }
}
