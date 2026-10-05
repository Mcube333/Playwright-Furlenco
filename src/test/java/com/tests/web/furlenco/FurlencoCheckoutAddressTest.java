package com.tests.web.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.tests.base.BaseWebTest;
import com.tests.pages.furlenco.FurlencoCartDrawer;
import com.tests.pages.furlenco.FurlencoCheckoutAddressPage;
import com.tests.pages.furlenco.FurlencoHomePage;
import com.tests.pages.furlenco.FurlencoLoginPage;
import com.tests.pages.furlenco.FurlencoPlpPage;
import com.tests.pages.furlenco.FurlencoProductPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Delivery Address step of checkout — covers the parts of the app smoke scenarios "Existing
 * Address" and "Add New Address" that the verified page object supports (saved address listed and
 * pre-selected; Add New Address entry point available). The add-address form itself, address
 * search by area/pincode and "Invalid address" are NOT covered: that form was never inspected
 * live, so no locators exist for it yet — see docs/qa-trackers/kaneai-app-smoke-coverage.md.
 */
@Epic("Furlenco Web Automation")
@Feature("Checkout - Delivery Address")
public class FurlencoCheckoutAddressTest extends BaseWebTest {

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    private void login() {
        FurlencoLoginPage loginPage = homePage.openLogin();
        if (loginPage.isOpen()) {
            loginPage.loginWithOtp(config.get("test.user.username"), config.get("test.user.password"));
        }
    }

    private FurlencoCheckoutAddressPage reachAddressStep() {
        homePage.open(furlencoUrl);
        homePage.enterPincode(config.get("test.delivery.pincode", "110001"));
        login();
        homePage.clickRentTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);
        productPage.clickAddToCart();
        FurlencoCartDrawer cartDrawer = homePage.openCart();
        return cartDrawer.clickCheckout();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify the Delivery Address step lists saved addresses with one pre-selected")
    public void verifyExistingAddressPreSelected() {
        FurlencoCheckoutAddressPage addressPage = reachAddressStep();

        assertThat(addressPage.isLoaded()).as("Delivery Address step should load").isTrue();
        assertThat(addressPage.hasSavedAddress()).as("Saved address should be listed").isTrue();
        assertThat(addressPage.isAnyAddressSelected()).as("A saved address should be pre-selected").isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify the Add New Address action is offered on the Delivery Address step")
    public void verifyAddNewAddressOptionAvailable() {
        FurlencoCheckoutAddressPage addressPage = reachAddressStep();

        assertThat(addressPage.isAddNewAddressAvailable())
                .as("Add New Address action should be available")
                .isTrue();
    }
}
