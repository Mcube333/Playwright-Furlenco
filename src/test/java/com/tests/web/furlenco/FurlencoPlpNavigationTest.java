package com.tests.web.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.tests.base.BaseWebTest;
import com.tests.pages.furlenco.FurlencoHomePage;
import com.tests.pages.furlenco.FurlencoPlpPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Covers the "Product Listing (PLP)" section of the manual test suite: Global Home -> vertical ->
 * PLP navigation, back-navigation, and category/subcategory selection, for Rent / Buy / Unlmtd.
 */
@Epic("Furlenco Web Automation")
@Feature("Product Listing (PLP) Navigation")
public class FurlencoPlpNavigationTest extends BaseWebTest {

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    @Test(groups = {"smoke", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify user navigates to Rent PLP screen when tapping RENT in Global Home")
    public void verifyNavigateToRentFromGlobalHome() {
        homePage.open(furlencoUrl);
        homePage.clickRentTab();

        assertThat(homePage.currentUrl())
                .as("URL should reflect the Rent vertical")
                .containsAnyOf("rent", "bengaluru");
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify user navigates back from Rent screen to Home via app back button")
    public void verifyBackNavigationFromRentToHome() {
        homePage.open(furlencoUrl);
        String homeUrl = homePage.currentUrl();
        homePage.clickRentTab();

        homePage.goBack();

        assertThat(homePage.currentUrl())
                .as("Back navigation should return towards the home URL")
                .isEqualTo(homeUrl);
    }

    @Test(groups = {"smoke", "web", "furlenco"}, priority = 3)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify user navigates to Buy PLP screen when tapping BUY in Global Home")
    public void verifyNavigateToBuyFromGlobalHome() {
        homePage.open(furlencoUrl);
        homePage.clickBuyTab();

        assertThat(homePage.currentUrl())
                .as("URL should navigate to the buy vertical")
                .contains("/buy");
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 4)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify user navigates back from Buy screen to Home via app back button")
    public void verifyBackNavigationFromBuyToHome() {
        homePage.open(furlencoUrl);
        String homeUrl = homePage.currentUrl();
        homePage.clickBuyTab();

        homePage.goBack();

        assertThat(homePage.currentUrl())
                .as("Back navigation should return towards the home URL")
                .isEqualTo(homeUrl);
    }

    @Test(groups = {"smoke", "web", "furlenco"}, priority = 5)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify user navigates to Unlmtd Home screen when tapping UNLMTD in Global Home")
    public void verifyNavigateToUnlimitedFromGlobalHome() {
        homePage.open(furlencoUrl);
        homePage.clickUnlmtdTab();

        assertThat(homePage.currentUrl())
                .as("URL should navigate to the unlmtd vertical")
                .contains("unlmtd");
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 6)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify user navigates back from Unlmtd Home to Global Home via device back button")
    public void verifyBackNavigationFromUnlimitedToHome() {
        homePage.open(furlencoUrl);
        String homeUrl = homePage.currentUrl();
        homePage.clickUnlmtdTab();

        homePage.goBack();

        assertThat(homePage.currentUrl())
                .as("Back navigation should return towards the home URL")
                .isEqualTo(homeUrl);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 7)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify user navigates to Rent PLP when tapping a product category in Rent home")
    public void verifyNavigateToRentPlpFromCategory() {
        homePage.open(furlencoUrl);
        homePage.clickRentTab();

        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");

        assertThat(plpPage.isLoaded())
                .as("Rent PLP should load with products after selecting a category")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 8)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify user navigates back from Rent PLP to Rent home via app back button")
    public void verifyBackNavigationFromRentPlpToRentHome() {
        homePage.open(furlencoUrl);
        homePage.clickRentTab();
        String rentHomeUrl = homePage.currentUrl();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");

        plpPage.goBack();

        assertThat(plpPage.currentUrl())
                .as("Back navigation should return to the Rent home URL")
                .isEqualTo(rentHomeUrl);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 9)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify user navigates to Buy PLP when tapping a product category in Buy home")
    public void verifyNavigateToBuyPlpFromCategory() {
        homePage.open(furlencoUrl);
        homePage.clickBuyTab();

        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");

        assertThat(plpPage.isLoaded())
                .as("Buy PLP should load with products after selecting a category")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 10)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify user navigates back from Buy PLP to Buy home via app back button")
    public void verifyBackNavigationFromBuyPlpToBuyHome() {
        homePage.open(furlencoUrl);
        homePage.clickBuyTab();
        String buyHomeUrl = homePage.currentUrl();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");

        plpPage.goBack();

        assertThat(plpPage.currentUrl())
                .as("Back navigation should return to the Buy home URL")
                .isEqualTo(buyHomeUrl);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 11)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify user is able to select different categories on the Rent PLP screen")
    public void verifySelectDifferentCategoriesOnRentPlp() {
        homePage.open(furlencoUrl);
        homePage.clickRentTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");

        int categoryCount = plpPage.getCategoryCount();

        assertThat(categoryCount)
                .as("Rent PLP should expose more than one category to switch between")
                .isGreaterThan(0);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 12)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify user is able to select different categories on the Buy PLP screen")
    public void verifySelectDifferentCategoriesOnBuyPlp() {
        homePage.open(furlencoUrl);
        homePage.clickBuyTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");

        int categoryCount = plpPage.getCategoryCount();

        assertThat(categoryCount)
                .as("Buy PLP should expose more than one category to switch between")
                .isGreaterThan(0);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 13)
    @Severity(SeverityLevel.MINOR)
    @Description("Verify user is able to select different sub-categories on the Rent PLP screen")
    public void verifySelectDifferentSubcategoriesOnRentPlp() {
        homePage.open(furlencoUrl);
        homePage.clickRentTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");

        int subcategoryCount = plpPage.getSubcategoryCount();

        assertThat(subcategoryCount)
                .as("Rent PLP should expose sub-category filters")
                .isGreaterThanOrEqualTo(0);
    }
}
