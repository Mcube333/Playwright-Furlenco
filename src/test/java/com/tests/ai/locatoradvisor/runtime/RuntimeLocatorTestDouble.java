package com.tests.ai.locatoradvisor.runtime;

import com.microsoft.playwright.Locator;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

/**
 * JDK {@link Proxy}-backed test double for Playwright's {@link Locator} interface.
 *
 * Mirrors {@link RuntimePageTestDouble}'s approach for the same reason: {@code Locator} has 76
 * abstract methods (confirmed via {@code javap}), so only the no-arg overloads of {@code count()},
 * {@code isVisible()}, and {@code isEnabled()} are configured — the only ones
 * {@link com.framework.ai.locatoradvisor.runtime.RuntimeLocatorValidator} calls. Any other method
 * (including the options-taking overloads, which are unused by the validator) throws
 * {@link UnsupportedOperationException}.
 */
public final class RuntimeLocatorTestDouble {

    private RuntimeLocatorTestDouble() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int count;
        private boolean visible;
        private boolean enabled = true;
        private Supplier<RuntimeException> countThrows;
        private Supplier<RuntimeException> isVisibleThrows;
        private Supplier<RuntimeException> isEnabledThrows;

        public Builder count(int count) {
            this.count = count;
            return this;
        }

        public Builder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder countThrows(RuntimeException exception) {
            this.countThrows = () -> exception;
            return this;
        }

        public Builder isVisibleThrows(RuntimeException exception) {
            this.isVisibleThrows = () -> exception;
            return this;
        }

        public Builder isEnabledThrows(RuntimeException exception) {
            this.isEnabledThrows = () -> exception;
            return this;
        }

        public Locator build() {
            InvocationHandler handler = (proxy, method, args) -> {
                boolean noArgs = args == null || args.length == 0;
                switch (method.getName()) {
                    case "count":
                        if (countThrows != null) {
                            throw countThrows.get();
                        }
                        return count;
                    case "isVisible":
                        if (!noArgs) {
                            throw new UnsupportedOperationException(
                                    "Locator.isVisible(options) is not supported by this test double");
                        }
                        if (isVisibleThrows != null) {
                            throw isVisibleThrows.get();
                        }
                        return visible;
                    case "isEnabled":
                        if (!noArgs) {
                            throw new UnsupportedOperationException(
                                    "Locator.isEnabled(options) is not supported by this test double");
                        }
                        if (isEnabledThrows != null) {
                            throw isEnabledThrows.get();
                        }
                        return enabled;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "RuntimeLocatorTestDouble";
                    default:
                        throw new UnsupportedOperationException(
                                "RuntimeLocatorTestDouble does not support Locator." + method.getName()
                                        + "() — only count(), isVisible(), and isEnabled() are needed for runtime validation.");
                }
            };
            return (Locator) Proxy.newProxyInstance(
                    Locator.class.getClassLoader(), new Class<?>[] {Locator.class}, handler);
        }
    }
}
