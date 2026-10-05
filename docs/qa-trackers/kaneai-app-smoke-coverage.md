# KaneAI app smoke run (Test Summary_20261005_130438) — coverage vs. framework

Source: LambdaTest/KaneAI export, 128 runs / ~45 distinct scenarios, **Android app (Appium)**, all passed.
The framework's mobile suite is disabled scaffolding (no verified locators), so scenarios are mapped to
their **web equivalents** where the web app supports them.

| App scenario | Status | Framework test |
|---|---|---|
| PLP Listing / Category Navigation / Navigation to Rent HP, GHP | Already covered | `FurlencoPlpNavigationTest`, `FurlencoPdpNavigationTest` |
| Add to cart / Add First Festive Deal to Cart | Already covered | `FurlencoCheckoutFlowTest`, `FurlencoCartFlowTest` |
| Update cart / Remove item from cart | Already covered | `FurlencoCartQuantityAndPersistenceTest` |
| Apply Offer / Inline message for invalid offers | Already covered | `FurlencoCartCouponTest` |
| Add / Remove VAS | Already covered | `FurlencoCartVasTest` |
| Checkout functionality, Payment completion | Already covered | `FurlencoCheckoutFlowTest`, `FurlencoSuccessfulOrderTest` |
| New sign up, User login with OTP, Access profile and login | Already covered | `FurlencoNewUserSignupTest`, `FurlencoLoginTest` |
| DIY Order Cancel | Already covered | `FurlencoCancelOrderTest` |
| Invalid Pincode, Non-serviceable Pincode (City Selector) | **Added** | `FurlencoCitySelectorTest` |
| Select Other City, Other Cities pincode, City Pincode Visibility | **Added** | `FurlencoCitySelectorTest` |
| Delivery availability by pincode (location accepted) | **Added** (location level only) | `FurlencoCitySelectorTest` |
| Login and View My Orders, Order Details Consistency | **Added** | `FurlencoMyOrdersTest` |
| PDP layout | **Added** | `FurlencoPdpLayoutTest` |
| Existing Address, Add New Address (entry point) | **Added** (partial) | `FurlencoCheckoutAddressTest` |

## Still open (not automated — need live inspection first)

| App scenario | Blocker |
|---|---|
| Add New Address form, Search address by area / pincode, Invalid address | Address form never inspected; no verified locators. Needs a logged-in session. |
| Address change from order summary | Same — no order-summary address-change locators. |
| Custom offer code on cart summary | Coupon input only verified on the cart drawer, not order summary. |
| Replace similar items in cart | Feature UI not inspected. |
| Mandate / Autopay orders (new, upsell, new user) | Needs Razorpay mandate test flow; not covered by `FurlencoSuccessfulOrderTest`. |
| DIY return without Pay & X | Return flow UI not inspected (API side exists in `FurlencoReturnsApiTest`). |
| Out of stock for selected city and category | Data-dependent; needs a known OOS fixture. |
| Mobile (Appium) versions of all of the above | `FurlencoMobileNavigationTest` is disabled scaffolding; needs APK + device + verified locators. |
| Non-Furlenco entries in the export (Google Search, DB wait, Shipment/Token data) | Not Furlenco flows — ignored. |
