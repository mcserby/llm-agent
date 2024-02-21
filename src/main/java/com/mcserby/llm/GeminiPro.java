package com.mcserby.llm;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Tool;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class GeminiPro implements LlmModel {

    private final String project;
    private final String predictModel;
    private final String visionModel;
    private final String location;
    private final GenerationConfig generationConfig;

    public GeminiPro(VertexAiProperties vertexAiProperties) {
        this.project = vertexAiProperties.project();
        this.predictModel = vertexAiProperties.predictModel();
        this.visionModel = vertexAiProperties.visionModel();
        this.location = vertexAiProperties.location();
        this.generationConfig = GenerationConfig.newBuilder()
                .setTopK(1)
                .setTopP(0.8f)
                .setTemperature(0.5f)
                .setMaxOutputTokens(1000)
                .build();
    }

    @Override
    public GenerateContentResponse generate(String prompt) {
        return generate(prompt, List.of());
    }

    @Override
    public GenerateContentResponse generate(String prompt, List<String> tools) {
        try (VertexAI vertexAI = new VertexAI(this.project, this.location)) {
            GenerativeModel model = GenerativeModel.newBuilder()
                    .setModelName(this.predictModel)
                    .setGenerationConfig(this.generationConfig)
                    .setVertexAi(vertexAI)
                    .setTools(tools.stream().map(this::parseTool).toList())
                    .build();
            return model.generateContent(ContentMaker
                    .fromString(prompt));
        } catch (Exception e){
            throw new RuntimeException("Failed to generate content", e);
        }
    }

    @Override
    public String generateMultimodal(String prompt, byte[] image) throws IOException {
        try (VertexAI vertexAI = new VertexAI(project, location)) {
            GenerativeModel model = new GenerativeModel(this.visionModel, this.generationConfig, vertexAI);
            GenerateContentResponse response = model.generateContent(ContentMaker.fromMultiModalData(
                    PartMaker.fromMimeTypeAndData("image/jpeg", image),
                    prompt));
            return response.toString();
        }
    }

    private Tool parseTool(String tool) {
        try {
            Tool.Builder toolBuilder = Tool.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(tool, toolBuilder);
            return toolBuilder.build();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Invalid tool format: " + tool, e);
        }
    }
}
