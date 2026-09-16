package com.tests.web.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.tests.base.BaseWebTest;
import com.tests.pages.furlenco.FurlencoHomePage;
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
 * Covers the "Product Description (PDP)" section of the manual test suite: PLP -> PDP navigation
 * and back-navigation, for Rent / Buy / Unlmtd.
 */
@Epic("Furlenco Web Automation")
@Feature("Product Description (PDP) Navigation")
public class FurlencoPdpNavigationTest extends BaseWebTest {

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    @Test(groups = {"smoke", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify user navigates to Rent PDP when tapping a product in the Rent PLP screen")
    public void verifyNavigateToRentPdp() {
        homePage.open(furlencoUrl);
        homePage.clickRentTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");

        FurlencoProductPage productPage = plpPage.clickFirstProduct();

        assertThat(productPage.isLoaded()).as("Rent PDP should load after clicking a product card").isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify user navigates back from Rent PDP to Rent PLP via app back button")
    public void verifyBackNavigationFromRentPdpToPlp() {
        homePage.open(furlencoUrl);
        homePage.clickRentTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        String plpUrl = plpPage.currentUrl();

        FurlencoProductPage productPage = plpPage.clickFirstProduct();
        productPage.goBack();

        assertThat(productPage.currentUrl())
                .as("Back navigation should return to the Rent PLP URL")
                .isEqualTo(plpUrl);
    }

    @Test(groups = {"smoke", "web", "furlenco"}, priority = 3)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify user navigates to Buy PDP when tapping a product in the Buy PLP screen")
    public void verifyNavigateToBuyPdp() {
        homePage.open(furlencoUrl);
        homePage.clickBuyTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");

        FurlencoProductPage productPage = plpPage.clickFirstProduct();

        assertThat(productPage.isLoaded()).as("Buy PDP should load after clicking a product card").isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 4)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify user navigates back from Buy PDP to Buy PLP via app back button")
    public void verifyBackNavigationFromBuyPdpToPlp() {
        homePage.open(furlencoUrl);
        homePage.clickBuyTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        String plpUrl = plpPage.currentUrl();

        FurlencoProductPage productPage = plpPage.clickFirstProduct();
        productPage.goBack();

        assertThat(productPage.currentUrl())
                .as("Back navigation should return to the Buy PLP URL")
                .isEqualTo(plpUrl);
    }

    @Test(groups = {"smoke", "web", "furlenco"}, priority = 5)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify user navigates to Unlmtd PDP when tapping a product in the Unlmtd PLP screen")
    public void verifyNavigateToUnlimitedPdp() {
        homePage.open(furlencoUrl);
        homePage.clickUnlmtdTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");

        FurlencoProductPage productPage = plpPage.clickFirstProduct();

        assertThat(productPage.isLoaded()).as("Unlmtd PDP should load after clicking a product card").isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 6)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify user navigates back from Unlmtd PDP to Unlmtd PLP via app back button")
    public void verifyBackNavigationFromUnlimitedPdpToPlp() {
        homePage.open(furlencoUrl);
        homePage.clickUnlmtdTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        String plpUrl = plpPage.currentUrl();

        FurlencoProductPage productPage = plpPage.clickFirstProduct();
        productPage.goBack();

        assertThat(productPage.currentUrl())
                .as("Back navigation should return to the Unlmtd PLP URL")
                .isEqualTo(plpUrl);
    }
}
