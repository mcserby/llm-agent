package com.mcserby.agent.moe;

import com.mcserby.agent.model.Message;

import java.util.List;
import java.util.UUID;

public interface Expert {
    Message resolve(List<Message> conversation, UUID sessionId);

    String getName();

    void closeSession(UUID sessionId);
}
