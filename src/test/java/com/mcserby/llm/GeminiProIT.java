package com.mcserby.llm;

import com.google.cloud.vertexai.api.*;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("This test is disabled because it requires a valid API key")
@SpringBootTest
@EnableConfigurationProperties({VertexAiProperties.class})
class GeminiProIT {

    @Autowired
    private LlmModel model;

    @Test
    void testGenerate() throws IOException {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("pageAutomationTool.json")) {
            String result = model.generate("What are the services provided by Evalueserve?", List.of(new String(Objects.requireNonNull(is).readAllBytes())))
                    .toString();
            System.out.println(result);
        }
    }

    @Test
    void testReadSimpleTool() throws IOException {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("navigate_to_url_tool.json")) {
            Tool.Builder toolBuilder = Tool.newBuilder();
            JsonFormat.parser().merge(new InputStreamReader(is), toolBuilder);
            Tool tool = toolBuilder.build();
            assertNotNull(tool);
        }
    }

    @Test
    void testCreateAndCallModelWithToolFromScratch() throws IOException {
        Tool.Builder toolBuilder = Tool.newBuilder();
        Schema params = Schema.newBuilder()
                .setType(Type.OBJECT)
                .putProperties("url", Schema.newBuilder().setType(Type.STRING).build())
                .build();
        FunctionDeclaration functionDeclaration = FunctionDeclaration.newBuilder()
                .setName("navigate_to_url")
                .setDescription("Uses selenium to navigate to the specified URL. The result is a summary of the HTML content of that page, with text elements, links and buttons, with associated xpath.")
                .setParameters(params)
                .build();
        toolBuilder.addFunctionDeclarations(functionDeclaration);
        Tool tool = toolBuilder.build();
        String print = JsonFormat.printer().print(tool);
        System.out.println(print);
        String resultFromModel = model.generate("What are the services provided by Evalueserve?", List.of(print)).toString();
        System.out.println(resultFromModel);
        assertNotNull(tool);
    }

    @Test
    void testReadTool() throws IOException {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("pageAutomationTool.json")) {
            Tool.Builder toolBuilder = Tool.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(new InputStreamReader(is), toolBuilder);
            Tool tool = toolBuilder.build();
            assertNotNull(tool);
        }
    }

    @Test
    void testReadFunction() throws IOException {
        try (InputStream navToUrlIs = Thread.currentThread().getContextClassLoader().getResourceAsStream("navigate_to_url.json")) {
            FunctionDeclaration.Builder functionBuilder = FunctionDeclaration.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(new InputStreamReader(navToUrlIs), functionBuilder);
            FunctionDeclaration function = functionBuilder.build();
            assertNotNull(function);
        }
    }

}