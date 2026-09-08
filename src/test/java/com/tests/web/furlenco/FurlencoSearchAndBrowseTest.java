package com.tests.web.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.tests.base.BaseWebTest;
import com.tests.pages.furlenco.FurlencoHomePage;
import com.tests.pages.furlenco.FurlencoSearchResultsPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Epic("Furlenco Web Automation")
@Feature("Product Search and Catalog")
public class FurlencoSearchAndBrowseTest extends BaseWebTest {

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    @Test(groups = {"smoke", "regression", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify product search displays relevant catalog results URL and parameters")
    public void searchProductAndVerifyResults() {
        homePage.open(furlencoUrl);

        FurlencoSearchResultsPage searchResultsPage = homePage.searchProduct("sofa");
        searchResultsPage.waitForResults();

        assertThat(searchResultsPage.getResultsUrl())
                .as("URL should indicate search query parameter")
                .containsIgnoringCase("sofa");

        assertThat(searchResultsPage.isLoaded())
                .as("Search results page should be loaded")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify category catalog browsing from BUY vertical")
    public void browseBuyVerticalAndVerifyCatalog() {
        homePage.open(furlencoUrl);
        homePage.clickBuyTab();

        assertThat(homePage.currentUrl())
                .as("Current URL should be the Buy furniture catalog")
                .contains("/buy");

        assertThat(homePage.getTitle())
                .as("Page title should reflect Furlenco")
                .containsIgnoringCase("Furlenco");
    }
}
