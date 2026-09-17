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
     * Opens product URLs in order until one has an enabled Add to Cart button, up to {@code
     * maxAttempts}.
     * <p>
     * <b>Verified live root cause this works around:</b> clicking a PLP product card
     * ({@code Locator.click()}) reliably fails to navigate at all on this app — the page silently
     * stays on the PLP's own URL, confirmed by logging {@code page.url()} immediately after the
     * click. Every earlier "Add to Cart is disabled" failure traced back to this: the code was
     * checking for an Add to Cart button while still sitting on the listing page. A direct page
     * navigation to the product's own URL (extracted from the card's real {@code href} via the
     * DOM, not the possibly-relative attribute) is what every successful manual verification of
     * this app used, and is what actually reaches the product page reliably.
     */
    @Step("Click first available (purchasable) product card on PLP")
    public FurlencoProductPage clickFirstAvailableProduct(int maxAttempts) {
        Locator cards = page.locator(PRODUCT_CARDS);
        int count = Math.min(maxAttempts, cards.count());
        for (int i = 0; i < count; i++) {
            Object href = cards.nth(i).evaluate("el => el.href");
            if (href == null || href.toString().isBlank()) {
                continue;
            }
            LOGGER.info("Trying product URL (index {}): {}", i, href);
            page.navigate(href.toString());
            page.waitForLoadState();
            FurlencoProductPage productPage = new FurlencoProductPage(page);
            if (productPage.isAddToCartButtonEnabled()) {
                return productPage;
            }
            LOGGER.info("Product at index {} has Add to Cart disabled, trying next", i);
        }
        throw new IllegalStateException(
                "No purchasable product (enabled Add to Cart) found in the first " + maxAttempts + " cards on this PLP.");
    }
}
