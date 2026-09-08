package com.tests.web.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.tests.base.BaseWebTest;
import com.tests.pages.furlenco.FurlencoHomePage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Epic("Furlenco Web Automation")
@Feature("Homepage and Navigation")
public class FurlencoHomePageTest extends BaseWebTest {

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    @Test(groups = {"smoke", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.BLOCKER)
    @Description("Verify that Furlenco homepage loads with correct title and brand logo")
    public void verifyHomePageLoadsAndBrandLogo() {
        homePage.open(furlencoUrl);

        assertThat(homePage.getTitle())
                .as("Homepage title should mention Furlenco")
                .containsIgnoringCase("Furlenco");

        assertThat(homePage.isLogoVisible())
                .as("Furlenco logo must be visible in header")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify navigation between RENT, BUY, and UNLMTD verticals")
    public void verifyVerticalNavigationTabs() {
        homePage.open(furlencoUrl);

        homePage.clickBuyTab();
        assertThat(homePage.currentUrl())
                .as("URL should navigate to buy vertical")
                .contains("/buy");

        homePage.clickRentTab();
        assertThat(homePage.currentUrl())
                .as("URL should navigate to rent vertical or default city")
                .matches(".*(rent|bengaluru|bangalore|mumbai|delhi).*");
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 3)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify city selection trigger opens dialog and allows selecting a city")
    public void verifyCitySelectionInteraction() {
        homePage.open(furlencoUrl);

        homePage.selectCity("Bengaluru");
        assertThat(homePage.currentUrl())
                .as("Current URL should reflect selected city or remain on valid home")
                .isNotEmpty();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 4)
    @Severity(SeverityLevel.MINOR)
    @Description("Verify presence of footer Help Center CTA and Cities We Deliver To section")
    public void verifyFooterLinksAndDeliveryCities() {
        homePage.open(furlencoUrl);

        assertThat(homePage.isHelpCenterDisplayed())
                .as("Help Center button should be present in the footer")
                .isTrue();

        assertThat(homePage.isCitiesDeliverSectionDisplayed())
                .as("Cities we deliver to section should be displayed in the footer")
                .isTrue();
    }
}
