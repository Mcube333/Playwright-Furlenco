package com.tests.ai.diagnosis;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.testng.IClass;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

/**
 * Phase 7 Step 6: JDK {@link Proxy}-backed test double for TestNG's {@link ITestResult} (plus the
 * small {@link ITestNGMethod}/{@link IClass} objects it returns), following the same no-Mockito,
 * proxy-based convention used by the Phase 6 test doubles. Only the handful of methods
 * {@link com.framework.ai.diagnosis.FailureDiagnosisHelper} actually reads are configurable
 * (method name, class name, throwable, start/end millis); any other call throws
 * {@link UnsupportedOperationException} by design.
 */
final class FailureDiagnosisTestResultTestDouble {

    private FailureDiagnosisTestResultTestDouble() {
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private String methodName = "sampleTest";
        private String className = "com.tests.sample.SampleTest";
        private Throwable throwable;
        private long startMillis;
        private long endMillis;
        private boolean methodThrows;
        private boolean testClassThrows;
        private final List<String> invokedMethods = new ArrayList<>();

        /** Exposes every ITestResult method actually invoked on the built proxy, in call order. */
        List<String> invokedMethods() {
            return Collections.unmodifiableList(invokedMethods);
        }

        Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        Builder className(String className) {
            this.className = className;
            return this;
        }

        Builder throwable(Throwable throwable) {
            this.throwable = throwable;
            return this;
        }

        Builder startMillis(long startMillis) {
            this.startMillis = startMillis;
            return this;
        }

        Builder endMillis(long endMillis) {
            this.endMillis = endMillis;
            return this;
        }

        /** Makes getMethod() itself throw, to exercise the helper's defensive fallback. */
        Builder methodThrows() {
            this.methodThrows = true;
            return this;
        }

        /** Makes getTestClass() itself throw, to exercise the helper's defensive fallback. */
        Builder testClassThrows() {
            this.testClassThrows = true;
            return this;
        }

        ITestResult build() {
            ITestNGMethod method = methodThrows ? null : fakeMethod(methodName);
            IClass testClass = testClassThrows ? null : fakeClass(className);

            InvocationHandler handler = (proxy, m, args) -> {
                invokedMethods.add(m.getName());
                switch (m.getName()) {
                    case "getMethod":
                        if (methodThrows) {
                            throw new RuntimeException("Simulated getMethod() failure");
                        }
                        return method;
                    case "getTestClass":
                        if (testClassThrows) {
                            throw new RuntimeException("Simulated getTestClass() failure");
                        }
                        return testClass;
                    case "getThrowable":
                        return throwable;
                    case "getStartMillis":
                        return startMillis;
                    case "getEndMillis":
                        return endMillis;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "FailureDiagnosisTestResultTestDouble";
                    default:
                        throw new UnsupportedOperationException(
                                "FailureDiagnosisTestResultTestDouble does not support ITestResult." + m.getName() + "()");
                }
            };
            return (ITestResult) Proxy.newProxyInstance(
                    ITestResult.class.getClassLoader(), new Class<?>[] {ITestResult.class}, handler);
        }

        private static ITestNGMethod fakeMethod(String methodName) {
            InvocationHandler handler = (proxy, m, args) -> {
                switch (m.getName()) {
                    case "getMethodName":
                        return methodName;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "FakeTestNGMethod";
                    default:
                        throw new UnsupportedOperationException(
                                "Fake ITestNGMethod does not support " + m.getName() + "()");
                }
            };
            return (ITestNGMethod) Proxy.newProxyInstance(
                    ITestNGMethod.class.getClassLoader(), new Class<?>[] {ITestNGMethod.class}, handler);
        }

        private static IClass fakeClass(String className) {
            InvocationHandler handler = (proxy, m, args) -> {
                switch (m.getName()) {
                    case "getName":
                        return className;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "FakeIClass";
                    default:
                        throw new UnsupportedOperationException(
                                "Fake IClass does not support " + m.getName() + "()");
                }
            };
            return (IClass) Proxy.newProxyInstance(
                    IClass.class.getClassLoader(), new Class<?>[] {IClass.class}, handler);
        }
    }
}
