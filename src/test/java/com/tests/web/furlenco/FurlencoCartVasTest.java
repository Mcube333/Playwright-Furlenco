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
 * CART-05: VAS (Value Added Services) selection & information. Verified live: a rental item's
 * cart card shows a "Furlenco Care" VAS tile with its own price and an "Applied to N Items"
 * count, and an "EDIT" trigger that opens a "Value Added Services" panel ("Customize VAS"
 * heading).
 * <p>
 * <b>Unverified:</b> the panel's exact per-item checkbox mapping — see
 * {@link FurlencoCartDrawer} javadoc on {@code toggleFirstVasOption()}. The toggle test below
 * checks a functional side effect (the applied-count text changing) rather than asserting a
 * specific item was targeted, since that finer-grained mapping wasn't confirmed.
 */
@Epic("Furlenco Web Automation")
@Feature("Cart VAS Selection (CART-05)")
public class FurlencoCartVasTest extends BaseWebTest {

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
    @Description("CART-05: Verify the Value Added Services section is displayed for a rental cart")
    public void verifyVasSectionDisplayed() {
        FurlencoCartDrawer cartDrawer = addRentItemAndOpenCart();

        assertThat(cartDrawer.isVasSectionDisplayed())
                .as("Value added services section should be displayed for a rental item")
                .isTrue();
        assertThat(cartDrawer.getVasAppliedCountText())
                .as("VAS applied-count text should be shown")
                .isNotBlank();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("CART-05: Verify the VAS edit panel opens and closes correctly")
    public void verifyVasEditPanelOpensAndCloses() {
        FurlencoCartDrawer cartDrawer = addRentItemAndOpenCart();

        cartDrawer.openVasEditPanel();
        assertThat(cartDrawer.isVasEditPanelOpen()).as("VAS edit panel should open").isTrue();

        cartDrawer.closeVasEditPanel();
        assertThat(cartDrawer.isVasEditPanelOpen()).as("VAS edit panel should close").isFalse();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 3)
    @Severity(SeverityLevel.NORMAL)
    @Description("CART-05: Verify toggling a VAS option changes the applied-item count")
    public void verifyTogglingVasChangesAppliedCount() {
        FurlencoCartDrawer cartDrawer = addRentItemAndOpenCart();
        String countBefore = cartDrawer.getVasAppliedCountText();

        cartDrawer.openVasEditPanel();
        cartDrawer.toggleFirstVasOption();
        cartDrawer.closeVasEditPanel();

        assertThat(cartDrawer.getVasAppliedCountText())
                .as("Toggling a VAS option should change the applied-count text")
                .isNotEqualTo(countBefore);
    }
}
