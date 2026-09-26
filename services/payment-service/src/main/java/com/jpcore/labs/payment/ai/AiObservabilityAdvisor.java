package com.jpcore.labs.payment.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

import java.util.concurrent.TimeUnit;

public class AiObservabilityAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(AiObservabilityAdvisor.class);

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        log.debug("AI call started");
        long started = System.nanoTime();
        try {
            return chain.nextCall(request);
        } finally {
            log.debug("AI call finished durationMs={}",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
