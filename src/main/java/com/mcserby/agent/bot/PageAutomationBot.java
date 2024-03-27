package com.mcserby.agent.bot;

import com.google.common.base.Charsets;
import com.mcserby.agent.model.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
    private static final List<String> XPATH_ELEMENTS = List.of("input", "button", "a", "select", "textarea", "label");
    private static final List<String> FILTERED_ELEMENTS = List.of("script", "noscript", "img", "style", "svg", "iframe");
    private final String tool;

    public PageAutomationBot() {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("pageAutomationTool.json")) {
            this.tool = new String(Objects.requireNonNull(is).readAllBytes(), Charsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Could not read pageAutomationTool.json", e);
        }
    }

    public Message performAction(UUID sessionId, Action action) {
        LOGGER.info("Performing action: {}", action);
        WebDriver driver;
        if (sessions.containsKey(sessionId)) {
            driver = sessions.get(sessionId);
        } else {
            driver = initDriver();
        }
        switch (action.actionType()) {
            case NAVIGATE_TO_URL:
                if (driver.getCurrentUrl().equals(action.value())) {
                    LOGGER.info("Preventing LLM Agent loop by not navigating to the same URL...");
                    Observation observation = new Observation("You are already on web page " + action.value() + ". Think of more meaningful actions.", List.of());
                    return new Message(MessageType.OBSERVATION, observation.render(), false);
                } else {
                    driver.get(action.value());
                    break;
                }
            case FILL_INPUT:
                List<WebElement> fillElement = driver.findElements(By.xpath(action.elementIdentifier()));
                if(fillElement.isEmpty()){
                    return new Message(MessageType.OBSERVATION, "No element with XPATH: " + action.elementIdentifier() + " exists.", false);
                }
                WebElement inputElement = fillElement.getFirst();
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", inputElement);
                trySleep(200);
                driver.findElement(By.xpath(action.elementIdentifier())).sendKeys(action.value());
                break;
            case CLICK:
                List<WebElement> clickElements = driver.findElements(By.xpath(action.elementIdentifier()));
                if(clickElements.isEmpty()){
                    return new Message(MessageType.OBSERVATION, "No element with XPATH: " + action.elementIdentifier(), false);
                }
                WebElement element = clickElements.getFirst();
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
                trySleep(200);
                element.click();
                break;
        }
        sessions.putIfAbsent(sessionId, driver);
        return new Message(MessageType.OBSERVATION, processHtml(driver).render(), true);
    }

    private static WebDriver initDriver() {
        WebDriver driver;
        ChromeOptions options = new ChromeOptions();
        options.addArguments("profile-directory=Profile 1");
        options.addArguments("user-data-dir=C:/Users/mihai.serban/AppData/Local/Google/Chrome/User Data");
        driver = new ChromeDriver(options);
        return driver;
    }

    private void trySleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            LOGGER.error("Error while sleeping", e);
        }
    }

    private Observation processHtml(WebDriver driver) {
        String currentUrl = driver.getCurrentUrl();
        Document doc = Jsoup.parse(driver.getPageSource());
        Element body = doc.body();
        List<SimpleElement> elements = fastTreeTraversal("html/body", body);
        return new Observation("content of the website " + currentUrl + ":", elements);
    }

    private List<SimpleElement> fastTreeTraversal(String currentXpath, Element element) {
        String tagName = element.tagName();
        if (FILTERED_ELEMENTS.contains(tagName)) {
            return List.of();
        }
        if (!element.hasText()) {
            return List.of();
        }
        SimpleElement currentElement = toJsoupElement(currentXpath, element);
        if (tagName.equals("button") || tagName.equals("a") || tagName.equals("input")) {
            return List.of(currentElement);
        }
        List<Element> directChildren = element.children();
        if (directChildren.isEmpty()) {
            return List.of(currentElement);
        }
        Map<String, List<Element>> elementsGroupedByType = directChildren.stream()
                .collect(Collectors.groupingBy(Element::tagName));
        List<SimpleElement> children = elementsGroupedByType.entrySet().stream()
                .map(entry -> IntStream
                        .range(0, entry.getValue().size())
                        .mapToObj(elementNumber -> fastTreeTraversal(
                                calculateJsoupXpath(currentXpath, entry, elementNumber),
                                entry.getValue().get(elementNumber)))
                        .flatMap(List::stream)
                        .toList())
                .flatMap(List::stream)
                .toList();

        if (tagName.equals("nav") || tagName.equals("header") || tagName.equals("footer") || tagName.equals("section")) {
            return List.of(currentElement.withChildren(children));
        }
        return children;
    }


    @Deprecated(forRemoval = true, since = "in favor of jsoup faster parsing")
    private List<SimpleElement> recursiveTreeTraversal(String currentXpath, WebElement element) {
        String tagName = element.getTagName();
        if (FILTERED_ELEMENTS.contains(tagName)) {
            return List.of();
        }
        if (element.getText().isEmpty()) {
            return List.of();
        }
        SimpleElement currentElement = toElement(currentXpath, element);
        if (tagName.equals("button") || tagName.equals("a") || tagName.equals("input")) {
            return List.of(currentElement);
        }
        List<WebElement> directChildren = element.findElements(By.xpath("./*"));
        if (directChildren.isEmpty()) {
            return List.of(currentElement);
        }
        Map<String, List<WebElement>> elementsGroupedByType = directChildren.stream()
                .collect(Collectors.groupingBy(WebElement::getTagName));
        List<SimpleElement> children = elementsGroupedByType.entrySet().stream()
                .map(entry -> IntStream
                        .range(0, entry.getValue().size())
                        .mapToObj(elementNumber -> recursiveTreeTraversal(
                                calculateXpath(currentXpath, entry, elementNumber),
                                entry.getValue().get(elementNumber)))
                        .flatMap(List::stream)
                        .toList())
                .flatMap(List::stream)
                .toList();

        if (tagName.equals("nav") || tagName.equals("header") || tagName.equals("footer") || tagName.equals("section")) {
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

    private static String calculateJsoupXpath(String currentXpath, Map.Entry<String, List<org.jsoup.nodes.Element>> entry, int elementNumber) {
        org.jsoup.nodes.Element element = entry.getValue().get(elementNumber);
        //if(element.getAttribute("id") == null || element.getAttribute("id").isEmpty()) {
        return currentXpath + "/" + entry.getKey() + ((entry.getValue().size() > 1) ? "[" + (elementNumber + 1) + "]" : "");
        // } else {
        //      return "[@id='" + element.getAttribute("id") + "']";
        // }
    }

    private SimpleElement toJsoupElement(String currentXpath, Element element) {
        return new SimpleElement(element.tagName(),
                shouldHaveXpath(element) ? currentXpath: null,
                element.hasAttr("href")? element.attribute("href").getValue(): null,
                element.hasAttr("id")? element.attribute("id").getValue(): null,
                element.hasAttr("placeholder")? element.attribute("placeholder").getValue(): null,
                getTextOrValue(element),
                element.hasAttr("type")? element.attribute("type").getValue(): null,
                getLabelOrAreaLabel(element), List.of());
    }

    private SimpleElement toElement(String currentXpath, WebElement element) {

        return new SimpleElement(element.getTagName(),
                shouldHaveXpath(element) ? currentXpath: null,
                element.getAttribute("href"),
                element.getAttribute("id"),
                element.getAttribute("placeholder"),
                getTextOrValue(element),
                element.getAttribute("type"),
                getLabelOrAreaLabel(element), List.of());
    }

    private boolean shouldHaveXpath(WebElement element) {
        return XPATH_ELEMENTS.contains(element.getTagName());
    }

    private boolean shouldHaveXpath(org.jsoup.nodes.Element element) {
        return XPATH_ELEMENTS.contains(element.tagName());
    }

    private String getLabelOrAreaLabel(WebElement element) {
        return element.getAttribute("label") != null ? element.getAttribute("label") : element.getAttribute("area-label");
    }

    private String getLabelOrAreaLabel(org.jsoup.nodes.Element element) {
        return element.hasAttr("label")? element.attribute("label").getValue() : element.hasAttr("area-label")? element.attribute("area-label").getValue(): null;
    }

    private String getTextOrValue(WebElement element) {
        return element.getText() != null ? element.getText() : element.getAttribute("value");
    }

    private String getTextOrValue(Element element) {
        return element.hasText() ? element.text() : element.hasAttr("value")? element.attribute("value").getValue(): null;
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
