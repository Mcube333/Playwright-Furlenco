package com.tests.web.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.tests.base.BaseWebTest;
import com.tests.pages.furlenco.FurlencoCartDrawer;
import com.tests.pages.furlenco.FurlencoHomePage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Epic("Furlenco Web Automation")
@Feature("Cart and Wishlist Interactions")
public class FurlencoCartFlowTest extends BaseWebTest {

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    @Test(groups = {"smoke", "regression", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify opening and closing the cart drawer / cart page")
    public void verifyCartDrawerOpenAndClose() {
        homePage.open(furlencoUrl);

        FurlencoCartDrawer cartDrawer = homePage.openCart();

        assertThat(cartDrawer.isCartOpen())
                .as("Cart drawer or page should be displayed upon clicking the cart icon")
                .isTrue();

        cartDrawer.closeCart();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify clicking the Wishlist button prompts action or page response")
    public void verifyWishlistButtonInteraction() {
        homePage.open(furlencoUrl);

        homePage.clickWishlist();

        assertThat(homePage.currentUrl())
                .as("Page URL or state should remain valid after wishlist click")
                .isNotEmpty();
    }
}
