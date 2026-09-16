package com.tests.mobile.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.tests.base.BaseMobileTest;
import com.tests.pages.furlenco.mobile.FurlencoMobileHomeScreen;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Scaffolding for the native-app equivalents of the manual "Product Listing (PLP)" flows —
 * disabled by default. See {@link FurlencoMobileHomeScreen} javadoc: locators are unverified
 * placeholders because no device/emulator, APK, or Appium Inspector session was available to
 * build real ones against. Remove {@code enabled = false} once locators are verified and a real
 * Appium server + app is available in the run environment.
 */
@Epic("Furlenco Mobile App Automation")
@Feature("Product Listing (PLP) Navigation - Native")
public class FurlencoMobileNavigationTest extends BaseMobileTest {

    private FurlencoMobileHomeScreen homeScreen;

    @BeforeMethod(alwaysRun = true)
    public void initHomeScreen() {
        homeScreen = new FurlencoMobileHomeScreen(driver);
    }

    @Test(groups = {"mobile", "furlenco"}, priority = 1, enabled = false)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify user navigates to Rent PLP screen when tapping Rent in Global Home screen")
    public void verifyNavigateToRentPlp() {
        homeScreen.tapRentTab();
        // TODO: assert on the resulting screen once a FurlencoMobilePlpScreen page object exists.
        assertThat(true).isTrue();
    }

    @Test(groups = {"mobile", "furlenco"}, priority = 2, enabled = false)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify user navigates back from PLP screen to Home screen via the device back button")
    public void verifyDeviceBackFromPlpToHome() {
        homeScreen.tapRentTab();
        homeScreen.pressDeviceBack();
        // TODO: assert Home screen is visible again once a Home-screen state check exists.
        assertThat(true).isTrue();
    }
}
