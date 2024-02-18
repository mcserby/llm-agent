package com.mcserby.agent;

import com.google.cloud.vertexai.api.*;
import com.google.protobuf.Value;
import com.mcserby.agent.bot.PageAutomationBot;
import com.mcserby.agent.model.*;
import com.mcserby.llm.LlmModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class WebAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebAgent.class);

    private final LlmModel model;
    private final PageAutomationBot pageAutomationBot;
    private final List<Message> conversation;
    private final int maxNumberOfSteps;

    public WebAgent(LlmModel model, PageAutomationBot pageAutomationBot, Message system, int maxNumberOfSteps) {
        this.model = model;
        this.pageAutomationBot = pageAutomationBot;
        this.maxNumberOfSteps = maxNumberOfSteps;
        conversation = new ArrayList<>();
        conversation.add(system);
    }

    public void reasonActOnTask(String task) throws Exception {
        UUID sessionId = UUID.randomUUID();
        LOGGER.info("Reasoning and acting on task: {}", task);
        this.conversation.add(new Message(MessageType.TASK, task));
        int currentStep = 0;
        GenerateContentResponse currentResult = null;
        while (currentStep < maxNumberOfSteps) {
            currentStep++;
            LOGGER.info("Current step: {}", currentStep);
            String conversationSoFar = buildCurrentPrompt(this.conversation);
            currentResult = model.generate(conversationSoFar);
            Optional<Message> maybeTaskIsSolved = taskIsSolved(currentResult);
            if (maybeTaskIsSolved.isPresent()) {
                LOGGER.info("Task is solved: {}", maybeTaskIsSolved.get());
                this.conversation.add(maybeTaskIsSolved.get());
                break;
            }
            List<String> thoughts = extractThoughts(currentResult);
            thoughts.stream().map(t -> new Message(MessageType.THOUGHT, t)).forEach(this.conversation::add);

            List<Action> actions = extractAction(currentResult);
            actions.stream().map(a -> new Message(MessageType.ACTION, a.toString())).forEach(this.conversation::add);

            if(!actions.isEmpty()){
                Observation observation = pageAutomationBot.performActions(sessionId, actions);
                this.conversation.add(new Message(MessageType.OBSERVATION, observation.render()));
            }
        }
        LOGGER.info("Final result: {}", getResponseAsText(currentResult));
        LOGGER.info("Prompt: {}", buildCurrentPrompt(this.conversation));
        LOGGER.info(this.conversation.getLast().toString());
        pageAutomationBot.closeSession(sessionId);
    }

    private String buildCurrentPrompt(List<Message> conversation) {
        long indexOfLastObservation = IntStream.range(0, conversation.size())
                .filter(i -> conversation.get(i).type() == MessageType.OBSERVATION)
                .max()
                .orElse(-1);

        return IntStream.range(0, conversation.size())
                .mapToObj(index -> {
                    Message message = conversation.get(index);
                    if (index != indexOfLastObservation && message.type() == MessageType.OBSERVATION) {
                        return message.omittedForBrevity();
                    }
                    return message.toString();
                })
                .collect(Collectors.joining("\n\n"));

    }

    private List<String> extractThoughts(GenerateContentResponse currentResult) {
        String lineContains = "Thought:";
        Stream<String> lineStream = extractSemiStructuredContent(currentResult, lineContains);
        List<String> thoughts = lineStream.toList();
        if (!thoughts.isEmpty()) {
            thoughts.forEach(t -> LOGGER.info("Extracted thought: {}", t));
        }
        return thoughts;
    }

    private List<Action> extractAction(GenerateContentResponse currentResult) {
        String lineContains = "Action:";
        Stream<String> lineStream = extractSemiStructuredContent(currentResult, lineContains);
        List<Action> actions = lineStream.map(this::toAction)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        if (!actions.isEmpty()) {
            actions.forEach(a -> LOGGER.info("Extracted action: {}", a));
        }
        return actions;
    }

    private static String getResponseAsText(GenerateContentResponse llmResponse){
        Stream<String> textStream = extractTextStream(llmResponse);
        return textStream
                .collect(Collectors.joining("\n"));
    }

    private static Stream<String> extractTextStream(GenerateContentResponse llmResponse) {
        Candidate candidate = llmResponse.getCandidatesList().getFirst();
        Content content = candidate.getContent();
        List<Part> partsList = content.getPartsList();
        return partsList.stream()
                .filter(Part::hasText)
                .map(Part::getText);
    }


    private static Stream<String> extractSemiStructuredContent(GenerateContentResponse currentResult, String lineContains) {

        Stream<String> textStream = extractTextStream(currentResult);

        return textStream
                .map(s -> Stream.of(s.split("\n")).filter(line -> line.contains(lineContains)).toList())
                .flatMap(List::stream);
    }

    private Optional<Action> toAction(FunctionCall functionCall) {
        if (Arrays.stream(ActionType.values()).noneMatch(a -> a.name().equals(functionCall.getName().toUpperCase()))) {
            return Optional.empty();
        }
        ActionType type = ActionType.valueOf(functionCall.getName().toUpperCase());
        String elementIdentifier = null;
        String value = null;
        Map<String, Value> fieldsMap = functionCall.getArgs().getFieldsMap();
        if (fieldsMap.containsKey("url")) {
            value = fieldsMap.get("url").getStringValue();
        }
        return Optional.of(new Action(type, elementIdentifier, value));
    }

    private Optional<Action> toAction(String actionAsString) {
        // navigate_to_url, click_element, send_keys_to_element
        String function = actionAsString.replace("Action: ", "").trim();
        String[] functionParts = function.split(" ");
        if(functionParts.length < 2){
            LOGGER.error("Invalid action: {}", actionAsString);
            return Optional.empty();
        }
        if (function.toLowerCase().contains("navigate_to_url")) {
            return Optional.of(new Action(ActionType.NAVIGATE_TO_URL, null, functionParts[1]));
        }
        if (function.toLowerCase().contains("click_element")) {
            return Optional.of(new Action(ActionType.CLICK, functionParts[1], null));
        }
        if (function.toLowerCase().contains("send_keys_to_element")) {
            return Optional.of(new Action(ActionType.FILL_INPUT, functionParts[1], functionParts[2]));
        }
        return Optional.empty();
    }

    private Optional<Message> taskIsSolved(GenerateContentResponse currentResult) {
        Candidate candidate = currentResult.getCandidatesList().getFirst();
        Content content = candidate.getContent();
        List<Part> partsList = content.getPartsList();
        return partsList.stream().filter(Part::hasText)
                .filter(part -> part.getText().contains("Answer:"))
                .findFirst()
                .map(part -> new Message(MessageType.ANSWER,
                        part.getText().substring(part.getText().indexOf("Answer:"))));
    }

}
