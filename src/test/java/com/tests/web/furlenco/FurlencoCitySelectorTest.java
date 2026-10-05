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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * City / pincode selector ("Select Delivery Location" drawer). Web equivalents of the app smoke
 * scenarios "Invalid Pincode (City Selector)", "Non serviceable Pincode (City Selector)",
 * "Select Other City", "Verify City Pincode Visibility" and "Verify Delivery Availability by
 * Pincode". Needs no login. Messages verified live: "Invalid Pincode Entered" (malformed input),
 * "Pincode is not serviceable" (well-formed but unserviced); selecting a city updates the header
 * to "&lt;City&gt; &lt;pincode&gt;" and moves the URL to {@code /&lt;city&gt;}.
 */
@Epic("Furlenco Web Automation")
@Feature("City and Pincode Selector")
public class FurlencoCitySelectorTest extends BaseWebTest {

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    @DataProvider(name = "invalidPincodes")
    public Object[][] invalidPincodes() {
        return new Object[][] {{"123"}, {"000000"}};
    }

    @Test(dataProvider = "invalidPincodes", groups = {"regression", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify a malformed pincode shows 'Invalid Pincode Entered' and keeps the previous location")
    public void verifyInvalidPincodeShowsError(String pincode) {
        homePage.open(furlencoUrl);
        homePage.openCityModal();
        String previousPincode = homePage.getCurrentlySelectedPincode();

        homePage.enterPincode(pincode);

        assertThat(homePage.getPincodeErrorMessage())
                .as("Inline error for malformed pincode " + pincode)
                .containsIgnoringCase("Invalid Pincode");
        assertThat(homePage.getCurrentlySelectedPincode())
                .as("Selected pincode must be unchanged after an invalid entry")
                .isEqualTo(previousPincode);
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify a well-formed but non-serviceable pincode shows 'Pincode is not serviceable'")
    public void verifyNonServiceablePincodeShowsError() {
        homePage.open(furlencoUrl);
        homePage.openCityModal();
        String previousPincode = homePage.getCurrentlySelectedPincode();

        homePage.enterPincode("999999");

        assertThat(homePage.getPincodeErrorMessage())
                .as("Inline error for non-serviceable pincode")
                .containsIgnoringCase("not serviceable");
        assertThat(homePage.getCurrentlySelectedPincode())
                .as("Selected pincode must be unchanged after a non-serviceable entry")
                .isEqualTo(previousPincode);
    }

    @Test(groups = {"smoke", "web", "furlenco"}, priority = 3)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify a serviceable pincode is accepted, the drawer closes and the header shows the pincode")
    public void verifyServiceablePincodeUpdatesLocation() {
        homePage.open(furlencoUrl);

        homePage.enterPincode("560001");

        assertThat(homePage.isLocationDrawerOpen()).as("Drawer should close after a valid pincode").isFalse();
        assertThat(homePage.getHeaderLocationText())
                .as("Header should show the newly selected pincode (city + pincode visibility)")
                .contains("560001");
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 4)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify selecting another city updates the header location and the URL")
    public void verifySelectOtherCityUpdatesLocation() {
        homePage.open(furlencoUrl);

        homePage.selectCity("Mumbai");

        assertThat(homePage.getHeaderLocationText())
                .as("Header should show the selected city")
                .containsIgnoringCase("Mumbai");
        assertThat(homePage.currentUrl())
                .as("URL should move to the selected city's page")
                .containsIgnoringCase("mumbai");
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 5)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify a city from the 'Other Cities' list can be selected")
    public void verifySelectCityFromOtherCitiesList() {
        homePage.open(furlencoUrl);

        homePage.selectCity("Kolkata");

        assertThat(homePage.getHeaderLocationText())
                .as("Header should show the selected 'Other Cities' entry")
                .containsIgnoringCase("Kolkata");
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 6)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify the selected location persists across a verticals navigation (Rent -> Buy)")
    public void verifySelectedLocationPersistsAcrossVerticals() {
        homePage.open(furlencoUrl);
        homePage.enterPincode("560001");

        homePage.clickBuyTab();

        assertThat(homePage.getHeaderLocationText())
                .as("Location should still be the chosen pincode after switching vertical")
                .contains("560001");
    }
}
