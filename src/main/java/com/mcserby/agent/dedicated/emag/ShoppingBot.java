package com.mcserby.agent.dedicated.emag;

import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.mcserby.agent.WebAgent;
import com.mcserby.agent.bot.ShoppingAutomationBot;
import com.mcserby.llm.LlmModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ShoppingBot {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShoppingBot.class);

    private final LlmModel model;

    private final DedicatedMessage instructions;

    private final ShoppingAutomationBot shoppingAutomationBot;

    public ShoppingBot(LlmModel model, DedicatedMessage instructions, ShoppingAutomationBot shoppingAutomationBot) {
        this.model = model;
        this.instructions = instructions;
        this.shoppingAutomationBot = shoppingAutomationBot;
    }

    public void reAct(String task) {
        List<DedicatedMessage> conversation = new ArrayList<>();
        conversation.add(instructions);
        conversation.add(new DedicatedMessage(DedicatedMessageType.TASK, task));
        AtomicReference<UUID> sessionId = new AtomicReference<>();
        try {
            GenerateContentResponse planResult = model.generate(concat(conversation));
            String planAsText = WebAgent.getResponseAsText(planResult);
            System.out.println("Shopping PLAN: " + planAsText);
            sessionId.set(this.shoppingAutomationBot.newSession());
            List<ShoppingAction> extractActions = extractActions(planAsText);
            extractActions.stream().map(a -> this.executeAction(sessionId.get(), a)).forEach(conversation::add);
        } catch (Exception e) {
            LOGGER.error("Error while reasoning and acting on task", e);
        } finally {
            if(sessionId.get() != null) {
                this.shoppingAutomationBot.closeSession(sessionId.get());
            }
        }
        System.out.println("FULL CONVERSATION: " + System.lineSeparator() + concat(conversation));
    }

    private DedicatedMessage executeAction(UUID sessionId, ShoppingAction shoppingAction) {
        shoppingAutomationBot.trySleep(500);
        return shoppingAutomationBot.performAction(sessionId, shoppingAction);
    }

    private List<ShoppingAction> extractActions(String planAsText) {
        return planAsText.replaceAll("\\*\\*", "").lines()
                .map(ShoppingAction::fromString)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<DedicatedMessage> taskIsSolved(GenerateContentResponse currentResult) {
        return null;
    }

    public static String concat(List<DedicatedMessage> conversation) {
        return conversation.stream().map(DedicatedMessage::toString).collect(Collectors.joining("\n\n"));
    }


}
