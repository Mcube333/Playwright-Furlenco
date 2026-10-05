package com.tests.web.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.tests.base.BaseWebTest;
import com.tests.pages.furlenco.FurlencoHomePage;
import com.tests.pages.furlenco.FurlencoLoginPage;
import com.tests.pages.furlenco.FurlencoMyOrdersPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Read-only "My Orders" checks — web equivalents of the app smoke scenarios "Login and View My
 * Orders" and "Verify Order Details Consistency". Non-destructive (unlike
 * {@link FurlencoCancelOrderTest}); relies on the test account having at least one order.
 */
@Epic("Furlenco Web Automation")
@Feature("My Orders")
public class FurlencoMyOrdersTest extends BaseWebTest {

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

    @Test(groups = {"smoke", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify a logged-in user can open My Orders and see their orders")
    public void verifyLoginAndViewMyOrders() {
        homePage.open(furlencoUrl);
        login();

        FurlencoMyOrdersPage myOrders = new FurlencoMyOrdersPage(page).open(furlencoUrl);

        assertThat(myOrders.isLoaded()).as("My Orders should load for a logged-in user").isTrue();
        assertThat(myOrders.getOrderCount()).as("Test account should have at least one order").isGreaterThan(0);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify opening an order from the list lands on that same order's detail page")
    public void verifyOrderDetailsConsistency() {
        homePage.open(furlencoUrl);
        login();

        FurlencoMyOrdersPage myOrders = new FurlencoMyOrdersPage(page).open(furlencoUrl);
        String expectedHref = myOrders.getFirstOrderHref();

        myOrders.openFirstOrder();

        assertThat(myOrders.isOrderDetailLoaded()).as("Order detail page should load").isTrue();
        assertThat(myOrders.currentUrl())
                .as("Detail page should be for the order that was clicked")
                .contains(expectedHref);
    }
}
