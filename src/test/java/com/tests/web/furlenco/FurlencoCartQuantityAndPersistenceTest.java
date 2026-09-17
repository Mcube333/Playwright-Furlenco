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
 * CART-02: Cart quantity, remove & persistence. Verified live: quantity controls are two
 * unlabeled icon buttons (decrease, then increase) either side of a plain-text quantity, and
 * removal is a distinctly-classed delete button — see {@link FurlencoCartDrawer} javadoc.
 */
@Epic("Furlenco Web Automation")
@Feature("Cart Quantity, Removal and Persistence (CART-02)")
public class FurlencoCartQuantityAndPersistenceTest extends BaseWebTest {

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    /**
     * Logs in with a freshly-generated random phone number — see
     * {@code FurlencoCartRenderingTest.loginAndSetInventoryPincode()} javadoc for why (avoids
     * rate-limiting the two fixed test accounts, and deliberately doesn't switch delivery
     * pincode).
     */
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
    @Description("CART-02: Verify increasing an item's quantity updates the displayed quantity")
    public void verifyIncreaseQuantity() {
        FurlencoCartDrawer cartDrawer = addRentItemAndOpenCart();
        int before = cartDrawer.getItemQuantity(0);

        cartDrawer.increaseQuantity(0);

        assertThat(cartDrawer.getItemQuantity(0))
                .as("Quantity should increase by 1 after clicking the increase control")
                .isEqualTo(before + 1);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.CRITICAL)
    @Description("CART-02: Verify decreasing an item's quantity updates the displayed quantity")
    public void verifyDecreaseQuantity() {
        FurlencoCartDrawer cartDrawer = addRentItemAndOpenCart();
        // Increase first so the decrease has room to act without hitting the qty=1 floor/removal edge.
        cartDrawer.increaseQuantity(0);
        int before = cartDrawer.getItemQuantity(0);

        cartDrawer.decreaseQuantity(0);

        assertThat(cartDrawer.getItemQuantity(0))
                .as("Quantity should decrease by 1 after clicking the decrease control")
                .isEqualTo(before - 1);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 3)
    @Severity(SeverityLevel.CRITICAL)
    @Description("CART-02: Verify removing an item takes it out of the cart and recalculates the item count")
    public void verifyRemoveItem() {
        FurlencoCartDrawer cartDrawer = addRentItemAndOpenCart();
        int before = cartDrawer.getItemCount();

        cartDrawer.removeItem(0);

        assertThat(cartDrawer.getItemCount())
                .as("Item count should decrease by 1 after removing an item")
                .isEqualTo(before - 1);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 4)
    @Severity(SeverityLevel.NORMAL)
    @Description("CART-02: Verify cart contents persist across a page refresh")
    public void verifyCartPersistsAfterRefresh() {
        FurlencoCartDrawer cartDrawer = addRentItemAndOpenCart();
        String itemNameBefore = cartDrawer.getItemName(0);
        int quantityBefore = cartDrawer.getItemQuantity(0);

        page.reload();
        page.waitForLoadState();

        assertThat(cartDrawer.getItemName(0))
                .as("Item name should be unchanged after a refresh")
                .isEqualTo(itemNameBefore);
        assertThat(cartDrawer.getItemQuantity(0))
                .as("Item quantity should be unchanged after a refresh")
                .isEqualTo(quantityBefore);
    }
}
