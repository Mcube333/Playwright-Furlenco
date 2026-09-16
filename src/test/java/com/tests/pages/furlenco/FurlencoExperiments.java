package com.tests.pages.furlenco;

import com.framework.config.ConfigManager;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.Cookie;
import java.net.URI;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Pins Furlenco's live A/B experiment cookie ({@code _experiments}) to fixed, known variants
 * before navigation, so automated runs get a deterministic DOM instead of a random layout per
 * visit.
 * <p>
 * <b>Why this exists:</b> verified live on 2026-09-16 against {@code pp-janus.furlenco.com} —
 * {@code experiment_new_desktop=A} serves a redesigned header/homepage with no "Account menu"
 * control at all, while {@code =B} serves the classic header this framework's Furlenco page
 * objects (locators for RENT/BUY/UNLMTD nav, Account menu, Cart, etc.) are built against. Without
 * pinning, the same URL flips between incompatible layouts across runs — this was the root cause
 * of intermittent "element not found" failures, not selector bugs or environment flakiness.
 * <p>
 * Controlled by {@code furlenco.pin.experiments} (default {@code false} — opt in per environment;
 * see {@code preprod.properties}) and {@code furlenco.experiment.new.desktop.variant} (default
 * {@code "B"}, the classic layout). The cookie must be set via {@link BrowserContext#addCookies}
 * <i>before</i> the first navigation — Furlenco's Next.js middleware reads it server-side to decide
 * which variant to render, so setting {@code document.cookie} after the page has already loaded is
 * too late.
 */
public final class FurlencoExperiments {

    private static final Logger LOGGER = LogManager.getLogger(FurlencoExperiments.class);

    // Full captured `_experiments` cookie shape from a live pp-janus.furlenco.com session, with
    // every other experiment pinned to its control/first-listed variant so only
    // experiment_new_desktop varies via config. Re-capture and update this template if Furlenco
    // adds/removes experiments — a stale but structurally-shaped cookie degrades to "ignored by the
    // app" rather than an error, so this won't fail loudly if it drifts.
    private static final String EXPERIMENTS_TEMPLATE = "["
            + "{\"name\":\"HandlingFee\",\"value\":\"Fee24\",\"moengageAttributeName\":\"HandlingFee\",\"moengageAttributeValue\":\"Fee24\",\"isActive\":true,\"otherVariants\":[\"Fee15\",\"Fee19\"]},"
            + "{\"name\":\"experiment_new_desktop\",\"value\":\"%1$s\",\"moengageAttributeName\":\"experiment_new_desktop\",\"moengageAttributeValue\":\"%1$s\",\"isActive\":true,\"otherVariants\":[\"A\",\"B\",\"C\"]},"
            + "{\"name\":\"experiment_desktop_media\",\"value\":\"VariantA\",\"moengageAttributeName\":\"experiment_desktop_media\",\"moengageAttributeValue\":\"VariantA\",\"isActive\":true,\"otherVariants\":[\"VariantB\"]},"
            + "{\"name\":\"CartOffer\",\"value\":\"Control\",\"moengageAttributeName\":\"CartOffer\",\"moengageAttributeValue\":\"Control\",\"isActive\":true,\"otherVariants\":[\"VariantA\"]},"
            + "{\"name\":\"experiment_pdc\",\"value\":\"VariantA\",\"moengageAttributeName\":\"experiment_pdc\",\"moengageAttributeValue\":\"VariantA\",\"isActive\":true,\"otherVariants\":[\"VariantB\"]},"
            + "{\"name\":\"UnlmtdNexa\",\"value\":\"VariantA\",\"moengageAttributeName\":\"UnlmtdNexa\",\"moengageAttributeValue\":\"VariantA\",\"isActive\":true,\"otherVariants\":[\"VariantB\"]},"
            + "{\"name\":\"LoginCitySelector\",\"value\":\"VariantA\",\"moengageAttributeName\":\"LoginCitySelector\",\"moengageAttributeValue\":\"VariantA\",\"isActive\":true,\"otherVariants\":[\"VariantB\"]},"
            + "{\"name\":\"AutopayIncentive\",\"value\":\"VariantA\",\"moengageAttributeName\":\"AutopayIncentive\",\"moengageAttributeValue\":\"VariantA\",\"isActive\":true,\"otherVariants\":[\"VariantB\"]},"
            + "{\"name\":\"CheckoutRevamp\",\"value\":\"VariantA\",\"moengageAttributeName\":\"CheckoutRevamp\",\"moengageAttributeValue\":\"VariantA\",\"isActive\":true,\"otherVariants\":[\"VariantB\"]}"
            + "]";

    private FurlencoExperiments() {
    }

    /**
     * Adds the pinned {@code _experiments} cookie to {@code context} for the host in {@code
     * targetUrl}, if {@code furlenco.pin.experiments=true} in the active environment config. Call
     * this before the first navigation in a test/session.
     */
    public static void pinIfEnabled(BrowserContext context, String targetUrl) {
        ConfigManager config = ConfigManager.getInstance();
        if (!config.getBoolean("furlenco.pin.experiments", false)) {
            return;
        }
        String variant = config.get("furlenco.experiment.new.desktop.variant", "B");
        String host = URI.create(targetUrl).getHost();
        if (host == null) {
            LOGGER.warn("Could not parse host from {}, skipping experiment cookie pin", targetUrl);
            return;
        }

        String cookieValue = String.format(EXPERIMENTS_TEMPLATE, variant);
        Cookie cookie = new Cookie("_experiments", cookieValue)
                .setDomain(host)
                .setPath("/")
                .setExpires(System.currentTimeMillis() / 1000.0 + 34_560_000);
        context.addCookies(List.of(cookie));
        LOGGER.info("Pinned Furlenco _experiments cookie for {} (experiment_new_desktop={})", host, variant);
    }
}
