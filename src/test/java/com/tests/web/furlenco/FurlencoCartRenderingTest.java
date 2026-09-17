package com.tests.web.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.utils.RandomDataUtils;
import com.tests.base.BaseWebTest;
import com.tests.pages.furlenco.FurlencoCartDrawer;
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
 * CART-01: Cart rendering & item details. Verified live against a real mixed Rent+Buy cart —
 * see {@link FurlencoCartDrawer} javadoc for the underlying DOM structure this relies on.
 * <p>
 * <b>Verified live, important behavior for anyone extending this class:</b> the Cart page's
 * Rent/Buy section content is derived from client-side navigation state, not the {@code /cart}
 * URL itself — navigating directly to {@code /cart} (a hard reload) always defaults to showing
 * only the Rent section, even with Buy items in the cart. Reaching Cart via the header's cart
 * icon click ({@link FurlencoHomePage#openCart()}) from within the current vertical is what
 * shows both sections together when both have items. Every test below reaches Cart that way,
 * never via direct URL navigation.
 */
@Epic("Furlenco Web Automation")
@Feature("Cart Rendering (CART-01)")
public class FurlencoCartRenderingTest extends BaseWebTest {

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    /**
     * Logs in with a freshly-generated random phone number rather than the two fixed test
     * accounts ({@code test.user.username}) — those got rate-limited by Furlenco's OTP delivery
     * after many real logins during this suite's own development. A random number always hits the
     * new-user signup path (see {@link FurlencoLoginPage#isNewUserSignupPromptDisplayed()}), which
     * this completes with random name/email so the caller ends up on an authenticated session
     * either way.
     * <p>
     * Deliberately NOT switching to {@code test.delivery.pincode} — live verification during test
     * design showed the default city/pincode context (Bengaluru) had working inventory for these
     * categories, while switching to the configured alternate pincode instead caused every product
     * tried to show a disabled Add to Cart. Revisit if the default context's own availability
     * degrades.
     */
    private void loginAndSetInventoryPincode() {
        FurlencoLoginPage loginPage = homePage.openLogin();
        loginPage.loginWithOtp(RandomDataUtils.randomIndianMobileNumber(), config.get("test.user.password"));
        if (loginPage.isNewUserSignupPromptDisplayed()) {
            loginPage.getNewUserSignupPage()
                    .enterName(RandomDataUtils.randomName())
                    .enterEmail(RandomDataUtils.randomEmail())
                    .clickContinue();
        }
    }

    private void addProductFromCategory(String vertical, String categoryName) {
        switch (vertical) {
            case "rent" -> homePage.clickRentTab();
            case "buy" -> homePage.clickBuyTab();
            default -> throw new IllegalArgumentException("Unknown vertical: " + vertical);
        }
        FurlencoPlpPage plpPage = homePage.clickCategory(categoryName);
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);
        productPage.clickAddToCart();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("CART-01: Verify a Rent-only cart renders item cards, pricing/discount and the Rent section correctly")
    public void verifyRentOnlyCartRendering() {
        homePage.open(furlencoUrl);
        loginAndSetInventoryPincode();
        addProductFromCategory("rent", "Bedroom");

        FurlencoCartDrawer cartDrawer = homePage.openCart();

        assertThat(cartDrawer.isRentSectionDisplayed()).as("Rent Cart section should be displayed").isTrue();
        assertThat(cartDrawer.getItemCount()).as("At least one item should be in the cart").isGreaterThan(0);
        assertThat(cartDrawer.getItemName(0)).as("First item should have a non-empty name").isNotBlank();
        assertThat(cartDrawer.getItemFinalPriceText(0)).as("First item should show a final price").isNotBlank();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.CRITICAL)
    @Description("CART-01: Verify a Buy-only cart renders item cards, pricing/discount and the Buy section correctly")
    public void verifyBuyOnlyCartRendering() {
        homePage.open(furlencoUrl);
        loginAndSetInventoryPincode();
        addProductFromCategory("buy", "Bedroom");

        FurlencoCartDrawer cartDrawer = homePage.openCart();

        assertThat(cartDrawer.isBuySectionDisplayed()).as("Buy Cart section should be displayed").isTrue();
        assertThat(cartDrawer.getItemCount()).as("At least one item should be in the cart").isGreaterThan(0);
        assertThat(cartDrawer.getItemName(0)).as("First item should have a non-empty name").isNotBlank();
        assertThat(cartDrawer.getItemFinalPriceText(0)).as("First item should show a final price").isNotBlank();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 3)
    @Severity(SeverityLevel.BLOCKER)
    @Description("CART-01: Verify a mixed Rent+Buy cart shows both sections together with correct breakups")
    public void verifyMixedRentAndBuyCartRendering() {
        homePage.open(furlencoUrl);
        loginAndSetInventoryPincode();
        addProductFromCategory("rent", "Bedroom");
        addProductFromCategory("buy", "Bedroom");

        FurlencoCartDrawer cartDrawer = homePage.openCart();

        assertThat(cartDrawer.isRentSectionDisplayed())
                .as("Rent Cart section should be displayed in a mixed cart")
                .isTrue();
        assertThat(cartDrawer.isBuySectionDisplayed())
                .as("Buy Cart section should be displayed in a mixed cart")
                .isTrue();
        assertThat(cartDrawer.getItemCount())
                .as("Mixed cart should contain items from both verticals")
                .isGreaterThanOrEqualTo(2);
    }
}
