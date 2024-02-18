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
    void reasonActOnTask() throws Exception {
        WebAgent agent = new WebAgent(model, pageAutomationBot, Prompts.ZERO_SHOT_WEB_AGENT_PROMPT_V2.prompt, 4);
        agent.reasonActOnTask("Task: Based on bosch official website, https://www.bosch.ro/en, do they offer flexible working hours as benefit?");
    }

    @Test
    void reasonActOnTaskBoschWorkingHours() throws Exception {
        WebAgent agent = new WebAgent(model, pageAutomationBot, Prompts.ZERO_SHOT_WEB_AGENT_PROMPT_V2.prompt, 8);
        agent.reasonActOnTask("Task: Based on bosch official website, https://www.bosch.ro/en, do they offer flexible working hours as benefit?");
    }

    @Test
    void amazonCheapestTrimmer() throws Exception {
        WebAgent agent = new WebAgent(model, pageAutomationBot, Prompts.ZERO_SHOT_WEB_AGENT_PROMPT_V2.prompt, 8);
        agent.reasonActOnTask("Task: What is the cheapest trimmer for men you can find on https://www.amazon.de/ website?");
    }

}