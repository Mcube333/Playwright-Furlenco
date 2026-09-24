# Playwright-Furlenco

A Java-based test automation framework for reliable **web UI, API, and native mobile** coverage. The framework combines Playwright for browser and API automation, TestNG for suite orchestration, Allure for reporting, Log4j2 for diagnostics, Appium for native mobile readiness, and reusable utilities for data, schema, and database validation.

> **Repository status:** The native mobile/Appium module is scaffolded and covered by a disabled suite until verified Furlenco locators, an APK, and a connected device/emulator are available. Web and API automation are the currently executable paths.

## What this framework provides

- **Web UI automation** with Playwright for Java, Page Objects, auto-waiting, browser contexts, and parallel TestNG execution.
- **API automation** through Playwright `APIRequestContext`, allowing API and UI flows to share authentication/session state without maintaining a second HTTP client stack.
- **Native mobile foundation** through Appium Java Client for Android/iOS scenarios; this is separate from Playwright's mobile-web/device emulation.
- **Environment-based configuration** for `qa`, `staging`, `preprod`, and `prod`, with runtime system properties and environment variables taking precedence over checked-in defaults.
- **Data-driven testing** using JSON, CSV, and Excel test data sources.
- **Contract validation** with JSON Schema validation for API responses.
- **Database validation** using HikariCP and PostgreSQL connectivity.
- **Test diagnostics** with Log4j2, retry handling, screenshots/traces where configured, and Allure attachments.
- **AI-assisted QA analysis** that is additive, opt-in, sanitized, and fail-closed; recommendations are advisory and are not automatically applied.
- **CI execution** through GitHub Actions with selectable environment and suite inputs and downloadable Allure/log artifacts.

## Technology stack

| Area | Technology |
| --- | --- |
| Language | Java (Maven compiler release is currently 25) |
| Build | Maven Wrapper / Maven 3.9+ |
| Web and API | Playwright for Java 1.47.0 |
| Native mobile | Appium Java Client 9.3.0 with Selenium 4.25.0 |
| Test runner | TestNG 7.10.2 |
| Reporting | Allure 2.29.0 and Allure Maven plugin |
| Logging | Log4j2 2.24.1 |
| Serialization | Jackson 2.17.2 |
| Data | Apache Commons CSV 1.11.0 and Apache POI 5.3.0 |
| API contracts | NetworkNT JSON Schema Validator 1.5.1 |
| Database | HikariCP 6.0.0 and PostgreSQL JDBC 42.7.4 |
| Assertions | AssertJ 3.26.3 |

## Project layout

```text
.
├── pom.xml
├── mvnw / mvnw.cmd                         # Maven Wrapper
├── .github/workflows/regression.yml        # CI workflow
├── src/main/java/com/framework/
│   ├── api/                                # API client, responses, and assertions
│   ├── ai/                                 # Opt-in analysis, locator, and agent foundation
│   ├── base/                               # Shared page abstractions
│   ├── config/                             # Environment/configuration loading
│   ├── driver/                             # Playwright lifecycle and thread-local state
│   ├── listeners/                          # TestNG listeners and retry support
│   └── utils/                              # JSON, waits, dates, Excel, and DB helpers
├── src/test/java/com/tests/
│   ├── api/                                # API scenarios
│   ├── ai/                                 # AI safety and analysis tests
│   ├── base/                               # Base web/API test classes
│   ├── dataproviders/                      # TestNG data providers
│   ├── mobile/                             # Appium mobile scenarios
│   ├── models/                             # Request/response models
│   ├── pages/                              # Page Objects
│   └── web/                                # Web UI scenarios
└── src/test/resources/
    ├── config/                             # qa, staging, preprod, and prod properties
    ├── schemas/                            # JSON schemas
    ├── testdata/                           # JSON and CSV data
    ├── *-suite.xml                         # TestNG suites
    └── log4j2.xml                          # Logging configuration
```

## Prerequisites

- Java **25** (the Maven compiler is configured with `<release>25</release>`).
- Maven 3.9+ or the included Maven Wrapper.
- Playwright browser binaries.
- For native mobile tests only: Appium server, Android/iOS tooling, and a device or emulator with the application installed.
- For database-backed tests: PostgreSQL access and the required connection settings.

> **CI compatibility note:** `.github/workflows/regression.yml` currently provisions JDK 17, while `pom.xml` targets Java 25. Update the workflow to a Java 25 runner (or lower the Maven compiler release after confirming project compatibility) before relying on CI execution.

## Getting started

```bash
git clone https://github.com/Mcube333/Playwright-Furlenco.git
cd Playwright-Furlenco

# Install Playwright Chromium/Firefox/WebKit binaries and Linux dependencies
./mvnw -B exec:java@install-browsers

# Windows
mvnw.cmd -B exec:java@install-browsers
```

The browser installation command should be repeated when `playwright.version` changes.

## Configuration and secrets

The active environment defaults to `qa` and is selected with `-Denv`:

```bash
./mvnw test -Denv=staging -DsuiteXmlFile=src/test/resources/regression-suite.xml
```

Environment files are under `src/test/resources/config/`. Do not put credentials or tokens in these files. Supply secrets through environment variables or CI secrets, including values such as:

```text
API_TOKEN
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
```

The configuration loader is designed to support runtime overrides, so CI and local runs can use the same suite definitions without changing source files.

## Running tests

Maven defaults to `src/test/resources/regression-suite.xml` and `qa` when no suite or environment is supplied.

```bash
# Smoke: smoke-tagged web and API tests
./mvnw test -Denv=qa -DsuiteXmlFile=src/test/resources/smoke-suite.xml

# Full regression
./mvnw test -Denv=qa -DsuiteXmlFile=src/test/resources/regression-suite.xml

# API-only suite
./mvnw test -Denv=qa -DsuiteXmlFile=src/test/resources/api-suite.xml

# Select a TestNG group
./mvnw test -Dgroups=negative

# Run one test class
./mvnw test -Dtest=SauceDemoLoginTest
```

Available suite files also include the Furlenco-specific suites under `src/test/resources/` and `mobile-suite.xml`. The native mobile suite is intentionally not an executable mobile test run yet: its scenarios are disabled pending verified application/device prerequisites.

Use TestNG groups consistently, for example `smoke`, `regression`, `web`, `api`, `negative`, and `payment-critical`. Groups control suite selection and the CI payment-critical failure gate.

## Allure reports and logs

Serve a local interactive report after a test run:

```bash
./mvnw io.qameta.allure:allure-maven:serve
```

Or generate the static report without opening a browser:

```bash
./mvnw io.qameta.allure:allure-maven:report
```

Test output is written beneath `target/`; Log4j2 output is written to `logs/` when enabled by the logging configuration.

## CI workflow

`.github/workflows/regression.yml` runs on pushes and pull requests targeting `main`, and can also be started manually. Manual runs expose these inputs:

- `environment`: `qa`, `staging`, or `prod`.
- `suite`: `smoke-suite.xml`, `regression-suite.xml`, or `api-suite.xml`.

The workflow:

1. Checks out the repository and caches Maven dependencies.
2. Installs Playwright browsers.
3. Runs the selected TestNG suite.
4. Generates and uploads the Allure report.
5. Uploads test logs.
6. Blocks the job when a `payment-critical` test fails, while preserving reports for other failures.

Configure `API_TOKEN` and `DB_*` values as GitHub Actions secrets rather than committing them to the repository.

## Framework conventions

### Web tests

1. Add or update a Page Object under `src/test/java/com/tests/pages/`.
2. Extend `BasePage` and keep locator/action details in the Page Object.
3. Add the scenario under `src/test/java/com/tests/web/` and extend the appropriate base test.
4. Prefer Playwright auto-waiting and shared wait helpers; do not use `Thread.sleep`.

### API tests

1. Add the scenario under `src/test/java/com/tests/api/`.
2. Extend `BaseApiTest`.
3. Use the shared API client and `ApiAssertions`.
4. Use request/response models under `models/` for structured payloads.
5. Add JSON Schema assertions where an endpoint contract must be protected.

### Data and database validation

Put reusable JSON/CSV/Excel inputs in `src/test/resources/testdata/` and expose them through TestNG data providers. Use the shared polling utilities for asynchronous backend state and `DBUtils` for database checks; never hard-code credentials or rely on arbitrary sleeps.

## AI-assisted QA layer

The AI layer is **disabled by default** and is designed as a safety-conscious extension rather than a replacement for deterministic test evidence. It includes:

- Failure classification and root-cause suggestions through the configured `AiClient` provider.
- Offline and optional live locator candidate analysis.
- Failure diagnosis reports attached to Allure.
- An observe → reason → propose agent foundation.
- Human approval records for recommendations; approval does not execute or modify anything.
- Sensitive-data sanitization before provider calls and reports.

Typical feature flags remain off unless explicitly enabled:

```properties
ai.enabled=false
ai.failure.analysis.enabled=false
ai.locator.runtime.validation.enabled=false
ai.agent.execution.enabled=false
ai.agent.browser.mutation.enabled=false
ai.agent.max.actions=0
```

AI output is advisory. Deterministic DOM/runtime checks define evidence status, and no current configuration enables automatic browser mutation, source changes, or self-healing execution. See `src/test/java/com/tests/ai/` for safety, prompt-injection, and fail-closed coverage.

## Current limitations and extension points

- **Java/CI version alignment:** the project targets Java 25 but CI currently installs Java 17; align these before using the workflow as a release gate.
- **Native mobile execution:** Appium dependencies and suites are present, but verified locators, an APK/app build, and device/emulator access are still required.
- **Payment lifecycle coverage:** add gateway timeout, webhook delay, idempotency, retry, and database reconciliation scenarios under a dedicated payment package.
- **Contract governance:** make JSON Schema checks a required CI gate for endpoints consumed by mobile or other downstream clients.
- **Production safeguards:** use environment protection rules and carefully scoped secrets before enabling production suite execution.

## Contributing

Keep framework code in `src/main/java`, test-specific code in `src/test/java`, and test assets in `src/test/resources`. Run the smallest relevant suite locally, review the Allure report, avoid committed secrets, and format Java changes with the configured Spotless Maven plugin.

## License

No license file is currently present in the repository. Add an explicit license before distributing or reusing this framework outside the project.
