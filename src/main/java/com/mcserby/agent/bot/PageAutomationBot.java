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
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class PageAutomationBot {

    private static final Logger LOGGER = LoggerFactory.getLogger(PageAutomationBot.class);

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

    public Observation performActions(UUID sessionId, List<Action> actions) throws InterruptedException {
        LOGGER.info("Performing actions: {}", actions);
        WebDriver driver;
        if(sessions.containsKey(sessionId)){
            driver = sessions.get(sessionId);
        } else {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("profile-directory=Profile 1");
            options.addArguments("user-data-dir=C:/Users/mihai.serban/AppData/Local/Google/Chrome/User Data");
            driver = new ChromeDriver(options);
        }
        for (Action action : actions) {
            switch (action.actionType()) {
                case NAVIGATE_TO_URL:
                    driver.get(action.value());
                    break;
                case FILL_INPUT:
                    WebElement inputElement = driver.findElement(By.xpath(action.elementIdentifier()));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", inputElement);
                    Thread.sleep(500);
                    driver.findElement(By.xpath(action.elementIdentifier())).sendKeys(action.value());
                    break;
                case CLICK:
                    WebElement element = driver.findElement(By.xpath(action.elementIdentifier()));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
                    Thread.sleep(500);
                    element.click();
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
        String tagName = element.getTagName();
        if (filteredElements.contains(tagName)) {
            return List.of();
        }
        if (element.getText().isEmpty()) {
            return List.of();
        }
        Element currentElement = toElement(currentXpath, element);
        if(tagName.equals("button") || tagName.equals("a") || tagName.equals("input")){
            return List.of(currentElement);
        }
        List<WebElement> directChildren = element.findElements(By.xpath("./*"));
        if (directChildren.isEmpty()) {
            return List.of(currentElement);
        }
        Map<String, List<WebElement>> elementsGroupedByType = directChildren.stream()
                .collect(Collectors.groupingBy(WebElement::getTagName));
        List<Element> children = elementsGroupedByType.entrySet().stream()
                .map(entry -> IntStream
                        .range(0, entry.getValue().size())
                        .mapToObj(elementNumber -> recursiveTreeTraversal(
                                calculateXpath(currentXpath, entry, elementNumber),
                                entry.getValue().get(elementNumber)))
                        .flatMap(List::stream)
                        .toList())
                .flatMap(List::stream)
                .toList();

        if(tagName.equals("nav") || tagName.equals("header") || tagName.equals("footer") || tagName.equals("section")){
            return List.of(currentElement.withChildren(children));
        }
        return children;

    }

    private static String calculateXpath(String currentXpath, Map.Entry<String, List<WebElement>> entry, int elementNumber) {
        WebElement element = entry.getValue().get(elementNumber);
        //if(element.getAttribute("id") == null || element.getAttribute("id").isEmpty()) {
            return currentXpath + "/" + entry.getKey() + ((entry.getValue().size() > 1) ? "[" + (elementNumber + 1) + "]" : "");
       // } else {
      //      return "[@id='" + element.getAttribute("id") + "']";
       // }
    }

    private Element toElement(String currentXpath, WebElement element) {
        return new Element(element.getTagName(),
                currentXpath,
                element.getAttribute("href"),
                element.getAttribute("id"),
                element.getAttribute("placeholder"),
                getTextOrValue(element),
                element.getAttribute("type"),
                getLabelOrAreaLabel(element), List.of());
    }

    private String getLabelOrAreaLabel(WebElement element) {
        return element.getAttribute("label") != null? element.getAttribute("label") : element.getAttribute("area-label");
    }

    private String getTextOrValue(WebElement element) {
        return element.getText() != null? element.getText() : element.getAttribute("value");
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
