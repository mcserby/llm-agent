package com.mcserby.agent.moe;

import com.mcserby.agent.model.Message;
import com.mcserby.agent.model.MessageType;

import java.util.List;
import java.util.UUID;

public class DummyExpert implements Expert {
    @Override
    public Message resolve(List<Message> task, UUID sessionId) {
        return new Message(MessageType.THOUGHT, "No expert was found for this task. Try something else.", false);
    }

    @Override
    public String getName() {
        return "DUMMY EXPERT";
    }

    @Override
    public void closeSession(UUID sessionId) {
        // Do nothing
    }
}
