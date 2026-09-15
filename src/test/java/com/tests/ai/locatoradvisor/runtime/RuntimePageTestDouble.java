package com.tests.ai.locatoradvisor.runtime;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * JDK {@link Proxy}-backed test double for Playwright's {@link Page} interface.
 *
 * Playwright 1.47.0's {@code Page} interface has 168 abstract methods (confirmed via {@code javap}
 * during the Phase 6 feasibility spike), making a literal {@code class FakePage implements Page}
 * impractical. Since {@code Page} is a plain interface, a dynamic proxy needs no dependency beyond
 * the JDK (no Mockito) and only needs behavior for the handful of methods actually exercised by
 * {@link com.framework.ai.locatoradvisor.runtime.RuntimeLocatorValidator}: {@code isClosed()},
 * {@code url()}, and {@code locator(String)}. Any other method call throws
 * {@link UnsupportedOperationException} — that is intentional; it should never be hit by tests
 * that only exercise the documented runtime-validation call surface.
 */
public final class RuntimePageTestDouble {

    private RuntimePageTestDouble() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean closed;
        private String url = "";
        private Supplier<RuntimeException> isClosedThrows;
        private Supplier<RuntimeException> urlThrows;
        private Function<String, Locator> locatorFunction;
        private Supplier<RuntimeException> locatorThrows;

        public Builder closed(boolean closed) {
            this.closed = closed;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder isClosedThrows(RuntimeException exception) {
            this.isClosedThrows = () -> exception;
            return this;
        }

        public Builder urlThrows(RuntimeException exception) {
            this.urlThrows = () -> exception;
            return this;
        }

        /** The fake Locator to return for any selector passed to page.locator(...). */
        public Builder locator(Locator fakeLocator) {
            this.locatorFunction = selector -> fakeLocator;
            return this;
        }

        public Builder locatorThrows(RuntimeException exception) {
            this.locatorThrows = () -> exception;
            return this;
        }

        public Page build() {
            InvocationHandler handler = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "isClosed":
                        if (isClosedThrows != null) {
                            throw isClosedThrows.get();
                        }
                        return closed;
                    case "url":
                        if (urlThrows != null) {
                            throw urlThrows.get();
                        }
                        return url;
                    case "locator":
                        if (locatorThrows != null) {
                            throw locatorThrows.get();
                        }
                        if (locatorFunction == null) {
                            throw new UnsupportedOperationException(
                                    "No locator(...) behavior configured on RuntimePageTestDouble");
                        }
                        return locatorFunction.apply((String) args[0]);
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "RuntimePageTestDouble";
                    default:
                        throw new UnsupportedOperationException(
                                "RuntimePageTestDouble does not support Page." + method.getName()
                                        + "() — only isClosed(), url(), and locator(String) are needed for runtime validation.");
                }
            };
            return (Page) Proxy.newProxyInstance(
                    Page.class.getClassLoader(), new Class<?>[] {Page.class}, handler);
        }
    }
}
