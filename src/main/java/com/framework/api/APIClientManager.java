package com.framework.api;

import com.framework.config.ConfigManager;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.Playwright;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages Playwright APIRequestContext per test thread — separate from PlaywrightManager
 * so pure API test classes never spin up a browser at all.
 *
 * NOTE: For hybrid Web+API flows (e.g., seed data via API, verify in UI), pull the storage
 * state from PlaywrightManager.getContext().storageState() and feed it into
 * newContext(new APIRequest.NewContextOptions().setStorageState(...)) so cookies/auth carry over.
 */
public final class APIClientManager {

    private static final Logger LOGGER = LogManager.getLogger(APIClientManager.class);

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<APIRequestContext> REQUEST_CONTEXT = new ThreadLocal<>();

    private APIClientManager() {
    }

    public static void init() {
        ConfigManager config = ConfigManager.getInstance();

        Playwright playwright = Playwright.create();
        PLAYWRIGHT.set(playwright);

        Map<String, String> defaultHeaders = new HashMap<>();
        defaultHeaders.put("Content-Type", "application/json");
        defaultHeaders.put("Accept", "application/json");
        String token = config.get("api.token", "");
        if (!token.isBlank()) {
            defaultHeaders.put("Authorization", "Bearer " + token);
        }

        APIRequestContext requestContext = playwright.request().newContext(
                new APIRequest.NewContextOptions()
                        .setBaseURL(config.apiBaseUrl())
                        .setExtraHTTPHeaders(defaultHeaders)
                        .setTimeout(config.defaultTimeoutMs()));
        REQUEST_CONTEXT.set(requestContext);

        LOGGER.info("Initialized APIRequestContext for base URL [{}] on thread {}",
                config.apiBaseUrl(), Thread.currentThread().getId());
    }

    public static APIRequestContext getContext() {
        APIRequestContext context = REQUEST_CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException(
                    "No APIRequestContext bound to this thread. Call APIClientManager.init() in @BeforeMethod first.");
        }
        return context;
    }

    public static void tearDown() {
        try {
            if (REQUEST_CONTEXT.get() != null) {
                REQUEST_CONTEXT.get().dispose();
            }
            if (PLAYWRIGHT.get() != null) {
                PLAYWRIGHT.get().close();
            }
        } finally {
            REQUEST_CONTEXT.remove();
            PLAYWRIGHT.remove();
        }
    }
}
