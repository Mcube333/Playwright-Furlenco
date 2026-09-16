package com.tests.pages.furlenco.mobile;

import com.framework.base.BaseMobilePage;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.AppiumBy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;

/**
 * Page Object for the Global Home screen of the native Furlenco app.
 * <p>
 * <b>TODO before use:</b> every {@code By} locator below is a placeholder guess (Android
 * resource-id naming convention {@code com.furlenco.android:id/...}), not a verified locator.
 * There was no APK, running emulator/device, or Appium Inspector session available to capture the
 * actual accessibility ids/resource-ids from the real app. To finish this page object:
 * <ol>
 *   <li>Install the app under test on a device/emulator and start it with Appium Inspector
 *       (or {@code appium --allow-insecure=chromedriver_autodownload} + a UiAutomator2 session).</li>
 *   <li>Replace every locator below with the real {@code resource-id}/{@code accessibility id}
 *       captured from the inspector.</li>
 *   <li>Remove this TODO block once verified.</li>
 * </ol>
 * Follow the same page-object-per-screen structure as {@code com.tests.pages.furlenco} (web) —
 * one class per screen, only element interactions here, assertions stay in the test class.
 */
public class FurlencoMobileHomeScreen extends BaseMobilePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoMobileHomeScreen.class);

    private static final By RENT_TAB = AppiumBy.accessibilityId("rent_tab"); // TODO verify
    private static final By BUY_TAB = AppiumBy.accessibilityId("buy_tab"); // TODO verify
    private static final By UNLMTD_TAB = AppiumBy.accessibilityId("unlmtd_tab"); // TODO verify
    private static final By CART_ICON = AppiumBy.accessibilityId("cart_icon"); // TODO verify
    private static final By ACCOUNT_ICON = AppiumBy.accessibilityId("account_icon"); // TODO verify

    public FurlencoMobileHomeScreen(AppiumDriver driver) {
        super(driver);
    }

    public void tapRentTab() {
        LOGGER.info("Tapping RENT tab");
        click(RENT_TAB);
    }

    public void tapBuyTab() {
        LOGGER.info("Tapping BUY tab");
        click(BUY_TAB);
    }

    public void tapUnlimitedTab() {
        LOGGER.info("Tapping UNLMTD tab");
        click(UNLMTD_TAB);
    }

    public void tapCartIcon() {
        LOGGER.info("Tapping cart icon");
        click(CART_ICON);
    }

    public void tapAccountIcon() {
        LOGGER.info("Tapping account icon (login entry point)");
        click(ACCOUNT_ICON);
    }
}
