package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for Furlenco Product Details Page (PDP).
 */
public class FurlencoProductPage extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoProductPage.class);

    private static final String PRODUCT_TITLE = "h1, h2, [data-testid='product-title']";
    private static final String ADD_TO_CART_BTN = "button:has-text('Add to Cart'), button:has-text('ADD TO CART'), button:has-text('Rent Now'), button:has-text('Buy Now')";
    private static final String PRICE_TEXT = "[data-testid*='price'], p:has-text('₹'), span:has-text('₹')";

    public FurlencoProductPage(Page page) {
        super(page);
    }

    @Step("Check if Product Details Page is loaded")
    public boolean isLoaded() {
        return isVisible(PRODUCT_TITLE) || currentUrl().contains("/product");
    }

    @Step("Get product title text")
    public String getProductTitle() {
        Locator titleLocator = page.locator(PRODUCT_TITLE).first();
        titleLocator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        String title = titleLocator.textContent().trim();
        LOGGER.info("Product title is: {}", title);
        return title;
    }

    @Step("Check if Add to Cart / Rent CTA button is visible")
    public boolean isAddToCartButtonVisible() {
        return page.locator(ADD_TO_CART_BTN).first().isVisible();
    }

    /**
     * Checks if Add to Cart is enabled, polling briefly first — verified live that the button can
     * render disabled momentarily while price/availability data is still loading, then become
     * enabled a moment later, so a single instant check can false-negative.
     */
    @Step("Check if Add to Cart / Rent CTA button is enabled (in stock / no variant required)")
    public boolean isAddToCartButtonEnabled() {
        Locator btn = page.locator(ADD_TO_CART_BTN).first();
        if (btn.count() == 0) {
            return false;
        }
        for (int i = 0; i < 6; i++) {
            if (btn.isVisible() && btn.isEnabled()) {
                return true;
            }
            page.waitForTimeout(500);
        }
        return false;
    }

    @Step("Click Add to Cart / Rent CTA button")
    public void clickAddToCart() {
        LOGGER.info("Clicking Add to Cart / Rent button");
        Locator btn = page.locator(ADD_TO_CART_BTN).first();
        btn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        btn.click();
    }

    @Step("Check if product pricing is displayed")
    public boolean isPriceDisplayed() {
        return page.locator(PRICE_TEXT).first().isVisible();
    }
}
