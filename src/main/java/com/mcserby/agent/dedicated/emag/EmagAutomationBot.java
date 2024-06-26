package com.mcserby.agent.dedicated.emag;

import com.mcserby.agent.bot.PageAutomationBot;
import com.mcserby.agent.bot.ShoppingAutomationBot;
import com.mcserby.agent.model.ActionType;
import com.mcserby.agent.model.BasicAction;
import com.mcserby.agent.model.Message;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public class EmagAutomationBot implements ShoppingAutomationBot {

    private final PageAutomationBot pageAutomationBot;

    public EmagAutomationBot(PageAutomationBot pageAutomationBot) {
        this.pageAutomationBot = pageAutomationBot;
    }

    @Override
    public UUID newSession() {
        UUID uuid = UUID.randomUUID();
        BasicAction action = new BasicAction(ActionType.NAVIGATE_TO_URL, null, "https://www.emag.ro");
        this.pageAutomationBot.performAction(uuid, action);
        return uuid;
    }

    @Override
    public DedicatedMessage performAction(UUID sessionId, ShoppingAction shoppingAction) {
        return switch (shoppingAction.type()) {
            case SEARCH -> {
                this.pageAutomationBot.performAction(sessionId, new BasicAction(ActionType.FILL_INPUT_BY_ID, "searchboxTrigger", shoppingAction.command()));
                Message first5Products = this.pageAutomationBot.performAction(sessionId, new BasicAction(ActionType.GET_FIRST_N_CHILDREN_ATTRIBUTE, "card_grid", "5 data-name"));
                yield new DedicatedMessage(DedicatedMessageType.OBSERVATION, "Searched for " + shoppingAction.command() + " and found products: " + first5Products.text() + ".");
            }
            case FILTER_PRICE_UNDER -> applyFilters(sessionId, "Pret", buildPriceUnderFilter(shoppingAction), shoppingAction);
            case FILTER_PRICE_BETWEEN -> applyFilters(sessionId, "Pret", buildPriceBetweenFilter(shoppingAction), shoppingAction);
            case FILTER_BRAND -> applyFilters(sessionId, "Brand", buildBrandEqualToFilter(shoppingAction), shoppingAction);
            case SORT_BY_POPULARITY -> sortByPopularity(sessionId);
            case SORT_BY_PRICE_DESCENDING -> sortByPriceDescending(sessionId);
            case SORT_BY_PRICE_ASCENDING -> sortByPriceAscending(sessionId);
            case SELECT_FIRST_PRODUCT -> {
                Message res = this.pageAutomationBot.performAction(sessionId, new BasicAction(ActionType.CLICK_FIRST_CHILD, "card_grid", "data-name"));
                yield new DedicatedMessage(DedicatedMessageType.OBSERVATION, "Selected product: " + res.text() + ".");
            }
            case PRODUCT_DETAILS -> {
                WebDriver driver = this.pageAutomationBot.getDriver(sessionId);
                String title = driver.findElement(By.className("page-title")).getText();
                WebElement buyPanel = driver.findElement(By.className("main-product-form"));
                String priceInfo = buyPanel.findElement(By.className("highlight-content")).getText();
                String rating = driver.findElement(By.className("highlights-container")).findElement(By.className("feedback-rating-2")).getText();
                yield new DedicatedMessage(DedicatedMessageType.OBSERVATION, "Product details: " + title + ". " + priceInfo + ". " + rating + ".");
            }
            case ADD_TO_CART -> {
                WebDriver driver = this.pageAutomationBot.getDriver(sessionId);
                WebElement buyPanel = driver.findElement(By.className("main-product-form"));
                buyPanel.findElement(By.cssSelector("button[type='submit']")).click();
                yield new DedicatedMessage(DedicatedMessageType.OBSERVATION, "Added product to cart.");
            }
            default -> new DedicatedMessage(DedicatedMessageType.OBSERVATION, "Operation not supported.");
        };
    }

    @Override
    public void trySleep(int ms) {
        this.pageAutomationBot.trySleep(ms);
    }

    @Override
    public void closeSession(UUID uuid) {
        this.pageAutomationBot.closeSession(uuid);
    }

    private DedicatedMessage sortByPriceAscending(UUID sessionId) {
        return new DedicatedMessage(DedicatedMessageType.OBSERVATION, "Products sorted.");
    }

    private DedicatedMessage sortByPriceDescending(UUID sessionId) {
        return new DedicatedMessage(DedicatedMessageType.OBSERVATION, "Products sorted.");
    }

    private DedicatedMessage sortByPopularity(UUID sessionId) {
        WebDriver driver = this.pageAutomationBot.getDriver(sessionId);
        WebElement sortingDropdown = driver.findElement(By.className("listing-sorting-dropdown"));
        // TODO finish this
        return new DedicatedMessage(DedicatedMessageType.OBSERVATION, "Products sorted.");
    }

    private DedicatedMessage applyFilters(UUID sessionId, String filterName, Predicate<WebElement> filterOfFilters, ShoppingAction shoppingAction) {
        WebDriver driver = this.pageAutomationBot.getDriver(sessionId);
        WebElement sidebar = driver.findElement(By.className("sidebar-filter-container"));
        WebElement sidebarBody = sidebar.findElement(By.xpath("./div[contains(@class, 'sidebar-container-body')]"));
        Optional<WebElement> maybeBrandFilter = sidebarBody.findElements(By.xpath("./div[contains(@class, 'filter')]"))
                .stream()
                .filter(e -> !e.findElements(By.xpath("./a[span[text() = '" + filterName + "']]")).isEmpty())
                .findFirst();
        if (maybeBrandFilter.isEmpty()) {
            return new DedicatedMessage(DedicatedMessageType.OBSERVATION, "Could not find searched filter for this product.");
        }
        WebElement brandFilter = maybeBrandFilter.get();
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", brandFilter);
        pageAutomationBot.trySleep(200);
        List<WebElement> filterOptions = brandFilter.findElements(By.xpath("./div/div/a"));
        List<WebElement> validOptions = filterOptions.stream().filter(filterOfFilters).toList();
        validOptions.forEach(priceOption -> {
            priceOption.click();
            pageAutomationBot.trySleep(500); // to be removed
        });
        pageAutomationBot.trySleep(500);
        Message first5Products = this.pageAutomationBot.performAction(sessionId, new BasicAction(ActionType.GET_FIRST_N_CHILDREN_ATTRIBUTE, "card_grid", "5 data-name"));
        return new DedicatedMessage(DedicatedMessageType.OBSERVATION, "Searched for " + shoppingAction.command() + " and found products: " + first5Products.text() + ".");
    }

    private DedicatedMessage priceUnder(UUID sessionId, Predicate<WebElement> priceOptionFilter, ShoppingAction shoppingAction) {
        WebDriver driver = this.pageAutomationBot.getDriver(sessionId);
        WebElement sidebar = driver.findElement(By.className("sidebar-filter-container"));
        WebElement sidebarBody = sidebar.findElement(By.xpath("./div[contains(@class, 'sidebar-container-body')]"));
        Optional<WebElement> maybePriceFilter = sidebarBody.findElements(By.xpath("./div[contains(@class, 'filter')]"))
                .stream()
                .filter(e -> !e.findElements(By.xpath("./a[span[text() = 'Pret']]")).isEmpty())
                .findFirst();
        if (maybePriceFilter.isEmpty()) {
            return new DedicatedMessage(DedicatedMessageType.OBSERVATION, "Could not find price filter for this product.");
        }
        WebElement priceFilter = maybePriceFilter.get();
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", priceFilter);
        pageAutomationBot.trySleep(200);
        List<WebElement> priceOptions = priceFilter.findElements(By.xpath("./div/div/a"));
        List<WebElement> validPriceOptions = priceOptions.stream().filter(priceOptionFilter).toList();
        validPriceOptions.forEach(priceOption -> {
            priceOption.click();
            pageAutomationBot.trySleep(500); // to be removed
        });
        pageAutomationBot.trySleep(500);
        Message first5Products = this.pageAutomationBot.performAction(sessionId, new BasicAction(ActionType.GET_FIRST_N_CHILDREN_ATTRIBUTE, "card_grid", "5 data-name"));
        return new DedicatedMessage(DedicatedMessageType.OBSERVATION, "Searched for " + shoppingAction.command() + " and found products: " + first5Products.text() + ".");
    }

    private Predicate<WebElement> buildBrandEqualToFilter(ShoppingAction shoppingAction) {
        return e -> {
            String dataName = e.getAttribute("data-name");
            return dataName.equalsIgnoreCase(shoppingAction.command());
        };
    }

    private static Predicate<WebElement> buildPriceUnderFilter(ShoppingAction shoppingAction) {
        return e -> {
            String[] fromTo = e.getAttribute("data-option-id").split("-");
            List<String> fromToMargins = List.of(shoppingAction.command().split(" "));
            return Double.parseDouble(fromTo[1]) < Double.parseDouble(fromToMargins.getLast()) && Double.parseDouble(fromTo[0]) > Double.parseDouble(fromToMargins.getFirst());
        };
    }

    private static Predicate<WebElement> buildPriceBetweenFilter(ShoppingAction shoppingAction) {
        return e -> {
            String[] fromTo = e.getAttribute("data-option-id").split("-");
            return Double.parseDouble(fromTo[1]) < Double.parseDouble(shoppingAction.command());
        };
    }


}
