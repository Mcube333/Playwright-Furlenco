package com.tests.pages;

import com.framework.base.BasePage;
import com.microsoft.playwright.Page;

public class InventoryPage extends BasePage {

    private static final String INVENTORY_CONTAINER = ".inventory_list";
    private static final String CART_BADGE = ".shopping_cart_badge";
    private static final String ADD_TO_CART_BACKPACK = "[data-test='add-to-cart-sauce-labs-backpack']";

    public InventoryPage(Page page) {
        super(page);
    }

    public boolean isLoaded() {
        return isVisible(INVENTORY_CONTAINER);
    }

    public void addBackpackToCart() {
        click(ADD_TO_CART_BACKPACK);
    }

    public String getCartCount() {
        return isVisible(CART_BADGE) ? getText(CART_BADGE) : "0";
    }
}
