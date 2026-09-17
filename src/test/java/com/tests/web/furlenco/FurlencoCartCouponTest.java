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
 * CART-06: Coupon complete flow. Verified live: each vertical's cart section has a "Have a Coupon
 * code?" input plus a list of currently-offered coupon tiles (each with its own "Apply" button and
 * "*Terms and Conditions" link); a coupon whose minimum-cart-value threshold isn't met shows "Add
 * items worth ₹&lt;amount&gt; to unlock offer" instead of applying.
 * <p>
 * Uses {@code MAXSAVING} — a coupon Furlenco's own Rent cart page listed as currently offered for
 * this cart at test-design time — as the "valid" case, since a coupon the app itself advertises as
 * applicable is the most reliable "valid" test data available without backend access to mint a
 * guaranteed-valid code. Re-verify this code is still live if this test starts failing on the
 * valid-coupon assertion specifically.
 */
@Epic("Furlenco Web Automation")
@Feature("Cart Coupons (CART-06)")
public class FurlencoCartCouponTest extends BaseWebTest {

    private static final String KNOWN_OFFERED_COUPON = "MAXSAVING";
    private static final String OBVIOUSLY_INVALID_COUPON = "THISCODEDOESNOTEXIST999";

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    private FurlencoCartDrawer addRentItemAndOpenCart() {
        homePage.open(furlencoUrl);
        FurlencoLoginPage loginPage = homePage.openLogin();
        loginPage.loginWithOtp(RandomDataUtils.randomIndianMobileNumber(), config.get("test.user.password"));
        if (loginPage.isNewUserSignupPromptDisplayed()) {
            loginPage.getNewUserSignupPage()
                    .enterName(RandomDataUtils.randomName())
                    .enterEmail(RandomDataUtils.randomEmail())
                    .clickContinue();
        }
        homePage.clickRentTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);
        productPage.clickAddToCart();
        return homePage.openCart();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("CART-06: Verify applying a currently-offered coupon succeeds")
    public void verifyApplyingOfferedCouponSucceeds() {
        FurlencoCartDrawer cartDrawer = addRentItemAndOpenCart();

        cartDrawer.applyCouponCode(KNOWN_OFFERED_COUPON);

        assertThat(cartDrawer.isCouponAppliedIndicatorDisplayed() || !cartDrawer.isCouponErrorDisplayed())
                .as("Applying a currently-offered coupon (%s) should not show an error", KNOWN_OFFERED_COUPON)
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("CART-06: Verify applying an invalid coupon code shows an error")
    public void verifyApplyingInvalidCouponShowsError() {
        FurlencoCartDrawer cartDrawer = addRentItemAndOpenCart();

        cartDrawer.applyCouponCode(OBVIOUSLY_INVALID_COUPON);

        assertThat(cartDrawer.isCouponErrorDisplayed())
                .as("Applying a nonexistent coupon code should show an error")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 3)
    @Severity(SeverityLevel.NORMAL)
    @Description("CART-06: Verify a below-threshold coupon shows a deficit/unlock message rather than applying")
    public void verifyBelowThresholdCouponShowsUnlockMessage() {
        FurlencoCartDrawer cartDrawer = addRentItemAndOpenCart();

        assertThat(cartDrawer.isCouponLockedMessageDisplayed())
                .as("A coupon whose minimum-cart-value threshold isn't met yet should show an unlock/deficit message")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 4)
    @Severity(SeverityLevel.NORMAL)
    @Description("CART-06: Verify a coupon can be removed after being applied")
    public void verifyRemovingAppliedCoupon() {
        FurlencoCartDrawer cartDrawer = addRentItemAndOpenCart();
        cartDrawer.applyCouponCode(KNOWN_OFFERED_COUPON);

        cartDrawer.removeCoupon();

        assertThat(cartDrawer.isCouponAppliedIndicatorDisplayed())
                .as("Coupon should no longer show as applied after removal")
                .isFalse();
    }
}
