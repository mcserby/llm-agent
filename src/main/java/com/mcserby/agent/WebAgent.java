package com.mcserby.agent;

import com.google.cloud.vertexai.api.*;
import com.google.protobuf.Value;
import com.mcserby.agent.bot.PageAutomationBot;
import com.mcserby.agent.model.Action;
import com.mcserby.agent.model.ActionType;
import com.mcserby.agent.model.Observation;
import com.mcserby.llm.LlmModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class WebAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebAgent.class);

    private final LlmModel model;
    private final PageAutomationBot pageAutomationBot;
    private final List<String> conversation;
    private final int maxNumberOfSteps;

    public WebAgent(LlmModel model, PageAutomationBot pageAutomationBot, String system, int maxNumberOfSteps) {
        this.model = model;
        this.pageAutomationBot = pageAutomationBot;
        this.maxNumberOfSteps = maxNumberOfSteps;
        conversation = new ArrayList<>();
        conversation.add(system);
    }

    public void reasonActOnTask(String task) throws IOException {
        UUID sessionId = UUID.randomUUID();
        LOGGER.info("Reasoning and acting on task: {}", task);
        this.conversation.add("Your task: " + task);
        int currentStep = 0;
        GenerateContentResponse currentResult = null;
        while (currentStep < maxNumberOfSteps) {
            LOGGER.info("Current step: {}", currentStep);
            currentStep++;
            String conversationSoFar = String.join("\n\n", this.conversation);
            currentResult = model.generate(conversationSoFar, List.of(this.pageAutomationBot.getTool()));
            System.out.println(currentResult);
            Optional<String> thought = extractThought(currentResult);
            thought.ifPresent(this.conversation::add);
            if (taskIsSolved(currentResult)) {
                LOGGER.info("Task is solved!");
                break;
            }
            List<Action> actions = extractActions(currentResult);
            this.conversation.addAll(actions.stream().map(Action::toString).toList());
            if(actions.isEmpty()){
                LOGGER.info("No actions to perform");
                continue;
            }
            Observation observation = pageAutomationBot.performActions(sessionId, actions);
            conversation.add(observation.render());
        }
        LOGGER.info("Conversation: {}", String.join("\n\n", this.conversation));
        pageAutomationBot.closeSession(sessionId);
    }

    private Optional<String> extractThought(GenerateContentResponse currentResult) {
        Candidate candidate = currentResult.getCandidatesList().getFirst();
        Content content = candidate.getContent();
        List<Part> partsList = content.getPartsList();
        return partsList.stream().filter(Part::hasText)
                .map(Part::getText)
                .findFirst();
    }

    private List<Action> extractActions(GenerateContentResponse currentResult) {
        Candidate candidate = currentResult.getCandidatesList().getFirst();
        Content content = candidate.getContent();
        List<Part> partsList = content.getPartsList();
        return partsList.stream().filter(Part::hasFunctionCall)
                .map(Part::getFunctionCall)
                .map(this::toAction)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<Action> toAction(FunctionCall functionCall) {
        if(Arrays.stream(ActionType.values()).noneMatch(a -> a.name().equals(functionCall.getName().toUpperCase()))){
            return Optional.empty();
        }
        ActionType type = ActionType.valueOf(functionCall.getName().toUpperCase());
        String elementIdentifier = null;
        String value = null;
        Map<String, Value> fieldsMap = functionCall.getArgs().getFieldsMap();
        if(fieldsMap.containsKey("url")){
            value = fieldsMap.get("url").getStringValue();
        }
        return Optional.of(new Action(type, elementIdentifier, value));
    }

    private boolean taskIsSolved(GenerateContentResponse currentResult) {
        return false;
    }

}
