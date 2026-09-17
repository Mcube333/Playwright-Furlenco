package com.tests.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.framework.driver.PlaywrightManager;
import org.testng.annotations.Test;

/**
 * Phase 10 Step 6: a tiny, purely additive proof of the exact runtime fact this step's
 * architectural analysis depends on — that {@link PlaywrightManager} has no legitimate way to hand
 * out a {@code Page}/{@code BrowserContext} on a thread that never called
 * {@link PlaywrightManager#initBrowser()}, and that this failure is deterministic and closed
 * rather than silent.
 *
 * This class does not modify {@link PlaywrightManager} — it only calls its existing, unmodified
 * public static methods from a thread that has never initialized a browser (this test's own JVM
 * thread), which is exactly the situation any future runtime bridge would face if it tried to
 * "reach across" from an approval-processing context (which owns no thread-bound Page) into
 * {@link PlaywrightManager}'s ThreadLocal state.
 */
public class RuntimeBridgeReadinessBoundaryTest {

    @Test
    public void testGetPageFailsClosedOnAThreadWithNoBoundBrowser() {
        // No PlaywrightManager.initBrowser() was ever called on this thread — exactly the situation
        // an approval-processing call site is in today, since approval happens explicitly, and
        // typically asynchronously, outside of any @BeforeMethod/@AfterMethod-scoped browser
        // session.
        assertThatThrownBy(PlaywrightManager::getPage).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testGetContextReturnsNullRatherThanThrowingOnAnUnboundThread() {
        // Documented asymmetry (not a defect, not modified here): getPage() throws, but
        // getContext() simply returns null for the same unbound-thread condition. Any future
        // runtime bridge must independently null-check getContext()'s result — PlaywrightManager
        // itself does not enforce that for the caller.
        assertThat(PlaywrightManager.getContext()).isNull();
    }

    @Test
    public void testCaptureScreenshotReturnsEmptyArrayRatherThanThrowingOnAnUnboundThread() {
        // A third, distinct fail-closed shape for the same "no bound Page" condition — proving
        // PlaywrightManager's existing unbound-thread behavior is deterministic across all three
        // accessors, even though the three shapes (throw / null / empty array) differ from each
        // other. A future bridge must handle all three, not assume one.
        assertThat(PlaywrightManager.captureScreenshot()).isEmpty();
    }
}
