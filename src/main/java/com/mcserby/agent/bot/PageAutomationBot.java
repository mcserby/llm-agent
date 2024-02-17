package com.mcserby.agent.bot;

import com.google.common.base.Charsets;
import com.mcserby.agent.model.Action;
import com.mcserby.agent.model.Element;
import com.mcserby.agent.model.Observation;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class PageAutomationBot {

    private final Map<UUID, WebDriver> sessions = new HashMap<>();
    private final List<String> filteredElements = List.of("script", "noscript", "img", "style", "svg", "iframe");
    private final String tool;

    public PageAutomationBot() {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("pageAutomationTool.json")) {
            this.tool = new String(Objects.requireNonNull(is).readAllBytes(), Charsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Could not read pageAutomationTool.json", e);
        }
    }

    public Observation performActions(UUID sessionId, List<Action> actions) {
        WebDriver driver;
        if(sessions.containsKey(sessionId)){
            driver = sessions.get(sessionId);
        } else {
            driver = new ChromeDriver();
        }
        for (Action action : actions) {
            switch (action.actionType()) {
                case NAVIGATE_TO_URL:
                    driver.get(action.value());
                    break;
                case FILL_INPUT:
                    driver.findElement(By.xpath(action.elementIdentifier())).sendKeys(action.value());
                    break;
                case CLICK:
                    driver.findElement(By.xpath(action.elementIdentifier())).click();
                    break;
            }
        }
        sessions.putIfAbsent(sessionId, driver);
        return processHtml(driver);
    }

    private Observation processHtml(WebDriver driver) {
        WebElement element = driver.findElement(By.tagName("body"));
        List<Element> elements = recursiveTreeTraversal("html/body", element);
        return new Observation(elements);
    }

    private List<Element> recursiveTreeTraversal(String currentXpath, WebElement element) {
        if (filteredElements.contains(element.getTagName())) {
            return List.of();
        }
        if (element.getText().isEmpty()) {
            return List.of();
        }
        List<WebElement> directChildren = element.findElements(By.xpath("./*"));
        if (directChildren.isEmpty()) {
            return List.of(new Element(element.getAttribute("href"),
                    element.getTagName(),
                    element.getText(),
                    currentXpath));
        }
        Map<String, List<WebElement>> elementsGroupedByType = directChildren.stream()
                .collect(Collectors.groupingBy(WebElement::getTagName));
        return elementsGroupedByType.entrySet().stream()
                .map(entry -> IntStream
                        .range(0, entry.getValue().size())
                        .mapToObj(elementNumber -> recursiveTreeTraversal(
                                currentXpath + "/" + entry.getKey() + "[" + (elementNumber + 1) + "]",
                                entry.getValue().get(elementNumber)))
                        .flatMap(List::stream)
                        .toList())
                .flatMap(List::stream)
                .toList();
    }

    public void closeSession(UUID sessionId) {
        WebDriver driver = sessions.get(sessionId);
        if (driver != null) {
            driver.quit();
        }
        sessions.remove(sessionId);
    }

    public String getTool() {
        return tool;
    }
}
