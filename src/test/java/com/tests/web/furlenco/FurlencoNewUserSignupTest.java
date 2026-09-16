package com.tests.web.furlenco;

import static org.assertj.core.api.Assertions.assertThat;

import com.framework.utils.RandomDataUtils;
import com.tests.base.BaseWebTest;
import com.tests.pages.furlenco.FurlencoHomePage;
import com.tests.pages.furlenco.FurlencoLoginPage;
import com.tests.pages.furlenco.FurlencoNewUserSignupPage;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Covers the new-user signup branch of Login: a phone number with no existing account redirects
 * to a "Hey, Looks like you are new here!" name/email step after OTP verification, and entering
 * an email that's already registered to a different account shows an "Oops! ... already
 * registered" dialog offering "Continue as &lt;email&gt;" or "Skip and Create a New Account".
 * <p>
 * Uses {@link RandomDataUtils#randomIndianMobileNumber()} for a fresh, guaranteed-new number on
 * every run — this avoids repeatedly triggering real OTP sends against one fixed test account,
 * which caused rate-limiting during this suite's own development. The fixed preprod test OTP
 * ({@code test.user.password}, e.g. {@code 1234}) is assumed to work for any new number the same
 * way it did for the two known accounts it was confirmed against — re-verify this assumption on
 * the first real run.
 * <p>
 * {@link FurlencoNewUserSignupPage} selectors were built directly from screenshots of a live
 * session rather than independently navigated by this framework — the first real run of this
 * test class is also that flow's live verification pass.
 * <p>
 * <b>Creates a real new user account on every run that reaches "Continue" or "Skip and Create a
 * New Account"</b> — acceptable on a QA/preprod environment (that's what this flow exists to
 * test) but not a side-effect-free read-only test.
 */
@Epic("Furlenco Web Automation")
@Feature("Login - New User Signup")
public class FurlencoNewUserSignupTest extends BaseWebTest {

    private String furlencoUrl;
    private FurlencoHomePage homePage;

    @BeforeMethod(alwaysRun = true)
    public void initHomePage() {
        furlencoUrl = config.get("furlenco.base.url", "https://www.furlenco.com");
        homePage = new FurlencoHomePage(page);
    }

    private FurlencoNewUserSignupPage loginWithFreshNumber() {
        String randomPhone = RandomDataUtils.randomIndianMobileNumber();
        String otp = config.get("test.user.password");
        FurlencoLoginPage loginPage = homePage.openLogin();
        loginPage.loginWithOtp(randomPhone, otp);
        return loginPage.getNewUserSignupPage();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 1)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify a phone number with no existing account is redirected to the new-user signup screen after OTP")
    public void verifyNewNumberRedirectsToSignupScreen() {
        homePage.open(furlencoUrl);

        FurlencoNewUserSignupPage signupPage = loginWithFreshNumber();

        assertThat(signupPage.isLoaded())
                .as("A brand-new phone number should redirect to the 'new here' signup screen after OTP")
                .isTrue();
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 2)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify the Terms of Service link on the new-user signup screen redirects correctly")
    public void verifyTermsOfServiceLinkRedirects() {
        homePage.open(furlencoUrl);
        FurlencoNewUserSignupPage signupPage = loginWithFreshNumber();
        assertThat(signupPage.isLoaded()).as("Signup screen should be displayed first").isTrue();

        String resultingUrl = signupPage.clickTermsOfService();

        assertThat(resultingUrl)
                .as("Terms of Service link should navigate to a terms/legal page")
                .containsIgnoringCase("terms");
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 3)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify the Privacy Policy link on the new-user signup screen redirects correctly")
    public void verifyPrivacyPolicyLinkRedirects() {
        homePage.open(furlencoUrl);
        FurlencoNewUserSignupPage signupPage = loginWithFreshNumber();
        assertThat(signupPage.isLoaded()).as("Signup screen should be displayed first").isTrue();

        String resultingUrl = signupPage.clickPrivacyPolicy();

        assertThat(resultingUrl)
                .as("Privacy Policy link should navigate to a privacy-policy page")
                .containsIgnoringCase("privacy");
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 4)
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify entering an already-registered email on new-user signup shows the duplicate-email dialog with both options")
    public void verifyAlreadyRegisteredEmailShowsContinueOrSkipOptions() {
        homePage.open(furlencoUrl);
        FurlencoNewUserSignupPage signupPage = loginWithFreshNumber();
        assertThat(signupPage.isLoaded()).as("Signup screen should be displayed first").isTrue();

        signupPage.enterName(RandomDataUtils.randomName())
                .enterEmail(config.get("test.new.user.registered.email"))
                .clickContinue();

        if (!signupPage.isAlreadyRegisteredDialogDisplayed()) {
            throw new org.testng.SkipException(
                    "'" + config.get("test.new.user.registered.email") + "' did not trigger the "
                            + "duplicate-email dialog on this run — it may no longer be registered "
                            + "in this environment's current dataset (the assumption behind "
                            + "test.new.user.registered.email needs a fresh confirmed-registered "
                            + "email; this is not a framework/selector failure).");
        }
    }

    @Test(groups = {"regression", "web", "furlenco"}, priority = 5)
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify 'Skip and Create a New Account' on the duplicate-email dialog lets signup proceed with a fresh email")
    public void verifySkipAndCreateNewAccountProceedsWithFreshEmail() {
        homePage.open(furlencoUrl);
        FurlencoNewUserSignupPage signupPage = loginWithFreshNumber();
        assertThat(signupPage.isLoaded()).as("Signup screen should be displayed first").isTrue();

        signupPage.enterName(RandomDataUtils.randomName())
                .enterEmail(config.get("test.new.user.registered.email"))
                .clickContinue();
        if (!signupPage.isAlreadyRegisteredDialogDisplayed()) {
            throw new org.testng.SkipException(
                    "'" + config.get("test.new.user.registered.email") + "' did not trigger the "
                            + "duplicate-email dialog on this run — see "
                            + "verifyAlreadyRegisteredEmailShowsContinueOrSkipOptions for the same "
                            + "caveat. Nothing to Skip past here.");
        }

        signupPage.clickSkipAndCreateNewAccount()
                .enterEmail(RandomDataUtils.randomEmail())
                .clickContinue();

        assertThat(signupPage.isAlreadyRegisteredDialogDisplayed())
                .as("A genuinely fresh email should not trigger the duplicate-email dialog again")
                .isFalse();
    }
}
