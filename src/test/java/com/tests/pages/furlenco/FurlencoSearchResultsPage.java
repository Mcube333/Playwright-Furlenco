package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for Furlenco Search Results / Product Listing Page.
 */
public class FurlencoSearchResultsPage extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoSearchResultsPage.class);

    private static final String PRODUCT_CARDS = "a[href*='/product/'], a[href*='/package/'], a[href*='/products/'], div[data-testid*='product'], main a:has(img)";

    public FurlencoSearchResultsPage(Page page) {
        super(page);
    }

    @Step("Wait for search results to load")
    public FurlencoSearchResultsPage waitForResults() {
        LOGGER.info("Waiting for product results container to load");
        page.waitForLoadState();
        page.waitForTimeout(2000);
        return this;
    }

    @Step("Check if search results page is loaded")
    public boolean isLoaded() {
        return currentUrl().contains("/search/results") || currentUrl().contains("q=");
    }

    @Step("Check if any product card is displayed")
    public boolean isAnyProductDisplayed() {
        return getProductCount() > 0;
    }

    @Step("Get count of visible products")
    public int getProductCount() {
        Locator products = page.locator(PRODUCT_CARDS);
        int count = products.count();
        LOGGER.info("Found {} product cards on search page", count);
        return count;
    }

    @Step("Click first product card in results")
    public FurlencoProductPage clickFirstProduct() {
        LOGGER.info("Clicking first product");
        Locator firstProduct = page.locator(PRODUCT_CARDS).first();
        firstProduct.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        firstProduct.click();
        page.waitForLoadState();
        return new FurlencoProductPage(page);
    }

    @Step("Get current search page URL")
    public String getResultsUrl() {
        return currentUrl();
    }
}
