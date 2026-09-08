package com.tests.base;

import com.framework.config.ConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common ancestor for BaseWebTest and BaseApiTest — holds anything shared by both
 * (currently just config access + logger), so a hybrid test could extend a future
 * BaseHybridTest that combines both without duplicating this bit.
 */
public abstract class BaseTest {

    protected static final Logger LOGGER = LogManager.getLogger(BaseTest.class);
    protected final ConfigManager config = ConfigManager.getInstance();
}
