package com.jpcore.labs.payment.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    private final ChatModel chatModel;

    public AiService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String testCall() {
        return chatModel.call("Say hello in one short sentence.");
    }
}
