package com.tests.base;

import com.framework.driver.AppiumManager;
import io.appium.java_client.AppiumDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 * Base class for native Android/iOS Appium tests, mirroring {@link BaseWebTest}'s lifecycle for
 * Playwright. Requires a running Appium server and valid {@code appium.*} config (see
 * {@link com.framework.driver.AppiumManager} javadoc) — every mobile test extending this class
 * will fail fast with a clear error if that config isn't filled in yet.
 */
public abstract class BaseMobileTest extends BaseTest {

    protected AppiumDriver driver;

    @BeforeMethod(alwaysRun = true)
    public void setUpDriver() {
        AppiumManager.initDriver();
        driver = AppiumManager.getDriver();
        LOGGER.info("Appium session started for thread {}", Thread.currentThread().getId());
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownDriver() {
        AppiumManager.tearDown();
        LOGGER.info("Appium session closed for thread {}", Thread.currentThread().getId());
    }
}
