package com.tests.base;

import com.framework.api.APIClient;
import com.framework.api.APIClientManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public abstract class BaseApiTest extends BaseTest {

    protected APIClient apiClient;

    @BeforeMethod(alwaysRun = true)
    public void setUpApiContext() {
        APIClientManager.init();
        apiClient = new APIClient();
        LOGGER.info("API request context started for thread {}", Thread.currentThread().getId());
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownApiContext() {
        APIClientManager.tearDown();
        LOGGER.info("API request context closed for thread {}", Thread.currentThread().getId());
    }
}
