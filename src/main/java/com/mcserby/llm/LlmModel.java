package com.mcserby.llm;

import com.google.cloud.vertexai.api.GenerateContentResponse;

import java.io.IOException;
import java.util.List;

public interface LlmModel {

    GenerateContentResponse generate(String prompt) throws IOException;

    GenerateContentResponse generate(String prompt, List<String> tools) throws IOException;

    String generateMultimodal(String prompt, byte[] image) throws IOException;
}
