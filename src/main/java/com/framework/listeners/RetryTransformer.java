package com.framework.listeners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

/**
 * Registered in testng.xml under <listeners>. Attaches RetryAnalyzer to every @Test method
 * at the suite level so retry logic is consistent across the whole framework and can't be
 * accidentally omitted on a new test class.
 */
public class RetryTransformer implements IAnnotationTransformer {

    @Override
    public void transform(ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
        annotation.setRetryAnalyzer(RetryAnalyzer.class);
    }
}
