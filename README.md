# Java + Playwright Web & API Automation Framework

Unified automation framework for **Web UI** and **API** testing using Playwright for Java, TestNG,
Allure reporting, and Log4j2 logging. Built to support parallel execution, environment-based config,
data-driven testing, JSON schema validation, and backend (DB) validation.

## Why Playwright for API tests instead of REST Assured?

This framework uses Playwright's native `APIRequestContext` for API testing rather than REST Assured.
The main reason: it lets Web and API tests **share session/auth state** (cookies, storage state) —
useful for hybrid flows like seeding an order via API and verifying it in the UI, or logging in via
UI and then hitting authenticated APIs directly. It also means one HTTP client stack to maintain,
not two.

---

## 1. Project Structure

```
automation-framework/
├── pom.xml
├── src/main/java/com/framework/       # Framework core (not test-specific)
│   ├── config/ConfigManager.java      # Env-based config loader (system prop > env var > .properties)
│   ├── driver/PlaywrightManager.java  # ThreadLocal Playwright/Browser/Context/Page lifecycle
│   ├── api/                           # APIClientManager, APIClient, APIResponse, ApiAssertions
│   ├── base/BasePage.java             # Common Page Object actions (click/fill/getText/waits)
│   ├── listeners/                     # TestListener, RetryAnalyzer, RetryTransformer
│   └── utils/                         # JsonUtils, WaitUtils, ExcelUtils, DateUtils, DBUtils
├── src/test/java/com/tests/
│   ├── base/                          # BaseTest, BaseWebTest, BaseApiTest
│   ├── pages/                         # Page Objects (LoginPage, InventoryPage, ...)
│   ├── models/                        # Request/response POJOs with builder pattern
│   ├── dataproviders/                 # TestNG @DataProvider (JSON/CSV backed)
│   ├── web/                           # Web UI test classes
│   └── api/                           # API test classes
├── src/test/resources/
│   ├── config/{qa,staging,prod}.properties
│   ├── testdata/                      # JSON / CSV test data
│   ├── schemas/                       # JSON schema files for response validation
│   ├── log4j2.xml
│   └── {smoke,regression,api}-suite.xml
└── .github/workflows/regression.yml
```

## 2. Prerequisites

- Java 17+
- Maven 3.9+
- (First run only) Playwright browser binaries — see below

## 3. First-time setup

```bash
git clone <your-repo-url>
cd automation-framework
mvn -B exec:java@install-browsers
```

This installs Chromium/Firefox/WebKit binaries Playwright needs. Re-run it whenever you bump the
`playwright.version` in `pom.xml`.

## 4. Running tests locally

Environment defaults to `qa` if `-Denv` is omitted.

```bash
# Smoke suite (fast, both Web + API)
mvn test -DsuiteXmlFile=src/test/resources/smoke-suite.xml -Denv=qa

# Full regression suite
mvn test -DsuiteXmlFile=src/test/resources/regression-suite.xml -Denv=qa

# API-only suite (no browser spin-up at all)
mvn test -DsuiteXmlFile=src/test/resources/api-suite.xml -Denv=qa

# Against staging
mvn test -DsuiteXmlFile=src/test/resources/regression-suite.xml -Denv=staging
```

Run a specific TestNG group only (e.g. just negative scenarios):

```bash
mvn test -DsuiteXmlFile=src/test/resources/regression-suite.xml -Dgroups=negative
```

Run a single test class:

```bash
mvn test -Dtest=SauceDemoLoginTest
```

## 5. Viewing the Allure report

```bash
mvn io.qameta.allure:allure-maven:serve
```

This builds and opens the HTML report in your browser. For CI, the report is generated headlessly
and published as a build artifact (see `.github/workflows/regression.yml`).

## 6. How CI works

`.github/workflows/regression.yml` runs on push/PR to `main`, and also supports manual dispatch
where you pick the target environment (qa/staging/prod) and suite file. Secrets (`API_TOKEN`,
`DB_*`) are injected via GitHub encoded secrets — never committed to `.properties` files.

The pipeline does **not** hard-fail on every regression failure — it publishes the Allure report
and logs regardless, then fails the job specifically if any test tagged `payment-critical` failed.
Non-critical failures stay visible in the report for triage without blocking every PR.

## 7. Adding a new test (for a new team member)

1. **Web test**: create a Page Object under `src/test/java/com/tests/pages/` extending `BasePage`,
   using only `click`/`fill`/`getText`/`waitForVisible` — no raw Playwright locators in test classes.
   Then create a test class under `src/test/java/com/tests/web/` extending `BaseWebTest`.
2. **API test**: create a test class under `src/test/java/com/tests/api/` extending `BaseApiTest`.
   Use `apiClient.get/post/put/patch/delete(...)` and assert via `ApiAssertions`.
3. Tag the test with the right TestNG `groups` (`smoke`, `regression`, `api`, `web`,
   `payment-critical`, `negative`) — this drives both suite inclusion and the CI fail-gate.
4. If the test needs structured request/response payloads, add a POJO under `models/` using the
   builder pattern (see `User.java`, `PaymentRequest.java`) instead of raw JSON strings.
5. If the test needs data-driven inputs, add JSON/CSV under `testdata/` and wire a
   `@DataProvider` method in `TestDataProvider.java`.
6. Never use `Thread.sleep`. Use Playwright's auto-waiting, `BasePage`'s explicit waits, or
   `WaitUtils.pollUntil(...)` for backend/async state polling.

## 8. Known gaps / next steps to extend this framework for your domain

- **Payment/Retry/Webhook module**: add a dedicated `com.tests.api.payment` package with tests for
  gateway timeout simulation, webhook delay tolerance (`WaitUtils.pollUntil` on payment status),
  duplicate-payment idempotency, and retry-after-success reconciliation via `DBUtils`.
- **Mobile (Android/iOS/MWeb)**: Playwright covers Web + Mobile Web (via device emulation in
  `BrowserContext`) but not native Android/iOS — that needs Appium as a separate module if native
  app coverage is required.
- **Contract/schema drift**: consider wiring `assertMatchesSchema` checks into the CI pipeline
  as a required gate for any endpoint your mobile apps depend on, so backend changes that break
  the contract fail fast in CI rather than surfacing as a mobile app crash.
