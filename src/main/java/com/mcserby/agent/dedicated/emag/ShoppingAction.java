package com.mcserby.agent.dedicated.emag;

import com.mcserby.agent.bot.ShoppingActionType;

import java.util.Optional;

public record ShoppingAction(ShoppingActionType type, String command) {

    public static Optional<ShoppingAction> fromString(String line) {
        if(line.contains("SEARCH")){
            return Optional.of(new ShoppingAction(ShoppingActionType.SEARCH, line.substring(line.indexOf("SEARCH") + "SEARCH".length()).replace("\"", "").trim()));
        }
        if(line.contains("FILTER") && line.contains("PRICE UNDER")){
            return Optional.of(new ShoppingAction(ShoppingActionType.FILTER_PRICE_UNDER, line.substring(line.indexOf("FILTER PRICE UNDER") + "FILTER PRICE UNDER\"".length()).replace("\"", "").trim()));
        }
        if(line.contains("FILTER") && line.contains("PRICE BETWEEN")){
            return Optional.of(new ShoppingAction(ShoppingActionType.FILTER_PRICE_BETWEEN, line.substring(line.indexOf("FILTER PRICE BETWEEN") + "FILTER PRICE BETWEEN".length()).replace("\"", "").trim()));
        }
        if(line.contains("FILTER") && line.contains("BRAND")){
            return Optional.of(new ShoppingAction(ShoppingActionType.FILTER_BRAND, line.substring(line.indexOf("FILTER BRAND") + "FILTER BRAND".length()).replace("\"", "").trim()));
        }
        if(line.contains("SORT BY POPULARITY")){
            return Optional.of(new ShoppingAction(ShoppingActionType.SORT_BY_POPULARITY, ""));
        }
        if(line.contains("SORT BY PRICE ASCENDING")){
            return Optional.of(new ShoppingAction(ShoppingActionType.SORT_BY_PRICE_ASCENDING, ""));
        }
        if(line.contains("SORT BY PRICE DESCENDING")){
            return Optional.of(new ShoppingAction(ShoppingActionType.SORT_BY_PRICE_DESCENDING, ""));
        }
        if(line.contains("SELECT PRODUCT")){
            return Optional.of(new ShoppingAction(ShoppingActionType.SELECT_FIRST_PRODUCT, ""));
        }
        if(line.contains("PRODUCT DETAILS")){
            return Optional.of(new ShoppingAction(ShoppingActionType.PRODUCT_DETAILS, ""));
        }
        if(line.contains("ADD TO CART")){
            return Optional.of(new ShoppingAction(ShoppingActionType.ADD_TO_CART, ""));
        }
        return Optional.empty();
    }
}
