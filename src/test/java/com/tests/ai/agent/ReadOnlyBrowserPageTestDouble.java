package com.tests.ai.agent;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Phase 8 Step 4: JDK {@link Proxy}-backed test double for Playwright's {@link Page}, combining
 * exactly the read-only surface {@link com.framework.ai.agent.ReadOnlyBrowserTool} needs
 * ({@code isClosed/url/title/content/locator}) — following the same no-Mockito convention as the
 * existing Phase 6 ({@code RuntimePageTestDouble}) and Phase 7 Step 6/7
 * ({@code FailureDiagnosisPageTestDouble}) doubles. A separate double is used here rather than
 * reusing either of those so that neither Phase 6 nor Phase 7 test infrastructure is modified for
 * a Phase 8 concern.
 *
 * Records every invoked method name (in order) so tests can assert exactly which Playwright calls
 * a given {@code ReadOnlyBrowserTool} operation actually made — the empirical proof that no
 * mutation call (click/fill/navigate/etc.) is ever attempted, complementing the static
 * import/source-scan boundary tests.
 */
final class ReadOnlyBrowserPageTestDouble {

    private ReadOnlyBrowserPageTestDouble() {
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private boolean closed;
        private String url = "";
        private String title = "";
        private String content = "<html></html>";
        private Locator locatorResult;
        private Supplier<RuntimeException> isClosedThrows;
        private Supplier<RuntimeException> urlThrows;
        private Supplier<RuntimeException> titleThrows;
        private Supplier<RuntimeException> contentThrows;
        private Supplier<RuntimeException> locatorThrows;
        private final List<String> invokedMethods = new ArrayList<>();

        List<String> invokedMethods() {
            return invokedMethods;
        }

        Builder closed(boolean closed) { this.closed = closed; return this; }
        Builder url(String url) { this.url = url; return this; }
        Builder title(String title) { this.title = title; return this; }
        Builder content(String content) { this.content = content; return this; }
        Builder locatorResult(Locator locatorResult) { this.locatorResult = locatorResult; return this; }
        Builder isClosedThrows(RuntimeException e) { this.isClosedThrows = () -> e; return this; }
        Builder urlThrows(RuntimeException e) { this.urlThrows = () -> e; return this; }
        Builder titleThrows(RuntimeException e) { this.titleThrows = () -> e; return this; }
        Builder contentThrows(RuntimeException e) { this.contentThrows = () -> e; return this; }
        Builder locatorThrows(RuntimeException e) { this.locatorThrows = () -> e; return this; }

        Page build() {
            InvocationHandler handler = (proxy, method, args) -> {
                invokedMethods.add(method.getName());
                switch (method.getName()) {
                    case "isClosed":
                        if (isClosedThrows != null) throw isClosedThrows.get();
                        return closed;
                    case "url":
                        if (urlThrows != null) throw urlThrows.get();
                        return url;
                    case "title":
                        if (titleThrows != null) throw titleThrows.get();
                        return title;
                    case "content":
                        if (contentThrows != null) throw contentThrows.get();
                        return content;
                    case "locator":
                        if (locatorThrows != null) throw locatorThrows.get();
                        if (locatorResult == null) {
                            throw new UnsupportedOperationException("No locator(...) result configured");
                        }
                        return locatorResult;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "ReadOnlyBrowserPageTestDouble";
                    default:
                        throw new UnsupportedOperationException(
                                "ReadOnlyBrowserPageTestDouble does not support Page." + method.getName()
                                        + "() — only isClosed(), url(), title(), content(), and locator() are needed here.");
                }
            };
            return (Page) Proxy.newProxyInstance(Page.class.getClassLoader(), new Class<?>[] {Page.class}, handler);
        }
    }
}
