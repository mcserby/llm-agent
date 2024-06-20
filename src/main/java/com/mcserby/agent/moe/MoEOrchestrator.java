package com.mcserby.agent.moe;

import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.mcserby.agent.WebAgent;
import com.mcserby.agent.model.Message;
import com.mcserby.agent.model.MessageType;
import com.mcserby.llm.LlmModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class MoEOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebAgent.class);

    private final LlmModel model;
    private final int maxNumberOfSteps;
    private final List<Message> conversation;
    private final List<Expert> experts;

    public MoEOrchestrator(LlmModel model, Message system, List<Expert> experts, int maxNumberOfSteps) {
        this.model = model;
        this.maxNumberOfSteps = maxNumberOfSteps;
        this.conversation = new ArrayList<>();
        conversation.add(system);
        this.experts = experts;
    }

    public void reasonActOnTask(String task) {
        UUID sessionId = UUID.randomUUID();
        LOGGER.info("MoE Reasoning and acting on task: {}", task);
        this.conversation.add(new Message(MessageType.TASK, task, false));
        int currentStep = 0;
        GenerateContentResponse currentResult = null;
        try {
            while (currentStep < maxNumberOfSteps) {
                LOGGER.info("Current step: {}", currentStep++);
                String conversationSoFar = WebAgent.buildCurrentPrompt(this.conversation);
                currentResult = model.generate(conversationSoFar);
                LOGGER.info("LLM response metadata: {}. ", WebAgent.getResponseMetadata(currentResult));
                LOGGER.info("LLM response text: {}", WebAgent.getResponseAsText(currentResult));
                Optional<Message> maybeTaskIsSolved = WebAgent.taskIsSolved(currentResult);
                if (maybeTaskIsSolved.isPresent()) {
                    LOGGER.info("Task is solved: {}", maybeTaskIsSolved.get());
                    this.conversation.add(maybeTaskIsSolved.get());
                    break;
                }
                List<String> thoughts = WebAgent.extractThoughts(currentResult);
                thoughts.stream().map(t -> new Message(MessageType.THOUGHT, t, false)).forEach(this.conversation::add);

                Pair<String> expertNameAndTask = extractExpertName(currentResult);
                Expert expert = this.experts.stream().filter(e -> expertNameAndTask.first().contains(e.getName())).findFirst().orElse(new DummyExpert());
                List<Message> taskConversation = buildTaskConversations(expertNameAndTask);
                Message expertResult = expert.resolve(taskConversation, sessionId);
                this.conversation.add(expertResult);
            }
        } catch (Exception e) {
            LOGGER.error("Error while reasoning and acting on task", e);
        } finally {
            this.experts.stream().findAny().ifPresent(e -> e.closeSession(sessionId));
        }
        LOGGER.info(this.conversation.getLast().toString());
        LOGGER.info("Full Prompt: {}", WebAgent.buildCurrentPrompt(this.conversation));
        LOGGER.info("Final result: {}", WebAgent.getResponseAsText(currentResult));
    }

    private List<Message> buildTaskConversations(Pair<String> expertNameAndTask) {
        List<Message> taskConversation = new ArrayList<>();
        taskConversation.add(new Message(MessageType.TASK, expertNameAndTask.second().substring(expertNameAndTask.second().indexOf("TASK")), false));
        List<Message> observations = this.conversation.stream().filter(m -> m.type() == MessageType.OBSERVATION).toList();
        if(!observations.isEmpty()){
            taskConversation.add(observations.getLast());
        }
        return taskConversation;
    }

    private Pair<String> extractExpertName(GenerateContentResponse currentResult) {
        String lineContains = "AGENT:";
        List<String> responseLines = WebAgent.extractLines(currentResult).toList();
        String task = responseLines.stream().dropWhile(line -> !line.toUpperCase().contains("TASK:")).collect(Collectors.joining("\n"));
        return new Pair<>(responseLines.stream().filter(l-> l.contains(lineContains)).findFirst()
                .map(s -> s.replace("AGENT:", "").trim())
                .orElse(""), task);
    }

}
