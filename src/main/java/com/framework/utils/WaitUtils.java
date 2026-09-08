package com.framework.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Backend-state polling helper — for cases Playwright's built-in auto-waiting doesn't cover,
 * e.g. "poll the payment status API until it flips to SUCCESS/FAILED, or timeout after 60s."
 * Use this instead of Thread.sleep for any async/eventual-consistency backend flow
 * (webhook processing, retry queues, reconciliation jobs).
 */
public final class WaitUtils {

    private static final Logger LOGGER = LogManager.getLogger(WaitUtils.class);

    private WaitUtils() {
    }

    /**
     * Polls the supplier until it returns true, or throws once the timeout elapses.
     *
     * @param description human-readable description for logging/failure messages
     * @param condition   supplier returning true when the desired state is reached
     * @param timeout     max time to wait
     * @param pollEvery   interval between polls
     */
    public static void pollUntil(String description, Supplier<Boolean> condition, Duration timeout, Duration pollEvery) {
        Instant deadline = Instant.now().plus(timeout);
        int attempt = 0;
        while (Instant.now().isBefore(deadline)) {
            attempt++;
            try {
                if (Boolean.TRUE.equals(condition.get())) {
                    LOGGER.info("Condition met: [{}] after {} attempt(s)", description, attempt);
                    return;
                }
            } catch (Exception e) {
                LOGGER.debug("Poll attempt {} for [{}] threw, retrying: {}", attempt, description, e.getMessage());
            }
            sleep(pollEvery);
        }
        throw new IllegalStateException(
                "Timed out after " + timeout.toSeconds() + "s waiting for condition: " + description);
    }

    /** Convenience overload: default 60s timeout, 2s poll interval — matches typical webhook/payment SLAs. */
    public static void pollUntil(String description, Supplier<Boolean> condition) {
        pollUntil(description, condition, Duration.ofSeconds(60), Duration.ofSeconds(2));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Polling interrupted", e);
        }
    }
}
