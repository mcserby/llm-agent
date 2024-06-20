package com.mcserby.agent.moe;

import com.mcserby.agent.WebAgent;
import com.mcserby.agent.bot.PageAutomationBot;
import com.mcserby.agent.model.Message;
import com.mcserby.agent.model.MessageType;
import com.mcserby.agent.prompts.Prompts;
import com.mcserby.llm.LlmModel;
import com.mcserby.llm.VertexAiProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Disabled
@SpringBootTest
@EnableConfigurationProperties({VertexAiProperties.class})
class MoEOrchestratorTest {

    @Autowired
    private LlmModel model;
    @Autowired
    private PageAutomationBot pageAutomationBot;

    @Test
    void reActNavigateExpertOnWikipediaMainPage() {
        UUID uuid = UUID.randomUUID();
        Expert webNavigator = new ExpertImpl("WEB NAVIGATOR", new WebAgent(model, pageAutomationBot, Prompts.WEB_NAVIGATOR_EXPERT.prompt, 1));
        Message result = webNavigator.resolve(List.of(new Message(MessageType.TASK, """
                ## TASK: Please navigate to https://en.wikipedia.org/wiki/Portal:Current_events and provide the content of the "On this day" section.
                """, true)), uuid);
        System.out.println(result);
        webNavigator.closeSession(uuid);
    }


    @Test
    void reasonActOnTask() {
        Expert webNavigator = new ExpertImpl("WEB NAVIGATOR", new WebAgent(model, pageAutomationBot, Prompts.WEB_NAVIGATOR_EXPERT.prompt, 1));
        Expert searchBar = new ExpertImpl("WEBSITE SEARCHBAR", new WebAgent(model, pageAutomationBot, Prompts.SEARCH_BAR_EXPERT.prompt, 1));
        Expert onlineShopping = new ExpertImpl("ONLINE SHOPPING AGENT", new WebAgent(model, pageAutomationBot, Prompts.ONLINE_SHOPPING_EXPERT.prompt, 1));
        MoEOrchestrator moeAgent = new MoEOrchestrator(model, Prompts.MOE_ORCHESTRATOR_SYSTEM_ZERO_SHOT.prompt, List.of(webNavigator, searchBar, onlineShopping), 5);
        moeAgent.reasonActOnTask("Task: Summarize the daily events on https://en.wikipedia.org.");
    }
}