package com.mcserby.agent.moe;

import com.mcserby.agent.WebAgent;
import com.mcserby.agent.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class ExpertImpl implements Expert {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpertImpl.class);

    private final String name;
    private final WebAgent agent;

    public ExpertImpl(String name, WebAgent agent) {
        this.name = name;
        this.agent = agent;
    }

    @Override
    public Message resolve(List<Message> conversation, UUID sessionId) {
        LOGGER.info("Expert {} resolving task: {}", this.name, conversation);
        return agent.solveTask(conversation, sessionId);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void closeSession(UUID sessionId) {
        LOGGER.info("Closing session: {}", sessionId);
        agent.closeSession(sessionId);
    }
}
