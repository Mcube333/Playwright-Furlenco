package com.tests.web.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.tests.base.BaseWebTest;
import com.tests.pages.furlenco.FurlencoCartDrawer;
import com.tests.pages.furlenco.FurlencoCheckoutAddressPage;
import com.tests.pages.furlenco.FurlencoHomePage;
import com.tests.pages.furlenco.FurlencoLoginPage;
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
 * Covers "Cart", "Order Summary", "Checkout" and the observation-only part of "Payment" from the
 * manual test suite. Verified live checkout flow: Cart -&gt; click {@code CHECKOUT} -&gt; (Rent
 * only) a "Rental Terms Reminder" popup -&gt; Delivery Address ({@link FurlencoCheckoutAddressPage})
 * -&gt; Order Summary -&gt; Payment. Deliberately stops at verifying the Payment screen is reached
 * and offers Card/UPI — it never submits an actual payment (see
 * {@link FurlencoSuccessfulOrderTest} for the flow that does, via Razorpay test-mode).
 */
@Epic("Furlenco Web Automation")
@Feature("Cart, Order Summary and Checkout")
public class FurlencoCheckoutFlowTest extends BaseWebTest {

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
    @Description("Verify user is able to add a product to cart via Add to Cart on the Rent PDP screen")
    public void verifyAddToCartFromRentPdp() {
        homePage.open(furlencoUrl);
        homePage.clickRentTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);

        assertThat(productPage.isAddToCartButtonVisible())
                .as("Add to Cart CTA should be visible on Rent PDP")
                .isTrue();
        productPage.clickAddToCart();
    }

    @Test(groups = {"smoke", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify user is able to add a product to cart via Add to Cart on the Buy PDP screen")
    public void verifyAddToCartFromBuyPdp() {
        homePage.open(furlencoUrl);
        homePage.clickBuyTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);

        assertThat(productPage.isAddToCartButtonVisible())
                .as("Add to Cart CTA should be visible on Buy PDP")
                .isTrue();
        productPage.clickAddToCart();
    }

    @Test(groups = {"smoke", "web", "furlenco"}, priority = 3)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify user is able to add a product to cart via Add to Cart on the Unlmtd PDP screen")
    public void verifyAddToCartFromUnlimitedPdp() {
        homePage.open(furlencoUrl);
        homePage.clickUnlmtdTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);

        assertThat(productPage.isAddToCartButtonVisible())
                .as("Add to Cart CTA should be visible on Unlmtd PDP")
                .isTrue();
        productPage.clickAddToCart();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 4)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify clicking the cart icon from Rent Home/PLP/PDP navigates to the Cart screen")
    public void verifyCartIconNavigationRent() {
        homePage.open(furlencoUrl);
        homePage.clickRentTab();

        FurlencoCartDrawer cartDrawer = homePage.openCart();

        assertThat(cartDrawer.isCartOpen())
                .as("Cart screen/drawer should open from the Rent vertical")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 5)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify clicking the cart icon from Buy Home/PLP/PDP navigates to the Cart screen")
    public void verifyCartIconNavigationBuy() {
        homePage.open(furlencoUrl);
        homePage.clickBuyTab();

        FurlencoCartDrawer cartDrawer = homePage.openCart();

        assertThat(cartDrawer.isCartOpen())
                .as("Cart screen/drawer should open from the Buy vertical")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 6)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify clicking the cart icon from Unlmtd Home/PLP/PDP navigates to the Cart screen")
    public void verifyCartIconNavigationUnlimited() {
        homePage.open(furlencoUrl);
        homePage.clickUnlmtdTab();

        FurlencoCartDrawer cartDrawer = homePage.openCart();

        assertThat(cartDrawer.isCartOpen())
                .as("Cart screen/drawer should open from the Unlmtd vertical")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 7)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify clicking back on the Cart screen returns to the screen the user came from")
    public void verifyBackNavigationFromCart() {
        homePage.open(furlencoUrl);
        String homeUrl = homePage.currentUrl();
        FurlencoCartDrawer cartDrawer = homePage.openCart();
        assertThat(cartDrawer.isCartOpen()).as("Cart should be open before navigating back").isTrue();

        cartDrawer.goBack();

        assertThat(cartDrawer.currentUrl())
                .as("Back navigation from Cart should return to the originating screen")
                .isEqualTo(homeUrl);
    }

    @Test(groups = {"regression", "web", "furlenco", "payment-critical"}, priority = 8)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify checkout on Cart shows the Rental Terms popup and navigates to Delivery Address for Rent")
    public void verifyCheckoutToAddressRent() {
        homePage.open(furlencoUrl);
        homePage.enterPincode(config.get("test.delivery.pincode", "110001"));
        login();
        homePage.clickRentTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);
        productPage.clickAddToCart();

        FurlencoCartDrawer cartDrawer = homePage.openCart();
        FurlencoCheckoutAddressPage addressPage = cartDrawer.clickCheckout();

        assertThat(addressPage.isLoaded())
                .as("Delivery Address step should load after checkout (with Rental Terms popup handled) for Rent")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco", "payment-critical"}, priority = 9)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify checkout on Cart navigates to Delivery Address for Buy")
    public void verifyCheckoutToAddressBuy() {
        homePage.open(furlencoUrl);
        homePage.enterPincode(config.get("test.delivery.pincode", "110001"));
        login();
        homePage.clickBuyTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);
        productPage.clickAddToCart();

        FurlencoCartDrawer cartDrawer = homePage.openCart();
        FurlencoCheckoutAddressPage addressPage = cartDrawer.clickCheckout();

        assertThat(addressPage.isLoaded())
                .as("Delivery Address step should load after checkout for Buy")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 10)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify Order Summary loads after confirming the delivery address")
    public void verifyAddressToOrderSummary() {
        homePage.open(furlencoUrl);
        homePage.enterPincode(config.get("test.delivery.pincode", "110001"));
        login();
        homePage.clickRentTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);
        productPage.clickAddToCart();

        FurlencoCartDrawer cartDrawer = homePage.openCart();
        FurlencoCheckoutAddressPage addressPage = cartDrawer.clickCheckout();
        assertThat(addressPage.hasSavedAddress())
                .as("Test account should have at least one saved address")
                .isTrue();

        FurlencoOrderSummaryPage orderSummary = addressPage.confirmSelectedAddress();

        assertThat(orderSummary.isLoaded()).as("Order Summary should load after confirming address").isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco", "payment-critical"}, priority = 11)
    @Severity(SeverityLevel.BLOCKER)
    @Description("Verify clicking Proceed on Order Summary navigates to the Payment screen")
    public void verifyProceedToPaymentScreen() {
        homePage.open(furlencoUrl);
        homePage.enterPincode(config.get("test.delivery.pincode", "110001"));
        login();
        homePage.clickRentTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);
        productPage.clickAddToCart();

        FurlencoCartDrawer cartDrawer = homePage.openCart();
        FurlencoCheckoutAddressPage addressPage = cartDrawer.clickCheckout();
        FurlencoOrderSummaryPage orderSummary = addressPage.confirmSelectedAddress();
        FurlencoPaymentPage paymentPage = orderSummary.clickProceed();

        assertThat(paymentPage.isLoaded()).as("Payment screen should load after Proceed").isTrue();
        assertThat(paymentPage.isCardOptionDisplayed() || paymentPage.isUpiOptionDisplayed())
                .as("At least one payment method (Card/UPI) should be offered")
                .isTrue();
        // Deliberately stops here: no card/UPI details are entered and no payment is submitted.
        // See FurlencoSuccessfulOrderTest for the flow that completes payment via Razorpay test mode.
    }
}
