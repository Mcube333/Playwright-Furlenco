package com.tests.pages.furlenco;

import com.framework.base.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Page Object for the Furlenco Login dialog: phone number + 4-digit OTP (not a password) —
 * {@code test.user.password} in the environment properties files holds the fixed test OTP, not a
 * real password.
 * <p>
 * <b>Verified against two live, materially different implementations</b> (2026-09-16), confirming
 * Furlenco's live A/B experiment framework (see {@link FurlencoExperiments}) changes not just
 * layout but the login dialog's own markup:
 * <ul>
 *   <li><b>stag.furlenco.com</b> (Radix): dialog is {@code [data-slot='dialog-content']}, OTP boxes
 *       are {@code input[maxlength='1']}, submit button text is "Verify".</li>
 *   <li><b>pp-janus.furlenco.com</b> with {@code experiment_new_desktop=B} (MUI): no dialog
 *       wrapper attribute at all, OTP boxes have no {@code maxlength} attribute, submit button text
 *       is "CONFIRM &amp; CONTINUE" for both the phone and OTP steps.</li>
 * </ul>
 * Every selector below is written to match both: OTP boxes are identified as {@code input[type=
 * 'text']} with no {@code placeholder} attribute (the one trait both variants share and the
 * "Mobile Number"/search inputs don't), and the submit button matches either "Continue" or
 * "Verify" text.
 * <p>
 * The dialog itself is reached by <b>hovering</b> the header's Account menu button (a Radix
 * {@code HoverCard} trigger, not a click target) and then clicking "Login" inside the hover card
 * that appears — see {@link FurlencoHomePage#openLogin()}.
 */
public class FurlencoLoginPage extends BasePage {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoLoginPage.class);

    private static final String PHONE_INPUT = "input[placeholder='Mobile Number']";
    // OTP boxes: input[type=text] with no placeholder attribute — true in both verified variants,
    // and excludes the phone field (placeholder="Mobile Number") and the header search box.
    private static final String OTP_DIGIT_INPUTS = "input[type='text']:not([placeholder])";
    private static final String CONTINUE_OR_VERIFY_BUTTON =
            "button:has-text('Continue'), button:has-text('Verify')";
    // Deliberately scoped to role='alert' only — a bare ".error" class matched unrelated elements
    // elsewhere on the page in live testing (false positive), so it was dropped rather than kept
    // "just in case."
    private static final String ERROR_MESSAGE = "[role='alert']:has-text(\"Invalid\"), [role='alert']:has-text(\"incorrect\")";

    public FurlencoLoginPage(Page page) {
        super(page);
    }

    @Step("Check if login dialog is open")
    public boolean isOpen() {
        return isVisibleAndPresent(PHONE_INPUT) || isOtpStepDisplayed();
    }

    private boolean isVisibleAndPresent(String selector) {
        Locator locator = page.locator(selector);
        return locator.count() > 0 && locator.first().isVisible();
    }

    @Step("Enter phone number: {phoneNumber}")
    public FurlencoLoginPage enterPhoneNumber(String phoneNumber) {
        LOGGER.info("Entering phone number on login form");
        Locator phoneField = page.locator(PHONE_INPUT).first();
        phoneField.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        phoneField.fill(phoneNumber);
        return this;
    }

    @Step("Click Continue after entering phone number")
    public FurlencoLoginPage clickContinue() {
        Locator continueBtn = page.locator(CONTINUE_OR_VERIFY_BUTTON).first();
        continueBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        continueBtn.click();
        page.locator(OTP_DIGIT_INPUTS).first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        return this;
    }

    @Step("Check if the OTP step is displayed")
    public boolean isOtpStepDisplayed() {
        Locator otpInputs = page.locator(OTP_DIGIT_INPUTS);
        return otpInputs.count() == 4 && otpInputs.first().isVisible();
    }

    @Step("Enter OTP and submit")
    public FurlencoLoginPage enterOtpAndSubmit(String otp) {
        if (otp.length() != 4) {
            throw new IllegalArgumentException("Expected a 4-digit OTP, got length " + otp.length());
        }
        LOGGER.info("Entering OTP on login form (value not logged)");
        Locator digitBoxes = page.locator(OTP_DIGIT_INPUTS);
        for (int i = 0; i < 4; i++) {
            digitBoxes.nth(i).fill(String.valueOf(otp.charAt(i)));
        }
        page.locator(CONTINUE_OR_VERIFY_BUTTON).first().click();
        // The submit button shows a loading spinner while the OTP-verification request is
        // in-flight (verified live) — wait for the OTP step itself to disappear rather than a
        // fixed sleep, since the request can take longer than a short guess-timeout.
        try {
            page.locator(OTP_DIGIT_INPUTS).first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(15000));
        } catch (Exception e) {
            LOGGER.warn("OTP step still visible after submit — login may have failed: {}", e.getMessage());
        }
        return this;
    }

    /**
     * Full login flow: phone number + OTP. Uses {@code test.user.username}/{@code test.user.password}
     * from the active environment's properties file (the latter holds the fixed test OTP, e.g.
     * {@code 1234}) — callers pull those via {@code ConfigManager} rather than hardcoding them.
     */
    @Step("Login with phone number and OTP")
    public void loginWithOtp(String phoneNumber, String otp) {
        enterPhoneNumber(phoneNumber);
        clickContinue();
        if (!isOtpStepDisplayed()) {
            throw new IllegalStateException(
                    "Expected the 4-digit OTP step after Continue but it wasn't displayed — the "
                            + "login flow may have changed. Re-verify this page object's selectors "
                            + "against a live session.");
        }
        enterOtpAndSubmit(otp);
    }

    @Step("Check if login succeeded")
    public boolean isLoggedIn() {
        page.waitForTimeout(500);
        return !isOpen() && !isNewUserSignupPromptDisplayed();
    }

    /**
     * A phone number with no existing account redirects to a "Hey, Looks like you are new here!"
     * name/email signup step instead of completing login directly — check this after
     * {@link #enterOtpAndSubmit(String)} before assuming {@link #isLoggedIn()} means an existing
     * account was authenticated.
     */
    @Step("Check if the OTP submission redirected to new-user signup instead of logging in")
    public boolean isNewUserSignupPromptDisplayed() {
        return new FurlencoNewUserSignupPage(page).isLoaded();
    }

    @Step("Get the new-user signup page after OTP submission")
    public FurlencoNewUserSignupPage getNewUserSignupPage() {
        return new FurlencoNewUserSignupPage(page);
    }

    @Step("Check if a login error is displayed")
    public boolean isErrorDisplayed() {
        Locator error = page.locator(ERROR_MESSAGE);
        return error.count() > 0 && error.first().isVisible();
    }
}
