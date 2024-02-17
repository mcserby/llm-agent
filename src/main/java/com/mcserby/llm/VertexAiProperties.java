package com.mcserby.llm;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vertexai")
public record VertexAiProperties(String project, String predictModel, String visionModel, String location) {}

