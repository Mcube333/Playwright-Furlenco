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

/**
 * B2B is a separate site (business.furlenco.com) from the consumer app — per scope agreed with
 * the team, this only verifies it's reachable from the Global Home nav, not its own internal
 * flows.
 */
@Epic("Furlenco Web Automation")
@Feature("B2B Navigation")
public class FurlencoB2BSmokeTest extends BaseWebTest {

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    @Test(groups = {"smoke", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify clicking B2B in Global Home navigates to and loads business.furlenco.com")
    public void verifyB2BLinkLoads() {
        homePage.open(furlencoUrl);

        String resultingUrl = homePage.clickB2B();

        assertThat(resultingUrl)
                .as("B2B nav link should land on business.furlenco.com")
                .contains("business.furlenco.com");
    }
}
