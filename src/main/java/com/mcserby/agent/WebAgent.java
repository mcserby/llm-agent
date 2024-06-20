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
        } catch (Exception e) {
            LOGGER.error("Error while reasoning and acting on task", e);
        } finally {
            pageAutomationBot.closeSession(sessionId);
        }
        LOGGER.info(this.conversation.getLast().toString());
        LOGGER.info("Full Prompt: {}", buildCurrentPrompt(this.conversation));
        LOGGER.info("Final result: {}", getResponseAsText(currentResult));
    }

    public Message solveTask(List<Message> conversation, UUID sessionId) {
        try {
            this.conversation.addAll(conversation);
            String currentPrompt = buildCurrentPrompt(this.conversation);
            LOGGER.info("Reasoning and acting on task: {}", currentPrompt);
            GenerateContentResponse result = model.generate(currentPrompt);
            List<String> thoughts = extractThoughts(result);
            thoughts.stream().map(t -> new Message(MessageType.THOUGHT, t, false)).forEach(this.conversation::add);
            List<Action> actions = extractOneLinerAction(result);
            actions.stream().map(a -> pageAutomationBot.performAction(sessionId, a)).forEach(this.conversation::add);
            return this.conversation.getLast();
        } catch (Exception e) {
            LOGGER.error("Error while reasoning and acting on task", e);
            return new Message(MessageType.OBSERVATION, "Error while reasoning and acting on task.", false);
        }
    }

    private static List<Action> extractOneLinerAction(GenerateContentResponse result) {
        String responseAsText = getResponseAsText(result);
        return toAction(responseAsText)
                .map(List::of).orElse(List.of());
    }

    public static String getResponseMetadata(GenerateContentResponse response) {
        Candidate candidate = response.getCandidatesList().getFirst();
        String finishMessage = candidate.getFinishMessage().isEmpty() ? "" : "Finish message: " + candidate.getFinishMessage();
        String safetyRatings = candidate.getSafetyRatingsList().stream().map(sr -> "Blocked: " + sr.getBlocked() + ", " + sr.getCategory().name()).collect(Collectors.joining(", "));
        int totalTokenCount = response.getUsageMetadata().getTotalTokenCount();
        return finishMessage + ", Safety Ratings: " + safetyRatings + ", Total token count: " + totalTokenCount + ".";
    }

    public static String buildCurrentPrompt(List<Message> conversation) {
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

    public static List<String> extractThoughts(GenerateContentResponse currentResult) {
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
        List<Action> actions = lineStream.map(WebAgent::toAction)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        if (!actions.isEmpty()) {
            actions.forEach(a -> LOGGER.info("Extracted action: {}", a));
        }
        return actions;
    }

    public static String getResponseAsText(GenerateContentResponse llmResponse) {
        Stream<String> textStream = extractLines(llmResponse);
        return textStream
                .collect(Collectors.joining("\n"));
    }

    public static Stream<String> extractLines(GenerateContentResponse llmResponse) {
        Candidate candidate = llmResponse.getCandidatesList().getFirst();
        Content content = candidate.getContent();
        List<Part> partsList = content.getPartsList();
        return partsList.stream()
                .filter(Part::hasText)
                .map(Part::getText)
                .map(s -> Stream.of(s.split("\n")).map(String::trim).filter(l -> !l.isEmpty()).toList())
                .flatMap(List::stream);
    }

    public static Stream<String> extractSemiStructuredContent(GenerateContentResponse currentResult, String lineContains) {
        Stream<String> textStream = extractLines(currentResult);
        return textStream
                .filter(line -> line.contains(lineContains));
    }

    public static Optional<Action> toAction(FunctionCall functionCall) {
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

    public static Optional<Action> toAction(String actionAsString) {
        // navigate_to_url, click_element, send_keys_to_element
        String function = actionAsString.replace("Action: ", "").trim();
        String[] functionParts = function.split("\\h+");
        if (functionParts.length < 2) {
            LOGGER.error("Invalid action: {}", actionAsString);
            return Optional.empty();
        }
        if (function.toUpperCase().contains("NAVIGATE_TO_URL")) {
            String url = sanitize(functionParts[1]);
            return Optional.of(new Action(ActionType.NAVIGATE_TO_URL, null, url));
        }
        if (function.toUpperCase().contains("BROWSE_TO")) {
            String url = sanitize(functionParts[1]);
            return Optional.of(new Action(ActionType.BROWSE_TO, null, url));
        }
        if (function.toUpperCase().contains("CLICK_ELEMENT")) {
            return Optional.of(new Action(ActionType.CLICK, functionParts[1], null));
        }
        if (function.toUpperCase().contains("CLICK")) {
            return Optional.of(new Action(ActionType.CLICK, functionParts[1], null));
        }
        if (function.toUpperCase().contains("SEARCH")) {
            return Optional.of(new Action(ActionType.FILL_INPUT, functionParts[1], functionParts[2]));
        }
        return Optional.empty();
    }

    private static String sanitize(String url) {
        return url.trim().replace("'", "").replace("\"", "").replace("`", "");
    }

    public static Optional<Message> taskIsSolved(GenerateContentResponse currentResult) {
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

    public void closeSession(UUID sessionId) {
        this.pageAutomationBot.closeSession(sessionId);
    }
}
