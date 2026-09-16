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
    private static final String B2B_NAV_LINK = "header a:has-text('B2B')";
    private static final String CART_BUTTON = "button[aria-label='Cart']";
    private static final String WISHLIST_BUTTON = "button[aria-label='Wishlist']";
    private static final String ACCOUNT_MENU_BUTTON = "button[aria-label='Account menu']";
    private static final String HELP_CENTER_BUTTON = "a:has-text('Help Center'), a[href*='help.furlenco.com']";
    private static final String CITIES_DELIVER_SECTION = "text=CITIES WE DELIVER TO";
    // Category cards are plain divs with an onClick handler (not <a> tags) — text-match the card's
    // label directly, verified against a live session.
    private static final String HOME_CATEGORY_CARD_LABEL = "p.text-center:text-is('%s')";
    private static final String ACCOUNT_HOVER_CARD_CONTENT = "[data-slot='hover-card-content']";
    private static final String ACCOUNT_HOVER_CARD_LOGIN = "[data-slot='hover-card-content'] p:has-text('Login')";

    public FurlencoHomePage(Page page) {
        super(page);
    }

    @Step("Navigate to Furlenco home page: {url}")
    public FurlencoHomePage open(String url) {
        LOGGER.info("Opening Furlenco home page: {}", url);
        FurlencoExperiments.pinIfEnabled(page.context(), url);
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

    /**
     * Sets delivery location by pincode instead of picking a listed city — useful when the
     * currently-selected pincode has poor product availability (verified live: several Bedroom
     * category products under one pincode showed a consistently disabled Add to Cart, most likely
     * a delivery-serviceability/inventory gap for that pincode rather than a bug).
     */
    @Step("Enter delivery pincode: {pincode}")
    public FurlencoHomePage enterPincode(String pincode) {
        LOGGER.info("Entering delivery pincode: {}", pincode);
        openCityModal();
        Locator pincodeInput = page.locator("input[placeholder*='pincode' i]").first();
        pincodeInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        pincodeInput.fill(pincode);
        pincodeInput.press("Enter");
        page.waitForTimeout(1500);
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

    /**
     * Clicks B2B in the header, which links to a separate domain (business.furlenco.com). Returns
     * the resulting page's URL for a caller to assert on, since that site has no page objects of
     * its own here (out of scope — see project README "Known gaps").
     */
    @Step("Click B2B in header")
    public String clickB2B() {
        LOGGER.info("Clicking B2B nav link");
        dismissLocationModalIfOpen();
        Locator b2bLink = page.locator(B2B_NAV_LINK).first();
        String target = b2bLink.getAttribute("target");
        if ("_blank".equals(target)) {
            // External-domain link opens a new tab — the current page's URL never changes.
            Page popup = page.waitForPopup(b2bLink::click);
            popup.waitForLoadState();
            return popup.url();
        }
        b2bLink.click();
        page.waitForLoadState();
        return page.url();
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

    @Step("Click a product category card by visible text: {categoryName}")
    public FurlencoPlpPage clickCategory(String categoryName) {
        LOGGER.info("Clicking category card: {}", categoryName);
        dismissLocationModalIfOpen();
        // Move the mouse to a neutral spot first: a lingering hover-triggered nav mega-menu
        // (Radix popper, e.g. RENT's category flyout) can otherwise sit on top of the target card
        // and intercept the click — verified against a live session.
        page.mouse().move(0, 0);
        Locator category = page.locator(String.format(HOME_CATEGORY_CARD_LABEL, categoryName)).first();
        category.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        category.scrollIntoViewIfNeeded();
        category.click(new Locator.ClickOptions().setForce(true));
        page.waitForLoadState();
        return new FurlencoPlpPage(page);
    }

    /**
     * Opens the Account menu (a Radix HoverCard — opens on hover, not click) and clicks "Login"
     * inside it. Verified against a live session: the Account menu button itself never navigates
     * or opens a dialog on click; only hovering reveals the "Login" entry.
     */
    @Step("Open Account menu (hover) and click Login")
    public FurlencoLoginPage openLogin() {
        LOGGER.info("Hovering Account menu to reveal Login entry");
        dismissLocationModalIfOpen();
        Locator accountMenu = page.locator(ACCOUNT_MENU_BUTTON).first();
        // Force: a transient toast/backdrop (e.g. "Location has been updated") can otherwise fail
        // Playwright's "receives pointer events" actionability check even though the button is
        // visible and functional — verified against a live session.
        accountMenu.hover(new Locator.HoverOptions().setForce(true));
        page.locator(ACCOUNT_HOVER_CARD_CONTENT).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.locator(ACCOUNT_HOVER_CARD_LOGIN).first().click();
        return new FurlencoLoginPage(page);
    }
}
