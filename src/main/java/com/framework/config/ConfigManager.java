package com.framework.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Thread-safe singleton config loader.
 * Resolves environment via -Denv=qa|staging|prod (defaults to qa).
 * Property resolution order for any key: JVM system property > OS environment variable > properties file.
 * This keeps secrets (tokens, passwords, DB creds) out of the repo — CI injects them as env vars,
 * local dev falls back to the properties file for non-sensitive values.
 */
public final class ConfigManager {

    private static final Logger LOGGER = LogManager.getLogger(ConfigManager.class);
    private static volatile ConfigManager instance;

    private final Properties properties = new Properties();
    private final String environment;

    private ConfigManager() {
        this.environment = System.getProperty("env", "qa").toLowerCase();
        loadProperties(environment);
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager();
                }
            }
        }
        return instance;
    }

    private void loadProperties(String env) {
        String fileName = "config/" + env + ".properties";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Config file not found on classpath: " + fileName
                                + ". Expected under src/test/resources/config/");
            }
            properties.load(is);
            LOGGER.info("Loaded configuration for environment [{}] from {}", env, fileName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config file: " + fileName, e);
        }
    }

    /**
     * Resolves a value: system property (-Dkey=) wins, then OS env var (upper snake case),
     * then the properties file, then the supplied default.
     */
    public String get(String key, String defaultValue) {
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp;
        }
        String envVar = System.getenv(toEnvVarName(key));
        if (envVar != null && !envVar.isBlank()) {
            return envVar;
        }
        return properties.getProperty(key, defaultValue);
    }

    public String get(String key) {
        String value = get(key, null);
        if (value == null) {
            throw new IllegalStateException("Missing required config key: " + key
                    + " (checked system property, env var, and " + environment + ".properties)");
        }
        return value;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key, null);
        return value == null ? defaultValue : Integer.parseInt(value.trim());
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key, null);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    private String toEnvVarName(String key) {
        return key.toUpperCase().replace('.', '_').replace('-', '_');
    }

    public String getEnvironment() {
        return environment;
    }

    // ---- Convenience accessors for commonly used keys ----

    public String baseUrl() {
        return get("web.base.url");
    }

    public String apiBaseUrl() {
        return get("api.base.url");
    }

    public String browser() {
        return get("browser", "chromium");
    }

    public boolean headless() {
        return getBoolean("headless", true);
    }

    public double defaultTimeoutMs() {
        return getInt("default.timeout.ms", 30000);
    }
}
