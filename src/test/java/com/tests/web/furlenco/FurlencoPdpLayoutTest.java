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

/** Web equivalent of the app smoke scenario "PDP layout (PDP)": core PDP elements render. */
@Epic("Furlenco Web Automation")
@Feature("Product Details Page Layout")
public class FurlencoPdpLayoutTest extends BaseWebTest {

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify a Rent PDP shows its title, pricing and an Add to Cart CTA")
    public void verifyRentPdpLayout() {
        homePage.open(furlencoUrl);
        homePage.clickRentTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);

        assertThat(productPage.isLoaded()).as("PDP should load").isTrue();
        assertThat(productPage.getProductTitle()).as("Product title").isNotBlank();
        assertThat(productPage.isPriceDisplayed()).as("Pricing should be displayed").isTrue();
        assertThat(productPage.isAddToCartButtonVisible()).as("Add to Cart CTA").isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify a Buy PDP shows its title, pricing and an Add to Cart CTA")
    public void verifyBuyPdpLayout() {
        homePage.open(furlencoUrl);
        homePage.clickBuyTab();
        FurlencoPlpPage plpPage = homePage.clickCategory("Bedroom");
        FurlencoProductPage productPage = plpPage.clickFirstAvailableProduct(10);

        assertThat(productPage.isLoaded()).as("PDP should load").isTrue();
        assertThat(productPage.getProductTitle()).as("Product title").isNotBlank();
        assertThat(productPage.isPriceDisplayed()).as("Pricing should be displayed").isTrue();
        assertThat(productPage.isAddToCartButtonVisible()).as("Add to Cart CTA").isTrue();
    }
}
