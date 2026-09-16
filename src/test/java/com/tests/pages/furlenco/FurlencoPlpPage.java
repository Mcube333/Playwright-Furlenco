package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the Product Listing Page (PLP) shared across Rent / Buy / Unlmtd verticals.
 * Selectors are best-effort (see {@link FurlencoLoginPage} javadoc note on unverified selectors).
 */
public class FurlencoPlpPage extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoPlpPage.class);

    private static final String CATEGORY_TABS =
            "[data-slot='category-tabs'] button, [data-slot='category-tabs'] a, nav[aria-label='categories'] a";
    private static final String SUBCATEGORY_CHIPS =
            "[data-slot='subcategory-chips'] button, [data-slot='filters'] button, [data-slot='subcategory'] a";
    private static final String PRODUCT_CARDS =
            "a[href*='/product/'], a[href*='/products/'], a[href*='/package/'], div[data-testid*='product'], main a:has(img)";

    public FurlencoPlpPage(Page page) {
        super(page);
    }

    @Step("Check if PLP is loaded")
    public boolean isLoaded() {
        boolean urlMatches = currentUrl().contains("/listing") || currentUrl().contains("/rent")
                || currentUrl().contains("/buy") || currentUrl().contains("/unlmtd");
        boolean hasProducts = getProductCount() > 0;
        LOGGER.info("PLP loaded check: urlMatches={}, hasProducts={}", urlMatches, hasProducts);
        return urlMatches && hasProducts;
    }

    @Step("Get count of visible products on PLP")
    public int getProductCount() {
        return page.locator(PRODUCT_CARDS).count();
    }

    @Step("Get count of category tabs available")
    public int getCategoryCount() {
        return page.locator(CATEGORY_TABS).count();
    }

    @Step("Select category: {categoryName}")
    public FurlencoPlpPage selectCategory(String categoryName) {
        LOGGER.info("Selecting PLP category: {}", categoryName);
        Locator category = page.locator(CATEGORY_TABS).filter(new Locator.FilterOptions().setHasText(categoryName)).first();
        category.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        category.click(new Locator.ClickOptions().setForce(true));
        page.waitForLoadState();
        page.waitForTimeout(500);
        return this;
    }

    @Step("Get count of subcategory filter chips available")
    public int getSubcategoryCount() {
        return page.locator(SUBCATEGORY_CHIPS).count();
    }

    @Step("Select subcategory: {subcategoryName}")
    public FurlencoPlpPage selectSubcategory(String subcategoryName) {
        LOGGER.info("Selecting PLP subcategory: {}", subcategoryName);
        Locator subcategory = page.locator(SUBCATEGORY_CHIPS)
                .filter(new Locator.FilterOptions().setHasText(subcategoryName)).first();
        subcategory.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        subcategory.click(new Locator.ClickOptions().setForce(true));
        page.waitForLoadState();
        page.waitForTimeout(500);
        return this;
    }

    @Step("Click first product card on PLP")
    public FurlencoProductPage clickFirstProduct() {
        LOGGER.info("Clicking first product card on PLP");
        Locator firstProduct = page.locator(PRODUCT_CARDS).first();
        firstProduct.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        firstProduct.click();
        page.waitForLoadState();
        return new FurlencoProductPage(page);
    }

    /**
     * Opens product cards in order until one has an enabled Add to Cart button, up to {@code
     * maxAttempts}. Verified live: the plain "first product" on a PLP can have Add to Cart
     * disabled (out of stock, or requires a variant selection this framework doesn't drive) —
     * callers that need to actually add something to cart (checkout/order-placement flows) should
     * use this instead of {@link #clickFirstProduct()}.
     */
    @Step("Click first available (purchasable) product card on PLP")
    public FurlencoProductPage clickFirstAvailableProduct(int maxAttempts) {
        for (int i = 0; i < maxAttempts; i++) {
            Locator card = page.locator(PRODUCT_CARDS).nth(i);
            if (card.count() == 0) {
                break;
            }
            LOGGER.info("Trying product card index {} for availability", i);
            card.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            card.click();
            page.waitForLoadState();
            // Verified live: Add to Cart can stay stuck disabled after the SPA's client-side
            // navigation from a PLP card click, but is correctly enabled after a full page load of
            // the same product URL — force one rather than trusting the client-side transition.
            page.reload();
            page.waitForLoadState();
            FurlencoProductPage productPage = new FurlencoProductPage(page);
            if (productPage.isAddToCartButtonEnabled()) {
                return productPage;
            }
            LOGGER.info("Product at index {} has Add to Cart disabled, trying next", i);
            productPage.goBack();
        }
        throw new IllegalStateException(
                "No purchasable product (enabled Add to Cart) found in the first " + maxAttempts + " cards on this PLP.");
    }
}
