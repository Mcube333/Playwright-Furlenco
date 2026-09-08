package com.tests.base;

import com.framework.driver.PlaywrightManager;
import com.microsoft.playwright.Page;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public abstract class BaseWebTest extends BaseTest {

    protected Page page;

    @BeforeMethod(alwaysRun = true)
    public void setUpBrowser() {
        PlaywrightManager.initBrowser();
        page = PlaywrightManager.getPage();
        LOGGER.info("Browser session started for thread {}", Thread.currentThread().getId());
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownBrowser() {
        PlaywrightManager.tearDown();
        LOGGER.info("Browser session closed for thread {}", Thread.currentThread().getId());
    }
}
