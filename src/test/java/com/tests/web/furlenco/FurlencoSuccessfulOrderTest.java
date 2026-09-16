package com.tests.web.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.tests.base.BaseWebTest;
import com.tests.pages.furlenco.FurlencoCartDrawer;
import com.tests.pages.furlenco.FurlencoCheckoutAddressPage;
import com.tests.pages.furlenco.FurlencoHomePage;
import com.tests.pages.furlenco.FurlencoLoginPage;
import com.tests.pages.furlenco.FurlencoOrderResultPage;
import com.tests.pages.furlenco.FurlencoOrderSummaryPage;
import com.tests.pages.furlenco.FurlencoPaymentPage;
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
 * End-to-end "successful order" flow for Buy and Rent: Login -&gt; Add to Cart -&gt; Checkout -&gt;
 * Delivery Address -&gt; Order Summary -&gt; Payment (Razorpay test mode, card) -&gt; Order Success.
 * <p>
 * Uses Razorpay's own officially published Indian-payments test card (Visa Debit, {@code
 * test.payment.card.*} in the environment properties — other networks/card types from the same
 * published set are also available under {@code test.payment.card.<network>.*}) — never a real
 * card, and Razorpay test mode never moves real money.
 * <p>
 * <b>Creates a real order record in the target environment every run</b> — this is inherent to
 * testing order placement and is expected/acceptable on preprod, but be aware this isn't a
 * side-effect-free read-only test.
 * <p>
 * {@link FurlencoOrderResultPage} success-indicator selectors are unverified (see its javadoc) —
 * the first real run of this test is also this flow's live verification pass; tighten selectors
 * based on what it actually finds.
 */
@Epic("Furlenco Web Automation")
@Feature("Successful Order (Buy/Rent)")
public class FurlencoSuccessfulOrderTest extends BaseWebTest {

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
    @Severity(SeverityLevel.BLOCKER)
    @Description("Verify a Rent order can be placed successfully end-to-end via Razorpay test-mode card payment")
    public void verifySuccessfulRentOrder() {
        homePage.open(furlencoUrl);
        homePage.enterPincode(config.get("test.delivery.pincode", "110001"));
        login();

        homePage.clickRentTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);
        productPage.clickAddToCart();

        FurlencoCartDrawer cartDrawer = homePage.openCart();
        FurlencoCheckoutAddressPage addressPage = cartDrawer.clickCheckout();
        assertThat(addressPage.isLoaded()).as("Delivery Address step should load").isTrue();

        FurlencoOrderSummaryPage orderSummary = addressPage.confirmSelectedAddress();
        assertThat(orderSummary.isLoaded()).as("Order Summary should load for Rent").isTrue();

        FurlencoPaymentPage paymentPage = orderSummary.clickProceed();
        assertThat(paymentPage.isLoaded()).as("Payment screen should load").isTrue();

        FurlencoOrderResultPage result = paymentPage.selectCardPaymentMethod()
                .payWithCard(
                        config.get("test.payment.card.number"),
                        config.get("test.payment.card.name"),
                        config.get("test.payment.card.validity"),
                        config.get("test.payment.card.cvv"));

        assertThat(result.isSuccessDisplayed())
                .as("Order should be placed successfully for Rent")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco", "payment-critical"}, priority = 2)
    @Severity(SeverityLevel.BLOCKER)
    @Description("Verify a Buy order can be placed successfully end-to-end via Razorpay test-mode card payment")
    public void verifySuccessfulBuyOrder() {
        homePage.open(furlencoUrl);
        homePage.enterPincode(config.get("test.delivery.pincode", "110001"));
        login();

        homePage.clickBuyTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);
        productPage.clickAddToCart();

        FurlencoCartDrawer cartDrawer = homePage.openCart();
        FurlencoCheckoutAddressPage addressPage = cartDrawer.clickCheckout();
        assertThat(addressPage.isLoaded()).as("Delivery Address step should load").isTrue();

        FurlencoOrderSummaryPage orderSummary = addressPage.confirmSelectedAddress();
        assertThat(orderSummary.isLoaded()).as("Order Summary should load for Buy").isTrue();

        FurlencoPaymentPage paymentPage = orderSummary.clickProceed();
        assertThat(paymentPage.isLoaded()).as("Payment screen should load").isTrue();

        FurlencoOrderResultPage result = paymentPage.selectCardPaymentMethod()
                .payWithCard(
                        config.get("test.payment.card.number"),
                        config.get("test.payment.card.name"),
                        config.get("test.payment.card.validity"),
                        config.get("test.payment.card.cvv"));

        assertThat(result.isSuccessDisplayed())
                .as("Order should be placed successfully for Buy")
                .isTrue();
    }
}
