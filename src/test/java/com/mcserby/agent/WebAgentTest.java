package com.mcserby.agent;

import com.mcserby.agent.bot.PageAutomationBot;
import com.mcserby.agent.prompts.Prompts;
import com.mcserby.llm.LlmModel;
import com.mcserby.llm.VertexAiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnableConfigurationProperties({VertexAiProperties.class})
class WebAgentTest {

    @Autowired
    private LlmModel model;
    @Autowired
    private PageAutomationBot pageAutomationBot;

    @Test
    void reasonActOnTask() throws IOException {
        WebAgent agent = new WebAgent(model, pageAutomationBot, Prompts.ZERO_SHOT_WEB_AGENT_PROMPT.prompt, 4);
        agent.reasonActOnTask("Search on accesa website, https://accesa.eu, and find the services they provide.");
    }
}