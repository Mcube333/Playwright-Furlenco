package com.framework.base;

import io.appium.java_client.AppiumDriver;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Common actions for native-app Page Objects (Appium), mirroring {@link BasePage}'s role for
 * Playwright web page objects — only element interactions live here, never assertions.
 */
public abstract class BaseMobilePage {

    private static final Logger LOGGER = LogManager.getLogger(BaseMobilePage.class);
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(20);

    protected final AppiumDriver driver;

    protected BaseMobilePage(AppiumDriver driver) {
        this.driver = driver;
    }

    protected WebElement waitForVisible(By locator) {
        return new WebDriverWait(driver, DEFAULT_WAIT).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected void click(By locator) {
        LOGGER.debug("Tapping [{}]", locator);
        waitForVisible(locator).click();
    }

    protected void type(By locator, String value) {
        LOGGER.debug("Typing into [{}] (masked value, len={})", locator, value.length());
        WebElement element = waitForVisible(locator);
        element.clear();
        element.sendKeys(value);
    }

    protected String getText(By locator) {
        return waitForVisible(locator).getText();
    }

    protected boolean isVisible(By locator) {
        try {
            return !driver.findElements(locator).isEmpty() && driver.findElement(locator).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /** Presses the device/OS back button — distinct from an in-app "back" UI control. */
    public void pressDeviceBack() {
        LOGGER.info("Pressing device back button");
        driver.navigate().back();
    }
}
