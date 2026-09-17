package com.tests.web.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.utils.RandomDataUtils;
import com.tests.base.BaseWebTest;
import com.tests.pages.furlenco.FurlencoCartDrawer;
import com.tests.pages.furlenco.FurlencoHomePage;
import com.tests.pages.furlenco.FurlencoLoginPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * CART-08 (empty-state half only): a brand-new account's Cart correctly shows an empty state.
 * <p>
 * The other half of CART-08 — forcing a Cart load *failure* and verifying Retry — needs a way to
 * simulate a network/API failure (e.g. blocking the cart API request) that this framework doesn't
 * currently have a helper for; not attempted here.
 */
@Epic("Furlenco Web Automation")
@Feature("Cart Empty State (CART-08)")
public class FurlencoCartEmptyStateTest extends BaseWebTest {

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.NORMAL)
    @Description("CART-08: Verify a brand-new account with no items shows the empty-cart state")
    public void verifyEmptyCartStateForNewAccount() {
        homePage.open(furlencoUrl);
        FurlencoLoginPage loginPage = homePage.openLogin();
        loginPage.loginWithOtp(RandomDataUtils.randomIndianMobileNumber(), config.get("test.user.password"));
        if (loginPage.isNewUserSignupPromptDisplayed()) {
            loginPage.getNewUserSignupPage()
                    .enterName(RandomDataUtils.randomName())
                    .enterEmail(RandomDataUtils.randomEmail())
                    .clickContinue();
        }

        FurlencoCartDrawer cartDrawer = homePage.openCart();

        assertThat(cartDrawer.isEmptyStateDisplayed())
                .as("A brand-new account with nothing added should see the empty-cart state")
                .isTrue();
    }
}
