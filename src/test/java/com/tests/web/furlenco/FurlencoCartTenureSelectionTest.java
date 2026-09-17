package com.tests.web.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.utils.RandomDataUtils;
import com.tests.base.BaseWebTest;
import com.tests.pages.furlenco.FurlencoCartDrawer;
import com.tests.pages.furlenco.FurlencoHomePage;
import com.tests.pages.furlenco.FurlencoLoginPage;
import com.tests.pages.furlenco.FurlencoProductPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * CART-03: Rental tenure selection. Verified live: <b>not every rental product has a tenure/plan
 * choice</b> — standard furniture (chairs, tables, beds) is plain month-to-month with no plan
 * selector at all. Only "Upfront" plan-eligible products (appliances like ACs, in this
 * exploration) expose a "Choose a payment plan" switcher on the Cart page, offering "Upfront 6/9/12
 * Months" tiles in a "Cart Payment Plan Selector" overlay — see {@link FurlencoCartDrawer} javadoc
 * for the full verified structure.
 * <p>
 * Uses a specific known Upfront-plan-eligible product's direct URL rather than searching a PLP
 * category for one, since not every product in a category has this feature and searching
 * blindly would be unreliable. If this product becomes unavailable, find a replacement by
 * checking a candidate product's Cart page for a "Choose a payment plan" section.
 */
@Epic("Furlenco Web Automation")
@Feature("Rental Tenure Selection (CART-03)")
public class FurlencoCartTenureSelectionTest extends BaseWebTest {

    private static final String UPFRONT_PLAN_PRODUCT_URL_PATH =
            "/rent/products/1-5-ton-3-star-convertible-inverter-split-ac-4373-rent";

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    private FurlencoCartDrawer addUpfrontPlanItemAndOpenCart() {
        homePage.open(furlencoUrl);
        FurlencoLoginPage loginPage = homePage.openLogin();
        loginPage.loginWithOtp(RandomDataUtils.randomIndianMobileNumber(), config.get("test.user.password"));
        if (loginPage.isNewUserSignupPromptDisplayed()) {
            loginPage.getNewUserSignupPage()
                    .enterName(RandomDataUtils.randomName())
                    .enterEmail(RandomDataUtils.randomEmail())
                    .clickContinue();
        }
        page.navigate(furlencoUrl + UPFRONT_PLAN_PRODUCT_URL_PATH);
        page.waitForLoadState();
        FurlencoProductPage productPage = new FurlencoProductPage(page);
        assertThat(productPage.isAddToCartButtonEnabled())
                .as("Upfront-plan test product should be purchasable — replace UPFRONT_PLAN_PRODUCT_URL_PATH if not")
                .isTrue();
        productPage.clickAddToCart();
        return homePage.openCart();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("CART-03: Verify the payment plan selector is available for an Upfront-plan-eligible item")
    public void verifyPaymentPlanSelectorAvailable() {
        FurlencoCartDrawer cartDrawer = addUpfrontPlanItemAndOpenCart();

        assertThat(cartDrawer.isPaymentPlanSelectorAvailable())
                .as("Choose a payment plan switcher should be shown for an Upfront-plan-eligible cart item")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.CRITICAL)
    @Description("CART-03: Verify switching the payment plan updates the item's applied tenure and price")
    public void verifySwitchPaymentPlanAppliesToItem() {
        FurlencoCartDrawer cartDrawer = addUpfrontPlanItemAndOpenCart();
        String planBefore = cartDrawer.getItemPlanLabel(0);

        cartDrawer.openPaymentPlanSelector();
        assertThat(cartDrawer.isPaymentPlanSelectorOpen()).as("Payment plan selector overlay should open").isTrue();
        cartDrawer.selectPaymentPlan("Upfront 12 Months");

        assertThat(cartDrawer.getItemPlanLabel(0))
                .as("Item's applied plan should change after selecting a different one")
                .isNotEqualTo(planBefore);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 3)
    @Severity(SeverityLevel.NORMAL)
    @Description("CART-03: Verify closing the payment plan selector without selecting preserves the prior plan")
    public void verifyClosingSelectorWithoutSelectingPreservesPlan() {
        FurlencoCartDrawer cartDrawer = addUpfrontPlanItemAndOpenCart();
        String planBefore = cartDrawer.getItemPlanLabel(0);

        cartDrawer.openPaymentPlanSelector();
        assertThat(cartDrawer.isPaymentPlanSelectorOpen()).as("Payment plan selector overlay should open").isTrue();
        cartDrawer.closePaymentPlanSelectorWithoutSelecting();

        assertThat(cartDrawer.isPaymentPlanSelectorOpen()).as("Overlay should be closed").isFalse();
        assertThat(cartDrawer.getItemPlanLabel(0))
                .as("Closing without selecting should preserve the previously-applied plan")
                .isEqualTo(planBefore);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 4)
    @Severity(SeverityLevel.NORMAL)
    @Description("CART-03: Verify the selected payment plan persists across a page refresh")
    public void verifySelectedPlanPersistsAfterRefresh() {
        FurlencoCartDrawer cartDrawer = addUpfrontPlanItemAndOpenCart();
        cartDrawer.openPaymentPlanSelector();
        cartDrawer.selectPaymentPlan("Upfront 9 Months");
        String planAfterSelect = cartDrawer.getItemPlanLabel(0);

        page.reload();
        page.waitForLoadState();

        assertThat(cartDrawer.getItemPlanLabel(0))
                .as("Selected plan should persist across a refresh")
                .isEqualTo(planAfterSelect);
    }
}
