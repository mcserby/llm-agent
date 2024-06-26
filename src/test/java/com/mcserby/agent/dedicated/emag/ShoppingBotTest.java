package com.mcserby.agent.dedicated.emag;

import com.mcserby.agent.bot.PageAutomationBot;
import com.mcserby.agent.prompts.Prompts;
import com.mcserby.llm.LlmModel;
import com.mcserby.llm.VertexAiProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled
@SpringBootTest
@EnableConfigurationProperties({VertexAiProperties.class})
class ShoppingBotTest {

    @Autowired
    private LlmModel model;

    @Autowired
    private PageAutomationBot pageAutomationBot;

    @Test
    void emagShoppingPlan() throws Exception {
        EmagAutomationBot emagAutomationBot = new EmagAutomationBot(pageAutomationBot);
        ShoppingBot shoppingBot = new ShoppingBot(model, new DedicatedMessage(DedicatedMessageType.INSTRUCTIONS, Prompts.SHOPPING_BOT_PLANNER.prompt.text()), emagAutomationBot);
        shoppingBot.reAct("What is the best rated One Plus phone I can buy for 2000 lei?");
    }

    @Test
    void emagShoppingPlan2() throws Exception {
        EmagAutomationBot emagAutomationBot = new EmagAutomationBot(pageAutomationBot);
        ShoppingBot shoppingBot = new ShoppingBot(model, new DedicatedMessage(DedicatedMessageType.INSTRUCTIONS, Prompts.SHOPPING_BOT_PLANNER.prompt.text()), emagAutomationBot);
        shoppingBot.reAct("Add to cart a Samsung washing machine with dryer for 3000 lei and good reviews.");
    }

}