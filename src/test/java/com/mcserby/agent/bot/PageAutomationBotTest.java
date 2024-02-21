package com.mcserby.agent.bot;

import com.mcserby.agent.model.Action;
import com.mcserby.agent.model.ActionType;
import com.mcserby.agent.model.Message;
import com.mcserby.agent.model.Observation;
import com.mcserby.llm.VertexAiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnableConfigurationProperties({VertexAiProperties.class})
class PageAutomationBotTest {

    @Autowired
    PageAutomationBot pageAutomationBot;

    @Test
    void performActions() {
        UUID sessionId = UUID.randomUUID();
        Message observation = pageAutomationBot.performAction(sessionId, new Action(ActionType.NAVIGATE_TO_URL, null, "https://www.accesa.eu"));
        assertNotNull(observation);
        System.out.println(observation);
    }
}