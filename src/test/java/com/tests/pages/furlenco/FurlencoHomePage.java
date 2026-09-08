package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for Furlenco Home Page (www.furlenco.com).
 * Handles header branding, location/city selector, search, category navigation, and footer.
 */
public class FurlencoHomePage extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoHomePage.class);

    // Selectors
    private static final String LOGO_IMG = "header img[alt='Furlenco Logo'], img[alt='Furlenco Logo']";
    private static final String CITY_TRIGGER = "header .cursor-pointer:has(svg), header div:has-text('Select City')";
    private static final String LOCATION_DRAWER = "[data-slot='location-selector'], .MuiDrawer-root";
    private static final String SEARCH_INPUT = "input[placeholder*='Search']";
    private static final String SEARCH_ICON = "fieldset:has(input[placeholder*='Search']) svg";
    private static final String RENT_NAV_LINK = "header a:has-text('RENT')";
    private static final String BUY_NAV_LINK = "header a:has-text('BUY')";
    private static final String UNLMTD_NAV_LINK = "header a:has-text('UNLMTD')";
    private static final String CART_BUTTON = "button[aria-label='Cart']";
    private static final String WISHLIST_BUTTON = "button[aria-label='Wishlist']";
    private static final String HELP_CENTER_BUTTON = "a:has-text('Help Center'), a[href*='help.furlenco.com']";
    private static final String CITIES_DELIVER_SECTION = "text=CITIES WE DELIVER TO";

    public FurlencoHomePage(Page page) {
        super(page);
    }

    @Step("Navigate to Furlenco home page: {url}")
    public FurlencoHomePage open(String url) {
        LOGGER.info("Opening Furlenco home page: {}", url);
        navigateTo(url);
        page.waitForLoadState();
        dismissLocationModalIfOpen();
        return this;
    }

    @Step("Dismiss location drawer if open on load")
    public FurlencoHomePage dismissLocationModalIfOpen() {
        try {
            Locator drawer = page.locator(LOCATION_DRAWER);
            Locator backdrop = page.locator(".MuiBackdrop-root, [data-slot='drawer-overlay']");
            if (drawer.first().isVisible() || backdrop.first().isVisible()) {
                LOGGER.info("Initial location drawer or backdrop visible, dismissing");
                Locator closeBtn = page.locator("[data-slot='location-selector'] button, .MuiDrawer-root button[aria-label='Close'], button.close").first();
                if (closeBtn.isVisible()) {
                    closeBtn.click(new Locator.ClickOptions().setForce(true));
                } else {
                    page.keyboard().press("Escape");
                }
                page.waitForTimeout(600);
            }
        } catch (Exception e) {
            LOGGER.debug("No modal dismiss needed: {}", e.getMessage());
        }
        return this;
    }

    @Step("Check if Furlenco logo is visible")
    public boolean isLogoVisible() {
        return page.locator(LOGO_IMG).first().isVisible();
    }

    @Step("Get current page title")
    public String getTitle() {
        return currentTitle();
    }

    @Step("Open City Selector Modal")
    public FurlencoHomePage openCityModal() {
        LOGGER.info("Opening City Selection modal");
        Locator drawer = page.locator(LOCATION_DRAWER);
        if (!drawer.first().isVisible()) {
            Locator trigger = page.locator(CITY_TRIGGER).first();
            trigger.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            trigger.click(new Locator.ClickOptions().setForce(true));
            page.waitForTimeout(600);
        }
        return this;
    }

    @Step("Select City: {cityName}")
    public FurlencoHomePage selectCity(String cityName) {
        LOGGER.info("Selecting city: {}", cityName);
        openCityModal();
        Locator drawer = page.locator(LOCATION_DRAWER).first();
        Locator cityOption = drawer.locator(String.format("p:has-text('%s'), div:has-text('%s')", cityName, cityName)).last();
        cityOption.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        cityOption.click(new Locator.ClickOptions().setForce(true));
        page.waitForTimeout(1000);
        return this;
    }

    @Step("Search for product: {query}")
    public FurlencoSearchResultsPage searchProduct(String query) {
        LOGGER.info("Searching for: {}", query);
        dismissLocationModalIfOpen();
        Locator searchBox = page.locator(SEARCH_INPUT).first();
        searchBox.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        searchBox.click();
        searchBox.fill(query);
        page.waitForTimeout(500);
        // Click search icon svg to submit search
        Locator searchBtn = page.locator(SEARCH_ICON).first();
        if (searchBtn.isVisible()) {
            searchBtn.click(new Locator.ClickOptions().setForce(true));
        } else {
            searchBox.press("Enter");
        }
        page.waitForLoadState();
        return new FurlencoSearchResultsPage(page);
    }

    @Step("Click RENT tab in header")
    public FurlencoHomePage clickRentTab() {
        LOGGER.info("Clicking RENT tab");
        dismissLocationModalIfOpen();
        page.locator(RENT_NAV_LINK).first().click(new Locator.ClickOptions().setForce(true));
        page.waitForLoadState();
        return this;
    }

    @Step("Click BUY tab in header")
    public FurlencoHomePage clickBuyTab() {
        LOGGER.info("Clicking BUY tab");
        dismissLocationModalIfOpen();
        page.locator(BUY_NAV_LINK).first().click(new Locator.ClickOptions().setForce(true));
        page.waitForLoadState();
        return this;
    }

    @Step("Click UNLMTD tab in header")
    public FurlencoHomePage clickUnlmtdTab() {
        LOGGER.info("Clicking UNLMTD tab");
        dismissLocationModalIfOpen();
        page.locator(UNLMTD_NAV_LINK).first().click(new Locator.ClickOptions().setForce(true));
        page.waitForLoadState();
        return this;
    }

    @Step("Open Cart Drawer")
    public FurlencoCartDrawer openCart() {
        LOGGER.info("Clicking Cart button");
        dismissLocationModalIfOpen();
        page.locator(CART_BUTTON).first().click(new Locator.ClickOptions().setForce(true));
        page.waitForTimeout(1000);
        return new FurlencoCartDrawer(page);
    }

    @Step("Click Wishlist button")
    public void clickWishlist() {
        LOGGER.info("Clicking Wishlist button");
        dismissLocationModalIfOpen();
        page.locator(WISHLIST_BUTTON).first().click(new Locator.ClickOptions().setForce(true));
        page.waitForTimeout(500);
    }

    @Step("Check if Help Center link is displayed in footer")
    public boolean isHelpCenterDisplayed() {
        return page.locator(HELP_CENTER_BUTTON).first().isVisible();
    }

    @Step("Check if Cities We Deliver To section is displayed")
    public boolean isCitiesDeliverSectionDisplayed() {
        return page.locator(CITIES_DELIVER_SECTION).first().isVisible();
    }
}
