package com.mcserby.agent.bot;

import com.mcserby.agent.model.BasicAction;
import com.mcserby.agent.model.ActionType;
import com.mcserby.agent.model.Message;
import com.mcserby.llm.VertexAiProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Disabled
@SpringBootTest
@EnableConfigurationProperties({VertexAiProperties.class})
class PageShoppingAutomationBotTest {

    @Autowired
    PageAutomationBot pageAutomationBot;

    @Test
    void performActions() {
        UUID sessionId = UUID.randomUUID();
        Message observation = pageAutomationBot.performAction(sessionId, new BasicAction(ActionType.NAVIGATE_TO_URL, null, "https://www.accesa.eu"));
        assertNotNull(observation);
        System.out.println(observation);
    }
}