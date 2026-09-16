package com.framework.driver;

import com.framework.config.ConfigManager;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the Appium {@link AppiumDriver} lifecycle per test thread, mirroring
 * {@link PlaywrightManager}'s ThreadLocal pattern so the same parallel-execution safety applies to
 * native mobile tests.
 * <p>
 * This class is scaffolding: it starts a session against whatever Appium server + capabilities are
 * configured (see {@code appium.*} keys in the environment properties files), but every one of
 * those keys defaults to a placeholder. Before running a real mobile test, fill in:
 * <ul>
 *   <li>{@code appium.server.url} — the running Appium server (local or a device farm/BrowserStack/
 *       Sauce Labs-style grid)</li>
 *   <li>{@code appium.platform} — {@code android} or {@code ios}</li>
 *   <li>{@code appium.device.name} / {@code appium.platform.version}</li>
 *   <li>{@code appium.app.path} (a local/remote .apk or .ipa) OR
 *       {@code appium.app.package} + {@code appium.app.activity} (Android, app already installed)
 *       OR {@code appium.bundle.id} (iOS, app already installed)</li>
 * </ul>
 * None of this was verified against a real device/emulator or the actual Furlenco app — there was
 * no APK/IPA, device, or Appium Inspector session available to build real locators against.
 */
public final class AppiumManager {

    private static final Logger LOGGER = LogManager.getLogger(AppiumManager.class);

    private static final ThreadLocal<AppiumDriver> DRIVER = new ThreadLocal<>();

    private AppiumManager() {
    }

    public static void initDriver() {
        ConfigManager config = ConfigManager.getInstance();
        String platform = config.get("appium.platform", "android").toLowerCase();
        URL serverUrl = toUrl(config.get("appium.server.url", "http://127.0.0.1:4723"));

        AppiumDriver driver = "ios".equals(platform)
                ? new IOSDriver(serverUrl, buildIosOptions(config))
                : new AndroidDriver(serverUrl, buildAndroidOptions(config));

        driver.manage().timeouts().implicitlyWait(java.time.Duration.ZERO);
        DRIVER.set(driver);

        LOGGER.info("Initialized Appium {} session for thread {}", platform, Thread.currentThread().getId());
    }

    private static UiAutomator2Options buildAndroidOptions(ConfigManager config) {
        UiAutomator2Options options = new UiAutomator2Options()
                .setDeviceName(config.get("appium.device.name", "Android Emulator"))
                .setPlatformVersion(config.get("appium.platform.version", ""))
                .setAutomationName("UiAutomator2")
                .setNoReset(config.getBoolean("appium.no.reset", false))
                .setNewCommandTimeout(java.time.Duration.ofSeconds(config.getInt("appium.command.timeout.seconds", 60)));

        String appPath = config.get("appium.app.path", "");
        if (!appPath.isBlank()) {
            options.setApp(appPath);
        } else {
            String appPackage = config.get("appium.app.package", "");
            String appActivity = config.get("appium.app.activity", "");
            if (!appPackage.isBlank()) {
                options.setAppPackage(appPackage);
            }
            if (!appActivity.isBlank()) {
                options.setAppActivity(appActivity);
            }
        }
        return options;
    }

    private static XCUITestOptions buildIosOptions(ConfigManager config) {
        XCUITestOptions options = new XCUITestOptions()
                .setDeviceName(config.get("appium.device.name", "iPhone Simulator"))
                .setPlatformVersion(config.get("appium.platform.version", ""))
                .setAutomationName("XCUITest")
                .setNoReset(config.getBoolean("appium.no.reset", false))
                .setNewCommandTimeout(java.time.Duration.ofSeconds(config.getInt("appium.command.timeout.seconds", 60)));

        String appPath = config.get("appium.app.path", "");
        if (!appPath.isBlank()) {
            options.setApp(appPath);
        }
        String bundleId = config.get("appium.bundle.id", "");
        if (!bundleId.isBlank()) {
            options.setBundleId(bundleId);
        }
        return options;
    }

    private static URL toUrl(String url) {
        try {
            return URI.create(url).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid appium.server.url: " + url, e);
        }
    }

    public static AppiumDriver getDriver() {
        AppiumDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException(
                    "No AppiumDriver bound to this thread. Call AppiumManager.initDriver() in @BeforeMethod first.");
        }
        return driver;
    }

    public static byte[] captureScreenshot() {
        AppiumDriver driver = DRIVER.get();
        if (driver == null) {
            return new byte[0];
        }
        return driver.getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
    }

    public static void tearDown() {
        try {
            if (DRIVER.get() != null) {
                DRIVER.get().quit();
            }
        } finally {
            DRIVER.remove();
        }
    }
}
