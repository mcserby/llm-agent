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
    void reasonActOnAccesaSuccessStory() throws Exception {
        WebAgent agent = new WebAgent(model, pageAutomationBot, Prompts.ZERO_SHOT_WEB_AGENT_PROMPT_V2.prompt, 5);
        agent.reasonActOnTask("Task: Can you extract one success story from Accesa official website, https://accesa.eu?");
    }

    @Test
    void reasonActOnAccesaProvidedServices() throws Exception {
        WebAgent agent = new WebAgent(model, pageAutomationBot, Prompts.ZERO_SHOT_WEB_AGENT_PROMPT_V2.prompt, 5);
        agent.reasonActOnTask("Task: What are the services provided by Accesa on their website https://accesa.eu, as described on their official website?");
    }

    @Test
    void reasonActOnTaskBoschWorkingHours() throws Exception {
        WebAgent agent = new WebAgent(model, pageAutomationBot, Prompts.ZERO_SHOT_WEB_AGENT_PROMPT_V2.prompt, 6);
        agent.reasonActOnTask("Task: Based on bosch official website, https://www.bosch.ro/en, do they offer flexible working hours as benefit?");
    }

    @Test
    void amazonCheapestTrimmer() throws Exception {
        WebAgent agent = new WebAgent(model, pageAutomationBot, Prompts.ZERO_SHOT_WEB_AGENT_PROMPT_V2.prompt, 6);
        agent.reasonActOnTask("Task: Search for the cheapest hair trimmer for men you can find on www.amazon.de website and give me the price.");
    }

    @Test
    void buyCoffeFromEmagRo() throws Exception {
        WebAgent agent = new WebAgent(model, pageAutomationBot, Prompts.ZERO_SHOT_WEB_AGENT_PROMPT_V2.prompt, 6);
        agent.reasonActOnTask("Task: Cauta pe www.emag.ro cel mai ieftin telefon samsung si adauga-l in cos.");
    }

}