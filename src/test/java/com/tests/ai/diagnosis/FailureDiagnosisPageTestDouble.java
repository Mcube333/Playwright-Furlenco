package com.tests.ai.diagnosis;

import com.microsoft.playwright.Page;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Phase 7 Step 6: JDK {@link Proxy}-backed test double for Playwright's {@link Page} interface,
 * following the same no-Mockito convention established by the Phase 6
 * {@code com.tests.ai.locatoradvisor.runtime.RuntimePageTestDouble}. A separate double is used here
 * (rather than extending the Phase 6 one) because {@link FailureDiagnosisHelper} additionally needs
 * {@code title()} and {@code content()} behavior, which the Phase 6 double intentionally does not
 * support — keeping the two doubles independent avoids touching Phase 6 test code for a Phase 7
 * concern.
 *
 * Only {@code isClosed()}, {@code url()}, {@code title()}, and {@code content()} are configurable;
 * any other method call throws {@link UnsupportedOperationException} by design.
 */
final class FailureDiagnosisPageTestDouble {

    private FailureDiagnosisPageTestDouble() {
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private boolean closed;
        private String url = "";
        private String title = "";
        private String content = "<html></html>";
        private Supplier<RuntimeException> isClosedThrows;
        private Supplier<RuntimeException> urlThrows;
        private Supplier<RuntimeException> titleThrows;
        private Supplier<RuntimeException> contentThrows;
        private final List<String> invokedMethods = new ArrayList<>();

        /** Exposes every Page method actually invoked on the built proxy, in call order. */
        List<String> invokedMethods() {
            return Collections.unmodifiableList(invokedMethods);
        }

        Builder closed(boolean closed) {
            this.closed = closed;
            return this;
        }

        Builder url(String url) {
            this.url = url;
            return this;
        }

        Builder title(String title) {
            this.title = title;
            return this;
        }

        Builder content(String content) {
            this.content = content;
            return this;
        }

        Builder isClosedThrows(RuntimeException exception) {
            this.isClosedThrows = () -> exception;
            return this;
        }

        Builder urlThrows(RuntimeException exception) {
            this.urlThrows = () -> exception;
            return this;
        }

        Builder titleThrows(RuntimeException exception) {
            this.titleThrows = () -> exception;
            return this;
        }

        Builder contentThrows(RuntimeException exception) {
            this.contentThrows = () -> exception;
            return this;
        }

        Page build() {
            InvocationHandler handler = (proxy, method, args) -> {
                invokedMethods.add(method.getName());
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
                    case "title":
                        if (titleThrows != null) {
                            throw titleThrows.get();
                        }
                        return title;
                    case "content":
                        if (contentThrows != null) {
                            throw contentThrows.get();
                        }
                        return content;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "FailureDiagnosisPageTestDouble";
                    default:
                        throw new UnsupportedOperationException(
                                "FailureDiagnosisPageTestDouble does not support Page." + method.getName()
                                        + "() — only isClosed(), url(), title(), and content() are needed here.");
                }
            };
            return (Page) Proxy.newProxyInstance(
                    Page.class.getClassLoader(), new Class<?>[] {Page.class}, handler);
        }
    }
}
