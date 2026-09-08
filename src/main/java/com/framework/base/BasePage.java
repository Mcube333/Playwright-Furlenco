package com.framework.base;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common actions for all Page Objects. No test/assertion logic belongs here — only
 * page interactions. Every wait is explicit (Playwright auto-waiting + our own visible-state
 * checks) — Thread.sleep is never used anywhere in this framework.
 */
public abstract class BasePage {

    private static final Logger LOGGER = LogManager.getLogger(BasePage.class);

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    @Step("Navigate to {url}")
    public void navigateTo(String url) {
        LOGGER.info("Navigating to {}", url);
        page.navigate(url);
    }

    @Step("Click element: {selector}")
    protected void click(String selector) {
        Locator locator = page.locator(selector);
        locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        LOGGER.debug("Clicking [{}]", selector);
        locator.click();
    }

    @Step("Fill '{value}' into: {selector}")
    protected void fill(String selector, String value) {
        Locator locator = page.locator(selector);
        locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        LOGGER.debug("Filling [{}] with masked value (len={})", selector, value.length());
        locator.fill(value);
    }

    @Step("Get text of: {selector}")
    protected String getText(String selector) {
        Locator locator = page.locator(selector);
        locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        return locator.textContent().trim();
    }

    protected boolean isVisible(String selector) {
        return page.locator(selector).isVisible();
    }

    @Step("Wait for element to be visible: {selector}")
    protected void waitForVisible(String selector) {
        page.locator(selector).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    protected void waitForHidden(String selector) {
        page.locator(selector).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
    }

    public String currentUrl() {
        return page.url();
    }

    public String currentTitle() {
        return page.title();
    }
}
