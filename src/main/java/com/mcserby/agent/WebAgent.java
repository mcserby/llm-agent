package com.mcserby.agent;

import com.google.cloud.vertexai.api.*;
import com.google.protobuf.Value;
import com.mcserby.agent.bot.PageAutomationBot;
import com.mcserby.agent.model.Action;
import com.mcserby.agent.model.ActionType;
import com.mcserby.agent.model.Message;
import com.mcserby.agent.model.MessageType;
import com.mcserby.llm.LlmModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public void reasonActOnTask(String task) {
        UUID sessionId = UUID.randomUUID();
        LOGGER.info("Reasoning and acting on task: {}", task);
        this.conversation.add(new Message(MessageType.TASK, task, false));
        int currentStep = 0;
        GenerateContentResponse currentResult = null;
        try {
            while (currentStep < maxNumberOfSteps) {
                LOGGER.info("Current step: {}", currentStep++);
                String conversationSoFar = buildCurrentPrompt(this.conversation);
                currentResult = model.generate(conversationSoFar);
                LOGGER.info("LLM response metadata: {}. ", getResponseMetadata(currentResult));
                LOGGER.info("LLM response text: {}", getResponseAsText(currentResult));
                Optional<Message> maybeTaskIsSolved = taskIsSolved(currentResult);
                if (maybeTaskIsSolved.isPresent()) {
                    LOGGER.info("Task is solved: {}", maybeTaskIsSolved.get());
                    this.conversation.add(maybeTaskIsSolved.get());
                    break;
                }
                List<String> thoughts = extractThoughts(currentResult);
                thoughts.stream().map(t -> new Message(MessageType.THOUGHT, t, false)).forEach(this.conversation::add);

                List<Action> actions = extractAction(currentResult);
                actions.stream().map(a -> new Message(MessageType.ACTION, a.toString(), false)).forEach(this.conversation::add);
                actions.stream().map(a -> pageAutomationBot.performAction(sessionId, a)).forEach(this.conversation::add);
            }
        } catch (Exception e){
            LOGGER.error("Error while reasoning and acting on task", e);
        } finally {
            pageAutomationBot.closeSession(sessionId);
        }
        LOGGER.info(this.conversation.getLast().toString());
        LOGGER.info("Full Prompt: {}", buildCurrentPrompt(this.conversation));
        LOGGER.info("Final result: {}", getResponseAsText(currentResult));
    }

    private String getResponseMetadata(GenerateContentResponse response) {
        Candidate candidate = response.getCandidatesList().getFirst();
        String finishMessage = candidate.getFinishMessage().isEmpty() ? "" : "Finish message: " + candidate.getFinishMessage();
        String safetyRatings = candidate.getSafetyRatingsList().stream().map(sr -> "Blocked: " + sr.getBlocked() + ", " + sr.getCategory().name()).collect(Collectors.joining(", "));
        int totalTokenCount = response.getUsageMetadata().getTotalTokenCount();
        return finishMessage + ", Safety Ratings: " + safetyRatings + ", Total token count: " + totalTokenCount + ".";
    }

    private String buildCurrentPrompt(List<Message> conversation) {
        long indexOfLastObservation = IntStream.range(0, conversation.size())
                .filter(i -> conversation.get(i).type() == MessageType.OBSERVATION)
                .max()
                .orElse(-1);

        return IntStream.range(0, conversation.size())
                .mapToObj(index -> {
                    Message message = conversation.get(index);
                    if (index != indexOfLastObservation && message.type() == MessageType.OBSERVATION && message.canOmitForBrevity()) {
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
        String[] functionParts = function.split("\\h+");
        if(functionParts.length < 2){
            LOGGER.error("Invalid action: {}", actionAsString);
            return Optional.empty();
        }
        if (function.toLowerCase().contains("navigate_to_url")) {
            String url = sanitize(functionParts[1]);
            return Optional.of(new Action(ActionType.NAVIGATE_TO_URL, null, url));
        }
        if (function.toLowerCase().contains("click_element")) {
            return Optional.of(new Action(ActionType.CLICK, functionParts[1], null));
        }
        if (function.toLowerCase().contains("send_keys_to_element")) {
            return Optional.of(new Action(ActionType.FILL_INPUT, functionParts[1], functionParts[2]));
        }
        return Optional.empty();
    }

    private String sanitize(String url) {
        return url.trim().replace("'", "").replace("\"", "").replace("`", "");
    }

    private Optional<Message> taskIsSolved(GenerateContentResponse currentResult) {
        Candidate candidate = currentResult.getCandidatesList().getFirst();
        Content content = candidate.getContent();
        List<Part> partsList = content.getPartsList();
        return partsList.stream().filter(Part::hasText)
                .filter(part -> part.getText().contains("Answer:"))
                .findFirst()
                .map(part -> new Message(MessageType.ANSWER,
                        part.getText().substring(part.getText().indexOf("Answer:")),
                        false));
    }

}
