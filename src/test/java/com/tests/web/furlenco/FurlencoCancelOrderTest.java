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
 * Order cancellation for Buy and Rent, from an existing active order in "My Orders". Relies on
 * the test account already having at least one cancellable Buy order and one Rent order (this
 * account had several pre-seeded on preprod) — it does not place a fresh order first, both to
 * avoid coupling to {@link FurlencoSuccessfulOrderTest} and because cancelling something you just
 * created isn't the same test as cancelling an existing order a customer placed earlier.
 * <p>
 * <b>Unverified:</b> {@link FurlencoMyOrdersPage#confirmCancellation()} and
 * {@link FurlencoMyOrdersPage#isCancelled()} are best-effort guesses at the confirmation screen's
 * copy — the actual cancel-confirmation UI wasn't inspected this session (navigating directly to
 * a {@code /cancel/<id>} URL was correctly treated as potentially destructive and blocked). The
 * first real run of this test is also this flow's live verification pass.
 * <p>
 * <b>Irreversibly cancels a real order every run</b> — make sure the test account has enough
 * disposable seeded orders before running this repeatedly, or point it at dedicated
 * cancel-fixture orders rather than real customer-shaped data.
 */
@Epic("Furlenco Web Automation")
@Feature("Cancel Order (Buy/Rent)")
public class FurlencoCancelOrderTest extends BaseWebTest {

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

    @Test(groups = {"regression", "web", "furlenco", "payment-critical"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify an existing Buy order can be cancelled from My Orders")
    public void verifyCancelBuyOrder() {
        homePage.open(furlencoUrl);
        login();

        FurlencoMyOrdersPage myOrders = new FurlencoMyOrdersPage(page).open(furlencoUrl);
        assertThat(myOrders.isLoaded()).as("My Orders should load").isTrue();
        assertThat(myOrders.getOrderCount()).as("Test account should have at least one order seeded").isGreaterThan(0);

        myOrders.openFirstBuyOrder();
        assertThat(myOrders.isOrderDetailLoaded()).as("Buy order detail page should load").isTrue();
        assertThat(myOrders.isCancelAvailable()).as("Cancel action should be available on this Buy order").isTrue();

        myOrders.clickCancel().confirmCancellation();

        assertThat(myOrders.isCancelled()).as("Buy order should show as cancelled").isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco", "payment-critical"}, priority = 2)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify an existing Rent order can be cancelled from My Orders")
    public void verifyCancelRentOrder() {
        homePage.open(furlencoUrl);
        login();

        FurlencoMyOrdersPage myOrders = new FurlencoMyOrdersPage(page).open(furlencoUrl);
        assertThat(myOrders.isLoaded()).as("My Orders should load").isTrue();

        myOrders.openFirstRentOrder();
        assertThat(myOrders.isOrderDetailLoaded()).as("Rent order detail page should load").isTrue();
        assertThat(myOrders.isCancelAvailable()).as("Cancel action should be available on this Rent order").isTrue();

        myOrders.clickCancel().confirmCancellation();

        assertThat(myOrders.isCancelled()).as("Rent order should show as cancelled").isTrue();
    }
}
